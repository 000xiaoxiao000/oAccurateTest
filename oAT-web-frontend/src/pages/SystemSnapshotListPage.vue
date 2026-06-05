<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">System Snapshots</div>
        <h1>{{ payload?.app.name || '系统快照' }}</h1>
        <p class="subtext">管理应用系统快照、目录和用例关联。</p>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" :disabled="loading" @click="openDirectoryEditor()">新建目录</button>
        <button class="ghost-button" type="button" :disabled="loading" @click="load">{{ loading ? '刷新中...' : '刷新' }}</button>
      </div>
    </div>

    <div v-if="loading && !payload" class="status-card">正在加载系统快照...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <form v-if="directoryEditorOpen" class="inline-editor" @submit.prevent="submitDirectoryEditor">
        <label class="field inline-field">
          <span class="field-label">{{ directoryEditorId ? '重命名目录' : '新建目录' }}</span>
          <input v-model.trim="directoryEditorName" class="text-input" type="text" placeholder="请输入目录名称" required />
        </label>
        <button class="ghost-button" type="submit">保存</button>
        <button class="ghost-button secondary" type="button" @click="closeDirectoryEditor">取消</button>
      </form>

      <UsecasePicker
        v-model:open="usecasePickerOpen"
        :title="usecasePickerTitle"
        :description="usecasePickerDescription"
        :usecases="payload.allUsecases"
        :selected-ids="usecasePickerSelectedIds"
        :busy="loading"
        @submit="submitUsecaseBinding"
      />

      <div class="page-grid">
        <aside class="sidebar-panel">
          <div class="panel-head">
            <h2>目录</h2>
            <span class="count-badge">{{ payload.directories.length }}</span>
          </div>
          <div class="dir-list">
            <RouterLink class="dir-link" :class="{ active: currentDirectory === 'root' }" :to="`/p/${projectId}/apps/${appId}/snapshots`">
              / ROOT
            </RouterLink>
            <article v-for="dir in visibleDirectories" :key="dir.id" class="dir-item">
              <RouterLink class="dir-link" :class="{ active: currentDirectory === String(dir.id) }" :to="directoryLink(dir.id)">
                {{ dir.name }}
              </RouterLink>
              <div class="dir-actions">
                <button class="text-link" type="button" @click="openDirectoryEditor(dir.id, dir.name)">改</button>
                <button class="text-danger" type="button" @click="removeDirectory(dir.id)">删</button>
              </div>
            </article>
          </div>
          <button v-if="payload.directories.length > directoryPreviewLimit" class="expand-btn" type="button" @click="directoriesExpanded = !directoriesExpanded">
            {{ directoriesExpanded ? '收起 ▴' : `展开 (${payload.directories.length - directoryPreviewLimit}+) ▾` }}
          </button>
        </aside>

        <section class="main-panel">
          <div class="panel-head">
            <h2>快照列表</h2>
            <span class="count-badge">{{ payload.snapshots.length }}</span>
          </div>

          <div class="list-toolbar" aria-label="快照筛选和操作">
            <nav class="breadcrumb-path" aria-label="当前目录">
              <RouterLink :to="`/p/${projectId}/apps/${appId}/snapshots`">ROOT</RouterLink>
              <template v-for="tier in payload.directoryTiers" :key="tier.id">
                <span class="path-sep" aria-hidden="true">/</span>
                <RouterLink :to="directoryLink(tier.id)">{{ tier.name }}</RouterLink>
              </template>
            </nav>
            <form class="toolbar-controls" @submit.prevent="applyFilters">
              <input v-model="keywordDraft" class="text-input" type="search" placeholder="搜索快照名称、描述..." aria-label="搜索系统快照" />
              <select v-model="sortDraft" class="text-input compact">
                <option value="updateTime">按更新时间</option>
                <option value="name">按名称</option>
              </select>
              <button class="ghost-button" type="submit">查询</button>
            </form>
            <div class="batch-toolbar">
              <label class="batch-checkbox">
                <input type="checkbox" :checked="allCurrentPageSelected" :indeterminate="someCurrentPageSelected && !allCurrentPageSelected" @change="togglePageSelection" />
                <span>已选 <strong>{{ selectedSnapshotIds.length }}</strong> 项</span>
              </label>
              <button class="text-link" type="button" :disabled="!selectedSnapshotIds.length" @click="batchBindUsecases">批量关联用例</button>
              <button class="text-link" type="button" :disabled="!selectedSnapshotIds.length" @click="clearSelection">清空</button>
            </div>
          </div>

          <div v-if="!payload.snapshots.length" class="empty-card">暂无系统快照或没有匹配结果</div>
          <div v-else class="table-shell">
            <table class="snapshot-table">
              <colgroup>
                <col class="check-col" />
                <col class="title-col" />
                <col class="meta-col" />
                <col class="time-col" />
                <col class="status-col" />
                <col class="actions-col" />
              </colgroup>
              <thead>
                <tr>
                  <th><input type="checkbox" :checked="allCurrentPageSelected" :indeterminate="someCurrentPageSelected && !allCurrentPageSelected" @change="togglePageSelection" aria-label="全选当前页" /></th>
                  <th>快照名称</th>
                  <th>元信息</th>
                  <th>更新时间</th>
                  <th>报告状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="snapshot in paginatedSnapshots" :key="snapshot.id">
                  <td>
                    <input v-model="selectedSnapshotIds" type="checkbox" :value="snapshot.id" :aria-label="'选择 ' + snapshot.title" />
                  </td>
                  <td>
                    <RouterLink class="snapshot-link" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshot.id}`">
                      <strong>{{ snapshot.title }}</strong>
                    </RouterLink>
                    <div v-if="snapshot.describe || snapshot.subTitle" class="subtext">{{ snapshot.describe || snapshot.subTitle }}</div>
                  </td>
                  <td>
                    <div class="meta-group">
                      <span class="meta-badge" :title="snapshot.labels?.length ? `标签：${snapshot.labels.join('、')}` : '暂无标签'">
                        标签 {{ snapshot.labels?.length || 0 }}
                      </span>
                      <span class="meta-badge" :title="getPrincipalsTooltip(snapshot.principals)">
                        负责人 {{ snapshot.principals?.length || 0 }}
                      </span>
                    </div>
                  </td>
                  <td>{{ snapshot.versionLastUpdateRelativeText || snapshot.versionLastUpdateText || '-' }}</td>
                  <td>
                    <span :class="['tag', reportStatusTone(snapshot.reportStatus)]" :title="reportStatusTooltip(snapshot.reportStatus)">
                      {{ reportStatusText(snapshot.reportStatus) }}
                    </span>
                  </td>
                  <td>
                    <div class="action-cell">
                      <button class="text-link" type="button" @click="openSingleUsecasePicker(snapshot.id)">关联用例</button>
                      <RouterLink class="text-link" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshot.id}/report`">覆盖率</RouterLink>
                      <RouterLink class="text-link" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshot.id}/graph`">链路图</RouterLink>
                      <button class="text-danger" type="button" @click="removeSnapshot(snapshot.id, snapshot.title)">删除</button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <AppPagination
            v-if="payload.snapshots.length > 0"
            v-model:page="currentPage"
            v-model:page-size="pageSize"
            :total="payload.snapshots.length"
            item-name="快照"
          />
        </section>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import AppPagination from '@/components/AppPagination.vue'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'
import { reportStatusText, reportStatusTone } from '@/utils/snapshot'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const storeKey = computed(() => `${projectId.value}:${appId.value}`)
const payload = computed(() => projectStore.systemSnapshotListByKey[storeKey.value])
const currentDirectory = computed(() => String(route.query.directoryId || 'root'))
const currentKeyword = computed(() => String(route.query.keyword || ''))
const currentSort = computed(() => String(route.query.sort || 'updateTime'))
const keywordDraft = ref('')
const sortDraft = ref('updateTime')
const selectedSnapshotIds = ref<string[]>([])
const loading = ref(false)
const error = ref('')
const directoryEditorOpen = ref(false)
const directoryEditorId = ref('')
const directoryEditorName = ref('')
const usecasePickerOpen = ref(false)
const usecasePickerMode = ref<'single' | 'batch'>('single')
const usecasePickerSnapshotId = ref('')
const usecasePickerSelectedIds = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const directoriesExpanded = ref(false)
const directoryPreviewLimit = 12
const usecasePickerTitle = computed(() => (usecasePickerMode.value === 'batch' ? '批量关联用例' : '关联用例'))
const usecasePickerDescription = computed(() =>
  usecasePickerMode.value === 'batch'
    ? `将选中的 ${selectedSnapshotIds.value.length} 个系统快照统一关联到这些用例。`
    : '从项目用例中选择当前系统快照需要关联的条目。',
)
const visibleDirectories = computed(() => {
  const directories = payload.value?.directories || []
  return directoriesExpanded.value ? directories : directories.slice(0, directoryPreviewLimit)
})
const paginatedSnapshots = computed(() => {
  const snapshots = payload.value?.snapshots || []
  const start = (currentPage.value - 1) * pageSize.value
  return snapshots.slice(start, start + pageSize.value)
})

const allCurrentPageSelected = computed(() => {
  const pageIds = paginatedSnapshots.value.map((s) => s.id)
  return pageIds.length > 0 && pageIds.every((id) => selectedSnapshotIds.value.includes(id))
})

const someCurrentPageSelected = computed(() => {
  const pageIds = paginatedSnapshots.value.map((s) => s.id)
  return pageIds.some((id) => selectedSnapshotIds.value.includes(id))
})

function reportStatusTooltip(status?: number) {
  switch (status) {
    case 1:
      return '覆盖率报告生成任务正在处理中，请稍后刷新页面查看结果'
    case 2:
      return '覆盖率报告生成已完成，可点击「覆盖率报告」按钮查看详细数据。注意：已完成表示报告计算成功，不代表覆盖率高低'
    case 3:
      return '覆盖率报告生成失败，请在报告页面重新触发生成'
    default:
      return '尚未生成覆盖率报告，可进入报告页面发起首次计算'
  }
}

function getPrincipalsTooltip(principalIds?: string[]) {
  if (!principalIds || principalIds.length === 0) {
    return '暂无负责人'
  }
  const members = payload.value?.members || []
  const principalNames = principalIds
    .map(id => {
      const member = members.find(m => m.memberId === id)
      return member?.memberName || member?.memberEmail || id
    })
    .filter(Boolean)
  
  if (principalNames.length === 0) {
    return `负责人共 ${principalIds.length} 人`
  }
  
  return `负责人：${principalNames.join('、')}`
}

function directoryLink(directoryId: string) {
  const query = new URLSearchParams()
  query.set('directoryId', directoryId)
  if (currentSort.value !== 'updateTime') {
    query.set('sort', currentSort.value)
  }
  if (currentKeyword.value) {
    query.set('keyword', currentKeyword.value)
  }
  return `/p/${projectId.value}/apps/${appId.value}/snapshots?${query.toString()}`
}

async function load() {
  if (!projectId.value || !appId.value) {
    error.value = '缺少 projectId 或 appId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadSystemSnapshotList(projectId.value, appId.value, {
      directoryId: currentDirectory.value,
      sort: currentSort.value,
      keyword: currentKeyword.value || undefined,
    })
    keywordDraft.value = currentKeyword.value
    sortDraft.value = currentSort.value
    selectedSnapshotIds.value = []
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载系统快照失败'
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  const query: Record<string, string> = {}
  if (currentDirectory.value !== 'root') {
    query.directoryId = currentDirectory.value
  }
  if (sortDraft.value !== 'updateTime') {
    query.sort = sortDraft.value
  }
  if (keywordDraft.value.trim()) {
    query.keyword = keywordDraft.value.trim()
  }
  await router.push({ name: 'system-snapshot-list', params: { projectId: projectId.value, appId: appId.value }, query })
}

function openDirectoryEditor(id = '', currentName = '') {
  directoryEditorId.value = id
  directoryEditorName.value = currentName
  directoryEditorOpen.value = true
}

function closeDirectoryEditor() {
  directoryEditorOpen.value = false
  directoryEditorId.value = ''
  directoryEditorName.value = ''
}

async function submitDirectoryEditor() {
  const name = directoryEditorName.value.trim()
  if (!name) {
    error.value = '目录名称不能为空'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.persistSystemSnapshotDirectory(projectId.value, appId.value, {
      id: directoryEditorId.value ? Number(directoryEditorId.value) : undefined,
      parentId: currentDirectory.value,
      name,
    })
    closeDirectoryEditor()
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存目录失败'
  } finally {
    loading.value = false
  }
}

async function removeDirectory(id: string) {
  const confirmed = await dialog.confirm({
    title: '删除目录',
    message: '确认删除该目录？目录删除后快照会回到默认目录或按后端规则重新归档。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeSystemSnapshotDirectory(projectId.value, appId.value, id)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除目录失败'
  } finally {
    loading.value = false
  }
}

async function openSingleUsecasePicker(snapshotId: string) {
  usecasePickerMode.value = 'single'
  usecasePickerSnapshotId.value = snapshotId
  usecasePickerSelectedIds.value = []
  loading.value = true
  error.value = ''
  try {
    const detail = await projectStore.loadSystemSnapshotDetail(projectId.value, appId.value, snapshotId)
    usecasePickerSelectedIds.value = detail.usecases.map((usecase) => usecase.id)
    usecasePickerOpen.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载系统快照已关联用例失败'
  } finally {
    loading.value = false
  }
}

function togglePageSelection() {
  if (allCurrentPageSelected.value) {
    const pageIds = paginatedSnapshots.value.map((s) => s.id)
    selectedSnapshotIds.value = selectedSnapshotIds.value.filter((id) => !pageIds.includes(id))
  } else {
    const pageIds = paginatedSnapshots.value.map((s) => s.id)
    selectedSnapshotIds.value = Array.from(new Set([...selectedSnapshotIds.value, ...pageIds]))
  }
}

function selectAllSnapshots() {
  selectedSnapshotIds.value = paginatedSnapshots.value.map((snapshot) => snapshot.id)
}

function clearSelection() {
  selectedSnapshotIds.value = []
}

function batchBindUsecases() {
  if (!selectedSnapshotIds.value.length) {
    error.value = '请先选择至少一个系统快照'
    return
  }
  usecasePickerMode.value = 'batch'
  usecasePickerSnapshotId.value = ''
  usecasePickerSelectedIds.value = []
  usecasePickerOpen.value = true
}

async function removeSnapshot(snapshotId: string, title?: string) {
  const confirmed = await dialog.confirm({
    title: '删除系统快照',
    message: `确认删除系统快照「${title || snapshotId}」？覆盖率报告、链路图和用例关联将同步删除。`,
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) return
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeSystemSnapshot(projectId.value, appId.value, snapshotId)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除系统快照失败'
  } finally {
    loading.value = false
  }
}

async function submitUsecaseBinding(usecaseIds: string[]) {
  loading.value = true
  error.value = ''
  try {
    if (usecasePickerMode.value === 'batch') {
      await projectStore.batchUpdateSystemSnapshotUsecases(
        projectId.value,
        appId.value,
        selectedSnapshotIds.value,
        usecaseIds,
      )
    } else {
      await projectStore.updateSystemSnapshotUsecases(projectId.value, appId.value, usecasePickerSnapshotId.value, usecaseIds)
    }
    usecasePickerOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '关联系统快照用例失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    currentPage.value = 1
    load()
  },
)

watch(pageSize, () => {
  currentPage.value = 1
})

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.subtext {
  margin: 4px 0 0;
  color: var(--oat-text-muted);
  font-size: 14px;
  line-height: 1.5;
}

.ghost-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  border: 1px solid rgba(var(--oat-primary-rgb), 0.16);
  border-radius: 999px;
  padding: 8px 14px;
  background: rgba(var(--oat-primary-rgb), 0.08);
  color: var(--oat-primary-dark);
  font-weight: 700;
  cursor: pointer;
  transition: all 0.16s ease;
}

.ghost-button:hover:not(:disabled) {
  background: rgba(var(--oat-primary-rgb), 0.14);
  box-shadow: 0 10px 20px rgba(var(--oat-primary-rgb), 0.12);
  transform: translateY(-1px);
}

.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.ghost-button.secondary {
  border-color: var(--oat-border);
  background: var(--oat-surface);
  color: var(--oat-text-secondary);
}

.ghost-button.secondary:hover:not(:disabled) {
  background: var(--oat-surface-soft);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.08);
}

.inline-editor {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid rgba(var(--oat-primary-rgb), 0.2);
  border-radius: var(--oat-radius-lg);
  background: linear-gradient(135deg, rgba(240, 253, 250, 0.5), rgba(255, 255, 255, 0.8));
}

.field {
  display: grid;
  gap: 6px;
}

.field-label {
  color: var(--oat-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.inline-field {
  min-width: min(320px, 100%);
  flex: 1;
}

.page-grid {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.sidebar-panel,
.main-panel {
  padding: 16px;
  border: 1px solid var(--oat-border);
  border-radius: var(--oat-radius-lg);
  background: var(--oat-surface);
  box-shadow: var(--oat-shadow-xs);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--oat-border);
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  color: var(--oat-text);
  font-size: 14px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(var(--oat-primary-rgb), 0.12);
  color: var(--oat-primary-dark);
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
}

.dir-list {
  display: grid;
  gap: 4px;
  margin-bottom: 8px;
}

.dir-item {
  display: grid;
  gap: 3px;
}

.dir-link {
  display: block;
  padding: 7px 10px;
  border-radius: 8px;
  background: transparent;
  color: var(--oat-text);
  font-size: 13px;
  font-weight: 600;
  transition: all 0.15s ease;
}

.dir-link:hover,
.dir-link.active {
  background: rgba(var(--oat-primary-rgb), 0.08);
  color: var(--oat-primary-dark);
}

.dir-link.active {
  font-weight: 700;
}

.dir-actions {
  display: flex;
  gap: 8px;
  padding-left: 10px;
}

.expand-btn {
  width: 100%;
  min-height: 28px;
  margin-top: 6px;
  border: 1px solid var(--oat-border);
  border-radius: 8px;
  padding: 5px 8px;
  background: var(--oat-surface-soft);
  color: var(--oat-text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.expand-btn:hover {
  background: rgba(var(--oat-primary-rgb), 0.06);
  color: var(--oat-primary-dark);
}

.text-link,
.text-danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  padding: 3px 6px;
  color: var(--oat-primary);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.text-link:hover:not(:disabled) {
  color: var(--oat-primary-dark);
  background: rgba(var(--oat-primary-rgb), 0.08);
}

.text-danger {
  color: var(--oat-danger);
}

.text-danger:hover:not(:disabled) {
  color: #b91c1c;
  background: rgba(220, 38, 38, 0.08);
}

.text-link:disabled,
.text-danger:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.list-toolbar {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid var(--oat-border);
  border-radius: var(--oat-radius-md);
  background: rgba(248, 250, 252, 0.94);
  position: sticky;
  top: 78px;
  z-index: 4;
  backdrop-filter: saturate(180%) blur(14px);
}

.breadcrumb-path {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 12px;
  font-weight: 600;
}

.breadcrumb-path a {
  padding: 3px 7px;
  border-radius: 6px;
  color: var(--oat-primary-dark);
  transition: background 0.15s ease;
}

.breadcrumb-path a:hover {
  background: rgba(var(--oat-primary-rgb), 0.1);
}

.path-sep {
  color: var(--oat-border-strong);
  user-select: none;
}

.toolbar-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.text-input {
  min-height: 38px;
  border: 1px solid var(--oat-border);
  border-radius: var(--oat-radius-md);
  padding: 8px 12px;
  background: var(--oat-surface);
  color: var(--oat-text);
  font-size: 14px;
  transition: all 0.15s ease;
}

.text-input {
  flex: 1;
  min-width: min(300px, 100%);
}

.text-input.compact {
  flex: initial;
  min-width: 160px;
}

.text-input:focus {
  border-color: rgba(var(--oat-primary-rgb), 0.5);
  box-shadow: 0 0 0 3px rgba(var(--oat-primary-rgb), 0.1);
  outline: none;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 10px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.batch-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(var(--oat-primary-rgb), 0.06);
  color: var(--oat-text-secondary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.batch-checkbox:hover {
  background: rgba(var(--oat-primary-rgb), 0.1);
}

.batch-checkbox strong {
  color: var(--oat-primary-dark);
  font-weight: 800;
}

.empty-card {
  padding: 32px 20px;
  border-radius: var(--oat-radius-md);
  background: var(--oat-surface-soft);
  color: var(--oat-text-muted);
  text-align: center;
  font-size: 14px;
}

.table-shell {
  max-height: min(640px, calc(100vh - 320px));
  overflow: auto;
  border: 1px solid var(--oat-border);
  border-radius: var(--oat-radius-lg);
  background: var(--oat-surface);
}

.snapshot-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.check-col {
  width: 36px;
}

.title-col {
  width: 38%;
}

.meta-col {
  width: 14%;
}

.time-col {
  width: 10%;
}

.status-col {
  width: 10%;
}

.actions-col {
  width: 28%;
}

.snapshot-table thead {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(248, 250, 252, 0.98);
  backdrop-filter: blur(10px);
}

.snapshot-table th,
.snapshot-table td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--oat-border);
  text-align: left;
  vertical-align: middle;
}

.snapshot-table th {
  color: var(--oat-text-secondary);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.snapshot-table tbody tr {
  transition: background 0.15s ease;
}

.snapshot-table tbody tr:hover {
  background: rgba(248, 250, 252, 0.6);
}

.snapshot-table tbody tr:last-child td {
  border-bottom: 0;
}

.snapshot-link {
  display: block;
  max-width: 100%;
  overflow: hidden;
  color: var(--oat-text);
  font-weight: 700;
  transition: color 0.15s ease;
}

.snapshot-link:hover {
  color: var(--oat-primary);
}

.snapshot-link strong {
  display: block;
  font-size: 14px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subtext {
  margin-top: 3px;
  color: var(--oat-text-muted);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(var(--oat-primary-rgb), 0.08);
  color: var(--oat-primary-dark);
  font-size: 11px;
  font-weight: 700;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.tag.success {
  background: rgba(22, 163, 74, 0.1);
  color: #15803d;
}

.tag.warning {
  background: rgba(234, 88, 12, 0.1);
  color: #c2410c;
}

.tag.danger {
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
}

.tag.default {
  background: rgba(100, 116, 139, 0.08);
  color: var(--oat-text-muted);
}

.action-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

@media (max-width: 960px) {
  .page-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .toolbar-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .text-input {
    min-width: 100%;
  }

  .table-shell {
    overflow-x: auto;
  }

  .snapshot-table {
    min-width: 800px;
  }
}
</style>
