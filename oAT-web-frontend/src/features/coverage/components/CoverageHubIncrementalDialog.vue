<template>
  <div v-if="open && version" class="modal-mask" @click.self="close">
    <form class="modal-card incremental-modal" @submit.prevent="submit">
      <div class="modal-head">
        <div>
          <div class="eyebrow">Version Incremental</div>
          <h2>生成版本增量报告</h2>
          <p class="subtext">选择基准报告或手动指定基准版本，生成当前版本相对基准的增量覆盖率。</p>
        </div>
        <button class="modal-close" type="button" aria-label="关闭生成版本增量弹窗" @click="close">×</button>
      </div>

      <div class="incremental-summary-grid">
        <article class="summary-tile current">
          <span>当前版本</span>
          <strong>{{ version.versionNumber || '-' }}</strong>
          <small :title="commitTooltip(version.repoCommitId)">Commit {{ shortHash(version.repoCommitId) }}</small>
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
            <p class="subtext">优先选择已有版本全量报告，系统会自动填充基准版本和 Commit。</p>
          </div>
          <span class="count-badge">{{ baseOptions.length }} 个可选</span>
        </div>
        <div v-if="baseOptions.length" class="base-report-list">
          <button
            v-for="report in baseOptions"
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
            <input v-model.trim="form.baseCommitId" class="text-input" type="text" placeholder="可为空，默认使用基准版本" />
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
import type { CoverageReportCard, VersionItemSummary } from '@/api/types'

const props = defineProps<{
  open: boolean
  version: VersionItemSummary | null
  baseOptions: CoverageReportCard[]
  generating: boolean
  error: string
  commitTooltip: (value?: string) => string
  shortHash: (value?: string) => string
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'submit', payload: { baseVersionNumber: string; baseCommitId?: string }): void
  (event: 'clear-error'): void
}>()

const form = ref({ baseVersionNumber: '', baseCommitId: '', baseReportId: '' })
const localError = ref('')
const displayError = computed(() => props.error || localError.value)

watch(() => props.open, (open) => {
  if (!open) return
  localError.value = ''
  form.value = { baseVersionNumber: '', baseCommitId: '', baseReportId: '' }
  if (props.baseOptions.length) {
    selectBaseReport(props.baseOptions[0])
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
  emit('clear-error')
  emit('submit', {
    baseVersionNumber: form.value.baseVersionNumber,
    baseCommitId: form.value.baseCommitId || undefined,
  })
}

function baseReportKey(report: CoverageReportCard) {
  return report.id || `${report.versionNumber || ''}:${report.repoCommitId || ''}`
}

function selectBaseReport(report: CoverageReportCard) {
  form.value = {
    baseVersionNumber: report.versionNumber || '',
    baseCommitId: report.repoCommitId || '',
    baseReportId: baseReportKey(report),
  }
  localError.value = ''
  emit('clear-error')
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(760px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  padding: 22px;
  border-radius: 24px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.24);
}

.modal-head,
.modal-section-head,
.modal-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.modal-head h2,
.modal-head p,
.modal-section-head h3,
.modal-section-head p {
  margin: 0;
}

.modal-close {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
}

.incremental-modal,
.modal-section {
  display: grid;
  gap: 16px;
}

.incremental-summary-grid,
.incremental-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-tile {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.summary-tile span,
.summary-tile small,
.field span,
.subtext {
  color: #64748b;
}

.summary-tile span,
.summary-tile small,
.field span {
  font-size: 12px;
  font-weight: 700;
}

.summary-tile strong {
  color: #0f172a;
}

.count-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  font-weight: 800;
  white-space: nowrap;
}

.base-report-list {
  display: grid;
  gap: 10px;
  max-height: 260px;
  overflow: auto;
  padding-right: 4px;
}

.base-report-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(248, 250, 252, 0.82);
  cursor: pointer;
  text-align: left;
}

.base-report-option.active {
  border-color: rgba(15, 118, 110, 0.42);
  background: rgba(15, 118, 110, 0.08);
}

.base-report-option span,
.field {
  display: grid;
  gap: 8px;
}

.base-report-option span {
  gap: 4px;
}

.base-report-option small,
.base-report-option code,
.base-report-empty {
  color: #64748b;
}

.base-report-option code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

.base-report-empty,
.modal-error {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.9);
}

.modal-error {
  color: #b91c1c;
  background: rgba(220, 38, 38, 0.08);
  font-weight: 700;
}

.modal-actions {
  justify-content: flex-end;
  align-items: center;
}

.text-input,
.ghost-button,
.primary-button {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.ghost-button {
  background: rgba(15, 23, 42, 0.06);
  cursor: pointer;
}

.primary-button {
  background: #0f766e;
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.primary-button:disabled,
.ghost-button:disabled {
  cursor: not-allowed;
  opacity: .58;
}

@media (max-width: 900px) {
  .incremental-summary-grid,
  .incremental-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
