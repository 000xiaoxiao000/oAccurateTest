<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Runtime Monitor</div>
        <h1>实时监控台</h1>
        <p class="subtext">按探针状态展示实时链路、调用拓扑，并可将 trace 保存为我的快照或系统快照。</p>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" :disabled="traceLoading || probeLoading" @click="refreshAll">
          {{ (traceLoading || probeLoading) ? '刷新中...' : '刷新' }}
        </button>
        <RouterLink class="ghost-link" :to="`/p/${projectId}/home`">返回项目</RouterLink>
      </div>
    </div>

    <MonitorOverviewCards
      :probe-count="probes.length"
      :app-count="projectApps.length"
      :trace-count="filteredTraces.length"
      :last-receive-text="lastReceiveText"
    />

    <MonitorProbePanel
      v-model:keyword="probeKeyword"
      :filtered-probes="filteredProbes"
      :visible-probes="visibleProbes"
      :selected-client-ips="selectedClientIps"
      :total-count="probes.length"
      :preview-limit="probePreviewLimit"
      :expanded="probesExpanded"
      :loading="probeLoading"
      :error="probeError"
      @toggle-expanded="probesExpanded = !probesExpanded"
      @refresh="loadProbes"
      @toggle-probe="toggleProbeFilter"
    />

    <MonitorToolbar
      v-model:time-window="upToTime"
      v-model:max-size="maxSize"
      v-model:auto-save-my-snapshot="autoSaveMySnapshot"
      v-model:auto-save-system-snapshot="autoSaveSystemSnapshot"
      :project-apps="projectApps"
      :selected-app-ids="selectedAppIds"
      :available-client-ips="availableClientIps"
      :selected-client-ips="selectedClientIps"
      :selected-trace-id="selectedTraceId"
      :saving-snapshot="savingSnapshot"
      :snapshot-menu-open="snapshotMenuOpen"
      @clear-app-filter="clearAppFilter"
      @toggle-app-filter="toggleAppFilter"
      @clear-ip-filter="clearIpFilter"
      @toggle-ip-filter="toggleIpFilter"
      @query="loadTraces()"
      @toggle-snapshot-menu="toggleSnapshotMenu"
      @open-snapshot-dialog="openSnapshotDialog"
    />

    <div class="monitor-grid" :style="monitorGridStyle">
      <MonitorTraceListPanel
        v-model:keyword="traceKeyword"
        v-model:page="tracePage"
        v-model:page-size="tracePageSize"
        v-model:auto-refresh="autoRefresh"
        v-model:refresh-seconds="refreshSeconds"
        :traces="paginatedTraces"
        :total-count="filteredTraces.length"
        :selected-trace-id="selectedTraceId"
        :loading="traceLoading"
        :error="traceError"
        @refresh="loadTraces()"
        @clear-filters="clearMonitorFilters"
        @select-trace="selectTrace"
      />

      <div class="monitor-resizer" @pointerdown="startResize">
        <span class="resizer-dots">
          <i></i><i></i><i></i>
        </span>
      </div>

      <section class="panel graph-panel">
        <div class="panel-head graph-panel-head">
          <div class="panel-head-content">
            <h2>{{ graph ? '实时监控详情' : '实时监控示波器' }}</h2>
            <p class="panel-subtitle">{{ selectedTraceId || oscilloscopeSubtitle }}</p>
          </div>
          <div class="header-actions graph-actions">
            <button v-if="graph" class="ghost-button secondary-ghost" type="button" @click="showOscilloscope">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
              返回示波器
            </button>
            <button class="ghost-button" type="button" :disabled="!selectedTraceId || graphLoading" @click="loadGraph">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>
              重载拓扑
            </button>
            <button class="ghost-button primary-ghost" type="button" :disabled="!selectedTraceId || savingSnapshot" title="自动保存我的快照和系统快照" @click="autoSaveCurrentTraceSnapshots">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline></svg>
              自动保存快照
            </button>
          </div>
        </div>

        <div v-if="graphLoading" class="status-card">正在加载拓扑...</div>
        <div v-else-if="graphError" class="status-card error">{{ graphError }}</div>
        <MonitorOscilloscopeCard
          v-else-if="!graph"
          :subtitle="oscilloscopeSubtitle"
          :scope-mode="scopeMode"
          :wave-points="wavePoints"
          :wave-polyline="wavePolyline"
          :latest-trace-title="latestTrace?.title"
          :latest-trace-source="latestTraceSource"
          @set-scope-mode="setScopeMode"
        />
        <MonitorGraphTopologyPanel
          v-else
          :graph="graph"
          :graph-zoom="graphZoom"
          :graph-view-box="graphViewBox"
          :graph-transform="graphTransform"
          :graph-node-width="graphNodeWidth"
          :graph-node-height="graphNodeHeight"
          :edge-positions="edgePositions"
          :node-positions="nodePositions"
          :sorted-graph-nodes="sortedGraphNodes"
          :selected-node-id="selectedNodeId"
          :selected-graph-node="selectedGraphNode"
          :selected-node-detail="selectedNodeDetail"
          :node-detail-loading="nodeDetailLoading"
          :node-detail-error="nodeDetailError"
          @fit-graph="fitMonitorGraph"
          @zoom-graph="zoomGraph"
          @reset-graph="resetGraphView"
          @graph-wheel="handleGraphWheel"
          @graph-pan-start="startGraphPan"
          @graph-pan-move="moveGraphPan"
          @graph-pan-end="endGraphPan"
          @select-node="selectNode"
        />
      </section>
    </div>

    <MonitorSnapshotDialog
      v-if="snapshotDialogOpen"
      v-model:form="snapshotForm"
      :mode="snapshotDialogMode"
      :selected-trace-id="selectedTraceId"
      :context="snapshotContext"
      :error="snapshotFormError"
      :context-loading="snapshotContextLoading"
      :saving="savingSnapshot"
      :image-uploading="topicImageUploading"
      @close="closeSnapshotDialog"
      @submit="submitSnapshotForm"
      @upload-image="handleTopicImageUpload"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useProjectStore } from '@/stores/project'
import { apiGet, apiGetRaw } from '@/api/http'
import type { AppSummary, GraphNodeDetailPayload, GraphNodeSummary, GraphViewPayload, OnlineSessionSummary, TraceItemSummary } from '@/api/types'
import { useToast } from '@/composables/useToast'
import { useMonitorGraphLayout } from '@/features/monitor/composables/useMonitorGraphLayout'
import { useMonitorSnapshots } from '@/features/monitor/composables/useMonitorSnapshots'
import { useMonitorSources } from '@/features/monitor/composables/useMonitorSources'
import MonitorGraphTopologyPanel from '@/features/monitor/components/MonitorGraphTopologyPanel.vue'
import MonitorOverviewCards from '@/features/monitor/components/MonitorOverviewCards.vue'
import MonitorOscilloscopeCard from '@/features/monitor/components/MonitorOscilloscopeCard.vue'
import MonitorProbePanel from '@/features/monitor/components/MonitorProbePanel.vue'
import MonitorSnapshotDialog from '@/features/monitor/components/MonitorSnapshotDialog.vue'
import MonitorToolbar from '@/features/monitor/components/MonitorToolbar.vue'
import MonitorTraceListPanel from '@/features/monitor/components/MonitorTraceListPanel.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const toast = useToast()
const projectId = computed(() => String(route.params.projectId || ''))
const selectedAppId = computed(() => String(route.query.appId || ''))
const projectContext = computed(() => projectId.value ? projectStore.contextByProjectId[projectId.value] : undefined)
const projectApps = computed<AppSummary[]>(() => projectContext.value?.apps || [])

const traces = ref<TraceItemSummary[]>([])
const graph = ref<GraphViewPayload | null>(null)
const selectedTraceId = ref('')
const selectedNodeId = ref('')
const selectedNodeDetail = ref<GraphNodeDetailPayload | null>(null)
const traceKeyword = ref('')
const selectedAppIds = ref<string[]>([])
const selectedClientIps = ref<string[]>([])
const upToTime = ref(180)
const maxSize = ref(100)
const autoRefresh = ref(false)
const autoSaveMySnapshot = ref(false)
const autoSaveSystemSnapshot = ref(false)
const refreshSeconds = ref(5)
const scopeMode = ref<'aggregate' | 'single' | 'lanes'>('aggregate')
const monitorListWidth = ref(280)
const tracePage = ref(1)
const tracePageSize = ref(50)
const traceLoading = ref(false)
const graphLoading = ref(false)
const nodeDetailLoading = ref(false)
const traceError = ref('')
const graphError = ref('')
const nodeDetailError = ref('')
let refreshTimer: number | undefined
let graphRequestSeq = 0
let nodeDetailRequestSeq = 0

const {
  graphNodeWidth,
  graphNodeHeight,
  graphZoom,
  sortedGraphNodes,
  nodePositions,
  graphViewBox,
  graphTransform,
  edgePositions,
  fitMonitorGraph,
  zoomGraph,
  resetGraphView,
  handleGraphWheel,
  startGraphPan,
  moveGraphPan,
  endGraphPan,
} = useMonitorGraphLayout(graph)

const {
  probes,
  probeKeyword,
  probesExpanded,
  probePreviewLimit,
  probeLoading,
  probeError,
  filteredProbes,
  visibleProbes,
  availableClientIps,
  loadProbes,
} = useMonitorSources(projectId, projectStore, selectedAppIds)

const {
  savingSnapshot,
  snapshotDialogOpen,
  snapshotDialogMode,
  snapshotMenuOpen,
  snapshotContextLoading,
  topicImageUploading,
  snapshotFormError,
  snapshotContext,
  snapshotForm,
  autoSaveCurrentTraceSnapshots,
  runAutoSaveForNewTraces,
  batchSaveMySnapshots: saveFilteredMySnapshots,
  batchSaveSystemSnapshots: saveFilteredSystemSnapshots,
  toggleSnapshotMenu,
  openSnapshotDialog,
  closeSnapshotDialog,
  handleTopicImageUpload,
  submitSnapshotForm,
} = useMonitorSnapshots(projectId, traces, graph, selectedTraceId, autoSaveMySnapshot, autoSaveSystemSnapshot, toast)

watch(selectedAppId, (value) => {
  selectedAppIds.value = value ? [value] : []
}, { immediate: true })

watch(projectId, (value) => {
  if (value) projectStore.loadProjectContext(value).catch(() => undefined)
}, { immediate: true })

const filteredTraces = computed(() => {
  const needle = traceKeyword.value.toLowerCase()
  const filtered = traces.value.filter((trace) => {
    const haystack = [trace.traceId, trace.title, trace.displayName, trace.entryType, trace.entryName, trace.addressIp, trace.clientIp, trace.appId]
      .join(' ')
      .toLowerCase()
    const matchesApp = !selectedAppIds.value.length || selectedAppIds.value.includes(trace.appId || '')
    const traceIp = trace.clientIp || trace.addressIp || ''
    const matchesIp = !selectedClientIps.value.length || selectedClientIps.value.includes(traceIp)
      || selectedClientIps.value.includes(trace.addressIp || '')
    return matchesApp && (!needle || haystack.includes(needle))
      && matchesIp
  })
  return filtered.sort((a, b) => (b.cacheTime || 0) - (a.cacheTime || 0))
})
const paginatedTraces = computed(() => {
  const start = (tracePage.value - 1) * tracePageSize.value
  return filteredTraces.value.slice(start, start + tracePageSize.value)
})

const latestTrace = computed(() => filteredTraces.value[0])
const latestTraceSource = computed(() => latestTrace.value ? `${latestTrace.value.addressIp || '-'}${latestTrace.value.clientIp ? ` / ${latestTrace.value.clientIp}` : ''}` : '-')
const lastReceiveText = computed(() => formatTraceTime(latestTrace.value?.cacheTime))
const monitorGridStyle = computed(() => ({ gridTemplateColumns: `${monitorListWidth.value}px 10px minmax(0, 1fr)` }))
const oscilloscopeSubtitle = computed(() => {
  if (scopeMode.value === 'single') return selectedClientIps.value[0] ? `当前探针：${selectedClientIps.value[0]}` : '当前探针：请点击左侧探针'
  if (scopeMode.value === 'lanes') return '多探针泳道：按 IP 分组展示监控事件'
  return '聚合全部探针：圆点 = 一次 Trace 或覆盖率上报；折线 = 事件趋势；扫描线 = 实时监听节奏'
})
const wavePoints = computed(() => [...filteredTraces.value].reverse().slice(-80).map((trace, index, list) => {
  const cacheTime = Number(trace.cacheTime)
  const timeText = formatTraceDateTime(trace.cacheTime)
  const tooltip = [
    trace.title || trace.traceId,
    `时间：${timeText}`,
    `来源：${trace.addressIp || '-'} / ${trace.clientIp || '-'}`,
    trace.index !== undefined ? `序号：#${trace.index}` : '',
  ].filter(Boolean).join('\n')
  return {
    id: trace.traceId || `${trace.cacheTime}-${index}`,
    title: tooltip,
    x: list.length <= 1 ? 50 : 4 + (index / (list.length - 1)) * 92,
    y: 18 + ((trace.title || trace.traceId || '').length * 17 + index * 13) % 62,
    fresh: Number.isFinite(cacheTime) && Date.now() - cacheTime < 6000,
  }
}))
const wavePolyline = computed(() => wavePoints.value.map((point) => `${point.x},${point.y}`).join(' '))

const selectedGraphNode = computed(() =>
  sortedGraphNodes.value.find((n) => n.id === selectedNodeId.value) ?? null,
)

watch(autoRefresh, (enabled) => {
  if (enabled) startRefreshTimer()
  else stopRefreshTimer()
})

watch(refreshSeconds, () => {
  if (autoRefresh.value) startRefreshTimer()
})

watch([traceKeyword, tracePageSize, selectedAppIds, selectedClientIps], () => {
  tracePage.value = 1
})

watch(upToTime, () => {
  loadTraces()
})

async function refreshAll() {
  await Promise.all([loadProbes(), loadTraces()])
  if (!selectedTraceId.value && traces.value[0]) {
    await selectTrace(traces.value[0])
  }
}

async function applyInitialRouteState() {
  const initialTraceId = typeof route.query.traceId === 'string' ? route.query.traceId : ''
  const initialNodeId = typeof route.query.nodeId === 'string' ? route.query.nodeId : ''
  if (initialTraceId) {
    selectedTraceId.value = initialTraceId
    await loadGraph()
    if (initialNodeId) {
      await loadNodeDetail(initialNodeId)
      selectedNodeId.value = initialNodeId
    }
  }
  if (route.query.snapshot === 'system' && selectedTraceId.value) {
    await openSnapshotDialog()
  }
}

async function loadTraces(options: { silent?: boolean; keepCurrentView?: boolean } = {}) {
  if (!options.silent) traceLoading.value = true
  traceError.value = ''
  let nextTrace: TraceItemSummary | undefined
  try {
    const query = new URLSearchParams()
    query.set('upToTime', String(upToTime.value || 180))
    query.set('maxSize', String(maxSize.value || 100))
    selectedAppIds.value.forEach((appId) => query.append('appIds', appId))
    selectedClientIps.value.forEach((ip) => query.append('clientIps', ip))
    traces.value = await apiGetRaw<TraceItemSummary[]>(`/api/projects/${projectId.value}/monitor/getNodeByTime?${query.toString()}`)
    const visibleTraceIds = new Set(traces.value.map((trace) => trace.traceId))
    if (!options.keepCurrentView && traces.value[0] && (!selectedTraceId.value || !visibleTraceIds.has(selectedTraceId.value))) {
      nextTrace = traces.value[0]
    }
    await runAutoSaveForNewTraces(traces.value)
  } catch (err) {
    traceError.value = err instanceof Error ? err.message : '加载 trace 失败'
  } finally {
    if (!options.silent) traceLoading.value = false
  }
  if (nextTrace) {
    void selectTrace(nextTrace)
  }
}

async function selectTrace(trace: TraceItemSummary) {
  selectedTraceId.value = trace.traceId
  selectedNodeId.value = ''
  selectedNodeDetail.value = null
  await loadGraph()
}

function showOscilloscope() {
  graph.value = null
  selectedNodeId.value = ''
  selectedNodeDetail.value = null
}

function toggleProbeFilter(probe: OnlineSessionSummary) {
  const ip = probe.addressIp
  if (!ip) return
  selectedClientIps.value = [ip]
  scopeMode.value = 'single'
  loadTraces()
}

function toggleAppFilter(appId: string) {
  if (!appId) return
  selectedAppIds.value = selectedAppIds.value.includes(appId)
    ? selectedAppIds.value.filter((item) => item !== appId)
    : [...selectedAppIds.value, appId]
}

function clearAppFilter() {
  selectedAppIds.value = []
}

function toggleIpFilter(ip: string) {
  if (!ip) return
  selectedClientIps.value = selectedClientIps.value.includes(ip)
    ? selectedClientIps.value.filter((item) => item !== ip)
    : [...selectedClientIps.value, ip]
  scopeMode.value = selectedClientIps.value.length === 1 ? 'single' : selectedClientIps.value.length > 1 ? 'lanes' : 'aggregate'
}

function clearIpFilter() {
  selectedClientIps.value = []
  scopeMode.value = 'aggregate'
}

function setScopeMode(mode: 'aggregate' | 'single' | 'lanes') {
  scopeMode.value = mode
  if (mode !== 'single') {
    selectedClientIps.value = []
  }
  loadTraces()
}

function clearMonitorFilters() {
  traceKeyword.value = ''
  selectedAppIds.value = []
  selectedClientIps.value = []
  selectedTraceId.value = ''
  scopeMode.value = 'aggregate'
  loadTraces()
}

type MonitorActionDetail = { name?: string; seconds?: number }

async function handleMonitorAction(event: Event) {
  const detail = (event as CustomEvent<MonitorActionDetail>).detail || {}
  switch (detail.name) {
    case 'refreshMonitorList':
      await loadTraces()
      break
    case 'refreshProbeStatus':
      await loadProbes()
      break
    case 'enableAutoRefresh':
      autoRefresh.value = true
      break
    case 'disableAutoRefresh':
      autoRefresh.value = false
      break
    case 'setAutoRefreshSeconds':
      refreshSeconds.value = Math.min(120, Math.max(1, Number(detail.seconds) || refreshSeconds.value))
      autoRefresh.value = true
      break
    case 'enableAutoSaveMy':
      autoSaveMySnapshot.value = true
      break
    case 'disableAutoSaveMy':
      autoSaveMySnapshot.value = false
      break
    case 'enableAutoSaveSystem':
      autoSaveSystemSnapshot.value = true
      break
    case 'disableAutoSaveSystem':
      autoSaveSystemSnapshot.value = false
      break
    case 'openMySnapshots':
      await router.push(`/p/${projectId.value}/my-snapshots`)
      break
    case 'openCreateMySnapshot':
      await openSnapshotDialog('my')
      break
    case 'openCreateSystemSnapshot':
      await openSnapshotDialog('system')
      break
    case 'batchSaveMySnapshots':
      await saveFilteredMySnapshots(filteredTraces.value)
      break
    case 'batchSaveSystemSnapshots':
      await saveFilteredSystemSnapshots(filteredTraces.value)
      break
    case 'clearMonitorList':
      traces.value = []
      graph.value = null
      selectedTraceId.value = ''
      selectedNodeId.value = ''
      selectedNodeDetail.value = null
      break
    case 'setScopeAggregate':
      setScopeMode('aggregate')
      break
    case 'setScopeCurrent':
      if (!selectedClientIps.value.length && latestTrace.value) {
        const ip = latestTrace.value.clientIp || latestTrace.value.addressIp || ''
        selectedClientIps.value = ip ? [ip] : []
      }
      setScopeMode('single')
      break
    case 'setScopeLanes':
      setScopeMode('lanes')
      break
    default:
      break
  }
}

async function loadGraph() {
  if (!selectedTraceId.value) return
  const requestSeq = ++graphRequestSeq
  const traceId = selectedTraceId.value
  graphLoading.value = true
  graphError.value = ''
  nodeDetailError.value = ''
  selectedNodeDetail.value = null
  try {
    const query = new URLSearchParams()
    query.set('traceId', traceId)
    const graphPayload = await apiGetRaw<GraphViewPayload>(`/api/projects/${projectId.value}/monitor/getTraceGraph?${query.toString()}`)
    if (requestSeq !== graphRequestSeq || traceId !== selectedTraceId.value) return
    graph.value = graphPayload
    resetGraphView()
    selectedNodeId.value = graph.value?.showDefaultNode?.id || graph.value?.nodes?.[0]?.id || ''
  } catch (err) {
    if (requestSeq !== graphRequestSeq) return
    graphError.value = err instanceof Error ? err.message : '加载拓扑失败'
  } finally {
    if (requestSeq === graphRequestSeq) {
      graphLoading.value = false
    }
  }
  if (requestSeq === graphRequestSeq && selectedNodeId.value) {
    void loadNodeDetail(selectedNodeId.value)
  }
}

function selectNode(nodeId: string) {
  selectedNodeId.value = nodeId
  loadNodeDetail(nodeId)
}

async function loadNodeDetail(nodeId: string) {
  if (!selectedTraceId.value || !nodeId) return
  const requestSeq = ++nodeDetailRequestSeq
  const traceId = selectedTraceId.value
  nodeDetailLoading.value = true
  nodeDetailError.value = ''
  try {
    const query = new URLSearchParams()
    query.set('traceId', traceId)
    query.set('nodeId', nodeId)
    const detail = await apiGet<GraphNodeDetailPayload>(`/api/projects/${projectId.value}/monitor/getTraceGraphNode?${query.toString()}`)
    if (requestSeq !== nodeDetailRequestSeq || traceId !== selectedTraceId.value || nodeId !== selectedNodeId.value) return
    selectedNodeDetail.value = detail
  } catch (err) {
    if (requestSeq !== nodeDetailRequestSeq) return
    selectedNodeDetail.value = null
    nodeDetailError.value = err instanceof Error ? err.message : '加载节点详情失败'
  } finally {
    if (requestSeq === nodeDetailRequestSeq) {
      nodeDetailLoading.value = false
    }
  }
}

function startRefreshTimer() {
  stopRefreshTimer()
  refreshTimer = window.setInterval(() => {
    if (document.hidden) return
    loadProbes()
    loadTraces()
  }, Math.min(120, Math.max(3, refreshSeconds.value || 10)) * 1000)
}

function stopRefreshTimer() {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
}

function formatTraceTime(value?: number) {
  if (!value) return '等待中'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '等待中'
  return date.toLocaleTimeString()
}

function formatTraceDateTime(value?: number) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

function startResize(event: PointerEvent) {
  const startX = event.clientX
  const startWidth = monitorListWidth.value
  const move = (moveEvent: PointerEvent) => {
    monitorListWidth.value = Math.min(Math.max(startWidth + moveEvent.clientX - startX, 280), Math.max(320, window.innerWidth - 420))
    localStorage.setItem(`monitor:list-width:${projectId.value}`, String(monitorListWidth.value))
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

onMounted(async () => {
  const savedWidth = Number(localStorage.getItem(`monitor:list-width:${projectId.value}`))
  if (savedWidth) monitorListWidth.value = savedWidth
  window.addEventListener('oat:monitor-action', handleMonitorAction)
  await refreshAll()
  await applyInitialRouteState()
})
onBeforeUnmount(() => {
  stopRefreshTimer()
  window.removeEventListener('oat:monitor-action', handleMonitorAction)
})
</script>

<style scoped src="@/features/monitor/styles/monitor-page.css"></style>
