<template>
  <section class="my-snapshot-detail-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">My Snapshot</div>
        <h1>{{ payload?.snapshot.name || '我的快照详情' }}</h1>
        <p class="subtext">{{ payload?.snapshot.describe || '暂无描述' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/my-snapshots/${snapshotId}/report`">查看覆盖率报告</RouterLink>
        <RouterLink class="ghost-button" :to="`/p/${projectId}/my-snapshots/${snapshotId}/graph`">查看链路图</RouterLink>
        <button class="ghost-button primary-action" type="button" @click="openSaveAsSystemDialog">保存为系统快照</button>
        <RouterLink class="secondary-link" :to="`/p/${projectId}/my-snapshots`">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载我的快照详情...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="hero-card">
        <div class="hero-topline">
          <div class="tag-row snapshot-meta-strip">
            <span v-if="payload.snapshot.versionNumber" class="meta-pill" :title="payload.snapshot.versionNumber">版本 {{ payload.snapshot.versionNumber }}</span>
            <span v-if="payload.snapshot.repoBranch" class="meta-pill branch" :title="payload.snapshot.repoBranch">分支 {{ payload.snapshot.repoBranch }}</span>
            <span v-if="payload.snapshot.repoCommitId" class="meta-pill commit" :title="payload.snapshot.repoCommitId">Commit {{ abbreviateCommit(payload.snapshot.repoCommitId) }}</span>
            <span
              v-for="label in selectedSnapshotLabels"
              :key="label.name"
              class="tag"
              :style="{ '--tag-color': label.color || '#0f766e' }"
            >{{ label.name }}</span>
            <span v-if="!selectedSnapshotLabels.length" class="tag muted">暂无标签</span>
          </div>
          <span v-if="payload.snapshot.share" class="status-pill success">已共享</span>
          <span v-else class="status-pill">未共享</span>
        </div>
        <div class="meta-grid">
          <div class="meta-item">
            <span>创建者</span>
            <strong>{{ payload.createUser?.nickname || payload.createUser?.name || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>关联用例</span>
            <strong>{{ payload.usecases.length }}</strong>
          </div>
          <div class="meta-item">
            <span>接口覆盖</span>
            <strong>{{ payload.snapshot.apiCoverageText || '0 / 0' }}</strong>
          </div>
        </div>
      </div>

      <div class="detail-grid">
        <section class="panel">
          <div class="card-title panel-heading">
            <div>
              <h2>基本信息</h2>
              <p>修改名称、描述和标签后点击保存生效。</p>
            </div>
            <div class="header-actions compact-actions">
              <button class="ghost-button primary-action" type="button" @click="saveBasic">保存基本信息</button>
              <button class="ghost-button" type="button" @click="openUsecasePicker">更新关联用例</button>
            </div>
          </div>
          <div class="form-grid">
            <label class="field field-wide">
              <span>名称</span>
              <input v-model="form.name" class="text-input" type="text" />
            </label>
            <label class="field field-wide">
              <span>描述</span>
              <textarea v-model="form.describe" class="text-area" rows="4"></textarea>
            </label>
            <label class="field field-wide">
              <span>标签</span>
              <select v-model="form.labels" class="select" multiple>
                <option v-for="label in payload.labels" :key="label.name" :value="label.name">{{ label.name }}</option>
              </select>
            </label>
          </div>

          <div class="share-section">
            <div class="share-toggle-row">
              <div>
                <strong>共享链接</strong>
                <p>开启后其他用户可通过链接访问该快照。</p>
              </div>
              <label class="toggle-switch">
                <input :checked="Boolean(payload.snapshot.share)" type="checkbox" @change="toggleShare" />
                <span class="toggle-track"></span>
              </label>
            </div>
            <div v-if="payload.shareUrl && payload.snapshot.share" class="share-url-row">
              <input :value="absoluteShareUrl" class="text-input" readonly @focus="selectCurrentTarget" />
              <a class="ghost-button" :href="payload.shareUrl" target="_blank" rel="noreferrer">打开链接</a>
            </div>
          </div>

          <div class="subsection">
            <div class="subsection-title">
              <div>
                <h3>关联用例</h3>
                <p>当前快照关联的测试用例列表。</p>
              </div>
              <button class="ghost-button small-button" type="button" @click="openUsecasePicker">调整用例</button>
            </div>
            <UsecasePicker
              v-model:open="usecasePickerOpen"
              title="更新关联用例"
              description="从项目用例中选择当前我的快照需要关联的条目。"
              :usecases="payload.allUsecases"
              :selected-ids="payload.usecases.map((usecase) => usecase.id)"
              :busy="loading"
              @submit="bindUsecases"
            />
            <div v-if="!payload.usecases.length" class="empty-card compact">暂无关联用例</div>
            <div v-else class="usecase-list">
              <RouterLink
                v-for="usecase in payload.usecases"
                :key="usecase.id"
                class="usecase-card"
                :to="`/p/${projectId}/usecases/${usecase.id}`"
              >
                <strong>{{ usecase.title }}</strong>
                <span>{{ usecase.updateTimeText || '-' }}</span>
              </RouterLink>
            </div>
          </div>

          <div class="subsection graph-embed-section">
            <GraphView
              :eyebrow="'My Snapshot Graph'"
              :fallback-title="'我的快照链路图'"
              :back-route="`/p/${projectId}/my-snapshots/${snapshotId}/graph`"
              :back-label="'打开完整图谱'"
              :loading="graphPreviewLoading"
              :loading-text="'正在加载链路图...'"
              :graph="graphPreview"
              :arrow-marker-id="'graph-preview-my'"
              compact
            />
            <div v-if="!graphPreviewLoading && !graphPreview" class="empty-card compact">暂无链路图数据</div>
          </div>
        </section>

        <aside class="side-stack" aria-label="快照侧栏信息">
          <section class="panel side-panel">
            <div class="card-title panel-heading">
              <div><h2>创建者</h2></div>
            </div>
            <div class="member-card">
              <strong>{{ payload.createUser?.nickname || payload.createUser?.name || '-' }}</strong>
              <span>{{ payload.createUser?.email || '-' }}</span>
            </div>
          </section>

          <section class="panel side-panel">
            <div class="card-title panel-heading">
              <div>
                <h2>危险操作</h2>
                <p>删除后覆盖率报告、链路图和关联用例将不可恢复。</p>
              </div>
            </div>
            <button class="danger-button" type="button" @click="removeSnapshot">删除快照</button>
          </section>
        </aside>
      </div>
    </template>


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
                  v-for="label in availableSnapshotLabels"
                  :key="label.name"
                  :class="['label-chip', { active: saveAsSystemForm.labels.includes(label.name) }]"
                  type="button"
                  @click="toggleSaveAsSystemLabel(label.name)"
                >
                  {{ label.name }}
                </button>
                <span v-if="!availableSnapshotLabels.length" class="label-empty">暂无可选标签</span>
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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import type { AppSummary } from '@/api/types'
import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import GraphView from '@/components/snapshot/GraphView.vue'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const payload = computed(() => projectStore.mySnapshotDetailByKey[storeKey.value])
const graphPreview = computed(() => projectStore.mySnapshotGraphByKey[storeKey.value])
const selectedSnapshotLabels = computed(() => {
  const selectedNames = payload.value?.selectedLabelNames || payload.value?.snapshot.labels || []
  const labelMap = new Map((payload.value?.labels || []).map((label) => [label.name, label]))
  return selectedNames.map((name) => labelMap.get(name) || { name, color: '' })
})
const availableSnapshotLabels = computed(() => {
  const labelMap = new Map<string, { name: string; color?: string }>()
  payload.value?.labels?.forEach((label) => labelMap.set(label.name, label))
  saveAsSystemForm.labels.forEach((name) => {
    if (name && !labelMap.has(name)) {
      labelMap.set(name, { name, color: '' })
    }
  })
  return [...labelMap.values()]
})
const absoluteShareUrl = computed(() => {
  if (!payload.value?.shareUrl) {
    return ''
  }
  return `${window.location.origin}${payload.value.shareUrl}`
})
const availableApps = computed<AppSummary[]>(() => projectStore.contextByProjectId[projectId.value]?.apps || [])
const loading = ref(false)
const error = ref('')
const usecasePickerOpen = ref(false)
const graphPreviewLoading = ref(false)
const saveAsSystemOpen = ref(false)
const savingSystemSnapshot = ref(false)
const saveAsSystemForm = reactive({
  appId: '',
  directory: 'root',
  title: '',
  describe: '',
  versionCycle: 30,
  labels: [] as string[],
})

const form = reactive({
  name: '',
  describe: '',
  labels: [] as string[],
})

function syncForm() {
  if (!payload.value) {
    return
  }
  form.name = payload.value.snapshot.name || ''
  form.describe = payload.value.snapshot.describe || ''
  form.labels = [...(payload.value.selectedLabelNames || payload.value.snapshot.labels || [])]
}

function abbreviateCommit(commitId?: string) {
  if (!commitId) return ''
  return commitId.length > 12 ? commitId.slice(0, 12) : commitId
}

async function load() {
  if (!projectId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadProjectContext(projectId.value)
    await projectStore.loadMySnapshotDetail(projectId.value, snapshotId.value)
    syncForm()
    graphPreviewLoading.value = true
    try {
      await projectStore.loadMySnapshotGraph(projectId.value, snapshotId.value)
    } catch (graphErr) {
      console.error('Failed to load graph:', graphErr)
    } finally {
      graphPreviewLoading.value = false
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照详情失败'
  } finally {
    loading.value = false
  }
}

async function saveBasic() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateMySnapshotBasicInfo(projectId.value, snapshotId.value, {
      name: form.name.trim(),
      describe: form.describe.trim(),
      labels: form.labels,
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新我的快照失败'
  } finally {
    loading.value = false
  }
}

function openUsecasePicker() {
  usecasePickerOpen.value = true
}

async function bindUsecases(usecaseIds: string[]) {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateMySnapshotUsecases(projectId.value, snapshotId.value, usecaseIds)
    usecasePickerOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新我的快照关联用例失败'
  } finally {
    loading.value = false
  }
}

async function toggleShare(event: Event) {
  const target = event.target as HTMLInputElement
  loading.value = true
  error.value = ''
  try {
    await projectStore.changeMySnapshotShare(projectId.value, snapshotId.value, target.checked)
    await load()
  } catch (err) {
    target.checked = !target.checked
    error.value = err instanceof Error ? err.message : '更新共享状态失败'
  } finally {
    loading.value = false
  }
}

function selectCurrentTarget(event: Event) {
  const target = event.target as HTMLInputElement
  target.select()
}

function openSaveAsSystemDialog() {
  if (!payload.value) {
    void load().then(() => {
      if (payload.value) {
        openSaveAsSystemDialog()
      }
    })
    return
  }
  const snapshot = payload.value.snapshot
  saveAsSystemForm.appId = snapshot.appId || availableApps.value[0]?.id || ''
  saveAsSystemForm.directory = 'root'
  saveAsSystemForm.title = snapshot.name || snapshot.title || ''
  saveAsSystemForm.describe = snapshot.describe || ''
  saveAsSystemForm.versionCycle = 30
  saveAsSystemForm.labels = [...(payload.value.selectedLabelNames || snapshot.labels || [])]
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
}

async function submitSaveAsSystem() {
  if (!projectId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
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
    const systemSnapshotId = await projectStore.persistMySnapshotAsSystemSnapshot(projectId.value, snapshotId.value, {
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

async function removeSnapshot() {
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
    await projectStore.removeMySnapshot(projectId.value, snapshotId.value)
    await router.push(`/p/${projectId.value}/my-snapshots`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除我的快照失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped src="@/features/snapshot/styles/my-snapshot-detail-page.css"></style>
