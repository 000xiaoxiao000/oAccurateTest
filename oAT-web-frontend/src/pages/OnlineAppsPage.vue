<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Runtime</div>
        <h1>在线应用</h1>
      </div>
      <button class="action-button" type="button" @click="load">刷新</button>
    </div>

    <div v-if="loading" class="status-card">正在加载在线实例...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="runtime-overview">
        <div class="summary-card">
          <strong>{{ payload.total }}</strong>
          <span>当前在线实例数</span>
        </div>
        <div class="toolbar-card" aria-label="在线实例筛选">
          <input v-model.trim="keyword" class="text-input" type="search" placeholder="搜索应用、工程、IP、PID、路径" aria-label="搜索在线实例" />
          <span>{{ filteredSessions.length }} 个匹配实例</span>
        </div>
      </div>

      <div class="table-card">
        <table class="table">
          <thead>
            <tr>
              <th>系统 IP</th>
              <th>应用名称</th>
              <th>工程名称</th>
              <th>Agent 版本</th>
              <th>在线时长</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in paginatedSessions" :key="sessionKey(item)">
              <tr>
                <td>{{ item.addressIp || '-' }}</td>
                <td>{{ item.appName || '未定义' }}</td>
                <td>{{ item.projectSrcName || '-' }}</td>
                <td>{{ item.agentVersion || '-' }}</td>
                <td>{{ item.onlineTime || '-' }}</td>
                <td><button class="detail-button" type="button" @click="toggleDetail(item)">详情</button></td>
              </tr>
              <tr v-if="expandedKey === sessionKey(item)" class="detail-row">
                <td colspan="6">
                  <div class="detail-grid">
                    <div><span>部署路径</span><strong>{{ item.systemDir || '-' }}</strong></div>
                    <div><span>进程ID</span><strong>{{ item.pid || '-' }}</strong></div>
                    <div><span>JVM版本</span><strong>{{ item.jvmVersion || '-' }}</strong></div>
                    <div class="wide"><span>JVM启动参数</span><strong>{{ item.jvmOption || '-' }}</strong></div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <div v-if="!filteredSessions.length" class="empty-card">暂无在线实例或没有匹配结果</div>
      </div>
      <AppPagination
        v-if="filteredSessions.length > 0"
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :total="filteredSessions.length"
        item-name="实例"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
import { useProjectStore } from '@/stores/project'
import type { OnlineSessionSummary } from '@/api/types'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = computed(() => projectStore.onlineSessionsByProjectId[projectId.value])
const appId = computed(() => String(route.query.appId || ''))
const expandedKey = ref('')
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const filteredSessions = computed(() => {
  if (!payload.value) return []
  const term = keyword.value.toLowerCase()
  return payload.value.sessions.filter((item) => {
    const matchesApp = !appId.value || item.appId === appId.value || item.appName === appId.value
    const matchesKeyword = !term || [
      item.addressIp,
      item.appName,
      item.projectSrcName,
      item.agentVersion,
      item.onlineTime,
      item.systemDir,
      item.pid,
      item.jvmVersion,
    ].some((value) => String(value || '').toLowerCase().includes(term))
    return matchesApp && matchesKeyword
  })
})

const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredSessions.value.slice(start, start + pageSize.value)
})

function sessionKey(item: OnlineSessionSummary) {
  return `${item.addressIp || ''}-${item.pid || ''}-${item.systemDir || ''}-${item.appName || ''}`
}

function toggleDetail(item: OnlineSessionSummary) {
  const key = sessionKey(item)
  expandedKey.value = expandedKey.value === key ? '' : key
}

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadOnlineSessions(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载在线实例失败'
  } finally {
    loading.value = false
  }
}

watch([keyword, appId], () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

watch(() => filteredSessions.value.length, (total) => {
  const totalPages = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > totalPages) {
    currentPage.value = totalPages
  }
})

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.action-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: #0f172a;
  color: #fff;
  cursor: pointer;
}

.status-card,
.summary-card,
.toolbar-card,
.table-card,
.empty-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.runtime-overview {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.summary-card {
  display: inline-flex;
  flex-direction: column;
}

.toolbar-card {
  position: sticky;
  top: 12px;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.text-input {
  width: min(440px, 100%);
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
  background: #fff;
}

.summary-card strong {
  font-size: 28px;
}

.summary-card span {
  color: #64748b;
}

.table-card {
  max-height: min(620px, calc(100vh - 270px));
  overflow: auto;
}

.empty-card {
  margin-top: 12px;
  text-align: center;
  color: #64748b;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 11px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
}

.detail-button {
  border: none;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  cursor: pointer;
  font-weight: 800;
}

.detail-row td {
  background: #f8fafc;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid div {
  display: grid;
  gap: 5px;
}

.detail-grid .wide {
  grid-column: 1 / -1;
}

.detail-grid span {
  color: #64748b;
  font-size: 12px;
}

.detail-grid strong {
  color: #0f172a;
  word-break: break-word;
}

@media (max-width: 760px) {
  .runtime-overview,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-card {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
