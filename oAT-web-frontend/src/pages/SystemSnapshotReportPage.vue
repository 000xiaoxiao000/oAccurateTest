<template>
  <CoverageReportView
    :eyebrow="'Snapshot Report'"
    :title="payload?.snapshot.title || '系统快照覆盖率报告'"
    :subtext="`${payload?.snapshot.version || '未填写版本'} · ${reportStatusText(payload?.snapshot.reportStatus)}`"
    :graph-route="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`"
    :code-graph-route="codeGraphRoute"
    :back-route="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}`"
    :back-label="'返回快照详情'"
    :loading="loading"
    :loading-text="loadingText"
    :error="error"
    :report="payload?.report"
    :class-stats="payload?.classStats"
    :code-relationships="payload?.codeRelationships"
    :info-items="infoItems"
    :build-code-route="buildCodeRoute"
    :empty-title="'尚未生成覆盖率报告'"
    :empty-description="'当前快照还没有预计算汇总数据，可以先触发计算，再刷新本页查看最新结果。'"
    :secondary-action-label="'触发重新计算'"
    @secondary-action="triggerReport"
  />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import CoverageReportView from '@/components/snapshot/CoverageReportView.vue'
import { useProjectStore } from '@/stores/project'
import { reportStatusText } from '@/utils/snapshot'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const payload = computed(() => projectStore.systemSnapshotReportByKey[storeKey.value])
const loading = ref(false)
const generating = ref(false)
const error = ref('')
const generationMessage = ref('')
let reportPollTimer: number | undefined

const loadingText = computed(() => generationMessage.value || '正在加载覆盖率报告...')

const codeGraphRoute = computed(() => payload.value?.snapshot.traceId ? `/p/${projectId.value}/map/code?traceId=${encodeURIComponent(payload.value.snapshot.traceId)}` : undefined)

const infoItems = computed(() => [
  { label: '应用', value: payload.value?.app.name || payload.value?.app.id || '-' },
  { label: '版本号', value: payload.value?.report?.versionNumber || '-' },
  { label: '分支', value: payload.value?.report?.repoBranch || '-' },
  { label: '提交', value: payload.value?.report?.repoCommitId || '-' },
  { label: '生成时间', value: payload.value?.report?.createTimeText || '-' },
  { label: '最近处理时间', value: payload.value?.report?.lastProcessedTime || '-' },
  { label: '快照数', value: payload.value?.report?.snapshotCount ?? '-' },
  { label: '请求入口', value: payload.value?.codeRelationships?.length ?? 0 },
  { label: '圈复杂度总和', value: payload.value?.report?.totalComplexity ?? 0 },
])

function buildCodeRoute(className: string) {
  return {
    name: 'system-snapshot-code',
    params: {
      projectId: projectId.value,
      appId: appId.value,
      snapshotId: snapshotId.value,
    },
    query: { className },
  }
}

function clearReportPollTimer() {
  if (reportPollTimer) {
    window.clearTimeout(reportPollTimer)
    reportPollTimer = undefined
  }
}

async function pollReportUntilSettled(attempt = 0) {
  if (!projectId.value || !appId.value || !snapshotId.value) {
    return
  }

  try {
    await projectStore.loadSystemSnapshotReport(projectId.value, appId.value, snapshotId.value)
    const status = payload.value?.snapshot.reportStatus
    const hasReport = !!payload.value?.report

    if (status === 2 || hasReport) {
      generationMessage.value = ''
      generating.value = false
      loading.value = false
      clearReportPollTimer()
      return
    }

    if (status === 3) {
      generationMessage.value = ''
      generating.value = false
      loading.value = false
      clearReportPollTimer()
      error.value = '覆盖率报告生成失败，请检查快照是否包含可匹配的链路代码节点和静态源码数据。'
      return
    }

    if (attempt >= 20) {
      generationMessage.value = ''
      generating.value = false
      loading.value = false
      clearReportPollTimer()
      error.value = '覆盖率计算仍在处理中，请稍后刷新页面查看结果。'
      return
    }

    generationMessage.value = status === 1 ? '覆盖率报告正在生成，请稍候...' : '已提交覆盖率计算，正在等待结果...'
    reportPollTimer = window.setTimeout(() => {
      void pollReportUntilSettled(attempt + 1)
    }, Math.min(1200 + attempt * 300, 3000))
  } catch (err) {
    generationMessage.value = ''
    generating.value = false
    loading.value = false
    clearReportPollTimer()
    error.value = err instanceof Error ? err.message : '刷新覆盖率报告状态失败'
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
    await projectStore.loadSystemSnapshotReport(projectId.value, appId.value, snapshotId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载系统快照覆盖率报告失败'
  } finally {
    loading.value = false
  }
}

async function triggerReport() {
  if (generating.value) {
    return
  }

  loading.value = true
  generating.value = true
  error.value = ''
  generationMessage.value = '正在提交覆盖率计算任务...'
  clearReportPollTimer()
  try {
    await projectStore.triggerSystemSnapshotReport(projectId.value, appId.value, snapshotId.value)
    generationMessage.value = '覆盖率报告正在生成，请稍候...'
    await pollReportUntilSettled()
  } catch (err) {
    generationMessage.value = ''
    generating.value = false
    error.value = err instanceof Error ? err.message : '触发覆盖率计算失败'
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(clearReportPollTimer)
</script>
