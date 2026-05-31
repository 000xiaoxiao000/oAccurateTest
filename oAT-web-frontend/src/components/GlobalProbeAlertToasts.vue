<template>
  <Teleport to="body">
    <div v-if="notices.length" class="probe-alert-stack" aria-live="polite" aria-label="探针上下线告警提示">
      <article v-for="notice in notices" :key="notice.key" class="probe-alert-toast" :class="toneClass(notice.event.eventTypeColor)">
        <div class="toast-icon" aria-hidden="true">{{ eventIcon(notice.event.eventType) }}</div>
        <div class="toast-body">
          <div class="toast-title-row">
            <strong>{{ notice.event.appName || '应用探针' }} {{ eventTypeText(notice.event.eventType) }}</strong>
            <span>{{ notice.event.eventTimeText || '刚刚' }}</span>
          </div>
          <p>{{ notice.event.message || notice.event.probeText || '收到新的探针上下线事件' }}</p>
          <RouterLink v-if="notice.event.appId" class="toast-link" :to="`/p/${projectId}/apps/${notice.event.appId}/probe-alerts`">
            查看告警记录
          </RouterLink>
        </div>
        <button class="toast-close" type="button" aria-label="关闭探针告警提示" @click="dismissNotice(notice.key)">×</button>
      </article>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import type { ProbeAlertEventItem } from '@/api/types'

interface ProbeAlertNotice {
  key: string
  event: ProbeAlertEventItem
  timer: number
}

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const notices = ref<ProbeAlertNotice[]>([])
const seenEventIds = new Set<string>()
let eventSource: EventSource | undefined

function toneClass(color?: string) {
  switch (color) {
    case 'green':
      return 'success'
    case 'red':
      return 'danger'
    case 'orange':
    case 'yellow':
      return 'warning'
    case 'blue':
      return 'info'
    default:
      return 'default'
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
      return type || '状态变更'
  }
}

function eventIcon(type?: string) {
  switch (type) {
    case 'OFFLINE':
      return '↓'
    case 'RECOVERED':
      return '↗'
    case 'ONLINE':
      return '↑'
    default:
      return '•'
  }
}

function closeStream() {
  if (eventSource) {
    eventSource.close()
    eventSource = undefined
  }
}

function clearNotices() {
  notices.value.forEach((notice) => window.clearTimeout(notice.timer))
  notices.value = []
}

function dismissNotice(key: string) {
  const notice = notices.value.find((item) => item.key === key)
  if (notice) {
    window.clearTimeout(notice.timer)
  }
  notices.value = notices.value.filter((item) => item.key !== key)
}

function pushNotice(event: ProbeAlertEventItem) {
  if (route.name === 'app-probe-alerts') {
    return
  }
  const key = event.id || `${event.appId || 'project'}-${event.eventType || 'event'}-${Date.now()}`
  if (seenEventIds.has(key)) {
    return
  }
  seenEventIds.add(key)
  const timer = window.setTimeout(() => dismissNotice(key), 8000)
  notices.value = [{ key, event, timer }, ...notices.value].slice(0, 3)
}

function startStream() {
  closeStream()
  clearNotices()
  seenEventIds.clear()
  if (!projectId.value || typeof EventSource === 'undefined') {
    return
  }
  const source = new EventSource(`/p/${encodeURIComponent(projectId.value)}/app/probe-alerts/stream`)
  eventSource = source
  source.addEventListener('probe-alert', (message) => {
    try {
      pushNotice(JSON.parse(message.data) as ProbeAlertEventItem)
    } catch {
      return
    }
  })
}

watch(projectId, startStream, { immediate: true })
onBeforeUnmount(() => {
  closeStream()
  clearNotices()
})
</script>

<style scoped>
.probe-alert-stack {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 2200;
  display: grid;
  gap: 12px;
  width: min(420px, calc(100vw - 32px));
  pointer-events: none;
}

.probe-alert-toast {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 28px;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(100, 116, 139, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(14px);
  pointer-events: auto;
}

.toast-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 14px;
  background: #f1f5f9;
  color: #475569;
  font-size: 18px;
  font-weight: 900;
}

.toast-body {
  min-width: 0;
}

.toast-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.toast-title-row strong {
  min-width: 0;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-title-row span {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.probe-alert-toast p {
  margin: 6px 0 8px;
  color: #475569;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.toast-link {
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.toast-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #64748b;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}

.probe-alert-toast.success {
  border-color: rgba(22, 163, 74, 0.2);
}

.probe-alert-toast.success .toast-icon {
  background: #f0fdf4;
  color: #15803d;
}

.probe-alert-toast.danger {
  border-color: rgba(185, 28, 28, 0.22);
}

.probe-alert-toast.danger .toast-icon {
  background: #fef2f2;
  color: #b91c1c;
}

.probe-alert-toast.warning {
  border-color: rgba(234, 88, 12, 0.22);
}

.probe-alert-toast.warning .toast-icon {
  background: #fff7ed;
  color: #c2410c;
}

.probe-alert-toast.info .toast-icon {
  background: #eff6ff;
  color: #1d4ed8;
}

@media (max-width: 640px) {
  .probe-alert-stack {
    top: 12px;
    right: 12px;
    left: 12px;
    width: auto;
  }

  .probe-alert-toast {
    grid-template-columns: 34px minmax(0, 1fr) 28px;
  }
}
</style>
