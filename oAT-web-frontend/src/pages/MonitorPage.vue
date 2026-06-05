<template>
  <section>
    <div class="page-header monitor-compact-header plain-header">
      <div>
        <div class="eyebrow">Runtime Monitor</div>
        <h1>实时监控台</h1>
        <p class="subtext">查看在线探针、实时调用链路、调用拓扑，并可将 trace 保存为系统快照。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="`/p/${projectId}/home`">返回项目</RouterLink>
        <button class="action-button" type="button" @click="refreshAll">刷新</button>
      </div>
    </div>

    <div class="overview-grid">
      <div class="overview-card">
        <span>在线探针</span>
        <strong>{{ probes.length }}</strong>
        <small>当前项目已连接实例</small>
      </div>
      <div class="overview-card">
        <span>项目应用</span>
        <strong>{{ projectApps.length }}</strong>
        <small>已纳入监控范围</small>
      </div>
      <div class="overview-card">
        <span>实时请求</span>
        <strong>{{ filteredTraces.length }}</strong>
        <small>当前过滤窗口内</small>
      </div>
      <div class="overview-card">
        <span>最后接收</span>
        <strong class="time-value">{{ lastReceiveText }}</strong>
        <small>监控数据到达时间</small>
      </div>
    </div>

    <section class="panel probe-panel probe-dashboard-card">
      <div class="panel-head probe-head">
        <div>
          <h2>探针状态</h2>
          <p>点击探针可快速过滤 IP。</p>
        </div>
        <div class="header-actions">
          <input v-model.trim="probeKeyword" class="text-input compact" type="search" placeholder="搜索应用、IP、PID、Agent" aria-label="搜索探针" />
          <button v-if="filteredProbes.length > probePreviewLimit" class="ghost-button" type="button" @click="probesExpanded = !probesExpanded">
            {{ probesExpanded ? '收起探针' : '展开探针' }}
          </button>
          <button class="ghost-button" type="button" @click="loadProbes">刷新探针状态</button>
        </div>
      </div>
      <div class="probe-content">
        <div class="probe-summary">在线 {{ filteredProbes.length }} / {{ probes.length }} 个探针，展示 {{ visibleProbes.length }} 个</div>
        <div v-if="probeLoading" class="status-card compact-status">正在加载探针...</div>
        <div v-else-if="probeError" class="status-card compact-status error">{{ probeError }}</div>
        <div v-else-if="filteredProbes.length === 0" class="status-card compact-status">暂无在线探针</div>
        <div v-else class="probe-grid">
          <button v-for="probe in visibleProbes" :key="probeKey(probe)" class="probe-card" :class="{ active: selectedClientIps.includes(probe.addressIp || '') }" type="button" @click="toggleProbeFilter(probe)">
            <span class="probe-status-dot"></span>
            <strong>{{ probe.appName || '未定义应用' }}</strong>
            <span>{{ probe.addressIp || '-' }} · PID {{ probe.pid || '-' }}</span>
            <small>{{ probe.projectSrcName || '-' }} · {{ probe.agentVersion || 'unknown agent' }}</small>
            <small>在线 {{ probe.onlineTime || '-' }}</small>
          </button>
        </div>
      </div>
    </section>

    <section class="monitor-toolbar panel">
      <div class="toolbar-main">
        <div class="toolbar-group compact-group">
          <span class="toolbar-label">时间窗口</span>
          <select v-model.number="upToTime" class="text-input time-select">
            <option :value="60">一分钟内</option>
            <option :value="180">三分钟内</option>
            <option :value="300">五分钟内</option>
            <option :value="1800">三十分钟内</option>
          </select>
        </div>
        <div class="toolbar-group filter-group">
          <span class="toolbar-label">应用</span>
          <div class="filter-chip-row" role="listbox" aria-label="应用过滤">
            <button class="filter-chip" :class="{ active: selectedAppIds.length === 0 }" type="button" @click="clearAppFilter">全部</button>
            <button v-for="app in projectApps" :key="app.id" class="filter-chip" :class="{ active: selectedAppIds.includes(app.id) }" type="button" @click="toggleAppFilter(app.id)">
              {{ app.name }}
            </button>
          </div>
        </div>
        <div class="toolbar-group filter-group">
          <span class="toolbar-label">探针 IP</span>
          <div class="filter-chip-row" role="listbox" aria-label="探针 IP 过滤">
            <button class="filter-chip" :class="{ active: selectedClientIps.length === 0 }" type="button" @click="clearIpFilter">全部</button>
            <button v-for="ip in availableClientIps" :key="ip" class="filter-chip" :class="{ active: selectedClientIps.includes(ip) }" type="button" @click="toggleIpFilter(ip)">
              {{ ip }}
            </button>
          </div>
        </div>
        <div class="toolbar-group compact-group">
          <span class="toolbar-label">数量</span>
          <input v-model.number="maxSize" class="text-input size-input" type="number" min="10" max="500" />
        </div>
        <button class="ghost-button query-button" type="button" @click="loadTraces()">查询</button>
      </div>
      <div class="toolbar-actions">
        <div class="snapshot-actions" :class="{ disabled: !selectedTraceId, open: snapshotMenuOpen }">
          <button class="action-button snapshot-trigger" type="button" :disabled="!selectedTraceId || savingSnapshot" aria-haspopup="menu" :aria-expanded="snapshotMenuOpen" @click="toggleSnapshotMenu">
            保存快照 <span aria-hidden="true">⌄</span>
          </button>
          <div class="snapshot-menu">
            <button type="button" :disabled="!selectedTraceId || savingSnapshot" @click="openSnapshotDialog('my')">我的快照</button>
            <button type="button" :disabled="!selectedTraceId || savingSnapshot" @click="openSnapshotDialog('system')">系统快照</button>
          </div>
        </div>
        <label class="auto-refresh"><input v-model="autoSaveMySnapshot" type="checkbox" /> 自动保存我的快照</label>
        <label class="auto-refresh"><input v-model="autoSaveSystemSnapshot" type="checkbox" /> 自动保存系统快照</label>
      </div>
    </section>

    <div class="monitor-grid" :style="monitorGridStyle">
      <section class="panel trace-panel">
        <div class="panel-head trace-head">
          <div>
            <h2>Trace 列表</h2>
            <p>手动刷新或开启自动刷新，同步更新 Trace 列表与示波器。</p>
          </div>
          <div class="auto-refresh-controls">
            <label class="auto-refresh"><input v-model="autoRefresh" type="checkbox" /> 自动刷新</label>
            <label class="refresh-interval">
              <span>每</span>
              <input v-model.number="refreshSeconds" class="text-input refresh-input" type="number" min="3" max="120" aria-label="自动刷新间隔（秒）" />
              <span>秒</span>
            </label>
            <span class="refresh-state">{{ autoRefresh ? '运行中' : '已暂停' }}</span>
            <button class="ghost-button small-button" type="button" :disabled="traceLoading" @click="loadTraces()">
              {{ traceLoading ? '刷新中...' : '手动刷新' }}
            </button>
          </div>
        </div>

        <div class="toolbar">
          <input v-model.trim="traceKeyword" class="text-input" type="search" placeholder="搜索 URL / traceId / IP" aria-label="搜索调用链" />
          <select v-model.number="tracePageSize" class="text-input compact" aria-label="Trace 每页条数">
            <option :value="20">每页 20 条</option>
            <option :value="50">每页 50 条</option>
            <option :value="100">每页 100 条</option>
          </select>
          <button class="ghost-button" type="button" @click="clearMonitorFilters">清空过滤</button>
        </div>

        <div v-if="traceError" class="status-card error">{{ traceError }}</div>
        <div v-if="traceLoading && traces.length === 0" class="status-card">正在加载 trace...</div>
        <div v-else-if="filteredTraces.length === 0" class="status-card">暂无 trace 数据</div>
        <div v-else class="trace-list">
          <button
            v-for="trace in paginatedTraces"
            :key="trace.traceId"
            class="trace-item"
            :class="{ active: selectedTraceId === trace.traceId }"
            type="button"
            @click="selectTrace(trace)"
          >
            <strong>{{ trace.title || trace.traceId }}</strong>
            <span>{{ trace.addressIp || '-' }} · {{ trace.clientIp || '-' }}</span>
            <span class="trace-time">{{ formatTraceDateTime(trace.cacheTime) }}</span>
            <small>{{ trace.traceId }} · #{{ trace.index }}</small>
          </button>
        </div>
        <AppPagination
          v-if="filteredTraces.length > tracePageSize"
          v-model:page="tracePage"
          v-model:page-size="tracePageSize"
          :total="filteredTraces.length"
          item-name="条 Trace"
          :page-sizes="[20, 50, 100]"
        />
      </section>

      <div class="monitor-resizer" @pointerdown="startResize"><span>拖拽调整宽度</span></div>

      <section class="panel graph-panel">
        <div class="panel-head">
          <div>
            <h2>{{ graph ? '实时监控详情' : '实时请求示波器' }}</h2>
            <p>{{ selectedTraceId || oscilloscopeSubtitle }}</p>
          </div>
          <div class="header-actions">
            <button v-if="graph" class="ghost-button" type="button" @click="showOscilloscope">返回示波器</button>
            <button class="ghost-button" type="button" :disabled="!selectedTraceId || graphLoading" @click="loadGraph">重载拓扑</button>
            <button class="ghost-button" type="button" :disabled="!selectedTraceId || savingSnapshot" title="自动保存我的快照和系统快照" @click="autoSaveCurrentTraceSnapshots">自动保存快照</button>
          </div>
        </div>

        <div v-if="graphLoading" class="status-card">正在加载拓扑...</div>
        <div v-else-if="graphError" class="status-card error">{{ graphError }}</div>
        <div v-else-if="!graph" class="oscilloscope-card">
          <div class="oscilloscope-header">
            <div>
              <strong>实时请求示波器</strong>
              <p>{{ oscilloscopeSubtitle }}</p>
            </div>
            <div class="oscilloscope-actions">
              <button class="ghost-button" :class="{ active: scopeMode === 'aggregate' }" type="button" @click="setScopeMode('aggregate')">全部探针</button>
              <button class="ghost-button" :class="{ active: scopeMode === 'single' }" type="button" @click="setScopeMode('single')">当前探针</button>
              <button class="ghost-button" :class="{ active: scopeMode === 'lanes' }" type="button" @click="setScopeMode('lanes')">多探针泳道</button>
            </div>
          </div>
          <div class="wave-board">
            <div v-if="!wavePoints.length" class="wave-empty">暂无请求波形<br /><small>当监控列表收到请求后，每个请求会形成一个圆点</small></div>
            <svg v-if="wavePolyline" class="wave-line" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
              <polyline :points="wavePolyline" />
            </svg>
            <span v-for="point in wavePoints" :key="point.id" class="wave-point" :class="{ fresh: point.fresh }" :style="{ left: `${point.x}%`, top: `${point.y}%` }" :title="point.title"></span>
          </div>
          <div class="monitor-request-summary">
            <div><span>最新请求</span><strong>{{ latestTrace?.title || '暂无' }}</strong></div>
            <div><span>请求来源</span><strong>{{ latestTraceSource }}</strong></div>
            <div><span>操作提示</span><strong>点击左侧请求查看链路详情</strong></div>
          </div>
        </div>
        <template v-else>
          <div
            class="graph-board monitor-graph-board"
            @wheel.prevent="handleGraphWheel"
            @pointerdown="startGraphPan"
            @pointermove="moveGraphPan"
            @pointerup="endGraphPan"
            @pointerleave="endGraphPan"
          >
            <div class="graph-tools">
              <span>滚轮缩放 · 拖拽平移</span>
              <button type="button" @click="zoomGraph(0.15)">放大</button>
              <button type="button" @click="zoomGraph(-0.15)">缩小</button>
              <button type="button" @click="resetGraphView">重置</button>
            </div>
            <svg class="graph-svg" :viewBox="graphViewBox" preserveAspectRatio="xMidYMid meet">
              <defs>
                <marker id="monitorArrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
                  <path d="M0,0 L0,6 L9,3 z" fill="#94a3b8" />
                </marker>
              </defs>
              <g :transform="graphTransform">
                <path
                  v-for="edge in edgePositions"
                  :key="`${edge.from}-${edge.to}-${edge.label}`"
                  :d="edge.path"
                  class="graph-edge"
                  marker-end="url(#monitorArrow)"
                />
                <text v-for="edge in edgePositions" :key="`${edge.from}-${edge.to}-label`" :x="edge.mx" :y="edge.my" class="edge-label">
                  {{ edge.label || edge.type || edge.count }}
                </text>
                <g
                  v-for="node in nodePositions"
                  :key="node.id"
                  class="graph-node"
                  :class="{ selected: selectedNodeId === node.id }"
                  @click.stop="selectNode(node.id)"
                >
                  <title>{{ graphNodeTooltip(node) }}</title>
                  <rect :x="node.x" :y="node.y" rx="6" ry="6" :width="graphNodeWidth" :height="graphNodeHeight" />
                  <circle :cx="node.x + 30" :cy="node.y + 33" r="18" class="node-icon-ring" />
                  <text :x="node.x + 30" :y="node.y + 40" class="node-icon">{{ iconGlyph(node.icon || node.type) }}</text>
                  <text :x="node.x + 58" :y="node.y + 30" class="node-title">{{ compactGraphText(node.title || node.id, 22) }}</text>
                  <text :x="node.x + 58" :y="node.y + 56" class="node-subtitle">{{ compactGraphText(node.subTitle || '-', 26) }}</text>
                  <text :x="node.x + 14" :y="node.y + 82" class="node-type">{{ compactGraphText(node.tips || node.type || 'node', 32) }}</text>
                </g>
              </g>
            </svg>
          </div>

          <div class="node-detail-grid">
            <article class="node-detail-card full-detail-card">
              <h3>节点详情</h3>
              <div v-if="nodeDetailLoading" class="status-card">正在加载节点详情...</div>
              <div v-else-if="nodeDetailError" class="status-card error">{{ nodeDetailError }}</div>
              <GraphNodeDetailCard v-else :detail="selectedNodeDetail" empty-text="点击图中节点查看详情" />
            </article>
            <article class="node-detail-card">
              <h3>保存状态</h3>
              <span>{{ snapshotNotice || '可将当前 trace 自动保存为系统快照，重复保存会由后端去重。' }}</span>
            </article>
          </div>
        </template>
      </section>
    </div>

    <div v-if="snapshotDialogOpen" class="modal-backdrop" @click.self="closeSnapshotDialog">
      <form class="snapshot-modal" @submit.prevent="submitSnapshotForm">
        <div class="modal-head">
          <div>
            <div class="eyebrow">{{ snapshotDialogMode === 'my' ? 'My Snapshot' : 'System Snapshot' }}</div>
            <h2>{{ snapshotDialogMode === 'my' ? '保存我的快照' : '保存系统快照' }}</h2>
            <p>{{ snapshotContext?.subTitle || selectedTraceId }}</p>
          </div>
          <button class="icon-button" type="button" @click="closeSnapshotDialog">×</button>
        </div>

        <div v-if="snapshotFormError" class="status-card error">{{ snapshotFormError }}</div>
        <div v-if="snapshotContextLoading" class="status-card">正在加载保存上下文...</div>
        <template v-else>
          <div class="snapshot-form-grid">
            <div class="form-main">
              <label class="field required">
                <span>名称</span>
                <input v-model.trim="snapshotForm.title" class="text-input" type="text" maxlength="50" :placeholder="snapshotDialogMode === 'my' ? '我的快照名称' : '系统快照名称'" />
              </label>
              <label v-if="snapshotDialogMode === 'system'" class="field">
                <span>图片</span>
                <input class="text-input" type="file" accept="image/*" @change="handleTopicImageUpload" />
              </label>
              <div v-if="snapshotDialogMode === 'system' && snapshotForm.topicImage" class="image-preview-line">
                <img :src="snapshotForm.topicImage" alt="系统快照图片" />
                <span>{{ snapshotForm.topicImage }}</span>
              </div>
              <label class="field">
                <span>描述</span>
                <textarea v-model.trim="snapshotForm.describe" class="text-input textarea" maxlength="512" rows="6" placeholder="描述本次调用场景、关键输入或异常现象"></textarea>
              </label>
            </div>

            <div class="form-side">
              <label class="field">
                <span>所属应用</span>
                <input class="text-input" type="text" :value="snapshotContext?.appName || snapshotForm.appId" readonly />
              </label>
              <label v-if="snapshotDialogMode === 'system'" class="field">
                <span>目录</span>
                <select v-model="snapshotForm.directory" class="text-input">
                  <option value="root">/root</option>
                  <option v-for="dir in snapshotContext?.directories || []" :key="dir.id" :value="dir.id">
                    {{ dir.path || dir.name }}
                  </option>
                </select>
              </label>
              <label v-if="snapshotDialogMode === 'system'" class="field">
                <span>版本有效周期（天）</span>
                <input v-model.number="snapshotForm.versionCycle" class="text-input" type="number" min="1" max="3650" />
              </label>
              <div class="field">
                <span>添加标签</span>
                <div class="choice-grid">
                  <label v-for="label in snapshotContext?.labels || []" :key="label.name" class="choice-pill">
                    <input v-model="snapshotForm.labels" type="checkbox" :value="label.name" />
                    <span>{{ label.name }}</span>
                  </label>
                </div>
              </div>
              <div v-if="snapshotDialogMode === 'system'" class="field required">
                <span>负责人</span>
                <div class="choice-grid">
                  <label v-for="member in snapshotContext?.members || []" :key="member.memberId" class="choice-pill">
                    <input v-model="snapshotForm.principals" type="checkbox" :value="member.memberId" />
                    <span>{{ member.memberName || member.memberEmail || member.memberId }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-actions">
            <button class="ghost-button" type="button" @click="closeSnapshotDialog">算啦</button>
            <button class="action-button" type="submit" :disabled="savingSnapshot || topicImageUploading">
              {{ savingSnapshot ? '保存中...' : topicImageUploading ? '图片上传中...' : snapshotDialogMode === 'my' ? '保存到我的快照' : '保存到系统快照' }}
            </button>
          </div>
        </template>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { fetchMonitorSnapshotContext, saveMonitorMySnapshot, saveMonitorSystemSnapshot, uploadResource } from '@/api/bootstrap'
import { useProjectStore } from '@/stores/project'
import { apiGet, apiGetRaw, apiPost } from '@/api/http'
import type { AppSummary, GraphEdgeSummary, GraphNodeDetailPayload, GraphNodeSummary, GraphViewPayload, MonitorSnapshotContextPayload, OnlineSessionSummary, TraceItemSummary } from '@/api/types'
import GraphNodeDetailCard from '@/components/snapshot/GraphNodeDetailCard.vue'
import AppPagination from '@/components/AppPagination.vue'

type PositionedNode = GraphNodeSummary & { x: number; y: number; rank: number }
type PositionedEdge = GraphEdgeSummary & { path: string; mx: number; my: number }
type SnapshotDialogMode = 'my' | 'system'

type RawMonitorProbeSession = OnlineSessionSummary & {
  clientInfo?: {
    appKey?: string
    addressIp?: string
    agentVersion?: string
    systemDir?: string
    pid?: string
    jvmVersion?: string
    jvmOption?: string
  }
  application?: {
    appId?: string
    appName?: string
    projectSrcName?: string
  }
}

const graphNodeWidth = 240
const graphNodeHeight = 96

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const selectedAppId = computed(() => String(route.query.appId || ''))
const projectContext = computed(() => projectId.value ? projectStore.contextByProjectId[projectId.value] : undefined)
const projectApps = computed<AppSummary[]>(() => projectContext.value?.apps || [])

const probes = ref<OnlineSessionSummary[]>([])
const traces = ref<TraceItemSummary[]>([])
const graph = ref<GraphViewPayload | null>(null)
const graphZoom = ref(0.92)
const graphOffset = ref({ x: 0, y: 0 })
const graphPan = ref<{ startX: number; startY: number; originX: number; originY: number } | null>(null)
const selectedTraceId = ref('')
const selectedNodeId = ref('')
const selectedNodeDetail = ref<GraphNodeDetailPayload | null>(null)
const probeKeyword = ref('')
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
const monitorListWidth = ref(340)
const tracePage = ref(1)
const tracePageSize = ref(50)
const probesExpanded = ref(false)
const probePreviewLimit = 4
const probeLoading = ref(false)
const traceLoading = ref(false)
const graphLoading = ref(false)
const nodeDetailLoading = ref(false)
const savingSnapshot = ref(false)
const snapshotDialogOpen = ref(false)
const snapshotDialogMode = ref<SnapshotDialogMode>('system')
const snapshotMenuOpen = ref(false)
const snapshotContextLoading = ref(false)
const topicImageUploading = ref(false)
const probeError = ref('')
const traceError = ref('')
const graphError = ref('')
const nodeDetailError = ref('')
const snapshotNotice = ref('')
const snapshotFormError = ref('')
const snapshotContext = ref<MonitorSnapshotContextPayload | null>(null)
const snapshotForm = ref({
  traceId: '',
  appId: '',
  directory: 'root',
  title: '',
  topicImage: '',
  describe: '',
  versionCycle: 30,
  labels: [] as string[],
  principals: [] as string[],
})
let refreshTimer: number | undefined
let graphRequestSeq = 0
let nodeDetailRequestSeq = 0
const autoSavedTraceIds = {
  my: new Set<string>(),
  system: new Set<string>(),
}

watch(selectedAppId, (value) => {
  selectedAppIds.value = value ? [value] : []
}, { immediate: true })

watch(projectId, (value) => {
  if (value) projectStore.loadProjectContext(value).catch(() => undefined)
}, { immediate: true })

const filteredProbes = computed(() => {
  const needle = probeKeyword.value.toLowerCase()
  if (!needle) return probes.value
  return probes.value.filter((probe) => [probe.addressIp, probe.appName, probe.projectSrcName, probe.agentVersion, probe.systemDir]
    .join(' ')
    .toLowerCase()
    .includes(needle))
})
const visibleProbes = computed(() => probesExpanded.value ? filteredProbes.value : filteredProbes.value.slice(0, probePreviewLimit))

const filteredTraces = computed(() => {
  const needle = traceKeyword.value.toLowerCase()
  const filtered = traces.value.filter((trace) => {
    const haystack = [trace.traceId, trace.title, trace.addressIp, trace.clientIp, trace.appId]
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
const availableClientIps = computed(() => Array.from(new Set(probes.value.map((probe) => probe.addressIp).filter((ip): ip is string => Boolean(ip)))))
const monitorGridStyle = computed(() => ({ gridTemplateColumns: `${monitorListWidth.value}px 10px minmax(0, 1fr)` }))
const oscilloscopeSubtitle = computed(() => {
  if (scopeMode.value === 'single') return selectedClientIps.value[0] ? `当前探针：${selectedClientIps.value[0]}` : '当前探针：请点击左侧探针'
  if (scopeMode.value === 'lanes') return '多探针泳道：按 IP 分组展示请求脉冲'
  return '聚合全部探针：圆点 = 一次请求；折线 = 请求脉冲趋势；扫描线 = 实时监听节奏'
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

const nodePositions = computed<PositionedNode[]>(() => {
  const nodes = graph.value?.nodes || []
  const edges = graph.value?.edges || []
  const incoming = new Map<string, number>()
  const children = new Map<string, string[]>()
  nodes.forEach((node) => {
    incoming.set(node.id, 0)
    children.set(node.id, [])
  })
  edges.forEach((edge) => {
    if (!incoming.has(edge.from) || !incoming.has(edge.to)) return
    incoming.set(edge.to, (incoming.get(edge.to) || 0) + 1)
    children.get(edge.from)?.push(edge.to)
  })

  const ranks = new Map<string, number>()
  const roots = nodes.filter((node) => (incoming.get(node.id) || 0) === 0)
  const queue = (roots.length ? roots : nodes.slice(0, 1)).map((node) => node.id)
  queue.forEach((id) => ranks.set(id, 0))
  for (let index = 0; index < queue.length; index += 1) {
    const id = queue[index]
    const nextRank = (ranks.get(id) || 0) + 1
    ;(children.get(id) || []).forEach((childId) => {
      if ((ranks.get(childId) ?? -1) < nextRank) {
        ranks.set(childId, nextRank)
        queue.push(childId)
      }
    })
  }

  const buckets = new Map<number, GraphNodeSummary[]>()
  nodes.forEach((node, index) => {
    const rank = ranks.get(node.id) ?? Math.floor(index / 5)
    const list = buckets.get(rank) || []
    list.push(node)
    buckets.set(rank, list)
  })

  return nodes.map((node, index) => {
    const rank = ranks.get(node.id) ?? Math.floor(index / 5)
    const bucket = buckets.get(rank) || []
    const row = Math.max(0, bucket.findIndex((item) => item.id === node.id))
    return {
      ...node,
      rank,
      x: 42 + rank * 318,
      y: 42 + row * 132 + Math.max(0, 4 - bucket.length) * 36,
    }
  })
})

const graphViewBox = computed(() => {
  const maxX = Math.max(1200, ...nodePositions.value.map((node) => node.x + graphNodeWidth + 80))
  const maxY = Math.max(700, ...nodePositions.value.map((node) => node.y + graphNodeHeight + 80))
  return `0 0 ${maxX} ${maxY}`
})

const graphTransform = computed(() => `translate(${graphOffset.value.x} ${graphOffset.value.y}) scale(${graphZoom.value})`)

const edgePositions = computed<PositionedEdge[]>(() => {
  const nodeMap = new Map(nodePositions.value.map((node) => [node.id, node]))
  return (graph.value?.edges || [])
    .map((edge) => {
      const from = nodeMap.get(edge.from)
      const to = nodeMap.get(edge.to)
      if (!from || !to) return null
      const x1 = from.x + graphNodeWidth
      const y1 = from.y + graphNodeHeight / 2
      const x2 = to.x
      const y2 = to.y + graphNodeHeight / 2
      const dx = Math.max(56, Math.abs(x2 - x1) / 2)
      return {
        ...edge,
        path: `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`,
        mx: (x1 + x2) / 2,
        my: (y1 + y2) / 2 - 10,
      }
    })
    .filter((edge): edge is PositionedEdge => edge !== null)
})

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

async function loadProbes() {
  probeLoading.value = true
  probeError.value = ''
  try {
    const items = await apiGetRaw<RawMonitorProbeSession[]>(`/api/projects/${projectId.value}/monitor/probeStatus`)
    probes.value = items.map(normalizeProbeSession)
  } catch (err) {
    probeError.value = err instanceof Error ? err.message : '加载探针失败'
  } finally {
    probeLoading.value = false
  }
}

function normalizeProbeSession(item: RawMonitorProbeSession): OnlineSessionSummary {
  const clientInfo = item.clientInfo
  const application = item.application
  const appId = item.appId || clientInfo?.appKey || application?.appId
  return {
    appId,
    addressIp: item.addressIp || clientInfo?.addressIp,
    agentVersion: item.agentVersion || clientInfo?.agentVersion,
    systemDir: item.systemDir || clientInfo?.systemDir,
    pid: item.pid || clientInfo?.pid,
    jvmVersion: item.jvmVersion || clientInfo?.jvmVersion,
    jvmOption: item.jvmOption || clientInfo?.jvmOption,
    onlineTime: item.onlineTime,
    appName: item.appName || application?.appName || (appId ? '未识别应用' : '未绑定应用'),
    projectSrcName: item.projectSrcName || application?.projectSrcName || '-',
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
      await batchSaveMySnapshots()
      break
    case 'batchSaveSystemSnapshots':
      await batchSaveSystemSnapshots()
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

function clampGraphZoom(value: number) {
  return Math.min(2.4, Math.max(0.45, value))
}

function zoomGraph(delta: number) {
  graphZoom.value = clampGraphZoom(graphZoom.value + delta)
}

function resetGraphView() {
  graphZoom.value = 0.92
  graphOffset.value = { x: 0, y: 0 }
}

function handleGraphWheel(event: WheelEvent) {
  zoomGraph(event.deltaY > 0 ? -0.1 : 0.1)
}

function startGraphPan(event: PointerEvent) {
  if ((event.target as Element).closest('.graph-tools, .graph-node')) return
  graphPan.value = {
    startX: event.clientX,
    startY: event.clientY,
    originX: graphOffset.value.x,
    originY: graphOffset.value.y,
  }
}

function moveGraphPan(event: PointerEvent) {
  if (!graphPan.value) return
  graphOffset.value = {
    x: graphPan.value.originX + event.clientX - graphPan.value.startX,
    y: graphPan.value.originY + event.clientY - graphPan.value.startY,
  }
}

function endGraphPan() {
  graphPan.value = null
}

function compactGraphText(value: string, maxLength: number) {
  if (!value) return ''
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value
}

function graphNodeTooltip(node: GraphNodeSummary) {
  return [node.title || node.id, node.subTitle, node.tips || node.type]
    .filter(Boolean)
    .join('\n')
}

function iconGlyph(value?: string) {
  const normalized = (value || '').toLowerCase()
  if (normalized.includes('database') || normalized.includes('sql')) return 'DB'
  if (normalized.includes('redis')) return 'R'
  if (normalized.includes('server') || normalized.includes('application')) return 'A'
  if (normalized.includes('http') || normalized.includes('client')) return 'H'
  return 'N'
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

async function autoSaveSnapshot() {
  if (!selectedTraceId.value) return
  savingSnapshot.value = true
  snapshotNotice.value = ''
  try {
    const body = new URLSearchParams()
    body.set('traceId', selectedTraceId.value)
    body.set('title', graph.value?.title || '实时监控自动快照')
    const result = await apiPost<string>(`/api/projects/${projectId.value}/monitor/autoSaveSystemSnapshot`, body.toString(), 'application/x-www-form-urlencoded;charset=UTF-8')
    snapshotNotice.value = result || '系统快照已保存'
  } catch (err) {
    snapshotNotice.value = err instanceof Error ? err.message : '保存系统快照失败'
  } finally {
    savingSnapshot.value = false
  }
}

async function autoSaveCurrentTraceSnapshots() {
  if (!selectedTraceId.value) return
  await saveMySnapshot()
  await autoSaveSnapshot()
}

async function saveMySnapshot() {
  if (!selectedTraceId.value) return
  savingSnapshot.value = true
  snapshotNotice.value = ''
  try {
    const selectedTrace = traces.value.find((trace) => trace.traceId === selectedTraceId.value)
    const body = new URLSearchParams()
    body.set('traceId', selectedTraceId.value)
    body.set('autoSave', 'true')
    body.set('name', (selectedTrace?.title || graph.value?.title || selectedTraceId.value).trim().slice(0, 200))
    body.set('describe', '实时监控自动保存')
    body.append('labels', '自动保存')
    body.append('labels', '实时监控')
    const result = await apiPost<unknown>(`/api/projects/${projectId.value}/snapshots/my/save`, body.toString(), 'application/x-www-form-urlencoded;charset=UTF-8')
    snapshotNotice.value = extractResultMessage(result, '我的快照已保存')
  } catch (err) {
    snapshotNotice.value = err instanceof Error ? err.message : '保存我的快照失败'
  } finally {
    savingSnapshot.value = false
  }
}

async function runAutoSaveForNewTraces(items: TraceItemSummary[]) {
  for (const item of items.slice(0, 5)) {
    if (!item.traceId) continue
    if (autoSaveMySnapshot.value && !autoSavedTraceIds.my.has(item.traceId)) {
      autoSavedTraceIds.my.add(item.traceId)
      selectedTraceId.value = item.traceId
      await saveMySnapshot()
    }
    if (autoSaveSystemSnapshot.value && !autoSavedTraceIds.system.has(item.traceId)) {
      autoSavedTraceIds.system.add(item.traceId)
      selectedTraceId.value = item.traceId
      await autoSaveSnapshot()
    }
  }
}

async function batchSaveMySnapshots() {
  for (const trace of filteredTraces.value.slice(0, 10)) {
    if (!trace.traceId) continue
    selectedTraceId.value = trace.traceId
    await saveMySnapshot()
  }
}

async function batchSaveSystemSnapshots() {
  for (const trace of filteredTraces.value.slice(0, 10)) {
    if (!trace.traceId) continue
    selectedTraceId.value = trace.traceId
    await autoSaveSnapshot()
  }
}

function toggleSnapshotMenu() {
  if (!selectedTraceId.value || savingSnapshot.value) return
  snapshotMenuOpen.value = !snapshotMenuOpen.value
}

async function openSnapshotDialog(mode: SnapshotDialogMode = 'system') {
  if (!selectedTraceId.value) return
  snapshotMenuOpen.value = false
  snapshotDialogMode.value = mode
  snapshotDialogOpen.value = true
  snapshotContextLoading.value = true
  snapshotFormError.value = ''
  try {
    const context = await fetchMonitorSnapshotContext(projectId.value, selectedTraceId.value)
    snapshotContext.value = context
    snapshotForm.value = {
      traceId: context.traceId,
      appId: context.appId,
      directory: 'root',
      title: (context.defaultTitle || graph.value?.title || (mode === 'my' ? '实时监控我的快照' : '实时监控系统快照')).slice(0, 50),
      topicImage: '',
      describe: '',
      versionCycle: 30,
      labels: mode === 'my' ? ['实时监控'] : [],
      principals: context.currentUserId ? [context.currentUserId] : [],
    }
  } catch (err) {
    snapshotFormError.value = err instanceof Error ? err.message : `加载${mode === 'my' ? '我的' : '系统'}快照保存上下文失败`
  } finally {
    snapshotContextLoading.value = false
  }
}

function closeSnapshotDialog() {
  if (savingSnapshot.value || topicImageUploading.value) {
    return
  }
  snapshotDialogOpen.value = false
  snapshotFormError.value = ''
}

async function handleTopicImageUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  topicImageUploading.value = true
  snapshotFormError.value = ''
  try {
    snapshotForm.value.topicImage = await uploadResource(file)
  } catch (err) {
    snapshotFormError.value = err instanceof Error ? err.message : '图片上传失败'
  } finally {
    input.value = ''
    topicImageUploading.value = false
  }
}

async function submitSnapshotForm() {
  const form = snapshotForm.value
  if (!form.title || form.title.length < 4) {
    snapshotFormError.value = '名称至少包含4个字符'
    return
  }
  if (form.title.length > 50) {
    snapshotFormError.value = '名称不能超过50个字符'
    return
  }
  if (form.describe.length > 512) {
    snapshotFormError.value = '描述不能超过512个字符'
    return
  }
  if (snapshotDialogMode.value === 'system' && !form.principals.length) {
    snapshotFormError.value = '请至少选择一个负责人'
    return
  }
  savingSnapshot.value = true
  snapshotFormError.value = ''
  snapshotNotice.value = ''
  try {
    if (snapshotDialogMode.value === 'my') {
      await saveMonitorMySnapshot(projectId.value, {
        traceId: form.traceId,
        appId: form.appId,
        name: form.title,
        describe: form.describe,
        labels: form.labels,
      })
      snapshotNotice.value = '我的快照已保存'
    } else {
      const result = await saveMonitorSystemSnapshot(projectId.value, form)
      snapshotNotice.value = result || '系统快照已保存'
    }
    snapshotDialogOpen.value = false
  } catch (err) {
    snapshotFormError.value = err instanceof Error ? err.message : `${snapshotDialogMode.value === 'my' ? '我的' : '系统'}快照保存失败`
  } finally {
    savingSnapshot.value = false
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

function probeKey(probe: OnlineSessionSummary) {
  return `${probe.addressIp || ''}-${probe.pid || ''}-${probe.systemDir || ''}-${probe.appName || ''}`
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

function buildAutoSnapshotName(prefix: string, title: string) {
  const normalizedTitle = (title || '未命名链路').trim().slice(0, 24)
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  const timestamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `${prefix}-${normalizedTitle}-${timestamp}`
}

function extractResultMessage(result: unknown, fallback: string) {
  if (typeof result === 'string') return result || fallback
  if (result && typeof result === 'object' && 'message' in result) {
    return String((result as { message?: unknown }).message || fallback)
  }
  return fallback
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

<style scoped>
.page-header,
.header-actions,
.panel-head,
.toolbar,
.trace-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 10px;
}

.monitor-compact-header {
  padding: 10px 14px;
  border: 1px solid rgba(15, 23, 42, .07);
  border-radius: 20px;
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 10px 28px rgba(15, 23, 42, .05);
}

.monitor-compact-header h1 {
  margin: 0 0 3px;
  font-size: 24px;
  line-height: 1.08;
}

.monitor-compact-header .subtext {
  margin: 0;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .14em;
  text-transform: uppercase;
}

.subtext,
.panel-head p,
.probe-card span,
.probe-card small,
.trace-item span,
.trace-item small {
  color: #64748b;
}

.secondary-link {
  color: #0f766e;
  font-weight: 800;
}

.action-button,
.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
}

.action-button {
  background: #0f172a;
  color: #fff;
}

.snapshot-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ghost-button {
  background: #eef7f7;
  color: #0f766e;
  font-weight: 800;
}

.small-button {
  min-height: 34px;
  padding: 7px 12px;
  font-size: 13px;
}

.action-button:disabled,
.ghost-button:disabled {
  opacity: .45;
  cursor: not-allowed;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.overview-card,
.panel,
.status-card,
.probe-card,
.trace-item,
.node-detail-card {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 22px;
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 18px 42px rgba(15, 23, 42, .06);
}

.overview-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 4px 12px;
  padding: 10px 12px;
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, .16), transparent 42%),
    rgba(255, 255, 255, .94);
}

.overview-card span {
  color: #64748b;
  font-weight: 700;
}

.overview-card strong {
  grid-row: 1 / span 2;
  grid-column: 2;
  display: block;
  margin-top: 0;
  font-size: 22px;
  line-height: 1;
}

.overview-card small {
  display: block;
  margin-top: 0;
  color: #94a3b8;
  font-weight: 700;
}

.overview-card .time-value {
  font-size: 17px;
}

.panel,
.status-card {
  padding: 14px;
}

.status-card.error {
  color: #b91c1c;
}

.probe-panel {
  margin-bottom: 10px;
}

.probe-dashboard-card {
  display: grid;
  grid-template-columns: minmax(360px, .9fr) minmax(0, 1.4fr);
  align-items: start;
  gap: 12px;
  background: linear-gradient(135deg, rgba(255, 255, 255, .96), rgba(245, 251, 255, .96));
}

.probe-head {
  align-items: start;
}

.probe-head h2 {
  margin: 0 0 4px;
}

.probe-head p {
  margin: 0;
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
  font-weight: 800;
}

.compact-status {
  padding: 12px;
  border-radius: 16px;
}

.text-input {
  width: 100%;
  border: 1px solid #d9e5ea;
  border-radius: 14px;
  padding: 11px 12px;
  background: #fbfdfe;
  color: #0f172a;
}

.text-input.compact {
  width: min(320px, 100%);
}

.textarea {
  resize: vertical;
}

.size-input {
  width: 96px;
}

.refresh-input {
  width: 70px;
  padding: 7px 9px;
}

.time-select {
  width: 130px;
}

.probe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
  margin-top: 0;
}

.probe-card {
  position: relative;
  display: grid;
  gap: 3px;
  padding: 9px 11px;
  border-radius: 15px;
  border: 1px solid rgba(15, 23, 42, .08);
  text-align: left;
  cursor: pointer;
  box-shadow: none;
}

.probe-card strong,
.probe-card span,
.probe-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.probe-card.active {
  border-color: #0f766e;
  background: #ecfdf5;
}

.probe-status-dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: #22c55e;
  box-shadow: 0 0 0 6px rgba(34, 197, 94, .12);
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
  padding: 10px 12px;
  backdrop-filter: blur(14px);
}

.toolbar-main,
.toolbar-actions,
.auto-refresh-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.toolbar-main {
  min-width: 0;
}

.toolbar-actions {
  justify-content: flex-end;
  max-width: 420px;
}

.toolbar-group {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.compact-group {
  flex: 0 0 auto;
}

.filter-group {
  flex: 1 1 210px;
  max-width: 330px;
}

.toolbar-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.filter-chip-row {
  display: flex;
  gap: 6px;
  min-height: 36px;
  max-height: 72px;
  overflow: auto;
  padding: 2px;
  border: 1px solid #d9e5ea;
  border-radius: 14px;
  background: #fbfdfe;
}

.filter-chip {
  flex: 0 0 auto;
  max-width: 138px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 6px 10px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .04);
}

.filter-chip.active {
  border-color: rgba(15, 118, 110, .24);
  background: #0f766e;
  color: #fff;
}

.query-button {
  align-self: end;
}

.snapshot-actions {
  position: relative;
}

.snapshot-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 12;
  min-width: 130px;
  display: none;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, .10);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, .14);
}

.snapshot-actions.open .snapshot-menu,
.snapshot-actions:focus-within .snapshot-menu {
  display: grid;
  gap: 4px;
}

.snapshot-menu button {
  border: none;
  border-radius: 10px;
  padding: 9px 10px;
  background: transparent;
  color: #334155;
  text-align: left;
  font-weight: 800;
  cursor: pointer;
}

.snapshot-menu button:hover {
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
}

.monitor-grid {
  display: grid;
  gap: 0;
  align-items: stretch;
  min-height: max(660px, calc(100vh - 320px));
}

.monitor-grid > .panel {
  min-width: 0;
}

.monitor-resizer {
  display: grid;
  place-items: center;
  cursor: col-resize;
  color: #94a3b8;
}

.monitor-resizer span {
  writing-mode: vertical-rl;
  padding: 10px 2px;
  border-radius: 999px;
  background: rgba(15, 23, 42, .06);
  font-size: 11px;
  font-weight: 800;
}

.trace-panel,
.graph-panel {
  min-height: max(620px, calc(100vh - 360px));
}

.toolbar {
  margin: 14px 0;
}

.trace-panel .toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.trace-panel .toolbar .text-input[type='search'] {
  grid-column: 1 / -1;
}

.trace-panel .toolbar .ghost-button {
  min-width: 96px;
  white-space: nowrap;
}

.trace-head {
  align-items: flex-start;
}

.trace-head .auto-refresh-controls {
  justify-content: flex-end;
}

.refresh-interval,
.refresh-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-weight: 800;
  white-space: nowrap;
}

.auto-refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-weight: 800;
  white-space: nowrap;
}

.trace-list {
  display: grid;
  gap: 12px;
  max-height: max(420px, calc(100vh - 520px));
  overflow: auto;
  padding-right: 4px;
}

.trace-item {
  display: grid;
  gap: 8px;
  width: 100%;
  padding: 16px;
  border-radius: 20px;
  text-align: left;
  cursor: pointer;
  box-shadow: none;
}

.trace-item strong {
  overflow-wrap: anywhere;
  line-height: 1.35;
}

.trace-item span,
.trace-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-time {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.trace-item.active {
  border-color: #0f766e;
  background: #ecfdf5;
}

.oscilloscope-card {
  display: grid;
  gap: 14px;
}

.oscilloscope-header,
.oscilloscope-actions,
.monitor-request-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.oscilloscope-header p {
  margin: 5px 0 0;
  color: #64748b;
}

.ghost-button.active {
  background: #0f766e;
  color: #fff;
}

.wave-board {
  position: relative;
  height: clamp(360px, 44vh, 520px);
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 22px;
  background:
    linear-gradient(rgba(20, 184, 166, .10) 1px, transparent 1px),
    linear-gradient(90deg, rgba(20, 184, 166, .10) 1px, transparent 1px),
    radial-gradient(circle at center, rgba(20, 184, 166, .08), transparent 48%),
    #07111f;
  background-size: 28px 28px, 28px 28px, auto, auto;
}

.wave-board::after {
  content: '';
  position: absolute;
  inset: 0;
  width: 34%;
  background: linear-gradient(90deg, transparent, rgba(45, 212, 191, .14), transparent);
  animation: scan-line 3.6s linear infinite;
}

.wave-line {
  position: absolute;
  inset: 0;
  z-index: 1;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.wave-line polyline {
  fill: none;
  stroke: rgba(45, 212, 191, .55);
  stroke-width: .45;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 8px rgba(45, 212, 191, .42));
}

@keyframes scan-line {
  from { transform: translateX(-100%); }
  to { transform: translateX(320%); }
}

.wave-point {
  position: absolute;
  z-index: 2;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #2dd4bf;
  box-shadow: 0 0 0 7px rgba(45, 212, 191, .12), 0 0 18px rgba(45, 212, 191, .8);
  transform: translate(-50%, -50%);
}

.wave-point.fresh {
  animation: wave-pulse 1.1s ease-out infinite;
}

@keyframes wave-pulse {
  0% { box-shadow: 0 0 0 0 rgba(45, 212, 191, .42), 0 0 20px rgba(45, 212, 191, .92); }
  100% { box-shadow: 0 0 0 18px rgba(45, 212, 191, 0), 0 0 20px rgba(45, 212, 191, .72); }
}

.wave-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: rgba(226, 232, 240, .86);
  text-align: center;
  font-weight: 900;
}

.monitor-request-summary > div {
  flex: 1 1 180px;
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 18px;
  background: #f8fafc;
}

.monitor-request-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.monitor-request-summary strong {
  color: #1f2937;
  word-break: break-all;
}

.graph-board {
  position: relative;
  overflow: hidden;
  border-radius: 20px;
  background:
    linear-gradient(rgba(15, 23, 42, .04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15, 23, 42, .04) 1px, transparent 1px),
    #f8fbfc;
  background-size: 28px 28px;
  cursor: grab;
}

.graph-board:active {
  cursor: grabbing;
}

.graph-tools {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, .10);
  border-radius: 999px;
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 14px 32px rgba(15, 23, 42, .14);
}

.graph-tools span {
  padding: 0 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.graph-tools button {
  border: none;
  border-radius: 999px;
  padding: 6px 10px;
  background: #eef7f7;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.graph-tools button:hover {
  background: #0f766e;
  color: #fff;
}

.graph-svg {
  min-width: 900px;
  width: 100%;
  height: clamp(520px, 55vh, 720px);
}

.graph-edge {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 2;
}

.edge-label {
  fill: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.graph-node {
  cursor: pointer;
}

.graph-node rect {
  fill: #fff;
  stroke: #cbd5e1;
  stroke-width: 1.6;
  filter: drop-shadow(0 10px 18px rgba(15, 23, 42, .08));
}

.graph-node.selected rect {
  fill: #ecfdf5;
  stroke: dodgerblue;
  stroke-width: 2.6;
}

.node-icon-ring {
  fill: #ecfeff;
  stroke: rgba(15, 118, 110, .24);
}

.node-icon {
  fill: #0f766e;
  font-size: 12px;
  font-weight: 900;
  text-anchor: middle;
}

.node-title {
  fill: #0f172a;
  font-size: 15px;
  font-weight: 900;
}

.node-subtitle,
.node-type {
  fill: #64748b;
  font-size: 12px;
}

.node-type {
  font-size: 11px;
}

.node-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, .45fr);
  gap: 14px;
  margin-top: 14px;
}

.node-detail-card {
  display: grid;
  gap: 8px;
  padding: 16px;
  box-shadow: none;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .45);
}

.snapshot-modal {
  width: min(1040px, 100%);
  max-height: min(860px, 92vh);
  overflow: auto;
  border-radius: 28px;
  padding: 24px;
  background: #fff;
  box-shadow: 0 30px 90px rgba(15, 23, 42, .28);
}

.modal-head,
.modal-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.modal-head {
  margin-bottom: 18px;
}

.modal-head h2 {
  margin: 4px 0;
}

.modal-head p {
  margin: 0;
  color: #64748b;
  word-break: break-all;
}

.icon-button {
  border: none;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: #f1f5f9;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.snapshot-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, .65fr);
  gap: 18px;
}

.form-main,
.form-side,
.field {
  display: grid;
  gap: 10px;
}

.field {
  margin-bottom: 14px;
  font-weight: 800;
}

.field.required > span::after {
  content: ' *';
  color: #b91c1c;
}

.choice-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.choice-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  font-weight: 700;
}

.image-preview-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 14px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  word-break: break-all;
}

.image-preview-line img {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  object-fit: cover;
}

.modal-actions {
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 980px) {
  .overview-grid,
  .probe-dashboard-card,
  .monitor-grid,
  .node-detail-grid,
  .snapshot-form-grid {
    grid-template-columns: 1fr !important;
  }

  .monitor-resizer {
    display: none;
  }

  .monitor-toolbar {
    position: static;
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
    max-width: none;
  }

  .page-header,
  .panel-head,
  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
