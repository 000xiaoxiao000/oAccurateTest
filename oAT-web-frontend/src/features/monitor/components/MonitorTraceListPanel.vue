<template>
  <section class="panel trace-panel">
    <div class="panel-head trace-head">
      <div>
        <h2>监控事件</h2>
        <p>手动刷新或开启自动刷新，同步更新调用链与覆盖率上报。</p>
      </div>
      <div class="auto-refresh-controls">
        <label class="auto-refresh"><input v-model="autoRefreshModel" type="checkbox" /> 自动刷新</label>
        <label class="refresh-interval">
          <span>每</span>
          <input v-model.number="refreshSecondsModel" class="text-input refresh-input" type="number" min="3" max="120" aria-label="自动刷新间隔（秒）" />
          <span>秒</span>
        </label>
        <span class="refresh-state">{{ autoRefresh ? '运行中' : '已暂停' }}</span>
        <button class="ghost-button small-button" type="button" :disabled="loading" @click="$emit('refresh')">
          {{ loading ? '刷新中...' : '手动刷新' }}
        </button>
      </div>
    </div>

    <div class="trace-panel-content">
      <div class="toolbar">
        <input v-model.trim="keywordModel" class="text-input" type="search" placeholder="搜索 URL / traceId / 覆盖率 / IP" aria-label="搜索监控事件" />
        <select v-model.number="pageSizeModel" class="text-input compact" aria-label="Trace 每页条数">
          <option :value="20">每页 20 条</option>
          <option :value="50">每页 50 条</option>
          <option :value="100">每页 100 条</option>
        </select>
        <button class="ghost-button" type="button" @click="$emit('clear-filters')">清空过滤</button>
      </div>

      <div v-if="error" class="status-card error">{{ error }}</div>
      <div v-if="loading && totalCount === 0" class="status-card">正在加载监控事件...</div>
      <div v-else-if="totalCount === 0" class="status-card">暂无监控事件</div>
      <div v-else class="trace-list">
        <button
          v-for="trace in traces"
          :key="trace.traceId"
          class="trace-item"
          :class="{ active: selectedTraceId === trace.traceId }"
          type="button"
          @click="$emit('select-trace', trace)"
        >
          <div class="trace-item-header">
            <strong class="trace-title">{{ trace.title || trace.traceId }}</strong>
            <span class="trace-time">{{ formatTraceTime(trace.cacheTime) }}</span>
          </div>
          <div class="trace-item-meta">
            <span :class="['event-type-pill', { coverage: isCoverageEvent(trace) }]">{{ eventTypeLabel(trace) }}</span>
            <span>{{ trace.addressIp || '-' }}</span>
            <span>{{ trace.clientIp || '-' }}</span>
          </div>
          <small class="trace-item-id" :title="trace.traceId">{{ trace.traceId }}</small>
        </button>
      </div>

      <AppPagination
        v-if="totalCount > pageSize"
        v-model:page="pageModel"
        v-model:page-size="pageSizeModel"
        :total="totalCount"
        item-name="条事件"
        :page-sizes="[20, 50, 100]"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TraceItemSummary } from '@/api/types'
import AppPagination from '@/components/AppPagination.vue'

const props = defineProps<{
  traces: TraceItemSummary[]
  totalCount: number
  selectedTraceId: string
  keyword: string
  page: number
  pageSize: number
  loading: boolean
  error: string
  autoRefresh: boolean
  refreshSeconds: number
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:page': [value: number]
  'update:pageSize': [value: number]
  'update:autoRefresh': [value: boolean]
  'update:refreshSeconds': [value: number]
  refresh: []
  'clear-filters': []
  'select-trace': [trace: TraceItemSummary]
}>()

const keywordModel = computed({
  get: () => props.keyword,
  set: (value: string) => emit('update:keyword', value),
})
const pageModel = computed({
  get: () => props.page,
  set: (value: number) => emit('update:page', value),
})
const pageSizeModel = computed({
  get: () => props.pageSize,
  set: (value: number) => emit('update:pageSize', value),
})
const autoRefreshModel = computed({
  get: () => props.autoRefresh,
  set: (value: boolean) => emit('update:autoRefresh', value),
})
const refreshSecondsModel = computed({
  get: () => props.refreshSeconds,
  set: (value: number) => emit('update:refreshSeconds', value),
})

function formatTraceTime(value?: number) {
  if (!value) return '等待中'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '等待中'
  return date.toLocaleTimeString()
}

function isCoverageEvent(trace: TraceItemSummary) {
  return trace.entryType === 'coverage' || trace.traceId?.startsWith('coverage:')
}

function eventTypeLabel(trace: TraceItemSummary) {
  return isCoverageEvent(trace) ? '覆盖率上报' : 'Trace'
}
</script>

<style scoped>
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel,
.status-card,
.trace-item {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
  transition: all 200ms ease;
}

.panel,
.status-card {
  padding: 16px;
}

.status-card {
  border-radius: 16px;
  background: rgba(241, 245, 249, .6);
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.status-card.error {
  background: rgba(254, 226, 226, .8);
  color: #b91c1c;
}

.trace-panel {
  align-self: start;
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 140px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.trace-panel-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.trace-head {
  align-items: flex-start;
}

.trace-head p {
  color: #64748b;
}

.auto-refresh-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  margin: 8px 0;
  flex-shrink: 0;
}

.toolbar .text-input[type='search'] {
  grid-column: 1 / -1;
}

.toolbar .ghost-button {
  min-width: 96px;
  white-space: nowrap;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(203, 213, 225, .8);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fbfdfe;
  color: #0f172a;
  font-size: 14px;
  transition: all 150ms ease;
}

.text-input:focus {
  outline: none;
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(14, 116, 144, .1);
}

.refresh-input {
  width: 70px;
  padding: 7px 9px;
}

.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(14, 116, 144, .08);
  color: #0f766e;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms ease;
  white-space: nowrap;
}

.ghost-button:hover:not(:disabled) {
  background: rgba(14, 116, 144, .14);
  transform: translateY(-1px);
}

.small-button {
  min-height: 34px;
  padding: 7px 14px;
  font-size: 13px;
}

.auto-refresh,
.refresh-interval,
.refresh-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.auto-refresh {
  gap: 8px;
  cursor: pointer;
}

.auto-refresh input[type='checkbox'] {
  accent-color: #0f766e;
  width: 14px;
  height: 14px;
  cursor: pointer;
}

.refresh-state {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(241, 245, 249, .8);
  font-size: 12px;
  font-weight: 600;
}

.trace-list {
  display: grid;
  align-content: start;
  grid-auto-rows: max-content;
  gap: 6px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(15, 23, 42, .12) transparent;
}

.trace-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 10px 14px;
  border-radius: 14px;
  text-align: left;
  cursor: pointer;
  white-space: normal;
  min-height: 118px;
  box-shadow: none;
  transition: all 150ms ease;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(248, 250, 252, .96));
}

.trace-item:hover {
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
  transform: translateX(2px);
  background: linear-gradient(135deg, rgba(255, 255, 255, 1), rgba(241, 245, 249, .98));
}

.trace-item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.trace-title {
  display: -webkit-box;
  overflow-wrap: anywhere;
  word-break: break-word;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.45;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  flex: 1;
  min-width: 0;
}

.trace-item-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #64748b;
}

.trace-item-meta span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(241, 245, 249, .75);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-item-meta .event-type-pill {
  border: 1px solid rgba(59, 130, 246, .18);
  background: rgba(59, 130, 246, .08);
  color: #1d4ed8;
}

.trace-item-meta .event-type-pill.coverage {
  border-color: rgba(15, 118, 110, .22);
  background: rgba(20, 184, 166, .1);
  color: #0f766e;
}

.trace-item-id {
  padding: 3px 8px;
  border-radius: 8px;
  background: rgba(248, 250, 252, .9);
  border: 1px solid rgba(226, 232, 240, .8);
  color: #64748b;
  font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trace-time {
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  background: rgba(15, 118, 110, .1);
  padding: 3px 7px;
  border-radius: 8px;
  white-space: nowrap;
  flex-shrink: 0;
}

.trace-item.active {
  border-color: #0f766e;
  background: linear-gradient(135deg, rgba(236, 253, 245, .95), rgba(240, 253, 250, .92));
  box-shadow: 0 0 0 2px rgba(15, 118, 110, .2), 0 4px 12px rgba(15, 23, 42, .08);
  transform: translateX(2px);
}

.trace-list::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.trace-list::-webkit-scrollbar-track {
  background: transparent;
}

.trace-list::-webkit-scrollbar-thumb {
  background: rgba(15, 23, 42, .12);
  border-radius: 999px;
}

@media (max-width: 980px) {
  .panel-head,
  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .trace-panel {
    height: auto;
    min-height: 400px;
  }

  .trace-list {
    max-height: 360px;
  }
}
</style>
