<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Probe Alerts</div>
        <h1>探针状态与告警记录</h1>
        <p class="subtext">查看当前应用探针实例在线状态、最近上下线事件和通知结果。</p>
      </div>
      <div class="header-actions">
        <span class="stream-state" :class="{ active: alertStreamConnected }">{{ alertStreamConnected ? '实时提示已连接' : '实时提示未连接' }}</span>
        <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/settings`">应用设置</RouterLink>
        <button class="action-button" type="button" @click="load()">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载探针告警...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="overview-grid">
        <article class="metric-card green">
          <strong>{{ payload.probeAlertDashboard.onlineCount }}</strong>
          <span>在线探针</span>
        </article>
        <article class="metric-card red">
          <strong>{{ payload.probeAlertDashboard.offlineCount }}</strong>
          <span>离线探针</span>
        </article>
        <article class="metric-card blue">
          <strong>{{ payload.probeAlertDashboard.recentEventCount }}</strong>
          <span>最近告警</span>
        </article>
        <article class="metric-card amber">
          <strong>{{ payload.probeAlertDashboard.failedNotifyCount }}</strong>
          <span>通知失败</span>
        </article>
      </div>

      <section v-if="liveNotice" class="live-alert" :class="colorClass(liveNotice.eventTypeColor)" role="status" aria-live="polite">
        <div class="live-alert-main">
          <span class="status-pill" :class="colorClass(liveNotice.eventTypeColor)">
            {{ liveNotice.eventTypeLabel || eventTypeText(liveNotice.eventType) }}
          </span>
          <div>
            <strong>{{ liveNotice.appName || payload.app.name || '当前应用' }} 探针{{ liveNotice.eventTypeLabel || eventTypeText(liveNotice.eventType) }}</strong>
            <p>{{ liveNotice.message || liveNotice.probeText || '收到新的探针上下线事件' }}</p>
            <small>{{ liveNotice.eventTimeText || '刚刚' }}</small>
          </div>
        </div>
        <button type="button" @click="liveNotice = null">知道了</button>
      </section>

      <div class="page-grid">
        <aside class="side-card">
          <div class="card-title">
            <h2>应用导航</h2>
            <span>{{ payload.apps.length }} 个应用</span>
          </div>
          <div class="app-nav">
            <RouterLink
              v-for="item in paginatedApps"
              :key="item.id"
              class="app-link"
              :class="{ active: item.id === appId }"
              :to="`/p/${projectId}/apps/${item.id}/probe-alerts`"
            >
              <strong>{{ item.name }}</strong>
              <span>{{ item.onlineCount > 0 ? `${item.onlineCount} 在线` : '无在线探针' }}</span>
            </RouterLink>
          </div>
          <div v-if="appTotalPages > 1" class="mini-pagination" aria-label="应用导航分页">
            <button type="button" :disabled="appPage <= 1" @click="appPage -= 1">上一页</button>
            <span>{{ appPage }} / {{ appTotalPages }}</span>
            <button type="button" :disabled="appPage >= appTotalPages" @click="appPage += 1">下一页</button>
          </div>
        </aside>

        <div class="content-stack">
          <section class="panel">
            <div class="card-title">
              <h2>当前探针</h2>
              <span>{{ statuses.length }} 个实例</span>
            </div>
            <div v-if="!statuses.length" class="empty-card">暂无探针实例状态</div>
            <div v-else class="table-shell">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>状态</th>
                    <th>IP / PID</th>
                    <th>工作目录</th>
                    <th>Agent 版本</th>
                    <th>最后心跳</th>
                    <th>最近告警</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in paginatedStatuses" :key="item.probeKey || item.sessionId || item.addressIp">
                    <td><span class="status-pill" :class="colorClass(item.statusColor)">{{ item.statusLabel || '-' }}</span></td>
                    <td>
                      <div>{{ item.addressIp || '-' }}</div>
                      <div class="muted">pid={{ item.pid || '-' }}</div>
                    </td>
                    <td class="path-cell">{{ item.systemDir || '-' }}</td>
                    <td>{{ item.agentVersion || '-' }}</td>
                    <td>{{ item.lastHeartbeatTimeText || '-' }}</td>
                    <td>
                      <div>{{ eventTypeText(item.lastAlertEventType) }}</div>
                      <div class="muted">{{ item.lastAlertTimeText || '-' }}</div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <AppPagination
              v-if="statuses.length > 0"
              v-model:page="statusPage"
              v-model:page-size="statusPageSize"
              :total="statuses.length"
              item-name="实例"
              :page-sizes="[5, 10, 20, 50]"
            />
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>告警记录</h2>
              <span>{{ events.length }} 条</span>
            </div>
            <div v-if="!events.length" class="empty-card">暂无告警记录</div>
            <div v-else class="event-list">
              <article v-for="event in paginatedEvents" :key="event.id" class="event-card">
                <div class="event-top">
                  <span class="status-pill" :class="colorClass(event.eventTypeColor)">
                    {{ event.eventTypeLabel || event.eventType || '-' }}
                  </span>
                  <span class="muted">{{ event.eventTimeText || '-' }}</span>
                </div>
                <strong>{{ event.probeText || '未知探针' }}</strong>
                <p>{{ event.message || '无事件说明' }}</p>
                <div class="event-meta">
                  <span class="status-pill small" :class="colorClass(event.notifyStatusColor)">
                    {{ event.notifyStatusLabel || event.notifyStatus || '未知状态' }}
                  </span>
                  <span>{{ event.notifyResponse || '无 Webhook 响应' }}</span>
                  <span v-if="event.notifyError" class="error-text">{{ event.notifyError }}</span>
                </div>
              </article>
            </div>
            <AppPagination
              v-if="events.length > 0"
              v-model:page="eventPage"
              v-model:page-size="eventPageSize"
              :total="events.length"
              item-name="告警"
              :page-sizes="[5, 10, 20, 50]"
            />
          </section>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
import { useProjectStore } from '@/stores/project'
import type { ProbeAlertEventItem } from '@/api/types'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const storeKey = computed(() => `${projectId.value}:${appId.value}`)
const payload = computed(() => projectStore.probeAlertsByKey[storeKey.value])
const statuses = computed(() => payload.value?.probeAlertDashboard.statuses || [])
const events = computed(() => payload.value?.probeAlertDashboard.recentEvents || [])
const loading = ref(false)
const error = ref('')
const appPage = ref(1)
const appPageSize = 8
const statusPage = ref(1)
const statusPageSize = ref(5)
const eventPage = ref(1)
const eventPageSize = ref(5)
const liveNotice = ref<ProbeAlertEventItem | null>(null)
const alertStreamConnected = ref(false)
let alertEventSource: EventSource | undefined

const appTotalPages = computed(() => Math.max(1, Math.ceil((payload.value?.apps.length || 0) / appPageSize)))
const paginatedApps = computed(() => {
  const apps = payload.value?.apps || []
  const start = (appPage.value - 1) * appPageSize
  return apps.slice(start, start + appPageSize)
})
const paginatedStatuses = computed(() => {
  const start = (statusPage.value - 1) * statusPageSize.value
  return statuses.value.slice(start, start + statusPageSize.value)
})
const paginatedEvents = computed(() => {
  const start = (eventPage.value - 1) * eventPageSize.value
  return events.value.slice(start, start + eventPageSize.value)
})

function colorClass(color?: string) {
  switch (color) {
    case 'green':
      return 'green'
    case 'red':
      return 'red'
    case 'orange':
    case 'yellow':
      return 'amber'
    case 'blue':
      return 'blue'
    case 'grey':
    case 'gray':
      return 'slate'
    default:
      return 'slate'
  }
}

function eventTypeText(type?: string) {
  switch (type) {
    case 'ONLINE':
      return '上线'
    case 'OFFLINE':
      return '下线'
    case 'RECOVERED':
      return '恢复上线'
    default:
      return type || '-'
  }
}

async function load(options: { silent?: boolean } = {}) {
  if (!projectId.value || !appId.value) {
    error.value = '缺少 projectId 或 appId'
    return
  }
  if (!options.silent) {
    loading.value = true
    error.value = ''
  }
  try {
    await projectStore.loadProbeAlerts(projectId.value, appId.value)
  } catch (err) {
    if (!options.silent) {
      error.value = err instanceof Error ? err.message : '加载探针告警失败'
    }
  } finally {
    if (!options.silent) {
      loading.value = false
    }
  }
}

function isCurrentAppEvent(event: ProbeAlertEventItem) {
  return !event.appId || event.appId === appId.value || event.appName === payload.value?.app.name
}

function closeAlertStream() {
  alertStreamConnected.value = false
  if (alertEventSource) {
    alertEventSource.close()
    alertEventSource = undefined
  }
}

function startAlertStream() {
  closeAlertStream()
  if (!projectId.value || typeof EventSource === 'undefined') {
    return
  }
  const source = new EventSource(`/p/${encodeURIComponent(projectId.value)}/app/probe-alerts/stream`)
  alertEventSource = source
  source.addEventListener('connected', () => {
    alertStreamConnected.value = true
  })
  source.addEventListener('heartbeat', () => {
    alertStreamConnected.value = true
  })
  source.addEventListener('probe-alert', (message) => {
    alertStreamConnected.value = true
    try {
      const event = JSON.parse(message.data) as ProbeAlertEventItem
      if (!isCurrentAppEvent(event)) {
        return
      }
      liveNotice.value = event
      void load({ silent: true })
    } catch {
      return
    }
  })
  source.onerror = () => {
    alertStreamConnected.value = false
  }
}

watch(appTotalPages, (totalPages) => {
  if (appPage.value > totalPages) {
    appPage.value = totalPages
  }
})

watch([() => statuses.value.length, statusPageSize], ([total]) => {
  const totalPages = Math.max(1, Math.ceil(total / statusPageSize.value))
  if (statusPage.value > totalPages) {
    statusPage.value = totalPages
  }
})

watch([() => events.value.length, eventPageSize], ([total]) => {
  const totalPages = Math.max(1, Math.ceil(total / eventPageSize.value))
  if (eventPage.value > totalPages) {
    eventPage.value = totalPages
  }
})

watch([projectId, appId], () => {
  appPage.value = 1
  statusPage.value = 1
  eventPage.value = 1
  liveNotice.value = null
  void load()
  startAlertStream()
})

onMounted(() => {
  void load()
  startAlertStream()
})

onBeforeUnmount(closeAlertStream)
</script>

<style scoped>
.page-header,
.header-actions,
.card-title,
.event-top,
.event-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 20px;
}

.header-actions {
  align-items: center;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.muted {
  color: #64748b;
}

.action-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: #0f172a;
  color: #fff;
  cursor: pointer;
}

.secondary-link {
  color: #0f766e;
  font-weight: 700;
}

.stream-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.stream-state::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.stream-state.active {
  background: #f0fdf4;
  color: #15803d;
}

.status-card,
.side-card,
.panel {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error,
.error-text {
  color: #b91c1c;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.metric-card {
  padding: 18px;
  border-radius: 20px;
  color: #fff;
}

.metric-card strong {
  display: block;
  font-size: 28px;
}

.metric-card.green { background: linear-gradient(135deg, #15803d, #22c55e); }
.metric-card.red { background: linear-gradient(135deg, #b91c1c, #ef4444); }
.metric-card.blue { background: linear-gradient(135deg, #1d4ed8, #38bdf8); }
.metric-card.amber { background: linear-gradient(135deg, #b45309, #f59e0b); }

.live-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin: -4px 0 18px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(100, 116, 139, 0.16);
  background: #f8fafc;
}

.live-alert-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.live-alert strong {
  color: #0f172a;
}

.live-alert p {
  margin: 4px 0;
  color: #475569;
  overflow-wrap: anywhere;
}

.live-alert small {
  color: #64748b;
}

.live-alert button {
  flex: 0 0 auto;
  border: none;
  border-radius: 999px;
  padding: 8px 12px;
  background: rgba(15, 23, 42, 0.08);
  color: #0f172a;
  cursor: pointer;
  font-weight: 800;
}

.live-alert.green { background: #f0fdf4; border-color: rgba(22, 163, 74, 0.18); }
.live-alert.red { background: #fef2f2; border-color: rgba(185, 28, 28, 0.18); }
.live-alert.blue { background: #eff6ff; border-color: rgba(37, 99, 235, 0.18); }
.live-alert.amber { background: #fff7ed; border-color: rgba(234, 88, 12, 0.18); }

.page-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

.content-stack {
  display: grid;
  gap: 18px;
}

.app-nav {
  display: grid;
  gap: 10px;
  max-height: min(440px, calc(100vh - 300px));
  margin-top: 14px;
  overflow: auto;
}

.mini-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.mini-pagination button {
  border: 1px solid rgba(15, 118, 110, 0.14);
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  cursor: pointer;
  font-weight: 800;
}

.mini-pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.app-link {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.app-link.active {
  background: linear-gradient(180deg, #f0fdfa, #ecfeff);
  border-color: rgba(15, 118, 110, 0.22);
}

.empty-card {
  padding: 22px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.table-shell {
  max-height: min(360px, calc(100vh - 360px));
  overflow: auto;
  margin-top: 14px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 12px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
  vertical-align: top;
}

.path-cell {
  word-break: break-all;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.small {
  padding: 4px 8px;
}

.status-pill.green { background: rgba(22, 163, 74, 0.12); color: #15803d; }
.status-pill.red { background: rgba(239, 68, 68, 0.12); color: #b91c1c; }
.status-pill.blue { background: rgba(37, 99, 235, 0.12); color: #1d4ed8; }
.status-pill.amber { background: rgba(245, 158, 11, 0.14); color: #b45309; }
.status-pill.slate { background: rgba(148, 163, 184, 0.18); color: #475569; }

.event-list {
  display: grid;
  gap: 12px;
  max-height: min(460px, calc(100vh - 320px));
  margin-top: 14px;
  overflow: auto;
  padding-right: 4px;
}

.event-card {
  padding: 16px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.event-card p {
  margin: 8px 0 12px;
  color: #475569;
}

.event-meta {
  flex-wrap: wrap;
  justify-content: flex-start;
}

@media (max-width: 960px) {
  .overview-grid,
  .page-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .live-alert,
  .live-alert-main {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
