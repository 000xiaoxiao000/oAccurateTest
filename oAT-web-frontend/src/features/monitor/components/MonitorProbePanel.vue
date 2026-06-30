<template>
  <section class="panel probe-panel probe-dashboard-card">
    <div class="panel-head probe-head">
      <div>
        <h2>探针状态</h2>
        <p>点击探针可快速过滤 IP。</p>
      </div>
      <div class="header-actions">
        <input v-model.trim="keywordModel" class="text-input compact" type="search" placeholder="搜索应用、IP、PID、Agent" aria-label="搜索探针" />
        <button v-if="filteredProbes.length > previewLimit" class="ghost-button" type="button" @click="$emit('toggle-expanded')">
          {{ expanded ? '收起探针' : '展开探针' }}
        </button>
        <button class="ghost-button" type="button" @click="$emit('refresh')">刷新探针状态</button>
      </div>
    </div>
    <div class="probe-content">
      <div class="probe-summary">在线 {{ filteredProbes.length }} / {{ totalCount }} 个探针，展示 {{ visibleProbes.length }} 个</div>
      <div v-if="loading" class="status-card compact-status">正在加载探针...</div>
      <div v-else-if="error" class="status-card compact-status error">{{ error }}</div>
      <div v-else-if="filteredProbes.length === 0" class="status-card compact-status">暂无在线探针</div>
      <div v-else class="probe-grid">
        <button v-for="probe in visibleProbes" :key="probeKey(probe)" class="probe-card" :class="{ active: selectedClientIps.includes(probe.addressIp || '') }" type="button" @click="$emit('toggle-probe', probe)">
          <span class="probe-status-dot"></span>
          <strong>{{ probe.appName || '未定义应用' }}</strong>
          <span>{{ probe.addressIp || '-' }} · PID {{ probe.pid || '-' }}</span>
          <small>{{ probe.projectSrcName || '-' }} · {{ probe.agentVersion || 'unknown agent' }}</small>
          <small>在线 {{ probe.onlineTime || '-' }}</small>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { OnlineSessionSummary } from '@/api/types'

const props = defineProps<{
  keyword: string
  filteredProbes: OnlineSessionSummary[]
  visibleProbes: OnlineSessionSummary[]
  selectedClientIps: string[]
  totalCount: number
  previewLimit: number
  expanded: boolean
  loading: boolean
  error: string
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'toggle-expanded': []
  refresh: []
  'toggle-probe': [probe: OnlineSessionSummary]
}>()

const keywordModel = computed({
  get: () => props.keyword,
  set: (value: string) => emit('update:keyword', value),
})

function probeKey(probe: OnlineSessionSummary) {
  return `${probe.addressIp || ''}-${probe.pid || ''}-${probe.systemDir || ''}-${probe.appName || ''}`
}
</script>

<style scoped>
.panel-head,
.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel,
.status-card,
.probe-card {
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

.probe-panel {
  margin-bottom: 12px;
}

.probe-dashboard-card {
  display: grid;
  grid-template-columns: minmax(360px, .9fr) minmax(0, 1.4fr);
  align-items: start;
  gap: 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(248, 250, 252, .98));
}

.probe-head {
  align-items: start;
}

.probe-head h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.probe-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.probe-head .header-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.probe-content {
  min-width: 0;
}

.probe-summary {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.compact-status {
  padding: 12px;
  border-radius: 16px;
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

.text-input.compact {
  width: min(320px, 100%);
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

.probe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
  margin-top: 0;
}

.probe-card {
  position: relative;
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 16px;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(15, 23, 42, .08);
  background: #ffffff;
}

.probe-card:hover {
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
  transform: translateY(-2px);
  border-color: #0f766e;
}

.probe-card strong,
.probe-card span,
.probe-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.probe-card strong {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.probe-card span,
.probe-card small {
  color: #64748b;
}

.probe-card span {
  font-size: 12px;
}

.probe-card small {
  font-size: 11px;
}

.probe-card.active {
  border-color: #0f766e;
  background: rgba(236, 253, 245, .8);
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
}

.probe-status-dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, .2);
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(34, 197, 94, .2);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(34, 197, 94, .1);
  }
}

@media (max-width: 980px) {
  .probe-dashboard-card {
    grid-template-columns: 1fr !important;
  }

  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
