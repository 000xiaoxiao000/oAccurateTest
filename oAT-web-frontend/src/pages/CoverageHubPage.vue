<template>
  <section>
    <div class="page-header plain-header coverage-page-header">
      <div>
        <div class="eyebrow">Coverage Center</div>
        <h1>覆盖率中心</h1>
        <p class="subtext">按应用查看版本、已生成报告，并进入新的覆盖率详情页。</p>
      </div>
    </div>

    <CoverageHubFilters
      v-model:app-id="selectedAppId"
      v-model:keyword="keyword"
      v-model:source-type="selectedSourceType"
      :apps="apps"
      @refresh-apps="loadApps"
    />

    <div v-if="error" class="status-card error">{{ error }}</div>
    <div v-if="generationNotice" :class="['status-card', generationFailed ? 'error' : 'info']">{{ generationNotice }}</div>

    <template v-if="selectedCenter">
      <CoverageHubVersionPanel
        v-model:page="versionPage"
        v-model:page-size="versionPageSize"
        :project-id="projectId"
        :app-id="selectedAppId"
        :versions="paginatedVersions"
        :filtered-count="filteredVersions.length"
        :visible-count="visibleVersions.length"
        :refreshing="refreshingVersions"
        :generating-version-key="generatingVersionKey"
        :universal-generate-sources="universalGenerateSources"
        :coverage-data-status="coverageDataStatus"
        :time-tooltip="timeTooltip"
        :display-time="displayTime"
        :commit-tooltip="commitTooltip"
        :can-generate-version-type="canGenerateVersionType"
        :is-generating-version="isGeneratingVersion"
        :generate-disabled-title="generateDisabledTitle"
        :generate-action-label="generateActionLabel"
        @refresh="refreshVersionsList"
        @show-time-tooltip="showTimeTooltip"
        @hide-time-tooltip="hideTimeTooltip"
        @generate-full="generateVersionFull"
        @generate-current="generateVersionCurrent"
        @generate-frontend="generateFrontendCoverage"
        @generate-universal="generateUniversalCoverage"
        @open-incremental="openIncrementalDialog"
      />

      <CoverageHubReportPanel
        v-model:page="reportPage"
        v-model:page-size="reportPageSize"
        :project-id="projectId"
        :app-id="selectedAppId"
        :reports="paginatedReports"
        :filtered-count="filteredReports.length"
        :total-count="selectedCenter.coverageReports.length"
        :refreshing="refreshingReports"
        :deleting-report-id="deletingReportId"
        :source-type-label="sourceTypeLabel"
        :coverage-rate="coverageRate"
        :time-tooltip="timeTooltip"
        :display-time="displayTime"
        :commit-tooltip="commitTooltip"
        @refresh="refreshReports"
        @delete="removeCoverageReport"
        @show-time-tooltip="showTimeTooltip"
        @hide-time-tooltip="hideTimeTooltip"
      />
    </template>

    <CoverageHubIncrementalDialog
      :open="incrementalDialogOpen"
      :version="incrementalDialogVersion"
      :base-options="incrementalBaseOptions"
      :generating="Boolean(generatingVersionKey)"
      :error="incrementalError"
      :commit-tooltip="commitTooltip"
      :short-hash="shortHash"
      @close="closeIncrementalDialog"
      @clear-error="incrementalError = ''"
      @submit="generateVersionIncremental"
    />

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
import { useRoute } from 'vue-router'

import {
  fetchProjectApps,
  fetchVersionCenter,
} from '@/api/bootstrap'
import {
  deleteCoverageReportV2,
  fetchCoverageJobV2,
  triggerCoverageGenerateFullV2,
  triggerCoverageGenerateBySourceV2,
  triggerCoverageGenerateCurrentV2,
  triggerCoverageGenerateIncrementalV2,
  triggerFrontendCoverageGenerateV2,
  triggerUniversalCoverageGenerateV2,
} from '@/features/coverage/api/core'
import { useDialog } from '@/composables/useDialog'
import CoverageHubFilters from '@/features/coverage/components/CoverageHubFilters.vue'
import CoverageHubIncrementalDialog from '@/features/coverage/components/CoverageHubIncrementalDialog.vue'
import CoverageHubReportPanel from '@/features/coverage/components/CoverageHubReportPanel.vue'
import CoverageHubVersionPanel, {
  type CoverageHubUniversalGenerateType,
  type CoverageHubVersionGenerateType,
} from '@/features/coverage/components/CoverageHubVersionPanel.vue'
import type { AppSummary, CoverageReportCard, VersionCenterPayload, VersionItemSummary } from '@/api/types'

const route = useRoute()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = ref<AppSummary[]>([])
const centers = ref<Record<string, VersionCenterPayload>>({})
const selectedAppId = ref('')
const error = ref('')
const keyword = ref('')
const selectedSourceType = ref('ALL')
const versionPage = ref(1)
const versionPageSize = ref(6)
const reportPage = ref(1)
const reportPageSize = ref(6)
const deletingReportId = ref('')
const refreshingReports = ref(false)
const refreshingVersions = ref(false)
const generatingVersionKey = ref('')
const generationNotice = ref('')
const generationFailed = ref(false)
const incrementalDialogOpen = ref(false)
const incrementalDialogVersion = ref<VersionItemSummary | null>(null)
const incrementalError = ref('')
const floatingTimeTooltip = ref({ visible: false, text: '', x: 0, y: 0, arrowOffset: 0 })
const floatingTimeTooltipEl = ref<HTMLElement | null>(null)
const universalGenerateSources = [
  { type: 'GO', shortLabel: 'Go' },
  { type: 'PYTHON', shortLabel: 'Python' },
  { type: 'CPP', shortLabel: 'C/C++' },
] as const

const selectedCenter = computed(() => centers.value[selectedAppId.value])
const keywordTerm = computed(() => keyword.value.toLowerCase())
const visibleVersions = computed(() => compactVersionEntries(selectedCenter.value?.versions || []))
const sourceTypeMatches = (value?: string) => selectedSourceType.value === 'ALL' || normalizeSourceType(value) === selectedSourceType.value
const versionSourceTypeMatches = (version: VersionItemSummary) =>
  sourceTypeMatches(version.sourceType || 'JAVA')
  || reportsForVersion(version).some((report) => sourceTypeMatches(report.sourceType))
  || rawCoverageSourceTypes(version).some((sourceType) => sourceTypeMatches(sourceType))
const filteredVersions = computed(() => {
  const versions = visibleVersions.value.filter((version) => versionSourceTypeMatches(version))
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
  const reports = (selectedCenter.value?.coverageReports || []).filter((report) => sourceTypeMatches(report.sourceType))
  const term = keywordTerm.value
  if (!term) return reports
  return reports.filter((report) => [
    report.versionNumber,
    report.repoBranch,
    report.repoCommitId,
    sourceTypeLabel(report.sourceType),
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
      const key = report.id || `${report.versionNumber || ''}:${report.repoCommitId || ''}`
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
})

function versionDisplayKey(version: VersionItemSummary) {
  return `${version.versionNumber || ''}::${version.repoBranch || ''}::${version.repoCommitId || ''}`
}

function compactVersionEntries(versions: VersionItemSummary[]) {
  return versions
}

function commitTooltip(value?: string) {
  return value || '暂无 CommitID'
}

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function normalizeSourceType(value?: string) {
  return (value || 'JAVA').toUpperCase()
}

function sourceTypeLabel(value?: string) {
  const sourceType = normalizeSourceType(value)
  if (sourceType === 'FRONTEND') return '前端'
  if (sourceType === 'JAVA') return 'Java'
  if (sourceType === 'CPP') return 'C/C++'
  if (sourceType === 'GO') return 'Go'
  if (sourceType === 'PYTHON') return 'Python'
  return sourceType
}

function isSameCommit(left?: string, right?: string) {
  if (!left || !right) return true
  return left === right || left.startsWith(right) || right.startsWith(left)
}

function reportsForVersion(version: VersionItemSummary) {
  return (selectedCenter.value?.coverageReports || []).filter((report) =>
    report.versionNumber === version.versionNumber && isSameCommit(report.repoCommitId, version.repoCommitId)
  )
}

function rawCoverageSourceTypes(version: VersionItemSummary) {
  return (version.rawCoverageSourceTypes || []).map((sourceType) => normalizeSourceType(sourceType))
}

function hasRawCoverageSource(version: VersionItemSummary, sourceType: string) {
  return rawCoverageSourceTypes(version).includes(normalizeSourceType(sourceType))
}

function selectedAppSourceType() {
  return normalizeSourceType(selectedCenter.value?.app?.language || selectedCenter.value?.app?.sourceType || 'JAVA')
}

function reportSourceType(version: VersionItemSummary) {
  const selectedType = normalizeSourceType(selectedSourceType.value)
  if (selectedType !== 'ALL') return selectedType
  const appType = selectedAppSourceType()
  if (appType !== 'JAVA') return appType
  const rawTypes = rawCoverageSourceTypes(version)
  return rawTypes[0] || 'JAVA'
}

function hasCoverageInput(version: VersionItemSummary, sourceType = reportSourceType(version)) {
  const normalizedType = normalizeSourceType(sourceType)
  if (normalizedType === 'JAVA') return Boolean(version.versionNumber)
  return hasRawCoverageSource(version, normalizedType)
}

function coverageDataStatus(version: VersionItemSummary) {
  const reports = reportsForVersion(version)
  const rawSourceTypes = new Set(rawCoverageSourceTypes(version))
  if (!reports.length && !version.hasReport && !rawSourceTypes.size) {
    return [{ key: 'none', label: '暂无覆盖率数据', tone: 'empty' }]
  }
  const sourceTypes = new Set(reports.map((report) => normalizeSourceType(report.sourceType)))
  const snapshotCount = reports.reduce((sum, report) => sum + (Number(report.snapshotCount) || 0), 0)
  const statuses = []
  if (sourceTypes.has('JAVA') || (version.hasReport && sourceTypes.size === 0)) {
    statuses.push({ key: 'java', label: 'Java 覆盖率', tone: 'ok' })
  }
  if (sourceTypes.has('FRONTEND') || rawSourceTypes.has('FRONTEND')) {
    statuses.push({
      key: 'frontend',
      label: sourceTypes.has('FRONTEND') ? '前端覆盖率' : '前端待生成',
      tone: sourceTypes.has('FRONTEND') ? 'ok' : 'info',
    })
  }
  for (const coverageSource of universalGenerateSources) {
    if (sourceTypes.has(coverageSource.type) || rawSourceTypes.has(coverageSource.type)) {
      statuses.push({
        key: coverageSource.type.toLowerCase(),
        label: sourceTypes.has(coverageSource.type) ? `${coverageSource.shortLabel} 覆盖率` : `${coverageSource.shortLabel} 待生成`,
        tone: sourceTypes.has(coverageSource.type) ? 'ok' : 'info',
      })
    }
  }
  if (reports.length) {
    statuses.push({
      key: 'traffic',
      label: snapshotCount > 0 ? `流量数据 ${snapshotCount} 条` : '已生成报告',
      tone: snapshotCount > 0 ? 'info' : 'neutral',
    })
  }
  return statuses
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

type UniversalGenerateType = CoverageHubUniversalGenerateType
type VersionGenerateType = CoverageHubVersionGenerateType

function versionActionKey(version: VersionItemSummary, type: VersionGenerateType) {
  return `${type}:${version.id || version.versionNumber}:${version.repoCommitId || ''}`
}

function canGenerateVersion(version: VersionItemSummary) {
  return Boolean(version.current)
}

function canGenerateVersionType(version: VersionItemSummary, type: VersionGenerateType) {
  if (!canGenerateVersion(version)) return false
  if (type === 'full' || type === 'current' || type === 'incremental') return hasCoverageInput(version)
  if (type === 'frontend') return hasRawCoverageSource(version, 'FRONTEND')
  return hasRawCoverageSource(version, type)
}

function isGeneratingVersion(version: VersionItemSummary, type: VersionGenerateType) {
  return generatingVersionKey.value === versionActionKey(version, type)
}

function generateDisabledTitle(version: VersionItemSummary, type: VersionGenerateType) {
  if (!version.current) return '非当前版本仅支持查看覆盖率，不能生成报告'
  if (generatingVersionKey.value && !isGeneratingVersion(version, type)) return '已有覆盖率生成任务处理中，请稍后再试'
  if (!canGenerateVersionType(version, type)) {
    if (reportSourceType(version) === 'JAVA' && !version.versionNumber) return '缺少版本号，无法生成 Java 覆盖率报告'
    if (type === 'frontend') return '没有可生成的前端覆盖率上报数据'
    if (type === 'GO') return '没有可生成的 Go 覆盖率上报数据'
    if (type === 'PYTHON') return '没有可生成的 Python 覆盖率上报数据'
    if (type === 'CPP') return '没有可生成的 C/C++ 覆盖率上报数据'
    return `没有可生成的${sourceTypeLabel(reportSourceType(version))}覆盖率数据`
  }
  if (type === 'frontend') return '合并已上报的 Istanbul 前端覆盖率数据，生成前端覆盖率报告'
  if (type === 'GO') return '合并已上报的 Go cover profile 数据，生成 Go 覆盖率报告'
  if (type === 'PYTHON') return '合并已上报的 coverage.py JSON 数据，生成 Python 覆盖率报告'
  if (type === 'CPP') return '合并已上报的 gcov/llvm-cov JSON 数据，生成 C/C++ 覆盖率报告'
  if (type === 'full') return `生成当前版本的${sourceTypeLabel(reportSourceType(version))}版本全量报告`
  if (type === 'current') return `生成当前 Commit 的${sourceTypeLabel(reportSourceType(version))}覆盖率报告`
  return `选择基准后生成当前版本的${sourceTypeLabel(reportSourceType(version))}版本增量报告`
}

function generateActionLabel(version: VersionItemSummary, type: VersionGenerateType) {
  const sourceLabel = sourceTypeLabel(reportSourceType(version))
  if (type === 'full') return `生成${sourceLabel}版本全量`
  if (type === 'current') return `生成${sourceLabel}本次Commit`
  if (type === 'incremental') return `生成${sourceLabel}版本增量`
  return `生成${sourceLabel}报告`
}

function generationPayload(version: VersionItemSummary) {
  return {
    appId: selectedAppId.value,
    versionNumber: version.versionNumber,
    branch: version.repoBranch || undefined,
    commitId: version.repoCommitId || undefined,
  }
}

function generationTargetText(version: VersionItemSummary) {
  return [
    `版本 ${version.versionNumber || '-'}`,
    `分支 ${version.repoBranch || '-'}`,
    `Commit ${version.repoCommitId || '-'}`,
  ].join('，')
}

function generationFailureMessage(label: string, version: VersionItemSummary, reason?: string) {
  const detail = trimTrailingSentencePunctuation(reason)
  const target = generationTargetText(version)
  if (detail) {
    return `${label}失败：${detail}。本次生成对象：${target}。请按失败原因修正后重新生成。`
  }
  return `${label}失败：任务结束但未返回具体失败原因。本次生成对象：${target}。请确认该版本已有系统快照或覆盖率上报数据，并检查分支、Commit 是否与仓库代码一致后重新生成。`
}

function generationSubmitFailureMessage(label: string, version: VersionItemSummary, err: unknown, fallback: string) {
  const detail = trimTrailingSentencePunctuation(err instanceof Error && err.message ? err.message : fallback)
  return `${label}提交失败：${detail}。本次生成对象：${generationTargetText(version)}。`
}

function trimTrailingSentencePunctuation(value?: string) {
  return value?.trim().replace(/[。.!！\s]+$/, '') || ''
}

function closeIncrementalDialog() {
  if (generatingVersionKey.value) return
  incrementalDialogOpen.value = false
  incrementalDialogVersion.value = null
  incrementalError.value = ''
}

function openIncrementalDialog(version: VersionItemSummary) {
  if (!canGenerateVersionType(version, 'incremental')) return
  incrementalDialogVersion.value = version
  incrementalError.value = ''
  incrementalDialogOpen.value = true
}

async function pollCoverageJob(jobId: string, label: string, version: VersionItemSummary) {
  generationNotice.value = `已提交${label}任务`
  while (true) {
    try {
      const status = await fetchCoverageJobV2(jobId)
      const progress = Number.isFinite(Number(status.progress)) ? Math.max(0, Math.min(100, Number(status.progress))) : 0
      const stage = status.progressName || status.message || ''
      if (status.finish) {
        generationNotice.value = status.success === false
          ? generationFailureMessage(label, version, status.message || status.data || status.progressName)
          : `${label}完成`
        return status.success !== false
      }
      generationNotice.value = `${label}处理中，进度 ${Math.round(progress)}%${stage ? ` · ${stage}` : ''}`
    } catch (err) {
      generationFailed.value = true
      generationNotice.value = generationSubmitFailureMessage(`${label}状态查询`, version, err, `${label}状态查询失败`)
      return false
    }
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
}

async function generateVersionFull(version: VersionItemSummary) {
  if (!canGenerateVersionType(version, 'full') || generatingVersionKey.value) return
  generatingVersionKey.value = versionActionKey(version, 'full')
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  try {
    const payload = generationPayload(version)
    const sourceType = reportSourceType(version)
    if (sourceType === 'JAVA') {
      const jobId = await triggerCoverageGenerateFullV2(projectId.value, payload.appId, {
        versionNumber: payload.versionNumber,
        branch: payload.branch,
        commitId: payload.commitId,
      })
      const success = await pollCoverageJob(jobId, `${sourceTypeLabel(sourceType)}版本全量报告生成`, version)
      generationFailed.value = !success
      if (!success) return
    } else {
      await triggerCoverageGenerateBySourceV2(projectId.value, selectedAppId.value, sourceType as 'FRONTEND' | 'GO' | 'PYTHON' | 'CPP', {
        versionNumber: version.versionNumber,
        branch: version.repoBranch || undefined,
        commitId: version.repoCommitId || undefined,
      })
    }
    if (!generationFailed.value) {
      generationNotice.value = `${sourceTypeLabel(sourceType)}版本全量报告生成完成，已刷新报告列表`
      await refreshCenter(selectedAppId.value)
    }
  } catch (err) {
    generationFailed.value = true
    generationNotice.value = generationSubmitFailureMessage(`${sourceTypeLabel(reportSourceType(version))}版本全量报告生成`, version, err, '生成版本全量报告失败')
  } finally {
    generatingVersionKey.value = ''
  }
}

async function generateVersionCurrent(version: VersionItemSummary) {
  if (!canGenerateVersionType(version, 'current') || generatingVersionKey.value) return
  generatingVersionKey.value = versionActionKey(version, 'current')
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  try {
    const payload = generationPayload(version)
    const sourceType = reportSourceType(version)
    if (sourceType === 'JAVA') {
      const jobId = await triggerCoverageGenerateCurrentV2(projectId.value, payload.appId, {
        versionNumber: payload.versionNumber,
        branch: payload.branch,
        commitId: payload.commitId,
      })
      const success = await pollCoverageJob(jobId, `${sourceTypeLabel(sourceType)}本次 Commit 报告生成`, version)
      generationFailed.value = !success
      if (!success) return
    } else {
      await triggerCoverageGenerateBySourceV2(projectId.value, selectedAppId.value, sourceType as 'FRONTEND' | 'GO' | 'PYTHON' | 'CPP', {
        versionNumber: version.versionNumber,
        branch: version.repoBranch || undefined,
        commitId: version.repoCommitId || undefined,
        reportType: 2,
      })
    }
    if (!generationFailed.value) {
      generationNotice.value = `${sourceTypeLabel(sourceType)}本次 Commit 报告生成完成，已刷新报告列表`
      await refreshCenter(selectedAppId.value)
    }
  } catch (err) {
    generationFailed.value = true
    generationNotice.value = generationSubmitFailureMessage(`${sourceTypeLabel(reportSourceType(version))}本次 Commit 报告生成`, version, err, '生成本次 Commit 报告失败')
  } finally {
    generatingVersionKey.value = ''
  }
}

async function generateFrontendCoverage(version: VersionItemSummary) {
  if (!canGenerateVersionType(version, 'frontend') || generatingVersionKey.value || !selectedAppId.value) return
  generatingVersionKey.value = versionActionKey(version, 'frontend')
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  try {
    await triggerFrontendCoverageGenerateV2(projectId.value, selectedAppId.value, {
      versionNumber: version.versionNumber,
      branch: version.repoBranch || undefined,
      commitId: version.repoCommitId || undefined,
    })
    generationNotice.value = '前端覆盖率报告生成完成，已刷新报告列表'
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    generationFailed.value = true
    generationNotice.value = generationSubmitFailureMessage('前端覆盖率报告生成', version, err, '生成前端覆盖率报告失败')
  } finally {
    generatingVersionKey.value = ''
  }
}

async function generateUniversalCoverage(version: VersionItemSummary, sourceType: UniversalGenerateType) {
  if (!canGenerateVersionType(version, sourceType) || generatingVersionKey.value || !selectedAppId.value) return
  generatingVersionKey.value = versionActionKey(version, sourceType)
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  try {
    await triggerUniversalCoverageGenerateV2(projectId.value, selectedAppId.value, sourceType, {
      versionNumber: version.versionNumber,
      branch: version.repoBranch || undefined,
      commitId: version.repoCommitId || undefined,
    })
    generationNotice.value = `${sourceTypeLabel(sourceType)}覆盖率报告生成完成，已刷新报告列表`
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    generationFailed.value = true
    generationNotice.value = generationSubmitFailureMessage(`${sourceTypeLabel(sourceType)}覆盖率报告生成`, version, err, `生成${sourceTypeLabel(sourceType)}覆盖率报告失败`)
  } finally {
    generatingVersionKey.value = ''
  }
}

async function generateVersionIncremental(base: { baseVersionNumber: string; baseCommitId?: string }) {
  const version = incrementalDialogVersion.value
  if (!version || !canGenerateVersionType(version, 'incremental') || generatingVersionKey.value) return
  if (!base.baseVersionNumber) {
    incrementalError.value = '请填写基准版本号'
    return
  }
  generatingVersionKey.value = versionActionKey(version, 'incremental')
  generationFailed.value = false
  generationNotice.value = ''
  error.value = ''
  incrementalError.value = ''
  try {
    const payload = generationPayload(version)
    const sourceType = reportSourceType(version)
    incrementalDialogOpen.value = false
    if (sourceType === 'JAVA') {
      const jobId = await triggerCoverageGenerateIncrementalV2(projectId.value, payload.appId, {
        versionNumber: payload.versionNumber,
        branch: payload.branch,
        commitId: payload.commitId,
        baseVersionNumber: base.baseVersionNumber,
        baseCommitId: base.baseCommitId || undefined,
      })
      const success = await pollCoverageJob(jobId, `${sourceTypeLabel(sourceType)}版本增量报告生成`, version)
      generationFailed.value = !success
      if (!success) return
    } else {
      await triggerCoverageGenerateBySourceV2(projectId.value, selectedAppId.value, sourceType as 'FRONTEND' | 'GO' | 'PYTHON' | 'CPP', {
        versionNumber: version.versionNumber,
        branch: version.repoBranch || undefined,
        commitId: version.repoCommitId || undefined,
        reportType: 1,
        baseVersionNumber: base.baseVersionNumber,
        baseCommitId: base.baseCommitId || undefined,
      })
    }
    if (!generationFailed.value) {
      generationNotice.value = `${sourceTypeLabel(sourceType)}版本增量报告生成完成，已刷新报告列表`
      await refreshCenter(selectedAppId.value)
    }
  } catch (err) {
    generationFailed.value = true
    incrementalError.value = generationSubmitFailureMessage(`${sourceTypeLabel(reportSourceType(version))}版本增量报告生成`, version, err, '生成版本增量报告失败')
    generationNotice.value = incrementalError.value
  } finally {
    generatingVersionKey.value = ''
  }
}

async function loadApps() {
  error.value = ''
  try {
    apps.value = await fetchProjectApps(projectId.value)
    if (!selectedAppId.value) {
      const queryAppId = String(route.query.appId || '')
      const match = queryAppId && apps.value.find((app) => app.id === queryAppId)
      selectedAppId.value = match ? match.id : (apps.value[0]?.id || '')
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

async function refreshVersionsList() {
  if (!selectedAppId.value) return
  refreshingVersions.value = true
  error.value = ''
  try {
    await refreshCenter(selectedAppId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '刷新版本列表失败'
  } finally {
    refreshingVersions.value = false
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
    await deleteCoverageReportV2(projectId.value, reportId)
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

watch(selectedSourceType, () => {
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
.page-header {
  display: flex;
  gap: 12px;
}

.page-header {
  justify-content: space-between;
  align-items: center;
}

.page-header {
  margin-bottom: 12px;
}


.subtext {
  color: #64748b;
}

.status-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.status-card.info {
  color: #0f766e;
}

.floating-time-tooltip {
  position: fixed;
  z-index: 1000;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: min(360px, calc(100vw - 24px));
  padding: 9px 11px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  color: #172033;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.16), 0 2px 8px rgba(15, 23, 42, 0.08);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
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
  border-radius: 8px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 15px;
}

.floating-time-tooltip-content {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.floating-time-tooltip-content span {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
}

.floating-time-tooltip-content strong {
  color: #172033;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.35;
  white-space: nowrap;
}

.floating-time-tooltip-arrow {
  position: absolute;
  top: 100%;
  width: 10px;
  height: 10px;
  border-right: 1px solid rgba(15, 23, 42, 0.12);
  border-bottom: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.98);
  transform: translateX(-50%);
  rotate: 45deg;
}

</style>
