<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">My Snapshots</div>
        <h1>我的快照</h1>
        <p class="subtext">查看个人快照并批量关联用例。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/my-snapshots/code-report`">全量代码报告</RouterLink>
        <button class="ghost-button" type="button" :disabled="loading" @click="load">{{ loading ? '刷新中...' : '刷新' }}</button>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载我的快照...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <section class="snapshot-toolbar">
        <details class="filter-menu">
          <summary>
            <span class="filter-icon">#</span>
            <span>{{ labelFilterText }}</span>
          </summary>
          <div class="filter-popover">
            <label v-for="label in availableLabels" :key="label.name" class="label-option">
              <input v-model="labelDrafts" type="checkbox" :value="label.name" />
              <span class="label-dot" :style="labelDotStyle(label.color)"></span>
              <span>{{ label.name }}</span>
            </label>
            <div v-if="!availableLabels.length" class="filter-empty">暂无标签</div>
          </div>
        </details>
        <label class="search-field">
          <input v-model="keywordDraft" type="search" placeholder="搜索快照名称..." aria-label="搜索我的快照" @keyup.enter="applyFilters" />
          <span>⌕</span>
        </label>
        <select v-model="sortDraft" class="sort-select" aria-label="排序">
          <option value="updateTime">更新时间</option>
          <option value="name">快照名称</option>
        </select>
        <button class="submit-button" type="button" @click="applyFilters">查询</button>
        <button v-if="hasActiveFilters" class="mini-button" type="button" @click="clearFilters">清空</button>
        <select v-model.number="pageSize" class="sort-select" aria-label="每页条数">
          <option :value="10">每页 10 条</option>
          <option :value="20">每页 20 条</option>
          <option :value="50">每页 50 条</option>
        </select>
        <div class="toolbar-spacer"></div>
        <div class="bulk-group">
          <button class="submit-button compact" type="button" @click="batchBindUsecases">批量关联用例</button>
          <button class="mini-button" type="button" @click="selectAll">全选</button>
          <button class="mini-button" type="button" @click="clearSelection">清空选择</button>
          <span class="selected-badge coverage" title="当前列表快照覆盖接口并集 / 应用总接口数">接口覆盖 {{ payload.apiCoverageSummaryText || '0 / 0' }}</span>
          <span :class="['selected-badge', { active: selectedSnapshotIds.length > 0 }]">已选 {{ selectedSnapshotIds.length }} 项</span>
        </div>
      </section>

      <UsecasePicker
        v-model:open="usecasePickerOpen"
        :title="usecasePickerTitle"
        :description="usecasePickerDescription"
        :usecases="payload.allUsecases"
        :selected-ids="usecasePickerSelectedIds"
        :busy="loading"
        @submit="submitUsecaseBinding"
      />

      <Teleport to="body">
        <div v-if="activeRowMenuSnapshot" class="row-menu-dismiss" @click="closeRowMenu"></div>
        <div
          v-if="activeRowMenuSnapshot"
          class="row-menu-panel floating-row-menu-panel"
          :style="rowMenuStyle"
          role="menu"
          @click.stop
        >
          <RouterLink :to="`/p/${projectId}/my-snapshots/${activeRowMenuSnapshot.id}/report`" role="menuitem" @click="closeRowMenu">覆盖率报告</RouterLink>
          <RouterLink :to="`/p/${projectId}/my-snapshots/${activeRowMenuSnapshot.id}/graph`" role="menuitem" @click="closeRowMenu">链路图</RouterLink>
          <button type="button" role="menuitem" @click="runRowMenuAction(() => openSingleUsecasePicker(activeRowMenuSnapshot!.id))">关联用例</button>
          <button type="button" role="menuitem" @click="runRowMenuAction(() => startEdit(activeRowMenuSnapshot!))">编辑</button>
          <button type="button" role="menuitem" @click="runRowMenuAction(() => toggleShare(activeRowMenuSnapshot!))">{{ activeRowMenuSnapshot.share ? '关闭共享' : '开启共享' }}</button>
          <a v-if="activeRowMenuSnapshot.share" :href="`/share/snapshot/${activeRowMenuSnapshot.id}`" target="_blank" rel="noreferrer" role="menuitem" @click="closeRowMenu">访问共享页</a>
          <button class="danger-link" type="button" role="menuitem" @click="runRowMenuAction(() => remove(activeRowMenuSnapshot!.id))">删除</button>
        </div>
      </Teleport>

      <section class="panel">
        <div class="panel-head">
          <h2>快照条目</h2>
          <span>{{ payload.snapshots.length }}</span>
        </div>

        <div v-if="!payload.snapshots.length" class="empty-card">暂无数据</div>
        <div v-else class="table-shell" @scroll.passive="closeRowMenu">
          <table class="report-table">
            <colgroup>
              <col class="select-col" />
              <col />
              <col class="coverage-col" />
              <col class="time-col" />
              <col class="action-col" />
            </colgroup>
            <tbody>
              <template v-for="snapshot in paginatedSnapshots" :key="snapshot.id">
                <tr :class="['snapshot-row', { selected: selectedSnapshotIds.includes(snapshot.id), editing: editingId === snapshot.id }]">
                  <td class="select-cell">
                    <input v-model="selectedSnapshotIds" type="checkbox" :value="snapshot.id" :aria-label="`选择 ${snapshot.name || snapshot.id}`" />
                  </td>
                  <td class="snapshot-name-cell">
                    <RouterLink class="snapshot-title" :to="`/p/${projectId}/my-snapshots/${snapshot.id}`" :title="snapshot.name || snapshot.id">
                      <span class="file-icon">▱</span>
                      <span>{{ snapshot.name || snapshot.id }}</span>
                    </RouterLink>
                    <div class="snapshot-meta-row">
                      <span v-if="snapshot.describe" class="snapshot-desc" :title="snapshot.describe">{{ snapshot.describe }}</span>
                      <span 
                        v-if="snapshot.labels?.length" 
                        class="label-summary"
                        :title="`标签：${snapshot.labels.join('、')}`"
                      >标签 {{ snapshot.labels.length }}</span>
                    </div>
                  </td>
                  <td class="coverage-cell">{{ snapshot.apiCoverageText || '-' }}</td>
                  <td class="time-cell">
                    <span :title="snapshot.updateTimeText || '-'">{{ snapshot.updateTimeRelativeText || snapshot.updateTimeText || '-' }}</span>
                  </td>
                  <td class="snapshot-action-cell">
                    <button
                      :class="['row-menu-trigger', { active: activeRowMenuId === snapshot.id }]"
                      type="button"
                      title="打开快照操作菜单"
                      aria-haspopup="menu"
                      :aria-expanded="activeRowMenuId === snapshot.id"
                      @click.stop="toggleRowMenu(snapshot, $event)"
                    >
                      ⚙
                    </button>
                  </td>
                </tr>
                <tr v-if="editingId === snapshot.id" class="edit-row">
                  <td></td>
                  <td colspan="4">
                    <form class="edit-form" @submit.prevent="submitEdit(snapshot.id)">
                      <label class="field">
                        <span>快照名称</span>
                        <input v-model.trim="editForm.name" class="text-input" type="text" />
                      </label>
                      <label class="field wide">
                        <span>快照描述</span>
                        <textarea v-model.trim="editForm.describe" class="text-input" rows="2" />
                      </label>
                      <label class="field">
                        <span>标签</span>
                        <select v-model="editForm.labels" class="select" multiple>
                          <option v-for="label in payload.snapshotLabels" :key="label.name" :value="label.name">{{ label.name }}</option>
                        </select>
                      </label>
                      <div class="edit-actions">
                        <button class="submit-button compact" type="submit">保存</button>
                        <button class="mini-button" type="button" @click="cancelEdit">取消</button>
                      </div>
                    </form>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <AppPagination
          v-if="payload.snapshots.length > pageSize"
          v-model:page="currentPage"
          v-model:page-size="pageSize"
          :total="payload.snapshots.length"
          item-name="个快照"
        />
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { SnapshotOption } from '@/api/types'
import AppPagination from '@/components/AppPagination.vue'
import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = computed(() => projectStore.mySnapshotListByProjectId[projectId.value])
const sortDraft = ref(String(route.query.sort || 'updateTime'))
const keywordDraft = ref(String(route.query.keyword || ''))
const labelDrafts = ref<string[]>(String(route.query.labels || '').split(',').filter(Boolean))
const selectedSnapshotIds = ref<string[]>([])
const editingId = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const error = ref('')
const usecasePickerOpen = ref(false)
const usecasePickerMode = ref<'single' | 'batch'>('single')
const usecasePickerSnapshotId = ref('')
const usecasePickerSelectedIds = ref<string[]>([])
const activeRowMenuId = ref('')
const rowMenuStyle = ref<Record<string, string>>({})
const availableLabels = computed(() => {
  if (!payload.value) return []
  const labelsFromConfig = payload.value.snapshotLabels || []
  const labelsFromSnapshots = new Map<string, string>()
  
  payload.value.snapshots.forEach((snapshot) => {
    snapshot.labels?.forEach((labelName) => {
      if (!labelsFromSnapshots.has(labelName) && !labelsFromConfig.find((l) => l.name === labelName)) {
        labelsFromSnapshots.set(labelName, '')
      }
    })
  })
  
  const merged = [...labelsFromConfig]
  labelsFromSnapshots.forEach((color, name) => {
    merged.push({ name, color })
  })
  
  return merged
})
const labelFilterText = computed(() => (labelDrafts.value.length ? `标签过滤 ${labelDrafts.value.length}` : '标签过滤'))
const hasActiveFilters = computed(
  () => Boolean(keywordDraft.value.trim()) || sortDraft.value !== 'updateTime' || labelDrafts.value.length > 0,
)
const usecasePickerTitle = computed(() => (usecasePickerMode.value === 'batch' ? '批量关联用例' : '关联用例'))
const usecasePickerDescription = computed(() =>
  usecasePickerMode.value === 'batch'
    ? `将选中的 ${selectedSnapshotIds.value.length} 个快照统一关联到这些用例。`
    : '从项目用例中选择当前快照需要关联的条目。',
)
const editForm = reactive({
  name: '',
  describe: '',
  labels: [] as string[],
})
const paginatedSnapshots = computed(() => {
  const snapshots = payload.value?.snapshots || []
  const start = (currentPage.value - 1) * pageSize.value
  return snapshots.slice(start, start + pageSize.value)
})
const activeRowMenuSnapshot = computed(() => {
  if (!activeRowMenuId.value) return null
  return (payload.value?.snapshots || []).find((snapshot) => snapshot.id === activeRowMenuId.value) || null
})


async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadMySnapshotList(projectId.value, {
      sort: String(route.query.sort || 'updateTime'),
      keyword: String(route.query.keyword || ''),
      labels: String(route.query.labels || ''),
    })
    selectedSnapshotIds.value = []
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照失败'
  } finally {
    loading.value = false
  }
}

async function applyFilters() {
  const query: Record<string, string> = {}
  if (keywordDraft.value.trim()) {
    query.keyword = keywordDraft.value.trim()
  }
  if (sortDraft.value !== 'updateTime') {
    query.sort = sortDraft.value
  }
  if (labelDrafts.value.length) {
    query.labels = labelDrafts.value.join(',')
  }
  await router.push({ name: 'my-snapshot-list', params: { projectId: projectId.value }, query })
  await load()
}

async function clearFilters() {
  keywordDraft.value = ''
  sortDraft.value = 'updateTime'
  labelDrafts.value = []
  await applyFilters()
}

function labelDotStyle(color?: string) {
  const palette: Record<string, string> = {
    red: '#db2828',
    orange: '#f2711c',
    yellow: '#fbbd08',
    olive: '#b5cc18',
    green: '#21ba45',
    teal: '#00b5ad',
    blue: '#2185d0',
    violet: '#6435c9',
    purple: '#a333c8',
    pink: '#e03997',
    brown: '#a5673f',
    grey: '#767676',
    black: '#1b1c1d',
  }
  return { background: palette[color || ''] || color || '#2185d0' }
}

function syncFilterDrafts() {
  sortDraft.value = String(route.query.sort || 'updateTime')
  keywordDraft.value = String(route.query.keyword || '')
  labelDrafts.value = String(route.query.labels || '').split(',').filter(Boolean)
}

function selectAll() {
  selectedSnapshotIds.value = paginatedSnapshots.value.map((snapshot) => snapshot.id)
}

function clearSelection() {
  selectedSnapshotIds.value = []
}

function toggleRowMenu(snapshot: SnapshotOption, event: MouseEvent) {
  if (activeRowMenuId.value === snapshot.id) {
    closeRowMenu()
    return
  }
  const trigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  if (!trigger) return
  const rect = trigger.getBoundingClientRect()
  const panelWidth = 164
  const estimatedPanelHeight = snapshot.share ? 292 : 252
  const viewportPadding = 12
  const left = Math.min(Math.max(rect.right - panelWidth, viewportPadding), window.innerWidth - panelWidth - viewportPadding)
  const opensUp = rect.bottom + estimatedPanelHeight + viewportPadding > window.innerHeight
  const top = opensUp
    ? Math.max(viewportPadding, rect.top - estimatedPanelHeight - 8)
    : Math.min(rect.bottom + 8, window.innerHeight - estimatedPanelHeight - viewportPadding)
  rowMenuStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    width: `${panelWidth}px`,
  }
  activeRowMenuId.value = snapshot.id
}

function closeRowMenu() {
  activeRowMenuId.value = ''
}

function runRowMenuAction(action: () => void | Promise<void>) {
  const result = action()
  closeRowMenu()
  void result
}

function startEdit(snapshot: SnapshotOption) {
  editingId.value = snapshot.id
  editForm.name = snapshot.name || snapshot.title || ''
  editForm.describe = snapshot.describe || ''
  editForm.labels = [...(snapshot.labels || [])]
}

function cancelEdit() {
  editingId.value = ''
}

async function submitEdit(snapshotId: string) {
  if (!editForm.name) {
    error.value = '快照名称不能为空'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateMySnapshotBasicInfo(projectId.value, snapshotId, { ...editForm })
    editingId.value = ''
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新快照失败'
  } finally {
    loading.value = false
  }
}

async function toggleShare(snapshot: SnapshotOption) {
  loading.value = true
  error.value = ''
  try {
    await projectStore.changeMySnapshotShare(projectId.value, snapshot.id, !snapshot.share)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新共享状态失败'
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
    const detail = await projectStore.loadMySnapshotDetail(projectId.value, snapshotId)
    usecasePickerSelectedIds.value = detail.usecases.map((usecase) => usecase.id)
    usecasePickerOpen.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照已关联用例失败'
  } finally {
    loading.value = false
  }
}

function batchBindUsecases() {
  if (!selectedSnapshotIds.value.length) {
    error.value = '请先选择至少一个快照'
    return
  }
  usecasePickerMode.value = 'batch'
  usecasePickerSnapshotId.value = ''
  usecasePickerSelectedIds.value = []
  usecasePickerOpen.value = true
}

async function submitUsecaseBinding(usecaseIds: string[]) {
  loading.value = true
  error.value = ''
  try {
    if (usecasePickerMode.value === 'batch') {
      await projectStore.batchUpdateMySnapshotUsecases(projectId.value, selectedSnapshotIds.value, usecaseIds)
    } else {
      await projectStore.updateMySnapshotUsecases(projectId.value, usecasePickerSnapshotId.value, usecaseIds)
    }
    usecasePickerOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '关联我的快照用例失败'
  } finally {
    loading.value = false
  }
}

async function remove(snapshotId: string) {
  const confirmed = await dialog.confirm({
    title: '删除我的快照',
    message: '确认删除该快照？删除后覆盖率报告、链路图和关联用例信息将不可恢复。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeMySnapshot(projectId.value, snapshotId)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除快照失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    syncFilterDrafts()
    currentPage.value = 1
    closeRowMenu()
    load()
  },
)

watch(pageSize, () => {
  currentPage.value = 1
  closeRowMenu()
})

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
}

.subtext {
  color: #64748b;
}

.action-button,
.submit-button,
.ghost-button,
.mini-button,
.icon-button {
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
  transition: background .16s ease, border-color .16s ease, color .16s ease, transform .16s ease;
}

.action-button,
.submit-button {
  border: none;
  padding: 9px 14px;
  background: #0f766e;
  color: #fff;
}

.action-button {
  background: #0f172a;
}

.submit-button.compact {
  padding: 7px 12px;
  font-size: 13px;
}

.ghost-button,
.mini-button,
.icon-button {
  border: 1px solid rgba(15, 118, 110, .18);
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
}

.ghost-button {
  padding: 9px 14px;
  text-decoration: none;
}

.mini-button {
  padding: 7px 11px;
  background: #fff;
  font-size: 13px;
}

.icon-button {
  width: 30px;
  height: 30px;
  padding: 0;
  display: inline-grid;
  place-items: center;
}

.action-button:hover,
.submit-button:hover,
.ghost-button:hover,
.mini-button:hover,
.icon-button:hover {
  transform: translateY(-1px);
}

.status-card {
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, .96);
  border: 1px solid rgba(15, 23, 42, .08);
}

.status-card.error,
.danger-link {
  color: #b91c1c;
}

.snapshot-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  position: sticky;
  top: 12px;
  z-index: 12;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.94);
  backdrop-filter: blur(10px);
}

.toolbar-spacer {
  flex: 1 1 auto;
}

.filter-menu {
  position: relative;
}

.filter-menu summary {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.filter-menu summary::-webkit-details-marker {
  display: none;
}

.filter-icon {
  font-weight: 900;
}

.filter-popover {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 30;
  min-width: 220px;
  max-height: 280px;
  overflow: auto;
  padding: 8px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, .16);
}

.label-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 9px;
  color: #334155;
  cursor: pointer;
}

.label-option:hover {
  background: rgba(15, 118, 110, .07);
}

.label-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(15, 23, 42, .06);
}

.filter-empty {
  padding: 10px;
  color: #94a3b8;
}

.search-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  min-width: 230px;
  padding: 0 10px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 10px;
  background: #fff;
}

.search-field input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: #0f172a;
}

.search-field span {
  color: #94a3b8;
}

.sort-select,
.text-input,
.select {
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
}

.sort-select {
  min-height: 34px;
  padding: 0 10px;
}

.text-input,
.select {
  padding: 9px 10px;
}

.bulk-group {
  flex-wrap: wrap;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, .14);
  border-radius: 999px;
  background: rgba(15, 118, 110, .05);
}

.selected-badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.selected-badge.active,
.selected-badge.coverage {
  border-color: rgba(15, 118, 110, .2);
  background: rgba(15, 118, 110, .09);
  color: #0f766e;
}

.snapshot-panel {
  overflow: visible;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 14px 36px rgba(15, 23, 42, .05);
}

.snapshot-panel-header {
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, .08);
  background: #fbfcfd;
}

.snapshot-tabs {
  flex-wrap: wrap;
  padding: 2px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 10px;
  background: #fff;
}

.snapshot-tab {
  padding: 7px 10px;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.snapshot-tab.active {
  background: rgba(15, 118, 110, .1);
  color: #0f766e;
}

.snapshot-table-shell {
  position: relative;
  z-index: 1;
  max-height: calc(100vh - 318px);
  overflow: auto;
}

.snapshot-table {
  width: 100%;
  min-width: 760px;
  table-layout: fixed;
  border-collapse: collapse;
}

.select-col {
  width: 34px;
}

.coverage-col {
  width: 92px;
}

.time-col {
  width: 132px;
}

.action-col {
  width: 44px;
}

.snapshot-table td {
  padding: 10px 8px;
  border-bottom: 1px solid rgba(15, 23, 42, .07);
  vertical-align: middle;
}

.snapshot-row:hover td {
  background: rgba(15, 118, 110, .035);
}

.snapshot-row.selected td {
  background: #fff4bd;
}

.snapshot-row.editing td {
  border-bottom-color: transparent;
}

.select-cell {
  text-align: center;
}

.snapshot-name-cell {
  min-width: 0;
}

.snapshot-title {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #0f172a;
  font-weight: 800;
  text-decoration: none;
}

.snapshot-title span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-icon {
  color: #64748b;
  font-size: 16px;
}

.snapshot-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-top: 4px;
  font-size: 12px;
}

.snapshot-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.label-summary,
.coverage-cell {
  color: #0f766e;
}

.label-summary {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(15, 118, 110, .08);
  font-weight: 700;
}

.coverage-cell,
.time-cell {
  color: #64748b;
  font-size: 13px;
  text-align: right;
  white-space: nowrap;
}

.coverage-cell {
  color: #0f766e;
  font-weight: 800;
}

.snapshot-action-cell {
  position: relative;
  overflow: visible;
  text-align: center;
}

.row-menu-trigger {
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.row-menu-trigger.active,
.row-menu-trigger:hover {
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
}

.row-menu-dismiss {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: transparent;
}

.row-menu-panel {
  z-index: 90;
  min-width: 148px;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, .16);
  text-align: left;
}

.floating-row-menu-panel {
  position: fixed;
  max-height: min(320px, calc(100vh - 24px));
  overflow: auto;
}

.row-menu-panel a,
.row-menu-panel button {
  display: block;
  width: 100%;
  padding: 8px 9px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  font: inherit;
  font-size: 13px;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.row-menu-panel a:hover,
.row-menu-panel button:hover {
  background: rgba(15, 118, 110, .07);
  color: #0f766e;
}

.row-menu-panel .danger-link {
  color: #b91c1c;
}

.edit-row td {
  padding-top: 0;
  background: #fffdf2;
}

.field,
.edit-form {
  display: grid;
  gap: 8px;
}

.field span {
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.edit-form {
  grid-template-columns: minmax(180px, 1fr) minmax(260px, 2fr) minmax(180px, 1fr) auto;
  align-items: end;
  padding: 12px;
  border: 1px solid rgba(15, 118, 110, .12);
  border-radius: 12px;
  background: rgba(255, 255, 255, .88);
}

.edit-form .select {
  min-height: 38px;
  max-height: 80px;
}

.empty-card {
  padding: 28px;
  color: #64748b;
  text-align: center;
}

@media (max-width: 960px) {
  .page-header,
  .header-actions,
  .snapshot-toolbar,
  .bulk-group,
  .snapshot-panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-spacer {
    display: none;
  }

  .search-field {
    min-width: 100%;
  }

  .bulk-group {
    border-radius: 14px;
  }

  .edit-form {
    grid-template-columns: 1fr;
  }
}
</style>
