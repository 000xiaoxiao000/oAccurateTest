import { computed, type Ref } from 'vue'

export type CoverageJobStatus = {
  data?: string
  progress?: number
  progressName?: string
  finish?: boolean
  success?: boolean
  message?: string
} | null

export type CoverageJobLog = {
  time: string
  text: string
  tone: 'running' | 'done' | 'error'
}

export function useCoverageJobProgress(jobStatus: Ref<CoverageJobStatus>, jobLogs: Ref<CoverageJobLog[]>) {
  const parsedJobProgress = computed(() => {
    const raw = jobStatus.value?.progressName || ''
    try {
      return JSON.parse(raw) as { name?: string; percent?: number; loaded?: number; total?: number }
    } catch {
      return null
    }
  })

  const jobProgress = computed(() => {
    const directProgress = Number(jobStatus.value?.progress)
    const parsedProgress = Number(parsedJobProgress.value?.percent)
    const progress = Number.isFinite(directProgress) ? directProgress : Number.isFinite(parsedProgress) ? parsedProgress : 0
    return Math.max(0, Math.min(100, Math.round(progress)))
  })

  const jobFailed = computed(() => jobStatus.value?.success === false || /失败|错误|异常/.test(parsedJobProgress.value?.name || jobStatus.value?.message || ''))

  const jobStateText = computed(() => {
    if (jobFailed.value) return '失败'
    return jobStatus.value?.finish ? '已完成' : '处理中'
  })

  const jobStageText = computed(() => formatJobStage(jobStatus.value))

  function appendJobLog(text: string, tone: CoverageJobLog['tone'] = 'running') {
    const last = jobLogs.value[jobLogs.value.length - 1]
    if (last?.text === text && last.tone === tone) return
    jobLogs.value = [...jobLogs.value, { time: new Date().toLocaleTimeString(), text, tone }].slice(-8)
  }

  return {
    jobProgress,
    parsedJobProgress,
    jobFailed,
    jobStateText,
    jobStageText,
    appendJobLog,
    formatJobStage,
    readableJobError,
  }
}

export function formatJobStage(status: CoverageJobStatus) {
  const raw = status?.message || status?.progressName || '处理中'
  try {
    const parsed = JSON.parse(status?.progressName || '') as { name?: string; loaded?: number; total?: number }
    const name = parsed.name || raw
    if (typeof parsed.loaded === 'number' && typeof parsed.total === 'number' && parsed.total > 0) return `${name}（${parsed.loaded} / ${parsed.total}）`
    return name
  } catch {
    return raw
  }
}

export function readableJobError(err: unknown) {
  const message = err instanceof Error ? err.message : '任务状态查询失败'
  if (message.includes('非 JSON')) return '任务状态接口返回异常响应，请稍后刷新或检查后端任务日志'
  return message
}
