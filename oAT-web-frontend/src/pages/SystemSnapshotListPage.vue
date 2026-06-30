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
                      <div class="meta-row">
                        <span v-if="snapshot.versionNumber || snapshot.version" class="meta-badge version" :title="snapshot.versionNumber || snapshot.version">版本 {{ snapshot.versionNumber || snapshot.version }}</span>
                        <span v-if="snapshot.repoBranch" class="meta-badge branch" :title="snapshot.repoBranch">分支 {{ snapshot.repoBranch }}</span>
                        <span v-if="snapshot.repoCommitId" class="meta-badge commit" :title="snapshot.repoCommitId">Commit {{ abbreviateCommit(snapshot.repoCommitId) }}</span>
                        <span class="meta-badge" :title="snapshot.labels?.length ? `标签：${snapshot.labels.join('、')}` : '暂无标签'">标签 {{ snapshot.labels?.length || 0 }}</span>
                        <span class="meta-badge" :title="getPrincipalsTooltip(snapshot.principals)">负责人 {{ snapshot.principals?.length || 0 }}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span :title="snapshot.versionLastUpdateText || undefined">{{ snapshot.versionLastUpdateRelativeText || snapshot.versionLastUpdateText || '-' }}</span>
                  </td>
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
const usecasePickerTitle = computed(() => {
  if (usecasePickerMode.value === 'batch') return '批量关联用例'
  return '关联用例'
})
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

function abbreviateCommit(commitId?: string) {
  if (!commitId) return ''
  return commitId.length > 12 ? commitId.slice(0, 12) : commitId
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
    await Promise.all([
      projectStore.loadSystemSnapshotList(projectId.value, appId.value, {
        directoryId: currentDirectory.value,
        sort: currentSort.value,
        keyword: currentKeyword.value || undefined,
      }),
    ])
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

<style scoped src="@/features/snapshot/styles/system-snapshot-list-page.css"></style>
