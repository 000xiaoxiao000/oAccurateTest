<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">System Snapshots</div>
        <h1>{{ payload?.app.name || '系统快照' }}</h1>
        <p class="subtext">管理应用系统快照、目录和用例关联。</p>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" @click="openDirectoryEditor()">新建目录</button>
        <button class="action-button" type="button" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载系统快照...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <section class="toolbar-card">
        <div class="toolbar-path">
          <RouterLink :to="`/p/${projectId}/apps/${appId}/snapshots`">ROOT</RouterLink>
          <template v-for="tier in payload.directoryTiers" :key="tier.id">
            <span>/</span>
            <RouterLink :to="directoryLink(tier.id)">{{ tier.name }}</RouterLink>
          </template>
        </div>
        <form class="toolbar-actions" @submit.prevent="applyFilters">
          <input v-model="keywordDraft" class="text-input" type="text" placeholder="搜索快照名称..." />
          <select v-model="sortDraft" class="select">
            <option value="updateTime">按更新时间</option>
            <option value="name">按名称</option>
          </select>
          <button class="submit-button" type="submit">查询</button>
        </form>
      </section>

      <form v-if="directoryEditorOpen" class="inline-editor" @submit.prevent="submitDirectoryEditor">
        <label class="field inline-field">
          <span>{{ directoryEditorId ? '重命名目录' : '新建目录' }}</span>
          <input v-model.trim="directoryEditorName" class="text-input" type="text" placeholder="请输入目录名称" />
        </label>
        <button class="submit-button" type="submit">保存目录</button>
        <button class="ghost-button" type="button" @click="closeDirectoryEditor">取消</button>
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
        <aside class="side-card">
          <div class="card-title">
            <h2>目录</h2>
            <span>{{ payload.directories.length }}</span>
          </div>
          <div class="dir-list">
            <RouterLink class="dir-link root" :to="`/p/${projectId}/apps/${appId}/snapshots`">/ ROOT</RouterLink>
            <article v-for="dir in payload.directories" :key="dir.id" class="dir-link">
              <RouterLink :to="directoryLink(dir.id)">
                <strong>{{ dir.name }}</strong>
              </RouterLink>
              <div class="dir-actions">
                <button type="button" @click="openDirectoryEditor(dir.id, dir.name)">重命名</button>
                <button type="button" @click="removeDirectory(dir.id)">删除</button>
              </div>
            </article>
          </div>
        </aside>

        <section class="main-card">
          <div class="card-title">
            <h2>快照列表</h2>
            <div class="batch-actions">
              <span class="muted">已选 {{ selectedSnapshotIds.length }} 项</span>
              <button class="ghost-button small" type="button" @click="batchBindUsecases">批量关联用例</button>
            </div>
          </div>
          <div v-if="!payload.snapshots.length" class="empty-card">当前目录暂无系统快照</div>
          <div v-else class="snapshot-list">
            <article v-for="snapshot in payload.snapshots" :key="snapshot.id" class="snapshot-card">
              <label class="snapshot-select">
                <input v-model="selectedSnapshotIds" type="checkbox" :value="snapshot.id" />
              </label>
              <div class="snapshot-body">
                <div class="snapshot-top">
                  <RouterLink class="snapshot-title" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshot.id}`">
                    {{ snapshot.title }}
                  </RouterLink>
                  <span class="meta-line">{{ snapshot.versionLastUpdateRelativeText || snapshot.versionLastUpdateText || '-' }}</span>
                </div>
                <p class="snapshot-desc">{{ snapshot.describe || snapshot.subTitle || '暂无说明' }}</p>
                <div class="tag-row">
                  <span class="tag">版本 {{ snapshot.version || '-' }}</span>
                  <span class="tag">标签 {{ snapshot.labels?.length || 0 }}</span>
                  <span class="tag">负责人 {{ snapshot.principals?.length || 0 }}</span>
                  <span class="tag">报告状态 {{ reportStatusText(snapshot.reportStatus) }}</span>
                </div>
                <div class="snapshot-actions">
                  <button class="ghost-button small" type="button" @click="openSingleUsecasePicker(snapshot.id)">关联用例</button>
                  <RouterLink class="inline-link" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshot.id}`">
                    查看详情
                  </RouterLink>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import { useProjectStore } from '@/stores/project'
import { reportStatusText } from '@/utils/snapshot'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
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
const usecasePickerTitle = computed(() => (usecasePickerMode.value === 'batch' ? '批量关联用例' : '关联用例'))
const usecasePickerDescription = computed(() =>
  usecasePickerMode.value === 'batch'
    ? `将选中的 ${selectedSnapshotIds.value.length} 个系统快照统一关联到这些用例。`
    : '从项目用例中选择当前系统快照需要关联的条目。',
)

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
  const confirmed = window.confirm('确认删除该目录？')
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
    load()
  },
)

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.toolbar-card,
.toolbar-actions,
.card-title,
.snapshot-top,
.snapshot-actions,
.batch-actions,
.dir-actions {
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
.toolbar-card,
.side-card,
.main-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.toolbar-card {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.inline-editor {
  display: flex;
  align-items: end;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(240, 253, 250, 0.94);
  border: 1px solid rgba(15, 118, 110, 0.16);
}

.field {
  display: grid;
  gap: 8px;
}

.inline-field {
  min-width: min(360px, 100%);
  flex: 1;
}

.toolbar-path,
.toolbar-actions,
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.text-input,
.select {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.page-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

.dir-list,
.snapshot-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.dir-link,
.snapshot-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.dir-link.root {
  display: block;
}

.dir-link a,
.snapshot-title,
.inline-link {
  color: #0f172a;
  font-weight: 700;
}

.dir-actions button {
  border: none;
  background: transparent;
  color: #0f766e;
  cursor: pointer;
  padding: 0;
  font: inherit;
}

.snapshot-card {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 12px;
}

.snapshot-select {
  padding-top: 6px;
}

.tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.09);
  color: #0f766e;
  font-size: 12px;
}

.empty-card {
  padding: 22px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.muted {
  color: #64748b;
}

@media (max-width: 960px) {
  .page-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .toolbar-card,
  .toolbar-actions,
  .snapshot-top,
  .snapshot-actions,
  .card-title {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
