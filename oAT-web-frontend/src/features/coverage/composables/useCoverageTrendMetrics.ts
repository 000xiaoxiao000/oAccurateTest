import { computed, type MaybeRefOrGetter, toValue } from 'vue'

export type CoverageTrendReportType = 'full' | 'commit' | 'incremental'
export type TrendTone = 'up' | 'flat' | 'down'

type TrendItem = Record<string, unknown>

const trendKeyMap: Record<string, string[]> = {
  lineCoverage: ['lineCoverage', 'lineCoverageRate', 'lineRate'],
  branchCoverage: ['branchCoverage', 'branchCoverageRate', 'branchRate'],
  methodCoverage: ['methodCoverage', 'methodCoverageRate', 'methodRate'],
}

export function useCoverageTrendMetrics(options: {
  trend: MaybeRefOrGetter<TrendItem[]>
  trendReportType: MaybeRefOrGetter<CoverageTrendReportType>
  reportKind: (item: TrendItem) => CoverageTrendReportType
  reportTypeText: (type: CoverageTrendReportType) => string
  shortHash: (value?: string) => string
}) {
  const filteredTrend = computed(() => (toValue(options.trend) || [])
    .filter((item) => options.reportKind(item) === toValue(options.trendReportType))
    .slice()
    .sort((a, b) => trendTimestamp(b) - trendTimestamp(a)))

  const trendCounts = computed(() => ({
    full: (toValue(options.trend) || []).filter((item) => options.reportKind(item) === 'full').length,
    commit: (toValue(options.trend) || []).filter((item) => options.reportKind(item) === 'commit').length,
    incremental: (toValue(options.trend) || []).filter((item) => options.reportKind(item) === 'incremental').length,
  }))

  function trendMetric(item: TrendItem, key: string) {
    const value = Number(trendValue(item, key))
    if (!Number.isFinite(value)) return '-'
    return `${value.toFixed(1)}%`
  }

  function trendDelta(index: number, key: string) {
    const current = trendNumber(filteredTrend.value[index], key)
    const previous = trendNumber(filteredTrend.value[index + 1], key)
    if (current === null || previous === null) return null
    return current - previous
  }

  function trendDirectionTone(index: number, key: string): TrendTone {
    const delta = trendDelta(index, key)
    if (delta === null || Math.abs(delta) < 0.05) return 'flat'
    return delta > 0 ? 'up' : 'down'
  }

  function trendDirectionSymbol(index: number, key: string) {
    const tone = trendDirectionTone(index, key)
    if (tone === 'up') return '↗'
    if (tone === 'down') return '↘'
    return '→'
  }

  function trendDirectionText(index: number, key: string) {
    const delta = trendDelta(index, key)
    if (delta === null) return '水平'
    const absDelta = Math.abs(delta).toFixed(1)
    if (Math.abs(delta) < 0.05) return '水平'
    return delta > 0 ? `上升 ${absDelta}%` : `下降 ${absDelta}%`
  }

  function trendDirectionTitle(index: number, key: string) {
    const delta = trendDelta(index, key)
    if (delta === null) return '暂无更早趋势数据可比对'
    return `较上一条较早数据${trendDirectionText(index, key)}`
  }

  function trendTime(item: TrendItem) {
    const timestamp = Number(item.timestamp)
    if (Number.isFinite(timestamp) && timestamp > 0) {
      return new Date(timestamp).toLocaleString()
    }
    return String(item.time || item.createTimeText || '-')
  }

  function trendReportTypeLabel(item: TrendItem) {
    return options.reportTypeText(options.reportKind(item)).replace('报告', '')
  }

  function trendReportMeta(item: TrendItem) {
    if (options.reportKind(item) === 'incremental') {
      return `基准 ${String(item.baseVersionNumber || '-')} · Commit ${options.shortHash(String(item.baseRepoCommitId || ''))}`
    }
    return `Commit ${options.shortHash(String(item.repoCommitId || ''))}`
  }

  return {
    filteredTrend,
    trendCounts,
    trendMetric,
    trendDirectionTone,
    trendDirectionSymbol,
    trendDirectionText,
    trendDirectionTitle,
    trendTime,
    trendReportTypeLabel,
    trendReportMeta,
  }
}

function trendValue(item: TrendItem, key: string) {
  for (const candidate of trendKeyMap[key] || [key]) {
    if (item[candidate] !== undefined && item[candidate] !== null) return item[candidate]
  }
  return undefined
}

function trendTimestamp(item: TrendItem | undefined) {
  if (!item) return 0
  const timestamp = Number(item.timestamp)
  if (Number.isFinite(timestamp)) return timestamp
  const parsed = Date.parse(String(item.createTimeText || item.time || ''))
  return Number.isFinite(parsed) ? parsed : 0
}

function trendNumber(item: TrendItem | undefined, key: string) {
  const value = Number(item ? trendValue(item, key) : undefined)
  return Number.isFinite(value) ? value : null
}
