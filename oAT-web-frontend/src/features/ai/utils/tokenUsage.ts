import type { AITokenUsage } from '@/api/types'

export function normalizeTokenUsage(value: unknown): AITokenUsage | undefined {
  if (!value || typeof value !== 'object') return undefined
  const source = value as Record<string, unknown>
  const inputTokens = numeric(source.inputTokens ?? source.input)
  const outputTokens = numeric(source.outputTokens ?? source.output)
  const totalTokens = numeric(source.totalTokens ?? source.total)
  const normalizedTotal = totalTokens ?? ((inputTokens || 0) + (outputTokens || 0))
  if (!normalizedTotal) return undefined
  return {
    inputTokens,
    outputTokens,
    totalTokens: normalizedTotal,
    estimated: source.estimated === true,
  }
}

export function formatTokenUsage(usage?: AITokenUsage) {
  const normalized = normalizeTokenUsage(usage)
  if (!normalized?.totalTokens) return ''
  const parts = [`本次消耗 ${formatTokenCount(normalized.totalTokens)} tokens`]
  if (typeof normalized.inputTokens === 'number') parts.push(`输入 ${formatTokenCount(normalized.inputTokens)}`)
  if (typeof normalized.outputTokens === 'number') parts.push(`输出 ${formatTokenCount(normalized.outputTokens)}`)
  if (normalized.estimated) parts.push('估算')
  return parts.join(' · ')
}

function numeric(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return Math.max(0, Math.round(value))
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return Math.max(0, Math.round(parsed))
  }
  return undefined
}

function formatTokenCount(value: number) {
  if (value < 1000) return String(value)
  if (value < 10000) return `${Number((value / 1000).toFixed(1))}K`
  return `${Math.round(value / 1000)}K`
}
