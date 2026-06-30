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
        <div v-if="saveAsSystemOpen" class="modal-backdrop" @click.self="closeSaveAsSystemDialog">
          <form class="modal-card save-system-modal" @submit.prevent="submitSaveAsSystem">
            <div class="modal-head">
              <div>
                <div class="eyebrow">System Snapshot</div>
                <h2>保存为系统快照</h2>
                <p>将当前我的快照复制到系统快照，便于团队统一管理和覆盖率分析。</p>
              </div>
              <button class="modal-close" type="button" aria-label="关闭" @click="closeSaveAsSystemDialog">×</button>
            </div>
            <div class="form-grid save-system-grid">
              <label class="field">
                <span>应用</span>
                <select v-model="saveAsSystemForm.appId" class="select" required>
                  <option value="" disabled>请选择应用</option>
                  <option v-for="app in availableApps" :key="app.id" :value="app.id">{{ app.name || app.srcName || app.id }}</option>
                </select>
              </label>
              <label class="field">
                <span>目录</span>
                <input v-model.trim="saveAsSystemForm.directory" class="text-input" type="text" placeholder="root" />
              </label>
              <label class="field field-wide">
                <span>标题</span>
                <input v-model.trim="saveAsSystemForm.title" class="text-input" type="text" required />
              </label>
              <label class="field field-wide">
                <span>描述</span>
                <textarea v-model.trim="saveAsSystemForm.describe" class="text-area" rows="3"></textarea>
              </label>
              <label class="field compact-field">
                <span>版本周期（天）</span>
                <input v-model.number="saveAsSystemForm.versionCycle" class="text-input" type="number" min="1" />
              </label>
              <div class="field label-picker">
                <span>标签</span>
                <div class="label-chip-list">
                  <button
                    v-for="label in availableSaveAsSystemLabels"
                    :key="label.name"
                    :class="['label-chip', { active: saveAsSystemForm.labels.includes(label.name) }]"
                    type="button"
                    @click="toggleSaveAsSystemLabel(label.name)"
                  >
                    {{ label.name }}
                  </button>
                  <span v-if="!availableSaveAsSystemLabels.length" class="label-empty">暂无可选标签</span>
                </div>
              </div>
            </div>
            <div class="modal-actions">
              <button class="ghost-button" type="button" @click="closeSaveAsSystemDialog">取消</button>
              <button class="submit-button" type="submit" :disabled="savingSystemSnapshot">
                {{ savingSystemSnapshot ? '保存中...' : '保存为系统快照' }}
              </button>
            </div>
          </form>
        </div>
      </Teleport>

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
          <button type="button" role="menuitem" @click="runRowMenuAction(() => openSaveAsSystemDialog(activeRowMenuSnapshot!))">保存为系统快照</button>
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
                    </div>
                    <div v-if="snapshot.versionNumber || snapshot.repoBranch || snapshot.repoCommitId || snapshot.labels?.length" class="snapshot-meta-line">
                      <span class="meta-pill" :title="snapshot.versionNumber">版本 {{ snapshot.versionNumber }}</span>
                      <span v-if="snapshot.repoBranch" class="meta-pill branch" :title="snapshot.repoBranch">分支 {{ snapshot.repoBranch }}</span>
                      <span v-if="snapshot.repoCommitId" class="meta-pill commit" :title="snapshot.repoCommitId">Commit {{ abbreviateCommit(snapshot.repoCommitId) }}</span>
                      <span v-if="snapshot.labels?.length" class="meta-pill" :title="`标签：${snapshot.labels.join('、')}`">标签 {{ snapshot.labels.length }}</span>
                    </div>
                  </td>
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
                  <td colspan="3">
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

import type { AppSummary, SnapshotOption } from '@/api/types'
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
const saveAsSystemOpen = ref(false)
const savingSystemSnapshot = ref(false)
const saveAsSystemSnapshotId = ref('')
const saveAsSystemForm = reactive({
  appId: '',
  directory: 'root',
  title: '',
  describe: '',
  versionCycle: 30,
  labels: [] as string[],
})
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
const availableSaveAsSystemLabels = computed(() => {
  const labelMap = new Map<string, { name: string; color?: string }>()
  availableLabels.value.forEach((label) => labelMap.set(label.name, label))
  saveAsSystemForm.labels.forEach((name) => {
    if (name && !labelMap.has(name)) {
      labelMap.set(name, { name, color: '' })
    }
  })
  return [...labelMap.values()]
})
const labelFilterText = computed(() => (labelDrafts.value.length ? `标签过滤 ${labelDrafts.value.length}` : '标签过滤'))
const hasActiveFilters = computed(
  () => Boolean(keywordDraft.value.trim()) || sortDraft.value !== 'updateTime' || labelDrafts.value.length > 0,
)
const usecasePickerTitle = computed(() => {
  if (usecasePickerMode.value === 'batch') return '批量关联用例'
  return '关联用例'
})
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

const availableApps = computed<AppSummary[]>(() => projectStore.contextByProjectId[projectId.value]?.apps || [])

function abbreviateCommit(commitId?: string) {
  if (!commitId) return ''
  return commitId.length > 12 ? commitId.slice(0, 12) : commitId
}

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadProjectContext(projectId.value)
    await Promise.all([
      projectStore.loadMySnapshotList(projectId.value, {
        sort: String(route.query.sort || 'updateTime'),
        keyword: String(route.query.keyword || ''),
        labels: String(route.query.labels || ''),
      }),
    ])
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

function openSaveAsSystemDialog(snapshot: SnapshotOption) {
  saveAsSystemSnapshotId.value = snapshot.id
  saveAsSystemForm.appId = snapshot.appId || availableApps.value[0]?.id || ''
  saveAsSystemForm.directory = 'root'
  saveAsSystemForm.title = snapshot.name || snapshot.title || ''
  saveAsSystemForm.describe = snapshot.describe || ''
  saveAsSystemForm.versionCycle = 30
  saveAsSystemForm.labels = [...(snapshot.labels || [])]
  saveAsSystemOpen.value = true
}

function toggleSaveAsSystemLabel(labelName: string) {
  const index = saveAsSystemForm.labels.indexOf(labelName)
  if (index >= 0) {
    saveAsSystemForm.labels.splice(index, 1)
  } else {
    saveAsSystemForm.labels.push(labelName)
  }
}

function closeSaveAsSystemDialog() {
  if (savingSystemSnapshot.value) return
  saveAsSystemOpen.value = false
  saveAsSystemSnapshotId.value = ''
}

async function submitSaveAsSystem() {
  if (!saveAsSystemSnapshotId.value) return
  if (!saveAsSystemForm.appId) {
    error.value = '请选择系统快照所属应用'
    return
  }
  if (!saveAsSystemForm.title.trim()) {
    error.value = '系统快照标题不能为空'
    return
  }
  savingSystemSnapshot.value = true
  error.value = ''
  try {
    const systemSnapshotId = await projectStore.persistMySnapshotAsSystemSnapshot(projectId.value, saveAsSystemSnapshotId.value, {
      appId: saveAsSystemForm.appId,
      directory: saveAsSystemForm.directory.trim() || 'root',
      title: saveAsSystemForm.title.trim(),
      describe: saveAsSystemForm.describe.trim(),
      versionCycle: saveAsSystemForm.versionCycle || 30,
      labels: saveAsSystemForm.labels,
      principals: [],
    })
    const appId = saveAsSystemForm.appId
    savingSystemSnapshot.value = false
    closeSaveAsSystemDialog()
    if (appId && systemSnapshotId) {
      await router.push(`/p/${projectId.value}/apps/${appId}/snapshots/${systemSnapshotId}`)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存系统快照失败'
  } finally {
    savingSystemSnapshot.value = false
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

<style scoped src="@/features/snapshot/styles/my-snapshot-list-page.css"></style>
