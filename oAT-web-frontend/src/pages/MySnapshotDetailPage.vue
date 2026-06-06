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
        <RouterLink class="secondary-link" :to="`/p/${projectId}/my-snapshots`">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载我的快照详情...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="hero-card">
        <div class="hero-topline">
          <div class="tag-row">
            <span
              v-for="label in payload.labels"
              :key="label.name"
              class="tag"
              :style="{ '--tag-color': label.color || '#0f766e' }"
            >{{ label.name }}</span>
            <span v-if="!payload.labels.length" class="tag muted">暂无标签</span>
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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

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
const absoluteShareUrl = computed(() => {
  if (!payload.value?.shareUrl) {
    return ''
  }
  return `${window.location.origin}${payload.value.shareUrl}`
})
const loading = ref(false)
const error = ref('')
const usecasePickerOpen = ref(false)
const graphPreviewLoading = ref(false)

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
  form.labels = [...(payload.value.snapshot.labels || [])]
}

async function load() {
  if (!projectId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
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

<style scoped>
.my-snapshot-detail-page {
  display: grid;
  gap: 18px;
}

.page-header,
.header-actions,
.card-title,
.hero-topline,
.subsection-title,
.share-toggle-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 2px;
  align-items: flex-start;
}

.page-header h1,
.card-title h2 {
  margin: 0;
  color: #0f172a;
  letter-spacing: -0.03em;
  font-size: 15px;
  font-weight: 800;
}

.subsection-title h3 {
  margin: 0;
  color: #0f172a;
  letter-spacing: -0.03em;
}

.page-header h1 {
  font-size: clamp(20px, 3vw, 26px);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.subtext,
.panel-heading p,
.subsection-title p,
.share-toggle-row p,
.member-card span,
.usecase-card span {
  color: #64748b;
}

.subtext,
.panel-heading p,
.subsection-title p {
  margin: 6px 0 0;
  line-height: 1.6;
}

.header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.compact-actions {
  flex-shrink: 0;
}

.secondary-link,
.ghost-button,
.danger-button {
  color: #0f766e;
  font-weight: 800;
  text-decoration: none;
}

.ghost-button,
.danger-button {
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, 0.18);
  background: rgba(15, 118, 110, 0.07);
}

.ghost-button:hover,
.danger-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.primary-action {
  color: #fff;
  border-color: #0f766e;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.small-button {
  padding: 8px 12px;
  font-size: 13px;
}

.danger-button {
  color: #b91c1c;
  border: 1px solid rgba(185, 28, 28, 0.18);
  background: rgba(185, 28, 28, 0.06);
}

.status-card,
.hero-card,
.panel {
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.status-card.error {
  color: #b91c1c;
}

.hero-card {
  display: grid;
  gap: 18px;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, 0.14), transparent 32%),
    rgba(255, 255, 255, 0.95);
}

.hero-topline {
  align-items: flex-start;
  flex-wrap: wrap;
}

.tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.tag,
.status-pill {
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.tag {
  --tag-color: #0f766e;
  padding: 6px 10px;
  background: color-mix(in srgb, var(--tag-color) 14%, white);
  color: var(--tag-color);
}

.tag.muted {
  --tag-color: #64748b;
}

.status-pill {
  padding: 7px 11px;
  color: #475569;
  background: #f1f5f9;
  border: 1px solid rgba(100, 116, 139, 0.18);
}

.status-pill.success {
  color: #15803d;
  background: #f0fdf4;
  border-color: rgba(22, 163, 74, 0.18);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.meta-item {
  padding: 14px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
  min-width: 0;
}

.meta-item span {
  color: #64748b;
  font-size: 13px;
}

.meta-item strong {
  display: block;
  margin-top: 6px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #0f172a;
  overflow-wrap: anywhere;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: 18px;
  align-items: start;
}

.side-stack {
  display: grid;
  gap: 12px;
  position: sticky;
  top: 18px;
}

.side-panel {
  padding: 16px;
}

.panel-heading {
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--oat-border);
  margin-bottom: 14px;
}

.panel-heading h2 {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.panel-heading p {
  font-size: 13px;
}

.form-grid {
  display: grid;
  gap: 16px;
}

.field-wide {
  grid-column: 1 / -1;
}

.field {
  display: grid;
  gap: 8px;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.text-input,
.text-area,
.select {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 14px;
  padding: 11px 12px;
  background: #fff;
  color: #0f172a;
  font: inherit;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  box-sizing: border-box;
}

.text-input:focus,
.text-area:focus,
.select:focus {
  border-color: rgba(15, 118, 110, 0.45);
  box-shadow: 0 0 0 4px rgba(20, 184, 166, 0.12);
}

.text-area {
  resize: vertical;
  min-height: 92px;
}

.select[multiple] {
  min-height: 112px;
}

.share-section {
  margin-top: 20px;
  padding: 16px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
  display: grid;
  gap: 12px;
}

.share-toggle-row {
  align-items: flex-start;
}

.share-toggle-row div {
  display: grid;
  gap: 4px;
}

.share-toggle-row strong {
  color: #0f172a;
  font-size: 14px;
}

.share-toggle-row p {
  margin: 0;
  font-size: 13px;
}

.share-url-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.toggle-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  flex-shrink: 0;
}

.toggle-switch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-track {
  display: block;
  width: 44px;
  height: 24px;
  border-radius: 999px;
  background: #cbd5e1;
  transition: background 0.18s ease;
}

.toggle-track::after {
  content: '';
  display: block;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
  margin: 3px;
  transition: transform 0.18s ease;
}

.toggle-switch input:checked + .toggle-track {
  background: #0f766e;
}

.toggle-switch input:checked + .toggle-track::after {
  transform: translateX(20px);
}

.subsection {
  margin-top: 20px;
}

.subsection-title {
  align-items: flex-start;
  margin-bottom: 12px;
}

.graph-embed-section {
  margin-top: 20px;
}

.usecase-list {
  display: grid;
  gap: 10px;
  max-height: 320px;
  overflow: auto;
  padding-right: 4px;
}

.usecase-card {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
  color: #0f172a;
  text-decoration: none;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.usecase-card:hover {
  transform: translateY(-1px);
  border-color: rgba(15, 118, 110, 0.22);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.08);
}

.usecase-card span {
  font-size: 13px;
}

.member-card {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.member-card strong {
  color: #0f172a;
}

.empty-card {
  padding: 22px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.empty-card.compact {
  padding: 14px;
}

@media (max-width: 1180px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .side-stack {
    position: static;
  }
}

@media (max-width: 960px) {
  .page-header,
  .header-actions,
  .card-title,
  .subsection-title {
    flex-direction: column;
    align-items: stretch;
  }

  .compact-actions {
    justify-self: stretch;
  }

  .share-url-row {
    grid-template-columns: 1fr;
  }
}
</style>
