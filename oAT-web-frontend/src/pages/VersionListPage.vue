<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Version List</div>
        <h1>{{ payload?.app.name || appId }}</h1>
        <p class="subtext">管理版本创建、当前版本切换和文件清理。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-link" :to="`/p/${projectId}/apps/${appId}/versions/new`">新增版本</RouterLink>
        <RouterLink class="ghost-link" :to="`/p/${projectId}/apps/${appId}/compare`">比对与报告</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载版本列表...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <section v-else-if="payload" class="panel">
      <div class="panel-head">
        <h2>版本条目</h2>
        <span>{{ payload.versions.length }}</span>
      </div>
      <div v-if="!payload.versions.length" class="empty-card">暂无版本数据</div>
      <div v-else class="table-shell">
        <table class="report-table">
          <thead>
            <tr>
              <th>版本号</th>
              <th>来源</th>
              <th>分支 / Commit</th>
              <th>文件</th>
              <th>创建时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in payload.versions" :key="item.id">
              <td>
                <strong>{{ item.versionNumber }}</strong>
                <div class="subtext">{{ item.describe || '-' }}</div>
              </td>
              <td>{{ item.sourceType || '-' }}</td>
              <td>{{ item.repoBranch || '-' }} / {{ item.repoCommitId || '-' }}</td>
              <td>{{ item.programName || item.programFile || '-' }}</td>
              <td>{{ item.createTimeRelativeText || item.createTimeText || '-' }}</td>
              <td>
                <span :class="['tag', item.current ? 'current' : item.fileExist ? 'ok' : 'warn']">
                  {{ item.current ? '当前版本' : item.fileExist ? '文件存在' : '文件缺失' }}
                </span>
              </td>
              <td class="action-cell">
                <button class="text-link" type="button" :disabled="saving" @click="useCurrent(item)">设为当前</button>
                <button
                  v-if="item.programFile"
                  class="text-link"
                  type="button"
                  :disabled="saving"
                  @click="removeFile(item.programFile)"
                >
                  删除文件
                </button>
                <button class="text-danger" type="button" :disabled="saving" @click="removeVersion(item.id)">删除版本</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { deleteVersion, deleteVersionFile, fetchVersionCenter, setCurrentVersion } from '@/api/bootstrap'
import type { VersionCenterPayload, VersionItemSummary } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const payload = ref<VersionCenterPayload | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchVersionCenter(projectId.value, appId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载版本列表失败'
  } finally {
    loading.value = false
  }
}

async function useCurrent(item: VersionItemSummary) {
  saving.value = true
  error.value = ''
  try {
    await setCurrentVersion(projectId.value, appId.value, {
      versionNumber: item.versionNumber,
      branch: item.repoBranch,
      commitId: item.repoCommitId,
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '设置当前版本失败'
  } finally {
    saving.value = false
  }
}

async function removeVersion(id: string) {
  saving.value = true
  error.value = ''
  try {
    await deleteVersion(projectId.value, appId.value, id)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除版本失败'
  } finally {
    saving.value = false
  }
}

async function removeFile(filePath: string) {
  saving.value = true
  error.value = ''
  try {
    await deleteVersionFile(projectId.value, appId.value, filePath)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除文件失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext {
  color: #64748b;
}

.ghost-link,
.text-link {
  color: #0f766e;
  font-weight: 700;
}

.text-link,
.text-danger {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
}

.text-danger {
  color: #b91c1c;
  font-weight: 700;
}

.status-card,
.panel,
.empty-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.table-shell {
  overflow: auto;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
}

.report-table th,
.report-table td {
  padding: 12px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: top;
}

.action-cell {
  display: grid;
  gap: 8px;
}

.tag {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.tag.current {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.tag.ok {
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
}

.tag.warn {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}
</style>
