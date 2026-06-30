<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Runtime</div>
        <h1>在线应用</h1>
      </div>
      <button class="action-button" type="button" @click="reload">刷新</button>
    </div>

    <div v-if="loading" class="status-card">正在加载在线实例...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="runtime-overview">
        <div class="summary-row">
          <div class="summary-card">
            <strong>{{ payload.total }}</strong>
            <span>当前在线实例数</span>
          </div>
          <div class="summary-card">
            <strong>{{ collectorPayload?.total || 0 }}</strong>
            <span>探针状态数</span>
          </div>
          <div class="summary-card">
            <strong>{{ healthyCollectorCount }}</strong>
            <span>健康探针状态</span>
          </div>
          <div class="summary-card">
            <strong>{{ projectApps.length }}</strong>
            <span>项目应用数</span>
          </div>
        </div>
        <div class="toolbar-card" aria-label="在线实例筛选">
          <input v-model.trim="keyword" class="text-input" type="search" placeholder="搜索应用、工程、IP、PID、路径、Sandbox" aria-label="搜索在线实例" />
          <span>{{ filteredSessions.length }} 个匹配实例</span>
        </div>
      </div>

      <section class="table-card collector-card">
        <div class="card-title">
          <div>
            <h2>探针健康度</h2>
            <p class="subtext">统一按探针状态展示在线、静默和最近活跃时间。</p>
          </div>
        </div>
        <table class="table">
          <thead>
            <tr>
              <th>应用</th>
              <th>语言</th>
              <th>探针类型</th>
              <th>健康度</th>
              <th>最近活跃</th>
              <th>来源</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="source in filteredCollectorSources" :key="source.sourceId || `${source.appId}-${source.language}`">
              <td>{{ source.appName || source.appId || '-' }}</td>
              <td>{{ source.language || '-' }}</td>
              <td>{{ source.collectorType === 'RESIDENT' ? '常驻' : '批量' }}</td>
              <td><span :class="['health-badge', healthTone(source.health)]">{{ healthLabel(source.health) }}</span></td>
              <td>{{ formatLastSeen(source.lastSeenTime) }}</td>
              <td>{{ source.addressIp || source.sessionId || source.sourceId || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="!filteredCollectorSources.length" class="empty-card">暂无探针状态</div>
      </section>

      <div class="table-card">
        <table class="table">
          <thead>
            <tr>
              <th>系统 IP</th>
              <th>应用名称</th>
              <th>工程名称</th>
              <th>Agent 版本</th>
              <th>在线时长</th>
              <th>Sandbox</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in paginatedSessions" :key="sessionKey(item)">
              <tr>
                <td>{{ item.addressIp || '-' }}</td>
                <td>{{ item.appName || '未定义' }}</td>
                <td>{{ item.projectSrcName || '-' }}</td>
                <td>{{ item.agentVersion || '-' }}</td>
                <td>{{ item.onlineTime || '-' }}</td>
                <td>
                  <span :class="['sandbox-badge', sandboxSummary(item).tone]">{{ sandboxSummary(item).label }}</span>
                  <span v-if="pendingCommand(item)" class="pending-hint">{{ pendingCommandLabel(pendingCommand(item)!.command) }}</span>
                </td>
                <td>
                  <div class="row-actions">
                    <button class="detail-button" type="button" @click="toggleDetail(item)">详情</button>
                    <button class="detail-button" type="button" :disabled="sandboxCommandDisabled(item)" @click="runSandboxCommand(item, 'start')">
                      {{ pendingCommand(item)?.command === 'start' ? '启用中...' : '启用 Sandbox' }}
                    </button>
                    <button class="detail-button" type="button" :disabled="sandboxCommandDisabled(item)" @click="runSandboxCommand(item, 'stop')">
                      {{ pendingCommand(item)?.command === 'stop' ? '停用中...' : '停用 Sandbox' }}
                    </button>
                    <button class="detail-button" type="button" :disabled="sandboxCommandDisabled(item)" @click="runSandboxCommand(item, 'restart')">
                      {{ pendingCommand(item)?.command === 'restart' ? '重启中...' : '重启 Sandbox' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="expandedKey === sessionKey(item)" class="detail-row">
                <td colspan="7">
                  <div class="detail-grid">
                    <div><span>部署路径</span><strong>{{ item.systemDir || '-' }}</strong></div>
                    <div><span>进程ID</span><strong>{{ item.pid || '-' }}</strong></div>
                    <div><span>JVM版本</span><strong>{{ item.jvmVersion || '-' }}</strong></div>
                    <div><span>Sandbox 启动方式</span><strong>{{ parseSandboxStatus(item.sandboxStatus)?.startMode || '-' }}</strong></div>
                    <div><span>Sandbox 版本</span><strong>{{ parseSandboxStatus(item.sandboxStatus)?.sandboxVersion || '-' }}</strong></div>
                    <div><span>模块总览</span><strong>{{ sandboxSummary(item).label }}</strong></div>
                    <div class="wide">
                      <span>Sandbox 模块</span>
                      <div v-if="sandboxModuleEntries(item.sandboxStatus).length" class="module-panel">
                        <div class="module-stats">
                          <div v-for="stat in moduleStats(item.sandboxStatus)" :key="stat.state" :class="['module-stat', moduleTone(stat.state)]">
                            <strong>{{ stat.count }}</strong>
                            <span>{{ stat.state }}</span>
                          </div>
                        </div>
                        <div class="module-list-wrap">
                          <div class="module-list">
                            <span
                              v-for="module in visibleModuleEntries(item)"
                              :key="module.name"
                              :class="['module-chip', moduleTone(module.state)]"
                            >
                              <span class="module-chip-main">
                                <strong>{{ module.name }}</strong>
                                <em>{{ module.state }}</em>
                              </span>
                              <span
                                :class="['module-hit', moduleEnhancementInfo(item.sandboxStatus, module).hit ? 'hit' : 'miss']"
                                :title="moduleEnhancementInfo(item.sandboxStatus, module).title"
                              >
                                {{ moduleEnhancementInfo(item.sandboxStatus, module).label }}
                              </span>
                            </span>
                          </div>
                          <button
                            v-if="sandboxModuleEntries(item.sandboxStatus).length > modulePreviewLimit"
                            class="inline-button"
                            type="button"
                            @click="toggleModules(item)"
                          >
                            {{ expandedModulesKey === sessionKey(item) ? '收起模块' : `查看全部 ${sandboxModuleEntries(item.sandboxStatus).length} 个模块` }}
                          </button>
                        </div>
                      </div>
                      <strong v-else class="status-text">{{ formatSandboxStatus(item.sandboxStatus) }}</strong>
                    </div>
                    <div class="wide"><span>JVM启动参数</span><strong>{{ item.jvmOption || '-' }}</strong></div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <div v-if="!filteredSessions.length" class="empty-card">
          <template v-if="payload.total === 0">
            <div class="empty-title">暂无在线实例</div>
            <div class="empty-text">需要先在被测应用 JVM 中加载 agent，应用登录成功后这里才会出现可启停的 Sandbox 实例。</div>
          </template>
          <template v-else>
            暂无匹配结果
          </template>
        </div>
      </div>
      <div v-if="payload.total === 0" class="bootstrap-panel">
        <div class="bootstrap-header">
          <div>
            <strong>接入 Sandbox</strong>
            <span>选择应用并在目标机器执行命令</span>
          </div>
          <button class="inline-button" type="button" @click="reload">刷新在线状态</button>
        </div>
        <div class="bootstrap-grid">
          <label>
            <span>应用 appKey</span>
            <select v-model="selectedBootstrapAppId" class="text-input">
              <option v-for="app in projectApps" :key="app.id" :value="app.id">
                {{ app.name || app.srcName || app.id }}
              </option>
            </select>
          </label>
          <label>
            <span>目标 JVM PID</span>
            <input v-model.trim="bootstrapPid" class="text-input" type="text" placeholder="例如 40075" />
          </label>
        </div>
        <div class="command-block">
          <div class="command-title">
            <span>Attach 启动 Sandbox</span>
            <button class="inline-button" type="button" :disabled="!attachBootstrapCommand" @click="copyText(attachBootstrapCommand, 'attach')">
              {{ copiedKey === 'attach' ? '已复制' : '复制' }}
            </button>
          </div>
          <code>{{ attachBootstrapCommand || '填写目标 JVM PID 后生成 attach 命令' }}</code>
        </div>
        <div class="command-block">
          <div class="command-title">
            <span>应用启动时加载 agent</span>
            <button class="inline-button" type="button" @click="copyText(javaagentBootstrapArgument, 'javaagent')">
              {{ copiedKey === 'javaagent' ? '已复制' : '复制' }}
            </button>
          </div>
          <code>{{ javaagentBootstrapArgument }}</code>
        </div>
      </div>
      <AppPagination
        v-if="filteredSessions.length > 0"
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :total="filteredSessions.length"
        item-name="实例"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
import { useProjectStore } from '@/stores/project'
import type { AppSummary, CollectorSourceSummary, OnlineSessionSummary } from '@/api/types'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = computed(() => projectStore.onlineSessionsByProjectId[projectId.value])
const collectorPayload = computed(() => projectStore.collectorSourcesByProjectId[projectId.value])
const projectContext = computed(() => projectStore.contextByProjectId[projectId.value])
const projectApps = computed<AppSummary[]>(() => projectContext.value?.apps || [])
const appId = computed(() => String(route.query.appId || ''))
const expandedKey = ref('')
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const pendingCommands = ref<Record<string, PendingCommand>>({})
const expandedModulesKey = ref('')
const selectedBootstrapAppId = ref('')
const bootstrapPid = ref('')
const copiedKey = ref('')
const modulePreviewLimit = 6
const commandPollIntervalMs = 2000
const commandPollTimeoutMs = 45000
const onlineRefreshIntervalMs = 5000
let onlineRefreshTimer: number | undefined

type SandboxCommand = 'start' | 'stop' | 'restart' | 'status'

interface PendingCommand {
  command: SandboxCommand
  startedAt: number
  previousSignature: string
}

interface SandboxStatusPayload {
  sessionId?: string
  startMode?: string
  sandboxVersion?: string
  modules?: Record<string, unknown>
  moduleEnhancements?: Record<string, SandboxModuleEnhancement>
  bootstrapEnhancements?: SandboxBootstrapEnhancement[]
}

interface SandboxModuleEntry {
  name: string
  state: string
}

interface SandboxModuleEnhancement {
  enhancedClassCount?: number
  enhancedMethodCount?: number
  sampleMethods?: string[]
}

interface SandboxBootstrapEnhancement {
  moduleId?: string
  className?: string
  methodName?: string
  state?: string
  errorMessage?: string
}

const filteredSessions = computed(() => {
  if (!payload.value) return []
  const term = keyword.value.toLowerCase()
  return payload.value.sessions.filter((item) => {
    const matchesApp = !appId.value || item.appId === appId.value || item.appName === appId.value
    const matchesKeyword = !term || [
      item.addressIp,
      item.appName,
      item.projectSrcName,
      item.agentVersion,
      item.onlineTime,
      item.systemDir,
      item.pid,
      item.jvmVersion,
      item.sandboxStatus,
    ].some((value) => String(value || '').toLowerCase().includes(term))
    return matchesApp && matchesKeyword
  })
})

const filteredCollectorSources = computed(() => {
  const sources = collectorPayload.value?.sources || []
  const term = keyword.value.toLowerCase()
  return sources.filter((source) => {
    const matchesApp = !appId.value || source.appId === appId.value || source.appName === appId.value
    const matchesKeyword = !term || [
      source.appName,
      source.appId,
      source.language,
      source.collectorType,
      source.health,
      source.addressIp,
      source.pid,
      source.agentVersion,
      source.sandboxStatus,
    ].some((value) => String(value || '').toLowerCase().includes(term))
    return matchesApp && matchesKeyword
  })
})

const healthyCollectorCount = computed(() =>
  (collectorPayload.value?.sources || []).filter((source) => source.health === 'ONLINE').length,
)

const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredSessions.value.slice(start, start + pageSize.value)
})

const selectedBootstrapApp = computed(() => projectApps.value.find((app) => app.id === selectedBootstrapAppId.value) || projectApps.value[0])
const selectedBootstrapAppKey = computed(() => selectedBootstrapApp.value?.id || appId.value || '')
const sandboxAgentArgs = computed(() => [
  'action=start',
  `appKey=${selectedBootstrapAppKey.value || '<appKey>'}`,
  'server=127.0.0.1:8899',
  'sandbox.http-servlet.enabled=true',
  'sandbox.coverage.enabled=true',
  'codeStack.include=<your.package.*>',
].join(','))
const attachBootstrapCommand = computed(() => {
  if (!bootstrapPid.value) return ''
  return `java -jar /path/to/oAT-agent-core-1.0-SNAPSHOT.jar ${bootstrapPid.value} "${sandboxAgentArgs.value}"`
})
const javaagentBootstrapArgument = computed(() =>
  `-javaagent:/path/to/oAT-agent-core-1.0-SNAPSHOT.jar=${sandboxAgentArgs.value.replace('action=start,', '')}`,
)

function sessionKey(item: OnlineSessionSummary) {
  if (item.sessionId) return item.sessionId
  return `${item.addressIp || ''}-${item.pid || ''}-${item.systemDir || ''}-${item.appName || ''}`
}

function toggleDetail(item: OnlineSessionSummary) {
  const key = sessionKey(item)
  expandedKey.value = expandedKey.value === key ? '' : key
  expandedModulesKey.value = ''
}

function toggleModules(item: OnlineSessionSummary) {
  const key = sessionKey(item)
  expandedModulesKey.value = expandedModulesKey.value === key ? '' : key
}

async function runSandboxCommand(item: OnlineSessionSummary, command: SandboxCommand) {
  if (!projectId.value || !item.sessionId) return
  const key = sessionKey(item)
  if (pendingCommands.value[key]) return
  pendingCommands.value = {
    ...pendingCommands.value,
    [key]: {
      command,
      startedAt: Date.now(),
      previousSignature: sandboxStatusSignature(item),
    },
  }
  error.value = ''
  try {
    await projectStore.controlSandbox(projectId.value, item.sessionId, command)
    await waitSandboxCommandResult(key)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Sandbox 命令下发失败'
  } finally {
    clearPendingCommand(key)
  }
}

function pendingCommand(item: OnlineSessionSummary) {
  return pendingCommands.value[sessionKey(item)]
}

function sandboxCommandDisabled(item: OnlineSessionSummary) {
  return !item.sessionId || Boolean(pendingCommand(item))
}

function pendingCommandLabel(command: SandboxCommand) {
  if (command === 'start') return '等待启用生效'
  if (command === 'stop') return '等待停用生效'
  if (command === 'restart') return '等待重启生效'
  return '等待状态同步'
}

async function waitSandboxCommandResult(key: string) {
  const pending = pendingCommands.value[key]
  if (!pending) return
  const deadline = pending.startedAt + commandPollTimeoutMs
  while (Date.now() < deadline) {
    await delay(commandPollIntervalMs)
    await load({ silent: true })
    const current = findSessionByKey(key)
    if (!current) return
    if (isSandboxCommandApplied(current, pending)) {
      return
    }
  }
}

function isSandboxCommandApplied(item: OnlineSessionSummary, pending: PendingCommand) {
  const signature = sandboxStatusSignature(item)
  if (pending.command === 'stop') {
    const modules = sandboxModuleEntries(item.sandboxStatus)
    return modules.length > 0 && modules.every((module) => module.state === 'UNLOADED' || module.state === 'FROZEN')
  }
  if (pending.command === 'start') {
    return sandboxModuleEntries(item.sandboxStatus).some((module) => module.state === 'ACTIVE')
  }
  if (pending.command === 'restart') {
    return signature !== pending.previousSignature
  }
  return signature !== pending.previousSignature
}

function sandboxStatusSignature(item: OnlineSessionSummary) {
  return `${item.sandboxStatus || ''}|${item.onlineTime || ''}`
}

function findSessionByKey(key: string) {
  return payload.value?.sessions.find((item) => sessionKey(item) === key)
}

function clearPendingCommand(key: string) {
  const next = { ...pendingCommands.value }
  delete next[key]
  pendingCommands.value = next
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function copyText(text: string, key: string) {
  if (!text) return
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
  } else {
    window.prompt('复制命令', text)
  }
  copiedKey.value = key
  window.setTimeout(() => {
    if (copiedKey.value === key) copiedKey.value = ''
  }, 1500)
}

function parseSandboxStatus(status?: string): SandboxStatusPayload | null {
  if (!status) return null
  try {
    const parsed = JSON.parse(status)
    return parsed && typeof parsed === 'object' ? parsed as SandboxStatusPayload : null
  } catch {
    return null
  }
}

function sandboxModuleEntries(status?: string): SandboxModuleEntry[] {
  const parsed = parseSandboxStatus(status)
  if (!parsed?.modules) return []
  return Object.entries(parsed.modules)
    .map(([name, state]) => ({ name, state: normalizeModuleState(state) }))
    .sort((left, right) => left.name.localeCompare(right.name))
}

function visibleModuleEntries(item: OnlineSessionSummary) {
  const modules = sandboxModuleEntries(item.sandboxStatus)
  if (expandedModulesKey.value === sessionKey(item)) return modules
  const preferred = ['http-servlet', 'coverage', 'service', 'thread-pool', 'jdbc', 'redis']
  const selected = preferred
    .map((name) => modules.find((module) => module.name === name))
    .filter((module): module is SandboxModuleEntry => Boolean(module))
  for (const module of modules) {
    if (selected.length >= modulePreviewLimit) break
    if (!selected.some((item) => item.name === module.name)) {
      selected.push(module)
    }
  }
  return selected
}

function moduleEnhancementInfo(status: string | undefined, module: SandboxModuleEntry) {
  const moduleName = module.name
  if (module.state === 'SKIPPED') {
    return { hit: false, label: '已跳过', title: '模块配置不满足加载条件，未注册增强目标' }
  }
  const parsed = parseSandboxStatus(status)
  const bootstrapHits = (parsed?.bootstrapEnhancements || []).filter((item) => item.moduleId === moduleName && item.state === 'ACTIVE')
  if (bootstrapHits.length) {
    const samples = bootstrapHits
      .slice(0, 8)
      .map((item) => `${item.className || '-'}#${item.methodName || '-'}`)
    return { hit: true, label: `命中 ${bootstrapHits.length} 个增强点`, title: samples.join('\n') }
  }
  if (moduleName === 'system-log') {
    return { hit: true, label: '无需目标命中', title: 'SystemLog 模块安装 System.out/System.err 代理，不依赖业务类目标命中' }
  }
  if (!parsed?.moduleEnhancements) {
    return { hit: false, label: '未上报命中', title: '当前 agent 未上报模块实际增强记录' }
  }
  const enhancement = parsed.moduleEnhancements[moduleName]
  const classCount = Number(enhancement?.enhancedClassCount || 0)
  const methodCount = Number(enhancement?.enhancedMethodCount || 0)
  if (classCount <= 0 && methodCount <= 0) {
    return { hit: false, label: '未命中目标', title: '未发现被测 JVM 中加载并增强了该模块匹配的类/方法' }
  }
  const samples = Array.isArray(enhancement?.sampleMethods) ? enhancement.sampleMethods.slice(0, 8) : []
  const title = samples.length ? samples.join('\n') : `增强类 ${classCount} 个，方法 ${methodCount} 个`
  if (methodCount <= 0) {
    return { hit: true, label: `命中 ${classCount} 类`, title }
  }
  return { hit: true, label: `命中 ${classCount} 类/${methodCount} 方法`, title }
}

function moduleStats(status?: string) {
  const counts = new Map<string, number>()
  for (const module of sandboxModuleEntries(status)) {
    counts.set(module.state, (counts.get(module.state) || 0) + 1)
  }
  const order = ['ACTIVE', 'ERROR', 'SKIPPED', 'FROZEN', 'LOADED', 'UNLOADED', 'UNKNOWN']
  return Array.from(counts.entries())
    .map(([state, count]) => ({ state, count }))
    .sort((left, right) => moduleStateOrder(left.state, order) - moduleStateOrder(right.state, order))
}

function moduleStateOrder(state: string, order: string[]) {
  const index = order.indexOf(state)
  return index >= 0 ? index : order.length
}

function normalizeModuleState(state: unknown) {
  if (typeof state === 'string') return state
  if (state && typeof state === 'object') {
    const record = state as Record<string, unknown>
    const value = record.name || record.state || record.value || record['@value']
    if (typeof value === 'string') return value
  }
  return 'UNKNOWN'
}

function sandboxSummary(item: OnlineSessionSummary) {
  if (!item.sandboxStatus) {
    return { label: '未上报', tone: 'tone-muted' }
  }
  const modules = sandboxModuleEntries(item.sandboxStatus)
  if (!modules.length) {
    return parseSandboxStatus(item.sandboxStatus)
      ? { label: '已上报', tone: 'tone-ok' }
      : { label: '原始状态', tone: 'tone-warn' }
  }
  const errorCount = modules.filter((module) => module.state === 'ERROR').length
  if (errorCount > 0) {
    return { label: `异常 ${errorCount}/${modules.length}`, tone: 'tone-error' }
  }
  const activeCount = modules.filter((module) => module.state === 'ACTIVE').length
  return { label: `ACTIVE ${activeCount}/${modules.length}`, tone: activeCount === modules.length ? 'tone-ok' : 'tone-warn' }
}

function moduleTone(state: string) {
  if (state === 'ACTIVE') return 'tone-ok'
  if (state === 'ERROR') return 'tone-error'
  if (state === 'LOADED' || state === 'FROZEN') return 'tone-warn'
  return 'tone-muted'
}

function formatSandboxStatus(status?: string) {
  if (!status) return '-'
  try {
    return JSON.stringify(JSON.parse(status), null, 2)
  } catch {
    return status
  }
}

function healthLabel(health?: CollectorSourceSummary['health']) {
  if (health === 'ONLINE') return '在线'
  if (health === 'SILENT') return '静默'
  if (health === 'OFFLINE') return '离线'
  return '未知'
}

function healthTone(health?: CollectorSourceSummary['health']) {
  if (health === 'ONLINE') return 'tone-ok'
  if (health === 'SILENT') return 'tone-warn'
  if (health === 'OFFLINE') return 'tone-error'
  return 'tone-muted'
}

function formatLastSeen(value?: number) {
  if (!value) return '-'
  const diff = Math.max(0, Date.now() - value)
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return `${Math.floor(diff / 86_400_000)} 天前`
}

async function load(options: { silent?: boolean } = {}) {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  if (!options.silent) {
    loading.value = true
    error.value = ''
  }
  try {
    if (!projectContext.value) {
      await projectStore.loadProjectContext(projectId.value)
    }
    await Promise.all([
      projectStore.loadOnlineSessions(projectId.value),
      projectStore.loadCollectorSources(projectId.value),
    ])
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载在线实例失败'
  } finally {
    if (!options.silent) {
      loading.value = false
    }
  }
}

function reload() {
  load()
}

watch([keyword, appId], () => {
  currentPage.value = 1
})

watch(projectApps, (apps) => {
  if (!selectedBootstrapAppId.value && apps.length) {
    selectedBootstrapAppId.value = appId.value && apps.some((app) => app.id === appId.value) ? appId.value : apps[0].id
  }
}, { immediate: true })

watch(pageSize, () => {
  currentPage.value = 1
})

watch(() => filteredSessions.value.length, (total) => {
  const totalPages = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > totalPages) {
    currentPage.value = totalPages
  }
})

onMounted(() => {
  load()
  onlineRefreshTimer = window.setInterval(() => {
    if (!loading.value) {
      load({ silent: true })
    }
  }, onlineRefreshIntervalMs)
})

onUnmounted(() => {
  if (onlineRefreshTimer !== undefined) {
    window.clearInterval(onlineRefreshTimer)
    onlineRefreshTimer = undefined
  }
})
</script>

<style scoped src="@/features/admin/styles/online-apps-page.css"></style>
