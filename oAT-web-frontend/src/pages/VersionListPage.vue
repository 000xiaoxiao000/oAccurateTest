<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Version List</div>
        <h1>{{ payload?.app.name || appId }}</h1>
        <p class="subtext">管理版本创建、当前版本切换和文件清理。</p>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" :disabled="refreshing || saving" aria-label="刷新版本列表" @click="refreshVersions">
          {{ refreshing ? '刷新中...' : '刷新' }}
        </button>
        <RouterLink class="ghost-link" :to="`/p/${projectId}/apps/${appId}/versions/new`">新增版本</RouterLink>
        <RouterLink class="ghost-link" :to="`/p/${projectId}/apps/${appId}/compare`">比对与报告</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载版本列表...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <section v-else-if="payload" class="panel">
      <div class="panel-head">
        <h2>版本条目</h2>
        <span>{{ filteredVersions.length }} / {{ payload.versions.length }}</span>
      </div>
      <div class="list-toolbar" aria-label="版本筛选">
        <input v-model.trim="keyword" class="text-input" type="search" placeholder="搜索版本号、分支、Commit、描述" aria-label="搜索版本" />
        <select v-model="statusFilter" class="text-input compact" aria-label="筛选版本状态">
          <option value="">全部状态</option>
          <option value="current">当前版本</option>
          <option value="exists">文件存在</option>
          <option value="missing">文件缺失</option>
        </select>
      </div>
      <div v-if="!filteredVersions.length" class="empty-card">暂无版本数据或没有匹配结果</div>
      <div v-else class="table-shell">
        <table class="report-table">
          <colgroup>
            <col class="version-col" />
            <col class="source-col" />
            <col class="commit-col" />
            <col class="file-col" />
            <col class="time-col" />
            <col class="status-col" />
            <col class="actions-col" />
          </colgroup>
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
            <tr v-for="item in paginatedVersions" :key="item.id">
              <td>
                <strong>{{ item.versionNumber }}</strong>
                <div class="subtext">{{ item.describe || '-' }}</div>
              </td>
              <td>
                <span class="source-pill">{{ item.sourceType || '-' }}</span>
              </td>
              <td>
                <div class="commit-block">
                  <span class="branch-name">{{ item.repoBranch || '-' }}</span>
                  <span class="commit-id">{{ item.repoCommitId || '-' }}</span>
                </div>
              </td>
              <td>
                <span class="file-name">{{ item.programName || item.programFile || '-' }}</span>
              </td>
              <td>{{ item.createTimeRelativeText || item.createTimeText || '-' }}</td>
              <td>
                <span :class="['tag', item.current ? 'current' : item.fileExist ? 'ok' : 'warn']">
                  {{ item.current ? '当前版本' : item.fileExist ? '文件存在' : '文件缺失' }}
                </span>
              </td>
              <td>
                <div class="action-cell">
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
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <AppPagination
        v-if="filteredVersions.length > 0"
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :total="filteredVersions.length"
        item-name="版本"
      />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
import { deleteVersion, deleteVersionFile, fetchVersionCenter, setCurrentVersion } from '@/api/bootstrap'
import type { VersionCenterPayload, VersionItemSummary } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const payload = ref<VersionCenterPayload | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const saving = ref(false)
const error = ref('')
const keyword = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const filteredVersions = computed(() => {
  const versions = payload.value?.versions || []
  const term = keyword.value.toLowerCase()
  return versions.filter((item) => {
    const matchesKeyword = !term || [
      item.versionNumber,
      item.describe,
      item.sourceType,
      item.repoBranch,
      item.repoCommitId,
      item.programName,
      item.programFile,
    ].some((value) => String(value || '').toLowerCase().includes(term))
    const matchesStatus =
      !statusFilter.value ||
      (statusFilter.value === 'current' && item.current) ||
      (statusFilter.value === 'exists' && !item.current && item.fileExist) ||
      (statusFilter.value === 'missing' && !item.current && !item.fileExist)
    return matchesKeyword && matchesStatus
  })
})

const paginatedVersions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredVersions.value.slice(start, start + pageSize.value)
})

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

async function refreshVersions() {
  refreshing.value = true
  error.value = ''
  try {
    payload.value = await fetchVersionCenter(projectId.value, appId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '刷新版本列表失败'
  } finally {
    refreshing.value = false
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

watch([keyword, statusFilter], () => {
  currentPage.value = 1
})

watch(pageSize, () => {
  currentPage.value = 1
})

watch(() => filteredVersions.value.length, (total) => {
  const totalPages = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > totalPages) {
    currentPage.value = totalPages
  }
})

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.panel-head,
.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.list-toolbar {
  position: sticky;
  top: 12px;
  z-index: 4;
  margin: 14px 0;
  padding: 12px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.text-input {
  width: min(420px, 100%);
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
  background: #fff;
}

.text-input.compact {
  width: 180px;
}


.subtext {
  color: #64748b;
}

.ghost-link,
.ghost-button,
.text-link {
  color: #0f766e;
  font-weight: 700;
}

.ghost-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(15, 118, 110, 0.08);
  cursor: pointer;
  transition: background .16s ease, box-shadow .16s ease, transform .16s ease;
}

.ghost-button:hover:not(:disabled) {
  background: rgba(15, 118, 110, 0.14);
  box-shadow: 0 10px 20px rgba(15, 118, 110, 0.12);
  transform: translateY(-1px);
}

.ghost-button:disabled,
.text-link:disabled,
.text-danger:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.text-link,
.text-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px 10px;
  white-space: nowrap;
}

.text-danger {
  color: #dc2626;
  font-weight: 700;
}

.text-danger:hover:not(:disabled) {
  color: #b91c1c;
  background: rgba(220, 38, 38, 0.08);
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
  max-height: min(620px, calc(100vh - 280px));
  overflow: auto;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 18px;
  background: #fff;
}

.report-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.version-col {
  width: 9%;
}

.source-col {
  width: 7%;
}

.commit-col {
  width: 34%;
}

.file-col {
  width: 30%;
}

.time-col {
  width: 7%;
}

.status-col {
  width: 8%;
}

.actions-col {
  width: 104px;
}

.report-table thead {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(248, 250, 252, 0.98);
}

.report-table th,
.report-table td {
  padding: 18px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: middle;
}

.report-table tbody tr {
  transition: background .16s ease;
}

.report-table tbody tr:hover {
  background: rgba(248, 250, 252, 0.76);
}

.report-table tbody tr:last-child td {
  border-bottom: 0;
}

.commit-block {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.branch-name {
  font-weight: 700;
  color: #334155;
}

.commit-id,
.file-name {
  display: block;
  color: #1e293b;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.commit-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
}

.source-pill {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.action-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
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
