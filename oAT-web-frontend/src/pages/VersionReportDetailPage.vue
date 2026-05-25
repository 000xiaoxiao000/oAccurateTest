<template>
  <section>
    <div class="page-header report-hero">
      <div class="hero-copy">
        <div class="eyebrow">Compare Report</div>
        <h1>{{ reportTitle }}</h1>
        <p class="subtext">{{ payload?.app?.name || '版本比对报告' }} · {{ payload?.report?.createTimeText || '生成中' }}</p>
        <div v-if="payload?.report" class="hero-tags">
          <span v-if="payload.report.gitBranch">分支：{{ payload.report.gitBranch }}</span>
          <span>{{ isGitReport ? 'Git 版本比对' : '制品包比对' }}</span>
          <span>{{ totalDiffCount }} 项变更</span>
          <span>影响用例 {{ displayUsecaseCount }}</span>
          <span>影响接口 {{ endpointCount }}</span>
        </div>
      </div>
      <div class="header-actions">
        <RouterLink v-if="appId" class="secondary-link" :to="`/p/${projectId}/apps/${appId}/compare`">返回版本比对</RouterLink>
      </div>
    </div>

    <div v-if="loading && !payload" class="status-card">正在加载比对报告...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <div v-else-if="payload?.state === 'pending'" class="pending-card">
      <span class="pending-dot"></span>
      <h2>比对报告生成中</h2>
      <p>{{ payload.retryMessage || '报告正在生成或索引刷新中，页面会自动重试。' }}</p>
      <small>第 {{ pendingRetryCount }} / {{ maxPendingRetries }} 次重试</small>
      <RouterLink v-if="appId && pendingRetryCount >= maxPendingRetries" class="primary-button" :to="`/p/${projectId}/apps/${appId}/compare`">返回比对中心</RouterLink>
    </div>

    <template v-else-if="payload?.report">
      <div class="hero-grid">
        <article class="hero-card accent-class">
          <span>类变更</span>
          <strong>{{ classDiffCount }}</strong>
          <small>新增 {{ payload.report.addClassCount }} / 修改 {{ payload.report.updateClassCount }} / 删除 {{ payload.report.deleteClassCount }}</small>
        </article>
        <article class="hero-card accent-method">
          <span>方法变更</span>
          <strong>{{ methodDiffCount }}</strong>
          <small>新增 {{ payload.report.addMethodCount }} / 修改 {{ payload.report.updateMethodCount }} / 删除 {{ payload.report.deleteMethodCount }}</small>
        </article>
        <article class="hero-card accent-usecase">
          <span>影响用例</span>
          <strong>{{ displayUsecaseCount }}</strong>
          <small>{{ impactHintText }}</small>
        </article>
        <article class="hero-card accent-endpoint">
          <span>影响接口</span>
          <strong>{{ endpointCount }}</strong>
          <small>基于接口扫描与变更类/方法匹配</small>
        </article>
      </div>

      <section class="panel info-panel">
        <div class="panel-head">
          <h2>报告元信息</h2>
        </div>
        <div class="info-grid">
          <div class="info-item wide"><span>旧版本</span><strong>{{ payload.report.targetVersion || '-' }}</strong></div>
          <div class="info-item wide"><span>新版本</span><strong>{{ payload.report.sourceVersion || '-' }}</strong></div>
          <div class="info-item"><span>分支</span><strong>{{ payload.report.gitBranch || '-' }}</strong></div>
          <div class="info-item"><span>旧 Commit</span><strong :title="payload.report.gitOldCommit">{{ shortText(payload.report.gitOldCommit) }}</strong></div>
          <div class="info-item"><span>新 Commit</span><strong :title="payload.report.gitNewCommit">{{ shortText(payload.report.gitNewCommit) }}</strong></div>
          <div class="info-item"><span>生成时间</span><strong>{{ payload.report.createTimeText || '-' }}</strong></div>
        </div>
      </section>

      <section class="panel toolbar-panel">
        <div>
          <h2>变更项</h2>
          <p class="subtext">按老前端的类/方法结构展示，支持搜索和新增、修改、删除筛选。</p>
        </div>
        <div class="filter-actions">
          <input v-model.trim="keyword" class="text-input" type="search" placeholder="搜索类名、方法名或描述" />
          <button v-for="mode in diffModes" :key="mode.value" :class="['filter-chip', diffMode === mode.value && 'active']" type="button" @click="diffMode = mode.value">{{ mode.label }}</button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>差异清单</h2>
          <span>{{ filteredDifferences.length }}</span>
        </div>
        <div class="diff-list compact-list">
          <article v-for="item in filteredDifferences" :key="item.className" class="diff-card" :class="[modelClass(item.model), selectedClass === item.className && 'selected']">
            <button class="diff-top" type="button" @click="toggleClass(item.className)">
              <span class="class-name"><i :class="modelIconClass(item.model)"></i>{{ item.className }}</span>
              <span :class="['tag', modelClass(item.model)]">{{ modelText(item.model) }}</span>
            </button>
            <div v-show="isClassOpen(item.className)" class="method-list compact-methods">
              <button v-for="method in item.methods" :key="`${method.methodName}-${method.methodDesc}`" class="method-item" :class="modelClass(method.model || item.model)" type="button" @click="selectedClass = item.className">
                <span><i :class="modelIconClass(method.model || item.model)"></i>{{ method.methodName }}</span>
                <small>{{ method.methodDesc ? `行: ${method.methodDesc}` : '方法明细' }}</small>
              </button>
              <div v-if="!item.methods.length" class="empty-inline">类级变更，暂无方法明细</div>
            </div>
          </article>
          <div v-if="!filteredDifferences.length" class="empty-card">暂无匹配差异</div>
        </div>
      </section>

      <section class="impact-grid">
        <div class="panel">
          <div class="panel-head"><h2>影响用例</h2><span>{{ displayUsecaseCount }}</span></div>
          <div v-if="usecaseDisplayMismatch" class="info-message">
            原始命中 {{ payload.report.impactCaseCount }} 条，当前展示 {{ payload.usecases?.length || 0 }} 条；已自动跳过已删除或不可访问的用例，不影响当前报告查看。
          </div>
          <div v-if="showImpactHints" class="impact-hint-card">
            <button class="impact-hint-toggle" type="button" @click="impactHintOpen = !impactHintOpen">
              <span><strong>当前未命中影响用例，点击查看排查建议</strong><small>常见原因是快照样本少，或现有快照未覆盖本次变更类/方法。</small></span>
              <span :class="['caret', impactHintOpen && 'open']">⌄</span>
            </button>
            <div v-if="impactHintOpen" class="impact-hint-body">
              <div v-if="payload.impactHints?.snapshotCount !== undefined" class="hint-line">当前应用快照数：{{ payload.impactHints.snapshotCount }}</div>
              <div v-if="payload.impactHints?.hitSnapshots?.length" class="hint-line">命中快照：{{ payload.impactHints.hitSnapshots.join('、') }}</div>
              <div v-if="payload.impactHints?.zeroHitClasses?.length" class="hint-line">未命中的类：{{ payload.impactHints.zeroHitClasses.join('、') }}</div>
              <div class="hint-line">先展开下方任务日志，查看每个变更类对应的快照数、命中快照和命中用例信息。</div>
              <div class="hint-line">优先补录本次改动相关入口，例如对应 Controller / Service 的真实调用链，再重新发起比对。</div>
            </div>
          </div>
          <div class="usecase-groups">
            <article v-for="group in usecaseGroups" :key="group.directory" class="usecase-group">
              <button class="group-title" type="button" @click="toggleUsecaseGroup(group.directory)">
                <span>▾ {{ group.directory }}</span>
                <small>{{ group.items.length }} 条</small>
              </button>
              <div v-show="openUsecaseGroups.has(group.directory)" class="impact-list">
                <article v-for="item in group.items" :key="item.id" class="impact-card usecase-card">
                  <div class="impact-top">
                    <RouterLink class="result-link" :to="`/p/${projectId}/usecases/${item.id}`">{{ item.title }}</RouterLink>
                    <button class="inline-link" type="button" @click="toggleUsecase(item.id)">影响点：{{ item.differences.length }}</button>
                  </div>
                  <div v-if="item.labels?.length" class="label-list">
                    <span v-for="label in item.labels" :key="label">{{ label }}</span>
                  </div>
                  <div v-show="openUsecases.has(item.id)" class="difference-points">
                    <span v-for="difference in item.differences" :key="difference">{{ difference }}</span>
                    <span v-if="!item.differences.length">暂无影响点明细</span>
                  </div>
                </article>
              </div>
            </article>
            <div v-if="!usecaseGroups.length" class="empty-card">未发现影响用例。</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head"><h2>影响接口</h2><span>{{ endpointCount }}</span></div>
          <div class="impact-list">
            <article v-for="endpoint in payload.endpoints || []" :key="endpoint.id || `${endpoint.endpointType}-${endpoint.url}-${endpoint.methodName}`" class="impact-card endpoint-card">
              <div class="impact-top endpoint-top">
                <span class="method-badge">{{ endpoint.httpMethod || endpoint.endpointType || 'API' }}</span>
                <strong>{{ endpoint.url || endpoint.methodName || '-' }}</strong>
              </div>
              <p>{{ endpoint.className || '-' }}{{ endpoint.methodName ? `#${endpoint.methodName}` : '' }}</p>
              <div class="endpoint-state">
                <span :class="['coverage-chip', endpoint.covered ? 'covered' : 'uncovered']">{{ endpoint.covered ? '已覆盖' : '未覆盖' }}</span>
                <span>命中 {{ endpoint.hitCount || 0 }}</span>
                <span v-if="endpoint.coverageStatus">{{ endpoint.coverageStatus }}</span>
              </div>
              <div class="meta-list">
                <span v-for="name in endpoint.matchedClasses" :key="name">{{ name }}</span>
                <span v-for="name in endpoint.matchedMethods" :key="`m-${name}`">{{ name }}</span>
              </div>
              <div v-if="endpoint.linkedUsecases?.length" class="linked-usecases">
                <strong>关联用例</strong>
                <RouterLink v-for="usecase in endpoint.linkedUsecases" :key="usecase.id" :to="`/p/${projectId}/usecases/${usecase.id}`">
                  {{ usecase.title || usecase.id }}<small v-if="usecase.directory">{{ usecase.directory }}</small>
                </RouterLink>
              </div>
              <div v-else class="linked-usecases muted">暂无接口关联用例</div>
            </article>
            <div v-if="!payload.endpoints?.length" class="empty-card">未匹配到影响接口。请先在应用中心完成接口扫描，或确认变更类与接口实现类一致。</div>
          </div>
        </div>
      </section>

      <section class="panel log-panel">
        <div class="panel-head">
          <div>
            <h2>比对日志</h2>
            <p class="subtext">按老前端控制台分组：变更发现、比对汇总、影响分析、运行日志。</p>
          </div>
          <div class="log-actions">
            <button class="ghost-button small" type="button" @click="logCollapsed = !logCollapsed">{{ logCollapsed ? '展开日志' : '收起日志' }}</button>
            <button class="ghost-button small" type="button" :disabled="!payload.report.jobLog" @click="copyLog">复制日志</button>
          </div>
        </div>
        <div v-show="!logCollapsed" class="report-log">
          <article v-for="group in logGroups" :key="group.name" class="log-group">
            <div class="log-group-head"><strong>{{ group.name }}</strong><span>{{ group.lines.length }}</span></div>
            <div class="log-group-body">
              <div v-for="(line, index) in group.lines" :key="`${group.name}-${index}-${line.raw}`" class="log-line">
                <span :class="['log-marker', line.type]"></span>
                <span class="log-time">{{ line.time }}</span>
                <span :class="['log-tag', line.type]">{{ logTypeText(line.type) }}</span>
                <span class="log-text">{{ line.content }}</span>
              </div>
            </div>
          </article>
          <div v-if="!logGroups.length" class="empty-inline">暂无任务日志</div>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchCompareReport } from '@/api/bootstrap'
import type { DifferenceGroupSummary, UsecaseImpactSummary, VersionReportDetailPayload } from '@/api/types'

type LogLine = { raw: string; time: string; content: string; type: string }
type LogGroup = { name: string; lines: LogLine[] }

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const reportId = computed(() => String(route.params.reportId || ''))
const appId = computed(() => String(route.query.appId || payload.value?.app?.id || ''))
const payload = ref<VersionReportDetailPayload | null>(null)
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const diffMode = ref('all')
const selectedClass = ref('')
const openClasses = ref(new Set<string>())
const openUsecaseGroups = ref(new Set<string>())
const openUsecases = ref(new Set<string>())
const impactHintOpen = ref(false)
const logCollapsed = ref(false)
const pendingRetryCount = ref(0)
const maxPendingRetries = 5
let pendingTimer: number | undefined

const diffModes = [
  { value: 'all', label: '全部' },
  { value: 'add', label: '新增' },
  { value: 'update', label: '修改' },
  { value: 'delete', label: '删除' },
]

const reportTitle = computed(() => compactReportTitle(payload.value?.report?.jobName || reportId.value))
const isGitReport = computed(() => Boolean(payload.value?.report?.gitBranch || payload.value?.report?.gitOldCommit || payload.value?.report?.gitNewCommit))
const classDiffCount = computed(() => {
  const report = payload.value?.report
  return report ? report.addClassCount + report.updateClassCount + report.deleteClassCount : 0
})
const methodDiffCount = computed(() => {
  const report = payload.value?.report
  return report ? report.addMethodCount + report.updateMethodCount + report.deleteMethodCount : 0
})
const totalDiffCount = computed(() => classDiffCount.value + methodDiffCount.value)
const endpointCount = computed(() => payload.value?.endpoints?.length || 0)
const displayUsecaseCount = computed(() => payload.value?.usecases?.length || payload.value?.report?.impactCaseCount || 0)
const usecaseDisplayMismatch = computed(() => {
  const reportCount = payload.value?.report?.impactCaseCount || 0
  const displayedCount = payload.value?.usecases?.length || 0
  return reportCount > 0 && displayedCount !== reportCount
})
const impactHintText = computed(() => {
  const hints = payload.value?.impactHints
  if (!hints) return '基于快照与用例关联分析'
  if (hints.snapshotCount !== undefined) return `扫描 ${hints.snapshotCount} 个快照`
  return '基于快照与用例关联分析'
})
const showImpactHints = computed(() => displayUsecaseCount.value === 0)
const filteredDifferences = computed(() => {
  const needle = keyword.value.toLowerCase()
  return (payload.value?.differences || []).filter((item) => {
    if (diffMode.value !== 'all' && item.model !== diffMode.value) return false
    if (!needle) return true
    return [item.className, item.model, ...item.methods.flatMap((method) => [method.methodName, method.methodDesc || ''])]
      .join(' ')
      .toLowerCase()
      .includes(needle)
  })
})
const usecaseGroups = computed(() => {
  const groups = new Map<string, UsecaseImpactSummary[]>()
  for (const item of payload.value?.usecases || []) {
    const directory = item.directoryPath || 'ROOT'
    const list = groups.get(directory) || []
    list.push(item)
    groups.set(directory, list)
  }
  return Array.from(groups.entries()).map(([directory, items]) => ({ directory, items }))
})
const jobLogLines = computed(() => sanitizeJobLog(payload.value?.report?.jobLog || ''))
const logGroups = computed(() => groupLogLines(jobLogLines.value))

watch(() => payload.value?.differences, (differences) => {
  openClasses.value = new Set((differences || []).slice(0, 12).map((item) => item.className).filter(Boolean))
}, { immediate: true })

watch(usecaseGroups, (groups) => {
  openUsecaseGroups.value = new Set(groups.map((group) => group.directory))
}, { immediate: true })

async function load() {
  loading.value = true
  error.value = ''
  clearPendingTimer()
  try {
    payload.value = await fetchCompareReport(projectId.value, reportId.value)
    if (payload.value.state === 'pending' && pendingRetryCount.value < maxPendingRetries) {
      pendingRetryCount.value += 1
      pendingTimer = window.setTimeout(load, 1200)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载比对报告失败'
  } finally {
    loading.value = false
  }
}

function compactReportTitle(value: string) {
  return value
    .replace(/git-([^\s/]{8})[^\s/]*\.zip/g, 'git-$1...zip')
    .replace(/([a-f0-9]{10})[a-f0-9]{20,}/gi, '$1...')
}

function shortText(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}

function modelText(model?: string) {
  if (model === 'add') return '新增'
  if (model === 'delete') return '删除'
  return '修改'
}

function modelClass(model?: string) {
  return model === 'add' ? 'model-add' : model === 'delete' ? 'model-delete' : 'model-update'
}

function modelIconClass(model?: string) {
  return model === 'add' ? 'icon-add' : model === 'delete' ? 'icon-delete' : 'icon-update'
}

function isClassOpen(className: string) {
  return openClasses.value.has(className)
}

function toggleClass(className: string) {
  selectedClass.value = className
  const next = new Set(openClasses.value)
  if (next.has(className)) next.delete(className)
  else next.add(className)
  openClasses.value = next
}

function toggleUsecaseGroup(directory: string) {
  const next = new Set(openUsecaseGroups.value)
  if (next.has(directory)) next.delete(directory)
  else next.add(directory)
  openUsecaseGroups.value = next
}

function toggleUsecase(id: string) {
  const next = new Set(openUsecases.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  openUsecases.value = next
}

function sanitizeJobLog(rawLog: string) {
  if (!rawLog) return []
  return rawLog
    .replace(/<em\s+class=['"]logger\s+error['"]>/g, '')
    .replace(/<\/em>/g, '')
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter(Boolean)
}

function groupLogLines(lines: string[]) {
  const groups: LogGroup[] = []
  const groupMap = new Map<string, LogGroup>()
  for (const raw of lines) {
    const parsed = parseLogLine(raw)
    const groupName = detectLogGroup(parsed.content)
    let group = groupMap.get(groupName)
    if (!group) {
      group = { name: groupName, lines: [] }
      groupMap.set(groupName, group)
      groups.push(group)
    }
    group.lines.push(parsed)
  }
  return groups
}

function parseLogLine(raw: string): LogLine {
  const match = raw.match(/^(\d{2}:\d{2}:\d{2})\s*(.*)$/)
  const content = match ? match[2] : raw
  return { raw, time: match ? match[1] : '日志', content, type: detectLogType(content) }
}

function detectLogType(line: string) {
  if (line.includes('新增')) return 'add'
  if (line.includes('修改')) return 'update'
  if (line.includes('删除')) return 'delete'
  if (line.includes('失败') || line.toLowerCase().includes('error')) return 'error'
  if (line.includes('比对完成') || line.includes('分析完成') || line.includes('报告已生成')) return 'done'
  if (line.includes('查找') || line.includes('检索') || line.includes('命中') || line.includes('影响')) return 'search'
  return 'default'
}

function detectLogGroup(line: string) {
  if (line.includes('发现 [新增]') || line.includes('发现 [修改]') || line.includes('发现 [删除]') || line.includes('新增方法')) return '变更发现'
  if (line.includes('比对完成') || line.includes('变更统计') || line.includes('当前应用快照数')) return '比对汇总'
  if (line.includes('开始分析用例影响') || line.includes('查找快照影响') || line.includes('查找影响用例') || line.includes('命中用例')) return '影响分析'
  return '运行日志'
}

function logTypeText(type: string) {
  const labels: Record<string, string> = { add: '新增', update: '修改', delete: '删除', done: '完成', search: '分析', error: '错误', default: '日志' }
  return labels[type] || '日志'
}

async function copyLog() {
  await navigator.clipboard?.writeText(jobLogLines.value.join('\n'))
}

function clearPendingTimer() {
  if (pendingTimer !== undefined) {
    window.clearTimeout(pendingTimer)
    pendingTimer = undefined
  }
}

onMounted(load)
onBeforeUnmount(clearPendingTimer)
</script>

<style scoped>
.page-header,
.header-actions,
.panel-head,
.impact-top,
.filter-actions,
.hero-tags,
.log-actions,
.endpoint-state {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header,
.panel-head,
.impact-top {
  justify-content: space-between;
}

.report-hero {
  margin-bottom: 20px;
}

.hero-copy {
  min-width: 0;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .12em;
  text-transform: uppercase;
}

.page-header h1 {
  max-width: 980px;
  word-break: break-word;
}

.subtext,
.info-item span,
.meta-list,
.method-item small,
.hero-card small,
.impact-hint-toggle small {
  color: #64748b;
}

.hero-tags,
.filter-actions,
.meta-list,
.method-list,
.endpoint-state,
.log-actions {
  flex-wrap: wrap;
}

.hero-tags span,
.tag,
.method-badge,
.filter-chip,
.coverage-chip,
.log-tag {
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.hero-tags span {
  padding: 5px 10px;
  background: rgba(15, 118, 110, .10);
  color: #0f766e;
}

.secondary-link,
.result-link,
.inline-link {
  color: #0f766e;
  font-weight: 800;
}

.status-card,
.pending-card,
.hero-card,
.panel,
.diff-card,
.impact-card,
.impact-hint-card,
.usecase-group {
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 20px;
  background: rgba(255, 255, 255, .96);
}

.status-card.error {
  color: #b91c1c;
}

.pending-card {
  min-height: 320px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  text-align: center;
}

.pending-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #2185d0;
  animation: pending-pulse 1.4s infinite;
}

.hero-grid,
.info-grid,
.diff-list,
.impact-list,
.impact-grid,
.usecase-groups {
  display: grid;
  gap: 14px;
}

.hero-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.hero-card {
  position: relative;
  overflow: hidden;
}

.hero-card::after {
  content: '';
  position: absolute;
  right: -28px;
  bottom: -34px;
  width: 98px;
  height: 98px;
  border-radius: 999px;
  background: rgba(15, 118, 110, .08);
}

.hero-card strong,
.info-item strong {
  display: block;
  margin-top: 6px;
}

.hero-card strong {
  color: #111827;
  font-size: 30px;
}

.accent-class { border-color: rgba(37, 99, 235, .20); }
.accent-method { border-color: rgba(15, 118, 110, .22); }
.accent-usecase { border-color: rgba(234, 88, 12, .20); }
.accent-endpoint { border-color: rgba(124, 58, 237, .20); }

.panel {
  margin-top: 18px;
}

.info-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.info-item.wide {
  grid-column: span 2;
}

.info-item strong {
  overflow-wrap: anywhere;
}

.toolbar-panel {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.text-input {
  min-width: min(320px, 100%);
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 14px;
  padding: 10px 12px;
}

.filter-chip,
.ghost-button,
.primary-button,
.inline-link,
.group-title,
.diff-top {
  border: none;
  cursor: pointer;
  transition: transform .16s ease, box-shadow .16s ease, background .16s ease, color .16s ease;
}

.filter-chip,
.ghost-button {
  padding: 9px 13px;
  background: rgba(15, 23, 42, .08);
  color: #334155;
}

.filter-chip.active,
.filter-chip:hover,
.primary-button {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  box-shadow: 0 10px 22px rgba(20, 184, 166, .22);
}

.ghost-button:hover:not(:disabled),
.inline-link:hover,
.group-title:hover,
.diff-top:hover {
  background: rgba(15, 118, 110, .10);
  color: #0f766e;
}

.filter-chip:hover,
.ghost-button:hover:not(:disabled),
.primary-button:hover,
.inline-link:hover {
  transform: translateY(-1px);
}

.compact-list {
  gap: 10px;
}

.diff-card,
.impact-card,
.usecase-group {
  box-shadow: none;
}

.diff-card.selected {
  background: linear-gradient(135deg, rgba(15, 118, 110, .07), #fff);
}

.diff-card.model-add { border-left: 4px solid #16a34a; }
.diff-card.model-update { border-left: 4px solid #0f766e; }
.diff-card.model-delete { border-left: 4px solid #dc2626; }

.diff-top {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 14px;
  padding: 4px;
  background: transparent;
  text-align: left;
}

.class-name,
.method-item span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  word-break: break-word;
}

.icon-add,
.icon-update,
.icon-delete {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex: 0 0 auto;
}

.icon-add { background: #16a34a; }
.icon-update { background: #f59e0b; }
.icon-delete { background: #dc2626; }

.method-list,
.meta-list {
  display: flex;
  gap: 9px;
  margin-top: 12px;
}

.method-item {
  display: grid;
  gap: 3px;
  border: none;
  border-radius: 12px;
  padding: 9px 11px;
  background: #f4f8f8;
  color: #172033;
  text-align: left;
}

.method-item:hover {
  background: rgba(15, 118, 110, .12);
  color: #0f766e;
}

.tag {
  padding: 5px 10px;
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
}

.tag.model-add { background: rgba(22, 163, 74, .12); color: #15803d; }
.tag.model-update { background: rgba(15, 118, 110, .10); color: #0f766e; }
.tag.model-delete { background: rgba(220, 38, 38, .10); color: #b91c1c; }

.impact-grid {
  grid-template-columns: minmax(0, 1.08fr) minmax(0, .92fr);
}

.impact-card p {
  margin: 10px 0 0;
  color: #475569;
  word-break: break-word;
}

.impact-hint-card {
  margin-bottom: 14px;
  background: rgba(245, 158, 11, .10);
}

.info-message {
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid rgba(33, 133, 208, .16);
  border-radius: 14px;
  background: rgba(33, 133, 208, .08);
  color: #1f5f96;
  font-size: 13px;
  line-height: 1.7;
}

.impact-hint-toggle,
.group-title {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 14px;
  background: transparent;
  text-align: left;
}

.impact-hint-toggle span:first-child {
  display: grid;
  gap: 4px;
}

.impact-hint-body {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  color: #475569;
}

.hint-line {
  padding-left: 14px;
  border-left: 3px solid rgba(245, 158, 11, .28);
}

.caret.open {
  transform: rotate(180deg);
}

.usecase-group {
  padding: 12px;
}

.group-title {
  padding: 8px 10px;
  color: #172033;
  font-weight: 900;
}

.group-title small {
  color: #64748b;
}

.difference-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.difference-points span,
.label-list span,
.meta-list span,
.endpoint-state span {
  padding: 6px 9px;
  border-radius: 10px;
  background: rgba(15, 23, 42, .05);
  color: #475569;
  font-size: 12px;
}

.label-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.label-list span {
  background: rgba(33, 133, 208, .09);
  color: #1f5f96;
  font-weight: 800;
}

.inline-link {
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(15, 118, 110, .08);
}

.method-badge {
  flex: 0 0 auto;
  padding: 6px 10px;
  background: #0f172a;
  color: #fff;
}

.endpoint-top {
  align-items: flex-start;
}

.endpoint-top strong {
  word-break: break-word;
}

.coverage-chip.covered {
  background: rgba(22, 163, 74, .12);
  color: #15803d;
}

.coverage-chip.uncovered {
  background: rgba(220, 38, 38, .10);
  color: #b91c1c;
}

.linked-usecases {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: #475569;
}

.linked-usecases strong {
  color: #172033;
  font-size: 12px;
}

.linked-usecases a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.linked-usecases a:hover {
  background: #0f766e;
  color: #fff;
}

.linked-usecases small {
  color: inherit;
  opacity: .72;
}

.linked-usecases.muted {
  color: #94a3b8;
  font-size: 12px;
}

.empty-card,
.empty-inline {
  color: #94a3b8;
}

.report-log {
  max-height: 520px;
  overflow: auto;
  padding: 14px;
  border-radius: 16px;
  background: #0b1220;
  color: #dbeafe;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
}

.log-group {
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, .14);
  border-radius: 12px;
  background: rgba(255, 255, 255, .02);
}

.log-group + .log-group {
  margin-top: 12px;
}

.log-group-head {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  background: rgba(30, 41, 59, .88);
}

.log-group-body {
  display: grid;
  padding: 6px 12px 8px;
}

.log-line {
  display: grid;
  grid-template-columns: 10px 64px auto 1fr;
  gap: 10px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(148, 163, 184, .14);
}

.log-line:last-child {
  border-bottom: none;
}

.log-marker {
  width: 10px;
  height: 10px;
  margin-top: 7px;
  border-radius: 999px;
  background: #64748b;
}

.log-marker.add { background: #22c55e; }
.log-marker.update { background: #f59e0b; }
.log-marker.delete { background: #ef4444; }
.log-marker.done { background: #38bdf8; }
.log-marker.search { background: #a78bfa; }
.log-marker.error { background: #ef4444; }

.log-time {
  color: #93c5fd;
}

.log-tag {
  width: fit-content;
  padding: 1px 8px;
}

.log-tag.add { background: rgba(34, 197, 94, .15); color: #86efac; }
.log-tag.update { background: rgba(245, 158, 11, .15); color: #fcd34d; }
.log-tag.delete,
.log-tag.error { background: rgba(239, 68, 68, .15); color: #fca5a5; }
.log-tag.done { background: rgba(56, 189, 248, .16); color: #7dd3fc; }
.log-tag.search { background: rgba(167, 139, 250, .16); color: #c4b5fd; }
.log-tag.default { background: rgba(100, 116, 139, .16); color: #cbd5e1; }

.log-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #e5eefc;
}

.ghost-button.small {
  border-radius: 999px;
  padding: 8px 12px;
}

@keyframes pending-pulse {
  0% { box-shadow: 0 0 0 0 rgba(33, 133, 208, .45); }
  70% { box-shadow: 0 0 0 14px rgba(33, 133, 208, 0); }
  100% { box-shadow: 0 0 0 0 rgba(33, 133, 208, 0); }
}

@media (max-width: 980px) {
  .hero-grid,
  .info-grid,
  .impact-grid {
    grid-template-columns: 1fr;
  }

  .info-item.wide {
    grid-column: auto;
  }

  .toolbar-panel,
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .log-line {
    grid-template-columns: 10px 54px 1fr;
  }

  .log-tag {
    grid-column: 3;
    grid-row: 1;
    justify-self: start;
    margin-left: 50px;
  }

  .log-text {
    grid-column: 1 / -1;
  }
}
</style>
