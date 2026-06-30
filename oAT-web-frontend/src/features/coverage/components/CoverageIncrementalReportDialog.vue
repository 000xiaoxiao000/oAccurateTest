<template>
  <div v-if="open" class="modal-mask" @click.self="close">
    <form class="modal-card incremental-modal" @submit.prevent="submit">
      <div class="modal-head">
        <div>
          <div class="eyebrow">Incremental Coverage</div>
          <h2>生成增量覆盖率报告</h2>
          <p class="subtext">选择一个基准报告进行差异计算，也可手动指定基准版本与 Commit。</p>
        </div>
        <button class="modal-close" type="button" aria-label="关闭生成增量报告弹窗" @click="close">×</button>
      </div>

      <div class="incremental-summary-grid">
        <article class="summary-tile current">
          <span>当前版本</span>
          <strong>{{ currentVersionNumber || '-' }}</strong>
          <small :title="commitTooltip(currentCommitId)">Commit {{ shortHash(currentCommitId) }}</small>
        </article>
        <article class="summary-tile base">
          <span>基准版本</span>
          <strong>{{ form.baseVersionNumber || '未选择' }}</strong>
          <small :title="commitTooltip(form.baseCommitId)">Commit {{ shortHash(form.baseCommitId) }}</small>
        </article>
      </div>

      <section class="modal-section">
        <div class="modal-section-head">
          <div>
            <h3>选择基准报告</h3>
            <p class="subtext">优先选择已有全量报告，系统会自动填充基准版本号与 Commit。</p>
          </div>
          <span class="count-badge">{{ baseReportOptions.length }} 个可选</span>
        </div>
        <div v-if="baseLoading" class="base-report-empty">正在加载基准报告...</div>
        <div v-else-if="baseReportOptions.length" class="base-report-list">
          <button
            v-for="report in baseReportOptions"
            :key="baseReportKey(report)"
            class="base-report-option"
            :class="{ active: baseReportKey(report) === form.baseReportId }"
            type="button"
            @click="selectBaseReport(report)"
          >
            <span>
              <strong>{{ report.versionNumber || '-' }}</strong>
              <small>{{ report.repoBranch || '-' }} · {{ report.createTimeText || '-' }}</small>
            </span>
            <code :title="commitTooltip(report.repoCommitId)">{{ shortHash(report.repoCommitId) }}</code>
          </button>
        </div>
        <div v-else class="base-report-empty">暂无可选基准报告，请手动填写基准版本号。</div>
      </section>

      <section class="modal-section">
        <div class="modal-section-head compact">
          <h3>手动确认基准信息</h3>
        </div>
        <div class="incremental-form-grid">
          <label class="field">
            <span>基准版本号</span>
            <input v-model.trim="form.baseVersionNumber" class="text-input" type="text" placeholder="例如 v1.0.0" />
          </label>
          <label class="field">
            <span>基准 Commit</span>
            <input v-model.trim="form.baseCommitId" class="text-input" type="text" placeholder="可为空，默认使用基准报告提交" />
          </label>
        </div>
      </section>

      <div v-if="displayError" class="modal-error">{{ displayError }}</div>
      <div class="modal-actions">
        <button class="ghost-button" type="button" :disabled="generating" @click="close">取消</button>
        <button class="primary-button" type="submit" :disabled="generating || !form.baseVersionNumber">
          {{ generating ? '处理中...' : '确认生成' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fetchVersionCenter } from '@/api/bootstrap'

export type IncrementalBaseReport = {
  id?: string
  versionNumber?: string
  repoBranch?: string
  repoCommitId?: string
  createTimeText?: string
  reportType?: number
  baseVersionNumber?: string
  baseRepoCommitId?: string
}

const props = defineProps<{
  open: boolean
  generating: boolean
  projectId: string
  appId: string
  currentVersionNumber: string
  currentCommitId?: string
  initialReports: IncrementalBaseReport[]
  defaultReport: IncrementalBaseReport | null
  error: string
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'submit', payload: { baseVersionNumber: string; baseCommitId?: string }): void
  (event: 'error', message: string): void
}>()

const baseLoading = ref(false)
const loadedReports = ref<IncrementalBaseReport[]>([])
const form = ref({ baseVersionNumber: '', baseCommitId: '', baseReportId: '' })
const localError = ref('')
const displayError = computed(() => props.error || localError.value)

const baseReportOptions = computed(() => {
  const candidates = [
    ...(props.defaultReport ? [props.defaultReport] : []),
    ...props.initialReports,
    ...loadedReports.value,
  ]
  const seen = new Set<string>()
  return candidates
    .filter((report) => report.versionNumber && !isIncrementalReport(report))
    .filter((report) => {
      const key = baseReportKey(report)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
})

watch(() => props.open, async (open) => {
  if (!open) return
  localError.value = ''
  loadedReports.value = []
  if (props.defaultReport) {
    selectBaseReport(props.defaultReport)
  } else {
    form.value = { baseVersionNumber: '', baseCommitId: '', baseReportId: '' }
  }
  await loadBaseReports()
  if (!props.defaultReport && baseReportOptions.value.length) {
    selectBaseReport(baseReportOptions.value[0])
  }
})

function close() {
  if (props.generating) return
  localError.value = ''
  emit('close')
}

function submit() {
  if (!form.value.baseVersionNumber) {
    localError.value = '请填写基准版本号'
    return
  }
  localError.value = ''
  emit('submit', {
    baseVersionNumber: form.value.baseVersionNumber,
    baseCommitId: form.value.baseCommitId || undefined,
  })
}

async function loadBaseReports() {
  if (!props.projectId || !props.appId) return
  baseLoading.value = true
  try {
    const center = await fetchVersionCenter(props.projectId, props.appId)
    loadedReports.value = center.coverageReports || []
  } catch (err) {
    const message = err instanceof Error ? err.message : '加载基准报告失败，可手动填写基准信息'
    localError.value = message
    emit('error', message)
  } finally {
    baseLoading.value = false
  }
}

function baseReportKey(report: IncrementalBaseReport) {
  return report.id || `${report.versionNumber || ''}:${report.repoCommitId || ''}`
}

function selectBaseReport(report: IncrementalBaseReport) {
  form.value = {
    baseVersionNumber: report.versionNumber || '',
    baseCommitId: report.repoCommitId || '',
    baseReportId: baseReportKey(report),
  }
  localError.value = ''
}

function isIncrementalReport(report: IncrementalBaseReport) {
  return Number(report.reportType) === 1 || Boolean(report.baseVersionNumber || report.baseRepoCommitId)
}

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .52);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(720px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  display: grid;
  gap: 14px;
  padding: 22px;
  border-radius: 24px;
  background: rgba(255, 255, 255, .98);
  border: 1px solid rgba(15, 23, 42, .08);
  box-shadow: 0 28px 70px rgba(15, 23, 42, .24);
}

.incremental-modal {
  width: min(820px, calc(100vw - 32px));
}

.modal-head,
.modal-section-head,
.modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.modal-head {
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(15, 23, 42, .08);
}

.modal-head h2,
.modal-head .subtext,
.modal-section-head h3,
.modal-section-head .subtext {
  margin: 0;
}

.modal-head h2 {
  margin-top: 4px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.subtext,
.field span,
.summary-tile span,
.summary-tile small,
.base-report-option small,
.base-report-option code,
.base-report-empty {
  color: #64748b;
}

.modal-close {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, .06);
  color: #334155;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.modal-close:hover {
  background: rgba(220, 38, 38, .10);
  color: #dc2626;
}

.incremental-summary-grid,
.incremental-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-tile {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(15, 23, 42, .08);
  background: rgba(248, 250, 252, .86);
}

.summary-tile.current {
  border-color: rgba(15, 118, 110, .18);
  background: rgba(240, 253, 250, .86);
}

.summary-tile.base {
  border-color: rgba(37, 99, 235, .14);
  background: rgba(239, 246, 255, .82);
}

.summary-tile span,
.summary-tile small,
.field span {
  font-weight: 700;
}

.summary-tile strong,
.summary-tile small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-section {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, .08);
  background: rgba(248, 250, 252, .64);
}

.modal-section-head.compact {
  justify-content: flex-start;
}

.count-badge {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, .08);
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
}

.base-report-list {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow: auto;
  padding-right: 4px;
}

.base-report-option {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  padding: 12px;
  background: rgba(255, 255, 255, .88);
  text-align: left;
  cursor: pointer;
}

.base-report-option:hover,
.base-report-option.active {
  border-color: rgba(15, 118, 110, .26);
  background: rgba(240, 253, 250, .92);
}

.base-report-option span,
.field {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.base-report-option strong,
.base-report-option small,
.base-report-option code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.base-report-option code {
  max-width: 180px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 14px;
  padding: 10px 12px;
}

.base-report-empty,
.modal-error {
  padding: 12px 14px;
  border-radius: 16px;
}

.base-report-empty {
  background: rgba(241, 245, 249, .9);
  text-align: center;
}

.modal-error {
  border: 1px solid rgba(185, 28, 28, .14);
  background: rgba(254, 242, 242, .92);
  color: #b91c1c;
  font-weight: 800;
}

.modal-actions {
  justify-content: flex-end;
  padding-top: 4px;
}

.primary-button,
.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  text-decoration: none;
}

.primary-button {
  background: #0f172a;
  color: #fff;
}

.ghost-button {
  background: rgba(15, 23, 42, 0.08);
}

@media (max-width: 960px) {
  .incremental-summary-grid,
  .incremental-form-grid,
  .base-report-option {
    grid-template-columns: 1fr;
  }

  .base-report-option code {
    max-width: 100%;
  }
}
</style>
