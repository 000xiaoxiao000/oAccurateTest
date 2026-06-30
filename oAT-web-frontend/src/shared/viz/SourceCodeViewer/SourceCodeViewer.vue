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

    <div v-if="html" ref="htmlRef" class="source-html" v-html="html"></div>
    <div v-else class="source-table" role="table" aria-label="源码覆盖率">
      <div
        v-for="line in sourceLines"
        :key="line.line"
        class="source-row"
        :class="[lineTone(line), { changed: line.changed }]"
        role="row"
      >
        <span class="line-number" role="cell">{{ line.line }}</span>
        <span class="line-hits" role="cell">{{ line.hits || 0 }}</span>
        <code class="line-code" role="cell">{{ line.text || ' ' }}</code>
        <span v-if="line.cases?.length" class="line-cases" role="cell" :title="line.cases.join('\\n')">
          {{ line.cases.length }} cases
        </span>
        <span v-if="branchesByLine[line.line]?.length" class="line-branches" role="cell">
          {{ coveredBranchesByLine(line.line) }}/{{ branchesByLine[line.line].length }} branches
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SourceCoverageLine, SourceCoveragePayload } from './types'

const props = defineProps<{
  payload?: SourceCoveragePayload
  html?: string
}>()

const htmlRef = ref<HTMLElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const payload = computed<SourceCoveragePayload>(() => props.payload || { lines: [] })
const sourceLines = computed(() => payload.value.lines || [])

defineExpose({
  htmlRef,
  scrollToTop,
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

function lineTone(line: SourceCoverageLine) {
  if (Number(line.hits || 0) > 0) return 'covered'
  if (line.text.trim()) return 'uncovered'
  return 'neutral'
}

function scrollToTop() {
  const target = htmlRef.value || rootRef.value?.querySelector('.source-table')
  target?.scrollTo({ top: 0, left: 0, behavior: 'smooth' })
}
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
  grid-template-columns: 64px 56px minmax(0, 1fr) auto auto;
  min-height: 28px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.source-row.covered {
  background: rgba(15, 118, 110, 0.06);
}

.source-row.uncovered {
  background: rgba(185, 28, 28, 0.05);
}

.source-row.changed {
  box-shadow: inset 3px 0 #d97706;
}

.line-number,
.line-hits,
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

.line-number,
.line-hits {
  justify-content: flex-end;
  border-right: 1px solid rgba(15, 23, 42, 0.06);
}

.line-code {
  min-width: 0;
  padding: 5px 12px;
  color: #0f172a;
  white-space: pre;
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
