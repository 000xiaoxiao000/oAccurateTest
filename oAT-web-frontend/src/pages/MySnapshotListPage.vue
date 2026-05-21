<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">My Snapshots</div>
        <h1>我的快照</h1>
        <p class="subtext">查看个人快照并批量关联用例。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/my-snapshots/code-report`">代码报告</RouterLink>
        <button class="action-button" type="button" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载我的快照...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <section class="toolbar-card">
        <div class="toolbar-actions">
          <input v-model="keywordDraft" class="text-input" type="text" placeholder="搜索快照名称..." />
          <select v-model="sortDraft" class="select">
            <option value="updateTime">按更新时间</option>
            <option value="name">按快照名称</option>
          </select>
          <select v-model="labelDrafts" class="select" multiple>
            <option v-for="label in payload.snapshotLabels" :key="label.name" :value="label.name">{{ label.name }}</option>
          </select>
          <button class="submit-button" type="button" @click="applyFilters">查询</button>
          <button class="ghost-button" type="button" @click="batchBindUsecases">批量关联用例</button>
          <button class="ghost-button" type="button" @click="selectAll">全选</button>
          <button class="ghost-button" type="button" @click="clearSelection">清空选择</button>
          <span class="selected-badge">已选 {{ selectedSnapshotIds.length }} 项</span>
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

      <div v-if="!payload.snapshots.length" class="empty-card">暂无我的快照</div>
      <div v-else class="snapshot-list">
        <article v-for="snapshot in payload.snapshots" :key="snapshot.id" class="snapshot-card">
          <label class="snapshot-select">
            <input v-model="selectedSnapshotIds" type="checkbox" :value="snapshot.id" />
          </label>
          <div class="snapshot-body">
            <div class="snapshot-top">
              <RouterLink class="snapshot-link" :to="`/p/${projectId}/my-snapshots/${snapshot.id}`">
                {{ snapshot.name || snapshot.id }}
              </RouterLink>
              <span class="meta-line">{{ snapshot.updateTimeText || '-' }}</span>
            </div>
            <p class="snapshot-desc">{{ snapshot.describe || '暂无说明' }}</p>
            <div class="tag-row">
              <span class="tag">标签 {{ snapshot.labels?.length || 0 }}</span>
              <span class="tag">覆盖 {{ snapshot.apiCoverageText || '-' }}</span>
            </div>
            <div class="snapshot-actions">
              <RouterLink class="ghost-button small" :to="`/p/${projectId}/my-snapshots/${snapshot.id}/report`">报告</RouterLink>
              <RouterLink class="ghost-button small" :to="`/p/${projectId}/my-snapshots/${snapshot.id}/graph`">链路图</RouterLink>
              <RouterLink class="ghost-button small" :to="`/p/${projectId}/my-snapshots/${snapshot.id}/report/code`">源码</RouterLink>
              <button class="ghost-button small" type="button" @click="openSingleUsecasePicker(snapshot.id)">关联用例</button>
              <button class="ghost-button small" type="button" @click="startEdit(snapshot)">编辑</button>
              <button class="ghost-button small" type="button" @click="toggleShare(snapshot)">{{ snapshot.share ? '关闭共享' : '开启共享' }}</button>
              <a v-if="snapshot.share" class="ghost-button small" :href="`/share/snapshot/${snapshot.id}`" target="_blank" rel="noreferrer">共享链接</a>
              <button class="danger-link" type="button" @click="remove(snapshot.id)">删除</button>
            </div>

            <form v-if="editingId === snapshot.id" class="edit-form" @submit.prevent="submitEdit(snapshot.id)">
              <label class="field">
                <span>快照名称</span>
                <input v-model.trim="editForm.name" class="text-input" type="text" />
              </label>
              <label class="field">
                <span>快照描述</span>
                <textarea v-model.trim="editForm.describe" class="text-input" rows="3" />
              </label>
              <label class="field">
                <span>标签</span>
                <select v-model="editForm.labels" class="select" multiple>
                  <option v-for="label in payload.snapshotLabels" :key="label.name" :value="label.name">{{ label.name }}</option>
                </select>
              </label>
              <div class="snapshot-actions">
                <button class="submit-button" type="submit">保存</button>
                <button class="ghost-button small" type="button" @click="cancelEdit">取消</button>
              </div>
            </form>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { SnapshotOption } from '@/api/types'
import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = computed(() => projectStore.mySnapshotListByProjectId[projectId.value])
const sortDraft = ref(String(route.query.sort || 'updateTime'))
const keywordDraft = ref(String(route.query.keyword || ''))
const labelDrafts = ref<string[]>(String(route.query.labels || '').split(',').filter(Boolean))
const selectedSnapshotIds = ref<string[]>([])
const editingId = ref('')
const loading = ref(false)
const error = ref('')
const usecasePickerOpen = ref(false)
const usecasePickerMode = ref<'single' | 'batch'>('single')
const usecasePickerSnapshotId = ref('')
const usecasePickerSelectedIds = ref<string[]>([])
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

function syncFilterDrafts() {
  sortDraft.value = String(route.query.sort || 'updateTime')
  keywordDraft.value = String(route.query.keyword || '')
  labelDrafts.value = String(route.query.labels || '').split(',').filter(Boolean)
}

function selectAll() {
  selectedSnapshotIds.value = payload.value?.snapshots.map((snapshot) => snapshot.id) || []
}

function clearSelection() {
  selectedSnapshotIds.value = []
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
  const confirmed = window.confirm('确认删除该快照？')
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
    load()
  },
)

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.toolbar-actions,
.snapshot-top,
.snapshot-actions {
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

.subtext,
.meta-line,
.snapshot-desc {
  color: #64748b;
}

.action-button,
.submit-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: #0f172a;
  color: #fff;
  cursor: pointer;
}

.submit-button {
  background: #0f766e;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 118, 110, 0.06);
  color: #0f766e;
  cursor: pointer;
}

.ghost-button.small {
  padding: 8px 12px;
}

.status-card,
.toolbar-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error,
.danger-link {
  color: #b91c1c;
}

.toolbar-card {
  margin-bottom: 18px;
}

.toolbar-actions,
.snapshot-actions {
  flex-wrap: wrap;
}

.selected-badge {
  padding: 9px 12px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-weight: 800;
}

.field,
.edit-form {
  display: grid;
  gap: 8px;
}

.field span {
  color: #475569;
  font-size: 13px;
}

.edit-form {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.snapshot-actions a {
  text-decoration: none;
}

.text-input,
.select {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.snapshot-list {
  display: grid;
  gap: 12px;
}

.snapshot-card {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.snapshot-select {
  padding-top: 6px;
}

.tag-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.09);
  color: #0f766e;
  font-size: 12px;
}

.snapshot-link {
  color: #0f172a;
  font-weight: 700;
}

.danger-link {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.empty-card {
  padding: 22px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

@media (max-width: 960px) {
  .page-header,
  .header-actions,
  .toolbar-actions,
  .snapshot-top,
  .snapshot-actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
