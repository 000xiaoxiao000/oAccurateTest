<template>
  <section class="monitor-toolbar panel">
    <div class="toolbar-main">
      <div class="toolbar-group compact-group">
        <span class="toolbar-label">时间窗口</span>
        <select v-model.number="timeWindowModel" class="text-input time-select">
          <option :value="60">一分钟内</option>
          <option :value="180">三分钟内</option>
          <option :value="300">五分钟内</option>
          <option :value="1800">三十分钟内</option>
        </select>
      </div>
      <div class="toolbar-group filter-group">
        <span class="toolbar-label">应用</span>
        <div class="filter-chip-row" role="listbox" aria-label="应用过滤">
          <button class="filter-chip" :class="{ active: selectedAppIds.length === 0 }" type="button" @click="$emit('clear-app-filter')">全部</button>
          <button v-for="app in projectApps" :key="app.id" class="filter-chip" :class="{ active: selectedAppIds.includes(app.id) }" type="button" @click="$emit('toggle-app-filter', app.id)">
            {{ app.name }}
          </button>
        </div>
      </div>
      <div class="toolbar-group filter-group">
        <span class="toolbar-label">探针 IP</span>
        <div class="filter-chip-row" role="listbox" aria-label="探针 IP 过滤">
          <button class="filter-chip" :class="{ active: selectedClientIps.length === 0 }" type="button" @click="$emit('clear-ip-filter')">全部</button>
          <button v-for="ip in availableClientIps" :key="ip" class="filter-chip" :class="{ active: selectedClientIps.includes(ip) }" type="button" @click="$emit('toggle-ip-filter', ip)">
            {{ ip }}
          </button>
        </div>
      </div>
      <div class="toolbar-group compact-group">
        <span class="toolbar-label">数量</span>
        <input v-model.number="maxSizeModel" class="text-input size-input" type="number" min="10" max="500" />
      </div>
      <button class="ghost-button query-button" type="button" @click="$emit('query')">查询</button>
    </div>
    <div class="toolbar-actions">
      <div class="snapshot-actions" :class="{ disabled: snapshotUnavailable, open: snapshotMenuOpen }" :title="snapshotUnavailableReason">
        <button class="action-button snapshot-trigger" type="button" :disabled="snapshotUnavailable" aria-haspopup="menu" :aria-expanded="snapshotMenuOpen" @click="$emit('toggle-snapshot-menu')">
          保存快照 <span aria-hidden="true">⌄</span>
        </button>
        <div class="snapshot-menu">
          <button type="button" :disabled="snapshotUnavailable" @click="$emit('open-snapshot-dialog', 'my')">我的快照</button>
          <button type="button" :disabled="snapshotUnavailable" @click="$emit('open-snapshot-dialog', 'system')">系统快照</button>
        </div>
      </div>
      <label class="auto-refresh"><input v-model="autoSaveMyModel" type="checkbox" /> 自动保存我的快照</label>
      <label class="auto-refresh"><input v-model="autoSaveSystemModel" type="checkbox" /> 自动保存系统快照</label>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { AppSummary } from '@/api/types'

const props = defineProps<{
  timeWindow: number
  maxSize: number
  projectApps: AppSummary[]
  selectedAppIds: string[]
  availableClientIps: string[]
  selectedClientIps: string[]
  selectedTraceId: string
  savingSnapshot: boolean
  snapshotMenuOpen: boolean
  autoSaveMySnapshot: boolean
  autoSaveSystemSnapshot: boolean
}>()

const emit = defineEmits<{
  'update:timeWindow': [value: number]
  'update:maxSize': [value: number]
  'update:autoSaveMySnapshot': [value: boolean]
  'update:autoSaveSystemSnapshot': [value: boolean]
  'clear-app-filter': []
  'toggle-app-filter': [appId: string]
  'clear-ip-filter': []
  'toggle-ip-filter': [ip: string]
  query: []
  'toggle-snapshot-menu': []
  'open-snapshot-dialog': [mode: 'my' | 'system']
}>()

const timeWindowModel = computed({
  get: () => props.timeWindow,
  set: (value: number) => emit('update:timeWindow', value),
})
const maxSizeModel = computed({
  get: () => props.maxSize,
  set: (value: number) => emit('update:maxSize', value),
})
const snapshotUnavailable = computed(() => !props.selectedTraceId || props.savingSnapshot)
const snapshotUnavailableReason = computed(() => {
  if (!props.selectedTraceId) return '请先选择一条 trace'
  if (props.savingSnapshot) return '快照保存中'
  return ''
})
const autoSaveMyModel = computed({
  get: () => props.autoSaveMySnapshot,
  set: (value: boolean) => emit('update:autoSaveMySnapshot', value),
})
const autoSaveSystemModel = computed({
  get: () => props.autoSaveSystemSnapshot,
  set: (value: boolean) => emit('update:autoSaveSystemSnapshot', value),
})
</script>

<style scoped>
.panel {
  border: 1px solid rgba(15, 23, 42, .08);
  background: #ffffff;
}

.monitor-toolbar {
  position: sticky;
  top: 72px;
  z-index: 8;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 16px;
  backdrop-filter: blur(12px);
  background: rgba(255, 255, 255, .92);
  border-radius: 20px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
}

.toolbar-main,
.toolbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-main {
  min-width: 0;
}

.toolbar-actions {
  justify-content: flex-end;
  max-width: 480px;
}

.toolbar-group {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.compact-group {
  flex: 0 0 auto;
}

.filter-group {
  flex: 1 1 240px;
  max-width: 360px;
}

.toolbar-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.filter-chip-row {
  display: flex;
  gap: 4px;
  min-height: 38px;
  max-height: 76px;
  overflow: auto;
  padding: 4px;
  border: 1px solid rgba(203, 213, 225, .6);
  border-radius: 12px;
  background: #fbfdfe;
  scrollbar-width: thin;
}

.filter-chip {
  flex: 0 0 auto;
  max-width: 160px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 7px 12px;
  background: #ffffff;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(15, 23, 42, .08);
  transition: all 150ms ease;
}

.filter-chip:hover {
  background: rgba(241, 245, 249, .9);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
}

.filter-chip.active {
  border-color: #0f766e;
  background: #0f766e;
  color: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
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

.size-input {
  width: 96px;
}

.time-select {
  width: 140px;
}

.query-button {
  align-self: end;
}

.action-button,
.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms ease;
  white-space: nowrap;
}

.action-button {
  background: #0f766e;
  color: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, .08);
}

.ghost-button {
  background: rgba(14, 116, 144, .08);
  color: #0f766e;
}

.action-button:hover:not(:disabled),
.ghost-button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.snapshot-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.snapshot-actions {
  position: relative;
}

.snapshot-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 12;
  min-width: 140px;
  display: none;
  padding: 4px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, .14);
}

.snapshot-actions.open .snapshot-menu,
.snapshot-actions:focus-within .snapshot-menu {
  display: grid;
  gap: 4px;
}

.snapshot-menu button {
  border: none;
  border-radius: 8px;
  padding: 9px 12px;
  background: transparent;
  color: #334155;
  text-align: left;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.snapshot-menu button:hover {
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
}

.auto-refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
}

.auto-refresh input[type='checkbox'] {
  accent-color: #0f766e;
  width: 14px;
  height: 14px;
  cursor: pointer;
}

@media (max-width: 980px) {
  .monitor-toolbar {
    position: static;
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
    max-width: none;
  }
}
</style>
