<template>
  <section class="snapshot-detail-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Snapshot Detail</div>
        <h1>{{ payload?.snapshot.title || '系统快照详情' }}</h1>
        <p class="subtext">{{ payload?.snapshot.describe || payload?.snapshot.subTitle || '暂无说明' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`">
          查看链路图
        </RouterLink>
        <RouterLink
          class="ghost-button"
          :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/report`"
        >
          覆盖率报告
        </RouterLink>
        <button class="danger-button" type="button" @click="removeSnapshot">删除快照</button>
        <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/snapshots`">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载系统快照详情...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="hero-card" :class="{ 'has-topic-image': payload.snapshot.topicImage }">
        <img v-if="payload.snapshot.topicImage" class="topic-image" :src="payload.snapshot.topicImage" alt="topic" />
        <div class="hero-main">
          <div class="hero-topline">
            <div class="tag-row">
              <span v-for="label in payload.labels" :key="label.name" class="tag" :style="{ '--tag-color': label.color || '#0f766e' }">
                {{ label.name }}
              </span>
              <span v-if="!payload.labels.length" class="tag muted">暂无标签</span>
            </div>
            <span class="status-pill" :class="statusTone(payload.snapshot.reportStatus)">
              {{ reportStatusText(payload.snapshot.reportStatus) }}
            </span>
          </div>
          <div class="meta-grid">
            <div class="meta-item">
              <span>版本</span>
              <strong>{{ payload.snapshot.version || '-' }}</strong>
            </div>
            <div class="meta-item">
              <span>更新时间</span>
              <strong>{{ payload.snapshot.versionLastUpdateText || '-' }}</strong>
            </div>
            <div class="meta-item">
              <span>负责人</span>
              <strong>{{ payload.selectedPrincipalIds.length || 0 }}</strong>
            </div>
            <div class="meta-item">
              <span>关联用例</span>
              <strong>{{ payload.usecases.length }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-grid">
        <section class="panel">
          <div class="card-title panel-heading">
            <div>
              <h2>基本信息</h2>
              <p>聚焦标题、版本、负责人和标签，其他信息收敛在下方卡片中。</p>
            </div>
            <div class="header-actions compact-actions">
              <button class="ghost-button primary-action" type="button" @click="saveBasic">保存基本信息</button>
              <button class="ghost-button" type="button" @click="openUsecasePicker">更新关联用例</button>
            </div>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>标题</span>
              <input v-model="form.title" class="text-input" type="text" />
            </label>
            <label class="field field-wide">
              <span>描述</span>
              <textarea v-model="form.describe" class="text-area" rows="4"></textarea>
            </label>
            <label class="field">
              <span>版本</span>
              <input v-model="form.version" class="text-input" type="text" />
            </label>
            <label class="field">
              <span>版本周期</span>
              <input v-model.number="form.versionCycle" class="text-input" type="number" min="0" />
            </label>
            <label class="field">
              <span>标签</span>
              <select v-model="form.labels" class="select" multiple>
                <option v-for="label in payload.labels" :key="label.name" :value="label.name">
                  {{ label.name }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>负责人</span>
              <select v-model="form.principals" class="select" multiple>
                <option v-for="member in payload.members" :key="member.memberId" :value="member.memberId">
                  {{ member.memberName || member.memberId }}
                </option>
              </select>
            </label>
          </div>
          <div class="info-list">
            <div class="info-item"><span>副标题</span><strong>{{ payload.snapshot.subTitle || '-' }}</strong></div>
            <div class="info-item"><span>更新时间</span><strong>{{ payload.snapshot.versionLastUpdateText || '-' }}</strong></div>
            <div class="info-item"><span>报告状态</span><strong>{{ reportStatusText(payload.snapshot.reportStatus) }}</strong></div>
          </div>
          <div class="action-row">
            <RouterLink class="inline-link" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`">
              打开完整链路图
            </RouterLink>
          </div>
          <div class="status-callout" :class="statusTone(payload.snapshot.reportStatus)">
            <strong>覆盖率状态：{{ reportStatusText(payload.snapshot.reportStatus) }}</strong>
            <span>{{ reportStatusHint(payload.snapshot.reportStatus) }}</span>
          </div>

          <div class="subsection usecase-section">
            <div class="subsection-title">
              <div>
                <h3>关联用例</h3>
                <p>长列表在卡片内滚动，保持编辑区和链路入口在同一屏可见。</p>
              </div>
              <button class="ghost-button small-button" type="button" @click="openUsecasePicker">调整用例</button>
            </div>
            <UsecasePicker
              v-model:open="usecasePickerOpen"
              title="更新关联用例"
              description="从项目用例中选择当前系统快照需要关联的条目。"
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

          <div class="subsection graph-preview-section graph-embed-section">
            <GraphView
              :eyebrow="'Snapshot Graph'"
              :fallback-title="'系统快照链路图'"
              :back-route="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`"
              :back-label="'查看完整图谱'"
              :loading="graphPreviewLoading"
              :loading-text="'正在加载链路图...'"
              :graph="graphPreview"
              :arrow-marker-id="'graph-preview-system'"
              compact
            />
            <div v-if="!graphPreviewLoading && !graphPreview" class="empty-card compact">暂无链路图数据</div>
          </div>
        </section>

        <aside class="side-stack" aria-label="系统快照侧栏信息">
          <section class="panel side-panel dynamic-panel">
            <div class="card-title panel-heading">
              <div>
                <h2>动态记录</h2>
                <p>评论和变更记录固定在侧栏内浏览。</p>
              </div>
            </div>
            <form class="comment-form" @submit.prevent="addComment">
              <textarea
                v-model="commentText"
                class="text-area"
                rows="3"
                placeholder="输入评论内容"
              ></textarea>
              <button class="ghost-button" type="submit">添加评论</button>
            </form>
            <div v-if="!payload.dynamics.length" class="empty-card compact">暂无动态记录</div>
            <div v-else class="dynamic-list">
              <article v-for="item in payload.dynamics" :key="`${item.type}-${item.date}-${item.describe}`" class="dynamic-card">
                <div class="dynamic-top">
                  <strong>{{ item.title || '未知用户' }}</strong>
                  <span>{{ item.time || '-' }}</span>
                </div>
                <div class="dynamic-type">{{ item.type || '-' }}</div>
                <p>{{ item.describe || '-' }}</p>
                <button
                  v-if="item.type === 'comment' && item.describe && item.date"
                  class="danger-link"
                  type="button"
                  @click="removeComment(item.describe, item.date)"
                >
                  删除评论
                </button>
              </article>
            </div>
          </section>

          <section class="panel side-panel">
            <div class="card-title panel-heading">
              <div>
                <h2>负责人</h2>
                <p>当前快照维护人。</p>
              </div>
            </div>
            <div class="member-list">
              <article
                v-for="member in principalMembers"
                :key="member.memberId"
                class="member-card"
              >
                <strong>{{ member.memberName || member.memberId }}</strong>
                <span>{{ member.role || '-' }}</span>
              </article>
              <div v-if="!principalMembers.length" class="empty-card compact">暂无负责人</div>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import GraphView from '@/components/snapshot/GraphView.vue'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'
import { reportStatusText } from '@/utils/snapshot'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const payload = computed(() => projectStore.systemSnapshotDetailByKey[storeKey.value])
const graphPreview = computed(() => projectStore.systemSnapshotGraphByKey[storeKey.value])
const principalMembers = computed(() =>
  (payload.value?.members || []).filter((member) => payload.value?.selectedPrincipalIds.includes(member.memberId)),
)
const loading = ref(false)
const error = ref('')
const commentText = ref('')
const usecasePickerOpen = ref(false)
const graphPreviewLoading = ref(false)

const form = ref({
  title: '',
  describe: '',
  version: '',
  versionCycle: 0 as number | undefined,
  labels: [] as string[],
  principals: [] as string[],
})

function reportStatusHint(status?: number) {
  switch (status) {
    case 1:
      return '报告任务正在处理，刷新当前页面或进入报告页可查看最新结果。'
    case 2:
      return '覆盖率报告已完成，可直接进入报告页查看类级和源码级详情。'
    case 3:
      return '最近一次报告生成失败，可在报告页重新触发计算。'
    default:
      return '当前还没有生成覆盖率报告，可进入报告页发起首次计算。'
  }
}

function statusTone(status?: number) {
  switch (status) {
    case 1:
      return 'warning'
    case 2:
      return 'success'
    case 3:
      return 'danger'
    default:
      return 'default'
  }
}

async function load() {
  if (!projectId.value || !appId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadSystemSnapshotDetail(projectId.value, appId.value, snapshotId.value)
    graphPreviewLoading.value = true
    projectStore.loadSystemSnapshotGraph(projectId.value, appId.value, snapshotId.value)
      .catch(() => undefined)
      .finally(() => {
        graphPreviewLoading.value = false
      })
    if (payload.value) {
      form.value = {
        title: payload.value.snapshot.title || '',
        describe: payload.value.snapshot.describe || '',
        version: payload.value.snapshot.version || '',
        versionCycle: payload.value.snapshot.versionCycle,
        labels: [...payload.value.selectedLabelNames],
        principals: [...payload.value.selectedPrincipalIds],
      }
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载系统快照详情失败'
  } finally {
    loading.value = false
  }
}

async function saveBasic() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateSystemSnapshotBasicInfo(projectId.value, appId.value, snapshotId.value, {
      title: form.value.title.trim(),
      describe: form.value.describe.trim(),
      version: form.value.version.trim(),
      versionCycle: form.value.versionCycle,
      labels: form.value.labels,
      principals: form.value.principals,
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存系统快照基本信息失败'
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
    await projectStore.updateSystemSnapshotUsecases(projectId.value, appId.value, snapshotId.value, usecaseIds)
    usecasePickerOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新系统快照关联用例失败'
  } finally {
    loading.value = false
  }
}

async function addComment() {
  if (!commentText.value.trim()) {
    error.value = '请输入评论内容'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.appendSystemSnapshotComment(projectId.value, appId.value, snapshotId.value, commentText.value.trim())
    commentText.value = ''
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '添加评论失败'
  } finally {
    loading.value = false
  }
}

async function removeComment(content: string, dateTime: string) {
  const confirmed = await dialog.confirm({
    title: '删除评论',
    message: '确认删除这条评论？删除后系统快照动态中将不再显示该记录。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeSystemSnapshotComment(projectId.value, appId.value, snapshotId.value, {
      content,
      dateTime: String(dateTime).slice(0, 19).replace('T', ' '),
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除评论失败'
  } finally {
    loading.value = false
  }
}

async function removeSnapshot() {
  const confirmed = await dialog.confirm({
    title: '删除系统快照',
    message: '确认删除该系统快照？覆盖率报告、链路图、评论和关联用例将不可恢复。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeSystemSnapshot(projectId.value, appId.value, snapshotId.value)
    await router.push(`/p/${projectId.value}/apps/${appId.value}/snapshots`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除系统快照失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.snapshot-detail-page {
  display: grid;
  gap: 18px;
}

.page-header,
.header-actions,
.card-title,
.dynamic-top,
.hero-topline,
.subsection-title {
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
.card-title h2,
.subsection-title h3 {
  margin: 0;
  color: #0f172a;
  letter-spacing: -0.03em;
}

.page-header h1 {
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.08;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.subtext,
.dynamic-card p,
.member-card span,
.panel-heading p,
.subsection-title p {
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
.inline-link,
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

.danger-button,
.danger-link {
  color: #b91c1c;
}

.danger-button {
  border: 1px solid rgba(185, 28, 28, 0.18);
  background: rgba(185, 28, 28, 0.06);
}

.danger-link {
  justify-self: start;
  border: 0;
  background: transparent;
  padding: 0;
  font-weight: 700;
  cursor: pointer;
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
  overflow: hidden;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, 0.14), transparent 32%),
    rgba(255, 255, 255, 0.95);
}

.hero-card.has-topic-image {
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr);
}

.hero-main {
  min-width: 0;
}

.topic-image {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 20px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.3);
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

.status-pill.warning {
  color: #c2410c;
  background: #fff7ed;
  border-color: rgba(234, 88, 12, 0.18);
}

.status-pill.danger {
  color: #b91c1c;
  background: #fef2f2;
  border-color: rgba(185, 28, 28, 0.18);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.meta-item,
.info-item,
.dynamic-card,
.member-card,
.usecase-card {
  min-width: 0;
  padding: 14px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.meta-item span,
.info-item span {
  color: #64748b;
  font-size: 13px;
}

.meta-item strong,
.info-item strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.meta-item strong {
  font-size: 20px;
  letter-spacing: -0.02em;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
  gap: 18px;
  align-items: start;
}

.side-stack,
.dynamic-list,
.member-list,
.usecase-list,
.form-grid {
  display: grid;
  gap: 12px;
}

.side-stack {
  position: sticky;
  top: 18px;
  max-height: calc(100vh - 36px);
  overflow: auto;
  padding-right: 2px;
}

.side-panel {
  padding: 16px;
}

.panel-heading {
  align-items: flex-start;
  margin-bottom: 14px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field-wide {
  grid-column: 1 / -1;
}

.info-list,
.action-row,
.comment-form {
  display: grid;
  gap: 12px;
}

.info-list {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 14px;
}

.action-row {
  grid-template-columns: repeat(auto-fit, minmax(180px, max-content));
  margin-top: 16px;
}

.graph-embed-section {
  margin-top: 18px;
}

.status-callout {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #f8fbfb;
}

.status-callout span {
  color: #64748b;
}

.status-callout.success {
  background: rgba(240, 253, 244, 0.92);
  border-color: rgba(22, 163, 74, 0.18);
}

.status-callout.warning {
  background: rgba(255, 247, 237, 0.92);
  border-color: rgba(234, 88, 12, 0.18);
}

.status-callout.danger {
  background: rgba(254, 242, 242, 0.92);
  border-color: rgba(185, 28, 28, 0.18);
}

.field {
  display: grid;
  gap: 8px;
  min-width: 0;
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

.subsection {
  margin-top: 18px;
}

.subsection-title {
  align-items: flex-start;
  margin-bottom: 12px;
}

.usecase-list {
  max-height: 320px;
  overflow: auto;
  padding-right: 4px;
}

.usecase-card {
  display: grid;
  gap: 4px;
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
  color: #64748b;
  font-size: 13px;
}

.comment-form {
  margin-bottom: 12px;
}

.comment-form .ghost-button {
  justify-self: end;
}

.dynamic-list {
  max-height: min(430px, 48vh);
  overflow: auto;
  padding-right: 4px;
}

.dynamic-card {
  display: grid;
  gap: 8px;
}

.dynamic-top {
  align-items: flex-start;
}

.dynamic-top span {
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}

.dynamic-card p {
  margin: 0;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.dynamic-type {
  justify-self: start;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  border-radius: 999px;
  padding: 4px 8px;
  background: rgba(15, 118, 110, 0.08);
}

.member-card {
  display: grid;
  gap: 4px;
}

.empty-card {
  padding: 22px;
  border-radius: 18px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.empty-card.compact {
  padding: 14px;
}

@media (max-width: 1180px) {
  .page-header,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .side-stack {
    position: static;
    max-height: none;
    overflow: visible;
  }
}

@media (max-width: 960px) {
  .hero-card.has-topic-image,
  .form-grid,
  .info-list {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .card-title,
  .subsection-title {
    flex-direction: column;
    align-items: stretch;
  }

  .compact-actions,
  .comment-form .ghost-button {
    justify-self: stretch;
  }
}
</style>
