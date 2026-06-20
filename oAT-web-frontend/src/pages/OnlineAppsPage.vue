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
            <strong>{{ projectApps.length }}</strong>
            <span>项目应用数</span>
          </div>
        </div>
        <div class="toolbar-card" aria-label="在线实例筛选">
          <input v-model.trim="keyword" class="text-input" type="search" placeholder="搜索应用、工程、IP、PID、路径、Sandbox" aria-label="搜索在线实例" />
          <span>{{ filteredSessions.length }} 个匹配实例</span>
        </div>
      </div>

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
                        <div class="module-list">
                          <span
                            v-for="module in visibleModuleEntries(item)"
                            :key="module.name"
                            :class="['module-chip', moduleTone(module.state)]"
                          >
                            <strong>{{ module.name }}</strong>
                            <em>{{ module.state }}</em>
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import AppPagination from '@/components/AppPagination.vue'
import { useProjectStore } from '@/stores/project'
import type { AppSummary, OnlineSessionSummary } from '@/api/types'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = computed(() => projectStore.onlineSessionsByProjectId[projectId.value])
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
}

interface SandboxModuleEntry {
  name: string
  state: string
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

function moduleStats(status?: string) {
  const counts = new Map<string, number>()
  for (const module of sandboxModuleEntries(status)) {
    counts.set(module.state, (counts.get(module.state) || 0) + 1)
  }
  const order = ['ACTIVE', 'ERROR', 'FROZEN', 'LOADED', 'UNLOADED', 'UNKNOWN']
  return Array.from(counts.entries())
    .map(([state, count]) => ({ state, count }))
    .sort((left, right) => order.indexOf(left.state) - order.indexOf(right.state))
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
    await projectStore.loadOnlineSessions(projectId.value)
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

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}


.action-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: #0f172a;
  color: #fff;
  cursor: pointer;
}

.status-card,
.summary-card,
.toolbar-card,
.table-card,
.empty-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.runtime-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 220px)) minmax(420px, 1fr);
  align-items: stretch;
  gap: 14px;
  margin-bottom: 16px;
}

.summary-row {
  display: contents;
}

.summary-card {
  display: inline-flex;
  flex-direction: column;
  min-height: 112px;
}

.toolbar-card {
  position: sticky;
  top: 12px;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.text-input {
  width: 100%;
  max-width: 760px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
  background: #fff;
}

.summary-card strong {
  font-size: 28px;
}

.summary-card span {
  color: #64748b;
}

.table-card {
  max-height: min(620px, calc(100vh - 270px));
  overflow: auto;
}

.empty-card {
  margin-top: 12px;
  text-align: center;
  color: #64748b;
}

.empty-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 900;
}

.empty-text {
  margin-top: 6px;
}

.bootstrap-panel {
  display: grid;
  gap: 14px;
  margin-top: 16px;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.bootstrap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.bootstrap-header div,
.bootstrap-grid label {
  display: grid;
  gap: 6px;
}

.bootstrap-header strong {
  color: #0f172a;
  font-size: 18px;
}

.bootstrap-header span,
.bootstrap-grid span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.bootstrap-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 260px);
  gap: 12px;
}

.command-block {
  display: grid;
  gap: 8px;
}

.command-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: #334155;
  font-size: 13px;
  font-weight: 900;
}

.command-block code {
  display: block;
  padding: 12px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 11px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
}

.table th {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  white-space: nowrap;
}

.detail-button {
  border: none;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  cursor: pointer;
  font-weight: 800;
}

.detail-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-row td {
  background: #f8fafc;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid div {
  display: grid;
  gap: 5px;
}

.detail-grid .wide {
  grid-column: 1 / -1;
}

.detail-grid span {
  color: #64748b;
  font-size: 12px;
}

.detail-grid strong {
  color: #0f172a;
  word-break: break-word;
}

.status-text {
  white-space: pre-wrap;
}

.sandbox-badge,
.module-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}

.sandbox-badge {
  padding: 6px 9px;
}

.module-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.module-panel {
  display: grid;
  gap: 10px;
}

.module-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.module-stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  width: fit-content;
  border-radius: 8px;
  padding: 8px 10px;
}

.module-stat strong {
  color: inherit;
  font-size: 18px;
}

.module-stat span {
  color: inherit;
  font-size: 11px;
  font-weight: 800;
}

.module-chip {
  gap: 7px;
  padding: 7px 9px;
}

.inline-button {
  width: fit-content;
  border: none;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(15, 23, 42, 0.06);
  color: #475569;
  cursor: pointer;
  font-weight: 800;
}

.module-chip strong {
  color: inherit;
}

.module-chip em {
  font-style: normal;
  opacity: 0.78;
}

.tone-ok {
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
}

.tone-warn {
  background: rgba(217, 119, 6, 0.12);
  color: #b45309;
}

.tone-error {
  background: rgba(185, 28, 28, 0.1);
  color: #b91c1c;
}

.tone-muted {
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
}

@media (max-width: 760px) {
  .runtime-overview,
  .detail-grid,
  .bootstrap-grid {
    grid-template-columns: 1fr;
  }

  .summary-row {
    display: grid;
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .toolbar-card {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
