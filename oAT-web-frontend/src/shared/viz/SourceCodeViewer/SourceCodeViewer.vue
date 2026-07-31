<template>
  <section ref="rootRef" class="source-viewer">
    <header class="source-header">
      <div>
        <strong>{{ payload.sourcePath || 'source' }}</strong>
        <span>{{ payload.language || 'UNKNOWN' }}</span>
      </div>
      <div class="source-summary">
        <span>{{ coveredLineCount }} / {{ sourceLines.length }} 行</span>
        <span>{{ branchSummary }}</span>
      </div>
    </header>

    <div v-if="html" ref="htmlRef" class="source-html" v-html="html" @scroll="handleSourceScroll"></div>
    <div v-else ref="tableRef" class="source-table" role="table" aria-label="源码覆盖率" @scroll="handleSourceScroll">
      <div
        v-for="line in sourceLines"
        :key="line.line"
        class="source-row"
        :class="[lineTone(line), { changed: line.changed }]"
        :data-source-line="line.line"
        role="row"
      >
        <span class="line-number" role="cell">{{ line.line }}</span>
        <code class="line-code" role="cell">{{ line.text || ' ' }}</code>
        <span v-if="line.cases?.length" class="line-cases" role="cell" :title="line.cases.join('\\n')">
          {{ line.cases.length }} cases
        </span>
        <span
          v-if="branchesByLine[line.line]?.length"
          class="line-branches"
          :class="branchTone(line.line)"
          role="cell"
        >
          {{ coveredBranchesByLine(line.line) }}/{{ branchesByLine[line.line].length }} 分支
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { SourceCoverageLine, SourceCoveragePayload } from './types'

const props = defineProps<{
  payload?: SourceCoveragePayload
  html?: string
}>()
const emit = defineEmits<{
  visibleLineChange: [line: number]
}>()

const htmlRef = ref<HTMLElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const tableRef = ref<HTMLElement | null>(null)
const payload = computed<SourceCoveragePayload>(() => props.payload || { lines: [] })
const sourceLines = computed(() => payload.value.lines || [])
let scrollFrame = 0
let lastVisibleLine = 0

defineExpose({
  htmlRef,
  tableRef,
  scrollToTop,
  scrollToLine,
  scrollToRange,
  element: rootRef,
})

const coveredLineCount = computed(() => sourceLines.value.filter((line) => Number(line.hits || 0) > 0).length)

const branchesByLine = computed(() => {
  const result: Record<number, NonNullable<SourceCoveragePayload['branches']>> = {}
  for (const branch of payload.value.branches || []) {
    if (!result[branch.line]) result[branch.line] = []
    result[branch.line].push(branch)
  }
  return result
})

const branchSummary = computed(() => {
  const branches = payload.value.branches || []
  if (!branches.length) return '0 / 0 分支'
  const covered = branches.filter((branch) => Number(branch.hits || 0) > 0).length
  return `${covered} / ${branches.length} 分支`
})

function coveredBranchesByLine(line: number) {
  return (branchesByLine.value[line] || []).filter((branch) => Number(branch.hits || 0) > 0).length
}

function branchTone(line: number) {
  const total = branchesByLine.value[line]?.length || 0
  const covered = coveredBranchesByLine(line)
  if (!total || covered <= 0) return 'branch-none'
  if (covered < total) return 'branch-partial'
  return 'branch-full'
}

function lineTone(line: SourceCoverageLine) {
  if (Number(line.hits || 0) > 0) return 'covered'
  if (line.text.trim()) return 'uncovered'
  return 'neutral'
}

function handleSourceScroll(event: Event) {
  const container = event.currentTarget as HTMLElement | null
  if (!container || scrollFrame) {
    return
  }
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    const nextLine = findVisibleLine(container)
    if (nextLine && nextLine !== lastVisibleLine) {
      lastVisibleLine = nextLine
      emit('visibleLineChange', nextLine)
    }
  })
}

function findVisibleLine(container: HTMLElement) {
  const containerRect = container.getBoundingClientRect()
  const targetTop = containerRect.top + 40
  let fallback = 0
  for (const row of Array.from(container.querySelectorAll<HTMLElement>('[data-source-line]'))) {
    const rect = row.getBoundingClientRect()
    const line = Number(row.dataset.sourceLine || 0)
    if (!fallback && rect.bottom >= containerRect.top) {
      fallback = line
    }
    if (rect.top <= targetTop && rect.bottom >= targetTop) {
      return line
    }
    if (rect.top > targetTop) {
      return fallback || line
    }
  }
  return fallback
}

function scrollToTop() {
  const target = htmlRef.value || tableRef.value
  target?.scrollTo({ top: 0, left: 0, behavior: 'smooth' })
}

function scrollToLine(lineNumber?: number) {
  if (!lineNumber || lineNumber <= 0) {
    return false
  }
  const container = htmlRef.value || tableRef.value
  if (!container) {
    return false
  }
  const target = container.querySelector<HTMLElement>(`[data-source-line="${lineNumber}"]`)
  if (!target) {
    return false
  }
  clearJumpHighlights(container)
  target.dataset.sourceJumpHighlight = 'true'
  const sourceRect = container.getBoundingClientRect()
  const targetRect = target.getBoundingClientRect()
  const nextTop = container.scrollTop + targetRect.top - sourceRect.top - container.clientHeight / 2 + targetRect.height / 2
  container.scrollTo({ top: Math.max(0, nextTop), behavior: 'smooth' })
  container.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  return true
}

function scrollToRange(startLine?: number, endLine?: number) {
  if (!startLine || startLine <= 0) {
    return false
  }
  const container = htmlRef.value || tableRef.value
  if (!container) {
    return false
  }
  const normalizedEnd = Math.max(startLine, endLine || startLine)
  const target = container.querySelector<HTMLElement>(`[data-source-line="${startLine}"]`)
  if (!target) {
    return scrollToLine(startLine)
  }
  clearJumpHighlights(container)
  for (const row of Array.from(container.querySelectorAll<HTMLElement>('[data-source-line]'))) {
    const line = Number(row.dataset.sourceLine || 0)
    if (line >= startLine && line <= normalizedEnd) {
      row.dataset.sourceJumpHighlight = 'true'
      row.dataset.sourceRangeHighlight = 'true'
    }
  }
  const sourceRect = container.getBoundingClientRect()
  const targetRect = target.getBoundingClientRect()
  const nextTop = container.scrollTop + targetRect.top - sourceRect.top - 24
  container.scrollTo({ top: Math.max(0, nextTop), behavior: 'smooth' })
  container.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  return true
}

function clearJumpHighlights(container: HTMLElement) {
  container.querySelectorAll<HTMLElement>('[data-source-jump-highlight="true"], [data-source-range-highlight="true"]').forEach((node) => {
    delete node.dataset.sourceJumpHighlight
    delete node.dataset.sourceRangeHighlight
  })
}

onBeforeUnmount(() => {
  if (scrollFrame) {
    window.cancelAnimationFrame(scrollFrame)
  }
})
</script>

<style scoped>
.source-viewer {
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 8px;
  background: #fff;
}

.source-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: #f8fafc;
}

.source-header div,
.source-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  min-width: 0;
}

.source-header strong {
  overflow: hidden;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-header span,
.source-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.source-table,
.source-html {
  max-height: 680px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
}

.source-html {
  scrollbar-gutter: stable both-edges;
}

:deep(.source-html pre) {
  margin: 0;
}

:deep(.source-html td),
:deep(.source-html th),
:deep(.source-html code) {
  white-space: pre;
}

.source-row {
  display: grid;
  grid-template-columns: 64px minmax(max-content, 1fr) auto auto;
  width: max-content;
  min-width: 100%;
  min-height: 28px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.source-row.covered {
  background: #e6f6f1;
}

.source-row.uncovered {
  background: #fdebea;
}

.source-row.neutral {
  background: #f8fafc;
}

.source-row.changed {
  box-shadow: inset 3px 0 #d97706;
}

.source-row[data-source-jump-highlight="true"] {
  outline: 2px solid rgba(37, 99, 235, 0.5);
  outline-offset: -2px;
}

.line-cases,
.line-branches {
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.line-number {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 10px;
  border-right: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.42);
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.line-code {
  min-width: 0;
  padding: 5px 12px;
  color: #0f172a;
  white-space: pre;
}

.line-branches {
  max-width: 168px;
  margin: 4px 8px 4px 0;
  border-radius: 999px;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
}

.line-branches.branch-full {
  background: rgba(22, 163, 74, 0.16);
  color: #166534;
}

.line-branches.branch-partial {
  background: rgba(217, 119, 6, 0.18);
  color: #92400e;
}

.line-branches.branch-none {
  background: rgba(220, 38, 38, 0.16);
  color: #991b1b;
}

@media (max-width: 760px) {
  .source-header,
  .source-header div,
  .source-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .source-row {
    grid-template-columns: 48px 44px minmax(220px, 1fr);
  }

  .line-cases,
  .line-branches {
    display: none;
  }
}
</style>
