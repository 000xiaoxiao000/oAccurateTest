<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
      <div>
        <div class="eyebrow">Coverage Center</div>
        <h1>覆盖率中心</h1>
        <p class="subtext">按应用查看版本、已生成报告，并进入新的覆盖率详情页。</p>
      </div>
    </div>

    <div class="selector-row">
      <label class="field grow">
        <span>选择应用</span>
        <select v-model="selectedAppId" class="text-input">
          <option value="">请选择应用</option>
          <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.name }}</option>
        </select>
      </label>
      <label class="field search-field">
        <span>搜索版本/报告</span>
        <input v-model.trim="keyword" class="text-input" type="search" placeholder="版本号、分支、Commit" aria-label="搜索覆盖率版本或报告" />
      </label>
      <button class="ghost-button" type="button" @click="loadApps">刷新应用</button>
    </div>

    <div v-if="error" class="status-card error">{{ error }}</div>
    <div v-if="generationNotice" :class="['status-card', generationFailed ? 'error' : 'info']">{{ generationNotice }}</div>

    <template v-if="selectedCenter">
      <section class="panel">
        <div class="panel-head">
          <div class="panel-title-block">
            <h2>版本入口</h2>
            <p>选择版本查看覆盖率；仅当前版本允许生成版本全量或增量报告。</p>
          </div>
          <div class="panel-actions">
            <span class="count-badge">{{ filteredVersions.length }} / {{ visibleVersions.length }}</span>
            <button class="ghost-button small-button" type="button" :disabled="refreshingReports || !selectedAppId" @click="refreshReports">
              {{ refreshingReports ? '刷新中...' : '刷新报告' }}
            </button>
          </div>
        </div>
        <div class="card-grid version-card-grid">
          <article v-for="version in paginatedVersions" :key="version.id" class="card">
            <div class="card-main">
              <div class="card-title-block">
                <div class="card-top">
                  <strong>{{ version.versionNumber }}</strong>
                  <span v-if="version.current" class="tag">当前版本</span>
                </div>
                <p class="subtext">{{ version.describe || '暂无描述' }}</p>
              </div>
              <div class="meta-stack">
                <span class="branch-name">{{ version.repoBranch || '-' }}</span>
                <span class="commit-id" :title="commitTooltip(version.repoCommitId)">{{ version.repoCommitId || '-' }}</span>
              </div>
              <span
                class="time-text time-tooltip"
                :class="{ 'has-tooltip': timeTooltip(version) }"
                :aria-label="timeTooltip(version) || undefined"
                :tabindex="timeTooltip(version) ? 0 : undefined"
                @mouseenter="showTimeTooltip($event, timeTooltip(version))"
                @mouseleave="hideTimeTooltip"
                @focus="showTimeTooltip($event, timeTooltip(version))"
                @blur="hideTimeTooltip"
              >{{ displayTime(version) }}</span>
            </div>
            <div class="action-row">
              <RouterLink
                class="table-link"
                :to="{
                  name: 'coverage-overview',
                  params: { projectId, appId: selectedAppId },
                  query: { versionNumber: version.versionNumber, commitId: version.repoCommitId || undefined },
                }"
              >
                查看覆盖率概览
              </RouterLink>
              <button
                class="ghost-button small-button generate-button"
                type="button"
                :disabled="!canGenerateVersion(version) || Boolean(generatingVersionKey)"
                :title="generateDisabledTitle(version, 'full')"
                @click="generateVersionFull(version)"
              >
                {{ isGeneratingVersion(version, 'full') ? '全量生成中...' : '生成版本全量' }}
              </button>
              <button
                class="ghost-button small-button generate-button"
                type="button"
                :disabled="!canGenerateVersion(version) || Boolean(generatingVersionKey)"
                :title="generateDisabledTitle(version, 'incremental')"
                @click="openIncrementalDialog(version)"
              >
                {{ isGeneratingVersion(version, 'incremental') ? '增量生成中...' : '生成版本增量' }}
              </button>
            </div>
          </article>
        </div>
        <div v-if="!filteredVersions.length" class="empty-card compact">暂无匹配版本</div>
        <AppPagination
          v-if="filteredVersions.length > 0"
          v-model:page="versionPage"
          v-model:page-size="versionPageSize"
          :total="filteredVersions.length"
          item-name="版本"
          :page-sizes="[6, 12, 24, 48]"
        />
      </section>

      <section class="panel">
        <div class="panel-head">
          <div class="panel-title-block">
            <h2>已生成报告</h2>
            <p>查看最新生成结果，可手动刷新同步报告列表。</p>
          </div>
          <div class="panel-actions">
            <span class="count-badge">{{ filteredReports.length }} / {{ selectedCenter.coverageReports.length }}</span>
            <button class="ghost-button small-button" type="button" :disabled="refreshingReports || !selectedAppId" @click="refreshReports">
              {{ refreshingReports ? '刷新中...' : '刷新报告' }}
            </button>
          </div>
        </div>
        <div class="card-grid report-card-grid">
          <article v-for="report in paginatedReports" :key="report.id" class="card">
            <div class="card-main report-main">
              <div class="card-title-block">
                <div class="card-top">
                  <strong>{{ report.versionNumber || '未命名版本' }}</strong>
                  <span :class="['tag', report.reportType === 1 || report.baseVersionNumber || report.baseRepoCommitId ? 'increment' : report.reportType === 2 ? 'commit' : 'full']">
                    {{ report.reportType === 1 || report.baseVersionNumber || report.baseRepoCommitId ? '增量' : report.reportType === 2 ? '本次 Commit' : '全量' }}
                  </span>
                </div>
                <div class="meta-stack">
                  <span class="branch-name">{{ report.repoBranch || '-' }}</span>
                  <span class="commit-id" :title="commitTooltip(report.repoCommitId)">{{ report.repoCommitId || '-' }}</span>
                </div>
              </div>
              <div class="metric-grid">
                <span><b>{{ coverageRate(report.coveredClasses, report.totalClasses) }}</b><small>{{ report.coveredClasses }} / {{ report.totalClasses }} 类</small></span>
                <span><b>{{ coverageRate(report.coveredMethods, report.totalMethods) }}</b><small>{{ report.coveredMethods }} / {{ report.totalMethods }} 方法</small></span>
                <span><b>{{ coverageRate(report.coveredLines, report.totalLines) }}</b><small>{{ report.coveredLines }} / {{ report.totalLines }} 行</small></span>
              </div>
              <span
                class="time-text time-tooltip"
                :class="{ 'has-tooltip': timeTooltip(report) }"
                :aria-label="timeTooltip(report) || undefined"
                :tabindex="timeTooltip(report) ? 0 : undefined"
                @mouseenter="showTimeTooltip($event, timeTooltip(report))"
                @mouseleave="hideTimeTooltip"
                @focus="showTimeTooltip($event, timeTooltip(report))"
                @blur="hideTimeTooltip"
              >{{ displayTime(report) }}</span>
            </div>
            <div class="action-row">
              <RouterLink
                class="table-link"
                :to="report.reportType !== 1 && report.reportType !== 2 && !report.baseVersionNumber && !report.baseRepoCommitId
                  ? { name: 'coverage-details', params: { projectId, appId: selectedAppId }, query: { reportId: report.id } }
                  : {
                    name: 'coverage-overview',
                    params: { projectId, appId: selectedAppId },
                    query: {
                      versionNumber: report.versionNumber || undefined,
                      reportId: report.id,
                      commitId: report.repoCommitId || undefined,
                    },
                  }"
              >
                {{ report.reportType !== 1 && report.reportType !== 2 && !report.baseVersionNumber && !report.baseRepoCommitId ? '查看版本全量明细' : '打开报告' }}
              </RouterLink>
              <a class="table-link" :href="backendApiUrl(`/p/${projectId}/coverage/export?reportId=${report.id}`)">导出报告</a>
              <a class="table-link" :href="backendApiUrl(`/p/${projectId}/coverage/export-methods?reportId=${report.id}`)">导出方法</a>
              <button class="danger-link" type="button" :disabled="deletingReportId === report.id" @click="removeCoverageReport(report.id)">
                {{ deletingReportId === report.id ? '删除中...' : '删除' }}
              </button>
              <span v-if="report.hasNewerData" class="warn-text">有新数据待重新生成</span>
            </div>
          </article>
        </div>
        <div v-if="!filteredReports.length" class="empty-card compact">暂无匹配报告</div>
        <AppPagination
          v-if="filteredReports.length > 0"
          v-model:page="reportPage"
          v-model:page-size="reportPageSize"
          :total="filteredReports.length"
          item-name="报告"
          :page-sizes="[6, 12, 24, 48]"
        />
      </section>
    </template>

    <div v-if="incrementalDialogOpen && incrementalDialogVersion" class="modal-mask" @click.self="closeIncrementalDialog">
      <form class="modal-card incremental-modal" @submit.prevent="generateVersionIncremental">
        <div class="modal-head">
          <div>
            <div class="eyebrow">Version Incremental</div>
            <h2>生成版本增量报告</h2>
            <p class="subtext">选择基准报告或手动指定基准版本，生成当前版本相对基准的增量覆盖率。</p>
          </div>
          <button class="modal-close" type="button" aria-label="关闭生成版本增量弹窗" @click="closeIncrementalDialog">×</button>
        </div>

        <div class="incremental-summary-grid">
          <article class="summary-tile current">
            <span>当前版本</span>
            <strong>{{ incrementalDialogVersion.versionNumber || '-' }}</strong>
            <small :title="commitTooltip(incrementalDialogVersion.repoCommitId)">Commit {{ shortHash(incrementalDialogVersion.repoCommitId) }}</small>
          </article>
          <article class="summary-tile base">
            <span>基准版本</span>
            <strong>{{ incrementalForm.baseVersionNumber || '未选择' }}</strong>
            <small :title="commitTooltip(incrementalForm.baseCommitId)">Commit {{ shortHash(incrementalForm.baseCommitId) }}</small>
          </article>
        </div>

        <section class="modal-section">
          <div class="modal-section-head">
            <div>
              <h3>选择基准报告</h3>
              <p class="subtext">优先选择已有版本全量报告，系统会自动填充基准版本和 Commit。</p>
            </div>
            <span class="count-badge">{{ incrementalBaseOptions.length }} 个可选</span>
          </div>
          <div v-if="incrementalBaseOptions.length" class="base-report-list">
            <button
              v-for="report in incrementalBaseOptions"
              :key="baseReportKey(report)"
              class="base-report-option"
              :class="{ active: baseReportKey(report) === incrementalForm.baseReportId }"
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
              <input v-model.trim="incrementalForm.baseVersionNumber" class="text-input" type="text" placeholder="例如 v1.0.0" />
            </label>
            <label class="field">
              <span>基准 Commit</span>
              <input v-model.trim="incrementalForm.baseCommitId" class="text-input" type="text" placeholder="可为空，默认使用基准版本" />
            </label>
          </div>
        </section>

        <div v-if="incrementalError" class="modal-error">{{ incrementalError }}</div>
        <div class="modal-actions">
          <button class="ghost-button" type="button" :disabled="Boolean(generatingVersionKey)" @click="closeIncrementalDialog">取消</button>
          <button class="primary-button" type="submit" :disabled="Boolean(generatingVersionKey) || !incrementalForm.baseVersionNumber">
            {{ generatingVersionKey ? '处理中...' : '确认生成' }}
          </button>
        </div>
      </form>
    </div>

    <Teleport to="body">
      <div
        v-if="floatingTimeTooltip.visible"
        class="floating-time-tooltip"
        ref="floatingTimeTooltipEl"
        :style="floatingTimeTooltipStyle"
      >
        <span class="floating-time-tooltip-icon" aria-hidden="true">⏱</span>
        <span class="floating-time-tooltip-content">
          <span>生成时间</span>
          <strong>{{ floatingTimeTooltip.text }}</strong>
        </span>
        <span class="floating-time-tooltip-arrow" :style="floatingTimeTooltipArrowStyle" aria-hidden="true"></span>
      </div>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import {
  deleteCoverageReport,
  fetchCoverageJob,
  fetchProjectApps,
  fetchVersionCenter,
  triggerCoverageGenerate,
  triggerCoverageGenerateIncremental,
} from '@/api/bootstrap'
import { backendApiUrl } from '@/api/http'
import AppPagination from '@/components/AppPagination.vue'
import { useDialog } from '@/composables/useDialog'
import type { AppSummary, CoverageReportCard, VersionCenterPayload, VersionItemSummary } from '@/api/types'

const route = useRoute()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = ref<AppSummary[]>([])
const centers = ref<Record<string, VersionCenterPayload>>({})
const selectedAppId = ref('')
const error = ref('')
const keyword = ref('')
const versionPage = ref(1)
const versionPageSize = ref(6)
const reportPage = ref(1)
const reportPageSize = ref(6)
const deletingReportId = ref('')
const refreshingReports = ref(false)
const generatingVersionKey = ref('')
const generationNotice = ref('')
const generationFailed = ref(false)
const incrementalDialogOpen = ref(false)
const incrementalDialogVersion = ref<VersionItemSummary | null>(null)
const incrementalError = ref('')
const incrementalForm = ref({ baseVersionNumber: '', baseCommitId: '', baseReportId: '' })
const floatingTimeTooltip = ref({ visible: false, text: '', x: 0, y: 0, arrowOffset: 0 })
const floatingTimeTooltipEl = ref<HTMLElement | null>(null)

const selectedCenter = computed(() => centers.value[selectedAppId.value])
const keywordTerm = computed(() => keyword.value.toLowerCase())
const visibleVersions = computed(() => compactVersionEntries(selectedCenter.value?.versions || []))
const filteredVersions = computed(() => {
  const versions = visibleVersions.value
  const term = keywordTerm.value
  if (!term) return versions
  return versions.filter((version) => [
    version.versionNumber,
    version.describe,
    version.repoBranch,
    version.repoCommitId,
    version.sourceType,
  ].some((value) => String(value || '').toLowerCase().includes(term)))
})
const filteredReports = computed(() => {
  const reports = selectedCenter.value?.coverageReports || []
  const term = keywordTerm.value
  if (!term) return reports
  return reports.filter((report) => [
    report.versionNumber,
    report.repoBranch,
    report.repoCommitId,
    report.reportType === 1 || report.baseVersionNumber || report.baseRepoCommitId ? '增量' : report.reportType === 2 ? '本次 Commit' : '全量',
  ].some((value) => String(value || '').toLowerCase().includes(term)))
})
const paginatedVersions = computed(() => {
  const start = (versionPage.value - 1) * versionPageSize.value
  return filteredVersions.value.slice(start, start + versionPageSize.value)
})
const paginatedReports = computed(() => {
  const start = (reportPage.value - 1) * reportPageSize.value
  return filteredReports.value.slice(start, start + reportPageSize.value)
})
const floatingTimeTooltipStyle = computed(() => ({
  left: `${floatingTimeTooltip.value.x}px`,
  top: `${floatingTimeTooltip.value.y}px`,
}))
const floatingTimeTooltipArrowStyle = computed(() => ({
  left: `calc(50% + ${floatingTimeTooltip.value.arrowOffset}px)`,
}))
const incrementalBaseOptions = computed(() => {
  const currentVersion = incrementalDialogVersion.value
  const reports = selectedCenter.value?.coverageReports || []
  const seen = new Set<string>()
  return reports
    .filter((report) => report.versionNumber && isBaseReport(report))
    .filter((report) => !currentVersion || report.versionNumber !== currentVersion.versionNumber || report.repoCommitId !== currentVersion.repoCommitId)
    .filter((report) => {
      const key = baseReportKey(report)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
})

function versionDisplayKey(version: VersionItemSummary) {
  return `${version.versionNumber || ''}::${version.repoBranch || ''}`
}

function compactVersionEntries(versions: VersionItemSummary[]) {
  const currentKeys = new Set(versions.filter((version) => version.current).map(versionDisplayKey))
  return versions.filter((version) => version.current || !currentKeys.has(versionDisplayKey(version)))
}

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 10 ? value.slice(0, 10) : value
}

function displayTime(item: { createTimeRelativeText?: string; createTimeText?: string }) {
  return item.createTimeRelativeText || item.createTimeText || '-'
}

function timeTooltip(item: { createTimeText?: string }) {
  return formatAbsoluteTime(item.createTimeText)
}

function formatAbsoluteTime(value?: string) {
  if (!value) return ''
  const trimmed = value.trim()
  if (!trimmed || /^[-–—]$/.test(trimmed)) return ''
  if (/^\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?/.test(trimmed)) {
    return trimmed.replace('T', ' ')
  }
  const timestamp = Date.parse(trimmed)
  if (!Number.isFinite(timestamp)) return trimmed
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(timestamp)).replace(/\//g, '-')
}

async function showTimeTooltip(event: MouseEvent | FocusEvent, text: string) {
  if (!text) return
  const target = event.currentTarget as HTMLElement | null
  if (!target) return
  const rect = target.getBoundingClientRect()
  const viewportPadding = 12
  const centerX = rect.left + rect.width / 2
  floatingTimeTooltip.value = {
    visible: true,
    text,
    x: Math.round(centerX),
    y: Math.round(rect.top - 12),
    arrowOffset: 0,
  }
  await nextTick()
  const tooltipWidth = floatingTimeTooltipEl.value?.offsetWidth || 0
  const halfTooltipWidth = tooltipWidth / 2
  const minX = halfTooltipWidth + viewportPadding
  const maxX = window.innerWidth - halfTooltipWidth - viewportPadding
  const adjustedX = maxX < minX ? window.innerWidth / 2 : Math.min(Math.max(centerX, minX), maxX)
  const maxArrowOffset = Math.max(0, halfTooltipWidth - 16)
  const arrowOffset = Math.min(Math.max(centerX - adjustedX, -maxArrowOffset), maxArrowOffset)
  floatingTimeTooltip.value = {
    visible: true,
    text,
    x: Math.round(adjustedX),
    y: Math.round(rect.top - 12),
    arrowOffset: Math.round(arrowOffset),
  }
}

function hideTimeTooltip() {
  floatingTimeTooltip.value = { ...floatingTimeTooltip.value, visible: false, arrowOffset: 0 }
}

function isBaseReport(report: CoverageReportCard) {
  return report.reportType !== 1 && report.reportType !== 2 && !report.baseVersionNumber && !report.baseRepoCommitId
}

function baseReportKey(report: CoverageReportCard) {
  return report.id || `${report.versionNumber || ''}:${report.repoCommitId || ''}`
}

function selectBaseReport(report: CoverageReportCard) {
  incrementalForm.value = {
    baseVersionNumber: report.versionNumber || '',
    baseCommitId: report.repoCommitId || '',
    baseReportId: baseReportKey(report),
  }
  incrementalError.value = ''
}

function versionActionKey(version: VersionItemSummary, type: 'full' | 'incremental') {
  return `${type}:${version.id || version.versionNumber}:${version.repoCommitId || ''}`
}

function canGenerateVersion(version: VersionItemSummary) {
  return Boolean(version.current)
}

function isGeneratingVersion(version: VersionItemSummary, type: 'full' | 'incremental') {
  return generatingVersionKey.value === versionActionKey(version, type)
}

function generateDisabledTitle(version: VersionItemSummary, type: 'full' | 'incremental') {
  if (!version.current) return '非当前版本仅支持查看覆盖率，不能生成报告'
  if (generatingVersionKey.value && !isGeneratingVersion(version, type)) return '已有覆盖率生成任务处理中，请稍后再试'
  return type === 'full' ? '生成当前版本的版本全量报告' : '选择基准后生成当前版本的版本增量报告'
}

function generationPayload(version: VersionItemSummary) {
  return {
    appId: selectedAppId.value,
    versionNumber: version.versionNumber,
    branch: version.repoBranch || undefined,
    commitId: version.repoCommitId || undefined,
  }
}

function closeIncrementalDialog() {
  if (generatingVersionKey.value) return
  incrementalDialogOpen.value = false
  incrementalDialogVersion.value = null
  incrementalError.value = ''
}

function openIncrementalDialog(version: VersionItemSummary) {
  if (!canGenerateVersion(version)) return
  incrementalDialogVersion.value = version
  incrementalForm.value = { baseVersionNumber: '', baseCommitId: '', baseReportId: '' }
  incrementalError.value = ''
  incrementalDialogOpen.value = true
  const defaultBaseReport = incrementalBaseOptions.value[0]
  if (defaultBaseReport) {
    selectBaseReport(defaultBaseReport)
  }
}

async function pollCoverageJob(jobId: string, label: string) {
  generationNotice.value = `已提交${label}任务`
  while (true) {
    try {
      const status = await fetchCoverageJob(projectId.value, jobId)
      const progress = Number.isFinite(Number(status.progress)) ? Math.max(0, Math.min(100, Number(status.progress))) : 0
      const stage = status.progressName || status.message || ''
      generationNotice.value = status.finish
        ? `${label}${status.success === false ? '失败' : '完成'}`
        : `${label}处理中，进度 ${Math.round(progress)}%${stage ? ` · ${stage}` : ''}`
      if (status.finish) return status.success !== false
    } catch (err) {
      generationFailed.value = true
      generationNotice.value = err instanceof Error ? err.message : `${label}失败`
      return false
    }
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
}

async function generateVersionFull(version: VersionItemSummary) {
  if (!canGenerateVersion(version) || generatingVersionKey.value) return
  generatingVersionKey.value = versionActionKey(version, 'full')
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  try {
    const jobId = await triggerCoverageGenerate(projectId.value, generationPayload(version))
    const success = await pollCoverageJob(jobId, '版本全量报告生成')
    generationFailed.value = !success
    if (success) {
      generationNotice.value = '版本全量报告生成完成，已刷新报告列表'
      await refreshCenter(selectedAppId.value)
    }
  } catch (err) {
    generationFailed.value = true
    generationNotice.value = err instanceof Error ? err.message : '生成版本全量报告失败'
  } finally {
    generatingVersionKey.value = ''
  }
}

async function generateVersionIncremental() {
  const version = incrementalDialogVersion.value
  if (!version || !canGenerateVersion(version) || generatingVersionKey.value) return
  if (!incrementalForm.value.baseVersionNumber) {
    incrementalError.value = '请填写基准版本号'
    return
  }
  generatingVersionKey.value = versionActionKey(version, 'incremental')
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  incrementalError.value = ''
  try {
    const jobId = await triggerCoverageGenerateIncremental(projectId.value, {
      ...generationPayload(version),
      baseVersionNumber: incrementalForm.value.baseVersionNumber,
      baseCommitId: incrementalForm.value.baseCommitId || undefined,
    })
    incrementalDialogOpen.value = false
    const success = await pollCoverageJob(jobId, '版本增量报告生成')
    generationFailed.value = !success
    if (success) {
      generationNotice.value = '版本增量报告生成完成，已刷新报告列表'
      await refreshCenter(selectedAppId.value)
    }
  } catch (err) {
    generationFailed.value = true
    incrementalError.value = err instanceof Error ? err.message : '生成版本增量报告失败'
    generationNotice.value = incrementalError.value
  } finally {
    generatingVersionKey.value = ''
  }
}

async function loadApps() {
  error.value = ''
  try {
    apps.value = await fetchProjectApps(projectId.value)
    if (!selectedAppId.value && apps.value.length) {
      selectedAppId.value = apps.value[0].id
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用失败'
  }
}

async function loadCenter(appId: string) {
  if (!appId || centers.value[appId]) {
    return
  }
  error.value = ''
  try {
    centers.value = {
      ...centers.value,
      [appId]: await fetchVersionCenter(projectId.value, appId),
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载覆盖率中心失败'
  }
}

async function refreshCenter(appId: string) {
  if (!appId) return
  centers.value = {
    ...centers.value,
    [appId]: await fetchVersionCenter(projectId.value, appId),
  }
}

async function refreshReports() {
  if (!selectedAppId.value) return
  refreshingReports.value = true
  error.value = ''
  try {
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '刷新覆盖率报告失败'
  } finally {
    refreshingReports.value = false
  }
}

async function removeCoverageReport(reportId: string) {
  if (!selectedAppId.value || !reportId) return
  const confirmed = await dialog.confirm({
    title: '删除覆盖率报告',
    message: '确认删除该覆盖率报告？删除后需要重新生成才能查看。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) return
  deletingReportId.value = reportId
  error.value = ''
  try {
    await deleteCoverageReport(projectId.value, selectedAppId.value, reportId)
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除覆盖率报告失败'
  } finally {
    deletingReportId.value = ''
  }
}

watch(selectedAppId, (appId) => {
  versionPage.value = 1
  reportPage.value = 1
  void loadCenter(appId)
})

watch(keyword, () => {
  versionPage.value = 1
  reportPage.value = 1
})

watch([versionPageSize, () => filteredVersions.value.length], ([, total]) => {
  const totalPages = Math.max(1, Math.ceil(total / versionPageSize.value))
  if (versionPage.value > totalPages) {
    versionPage.value = totalPages
  }
})

watch([reportPageSize, () => filteredReports.value.length], ([, total]) => {
  const totalPages = Math.max(1, Math.ceil(total / reportPageSize.value))
  if (reportPage.value > totalPages) {
    reportPage.value = totalPages
  }
})

onMounted(loadApps)
</script>

<style scoped>
.page-header,
.selector-row,
.panel-head,
.panel-actions,
.action-row {
  display: flex;
  gap: 12px;
}

.selector-row {
  position: sticky;
  top: 12px;
  z-index: 4;
  align-items: end;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.search-field {
  min-width: min(360px, 100%);
}

.page-header,
.panel-head {
  justify-content: space-between;
  align-items: center;
}

.panel-title-block {
  display: grid;
  gap: 4px;
}

.panel-title-block h2,
.panel-title-block p {
  margin: 0;
}

.panel-title-block p {
  color: #64748b;
  font-size: 13px;
}

.panel-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.count-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  font-weight: 800;
  white-space: nowrap;
}

.action-row {
  align-items: center;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-header {
  margin-bottom: 12px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.field span {
  color: #64748b;
}

.grow {
  flex: 1;
}

.field {
  display: grid;
  gap: 8px;
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

.primary-button:disabled {
  cursor: not-allowed;
  opacity: .58;
}

.ghost-button.small-button {
  min-height: 36px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 800;
}

.ghost-button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.panel,
.card,
.status-card,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 12px;
}

.status-card.error,
.warn-text {
  color: #b91c1c;
}

.status-card.info {
  color: #0f766e;
}

.warn-text {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(220, 38, 38, 0.08);
  font-size: 13px;
  font-weight: 700;
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr;
  align-items: stretch;
  gap: 12px;
  max-height: min(520px, calc(100vh - 300px));
  overflow: auto;
  padding-right: 4px;
}

.card {
  display: grid;
  gap: 14px;
  transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease;
}

.card:hover {
  border-color: rgba(var(--oat-primary-rgb), 0.2);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.card-main {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(260px, 1.2fr) minmax(72px, auto);
  align-items: center;
  gap: 18px;
}

.report-main {
  grid-template-columns: minmax(220px, 0.9fr) minmax(420px, 1.45fr) minmax(72px, auto);
}

.card-title-block {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.card-title-block .subtext {
  margin: 0;
}

.meta-stack {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.branch-name {
  color: #64748b;
  font-weight: 700;
}

.commit-id {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-text {
  justify-self: end;
  color: #64748b;
  white-space: nowrap;
}

.time-tooltip {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: max-content;
  min-height: 28px;
  padding: 4px 8px;
  border-radius: 999px;
  outline: none;
  cursor: default;
  transition: color .16s ease, background .16s ease, box-shadow .16s ease;
}

.time-tooltip.has-tooltip:hover,
.time-tooltip.has-tooltip:focus-visible {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.08);
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.14);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-grid span {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.9);
  color: #64748b;
  white-space: normal;
}

.metric-grid b {
  color: #0f172a;
}

.metric-grid small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.action-row {
  justify-content: flex-start;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.report-card-grid .action-row {
  justify-content: flex-end;
}

.empty-card.compact {
  margin-top: 12px;
  padding: 14px;
  text-align: center;
  color: #64748b;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.tag.full {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.tag.increment {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.tag.commit {
  background: rgba(15, 118, 110, 0.12);
  color: #0f766e;
}

.table-link {
  color: #0f766e;
  font-weight: 700;
}

.danger-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: #dc2626;
  cursor: pointer;
  font-weight: 700;
}

.danger-link:hover:not(:disabled) {
  color: #b91c1c;
}

.danger-link:disabled {
  cursor: not-allowed;
  opacity: .58;
}

.generate-button {
  color: #0f766e;
}

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
.summary-tile small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.summary-tile strong {
  color: #0f172a;
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

.base-report-option span {
  display: grid;
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

.floating-time-tooltip {
  position: fixed;
  z-index: 1000;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: min(360px, calc(100vw - 24px));
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.96), rgba(30, 41, 59, 0.96));
  color: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.22);
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;
  text-align: left;
  white-space: normal;
  transform: translate(-50%, -100%);
}

.floating-time-tooltip-icon {
  display: grid;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 10px;
  background: rgba(20, 184, 166, 0.16);
  color: #99f6e4;
  font-size: 15px;
}

.floating-time-tooltip-content {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.floating-time-tooltip-content span {
  color: #cbd5e1;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.floating-time-tooltip-content strong {
  color: #f8fafc;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.35;
  white-space: nowrap;
}

.floating-time-tooltip-arrow {
  position: absolute;
  top: 100%;
  border: 6px solid transparent;
  border-top-color: rgba(15, 23, 42, 0.96);
  transform: translateX(-50%);
}

@media (max-width: 900px) {
  .selector-row,
  .card-main,
  .report-main,
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .selector-row {
    display: grid;
    align-items: stretch;
  }

  .time-text {
    justify-self: start;
  }

  .report-card-grid .action-row {
    justify-content: flex-start;
  }

  .incremental-summary-grid,
  .incremental-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
