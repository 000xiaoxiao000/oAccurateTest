<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Compare & Reports</div>
        <h1>{{ center?.app.name || appId }}</h1>
        <p class="subtext">版本比对、制品上传、Git Commit 选择、覆盖率报告和历史比对报告集中管理。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/versions`">返回版本列表</RouterLink>
        <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/repository`">代码仓库配置</RouterLink>
      </div>
    </div>

    <div v-if="center && !repositoryConfigured" class="warning-card">
      当前应用尚未配置代码仓库，Git 比对、分支刷新和 Commit 选择不可用；制品比对仍可使用。
    </div>

    <div class="tab-row">
      <button type="button" :class="['tab-button', mode === 'package' && 'active']" @click="setMode('package')">制品比对</button>
      <button type="button" :class="['tab-button', mode === 'git' && 'active']" :disabled="!repositoryConfigured" @click="setMode('git')">Git 比对</button>
    </div>

    <form class="editor-card" @submit.prevent="submitCompare">
      <div v-if="mode === 'package'" class="grid-two">
        <label class="field">
          <span>旧版本文件</span>
          <select v-model="packageCompare.targetFile" class="text-input" @change="persistDraft">
            <option value="">请选择</option>
            <option v-for="item in packageOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <input class="hidden-input" ref="targetFileInput" type="file" accept=".jar,.war,.zip" @change="uploadPackageFile('target', $event)" />
          <div class="inline-actions">
            <button class="ghost-button small" type="button" :disabled="busy" @click="targetFileInput?.click()">上传旧包</button>
            <button class="danger-button small" type="button" :disabled="busy || !packageCompare.targetFile" @click="deleteSelectedPackage('target')">删除文件</button>
          </div>
        </label>
        <label class="field">
          <span>新版本文件</span>
          <select v-model="packageCompare.sourceFile" class="text-input" @change="persistDraft">
            <option value="">请选择</option>
            <option v-for="item in packageOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
          <input class="hidden-input" ref="sourceFileInput" type="file" accept=".jar,.war,.zip" @change="uploadPackageFile('source', $event)" />
          <div class="inline-actions">
            <button class="ghost-button small" type="button" :disabled="busy" @click="sourceFileInput?.click()">上传新包</button>
            <button class="danger-button small" type="button" :disabled="busy || !packageCompare.sourceFile" @click="deleteSelectedPackage('source')">删除文件</button>
          </div>
        </label>
      </div>

      <div v-else class="grid-two">
        <label class="field">
          <span>分支</span>
          <div class="input-action">
            <input
              v-model.trim="gitCompare.branch"
              class="text-input branch-input"
              type="text"
              list="versionCompareBranchOptions"
              placeholder="例如 master / release/1.2.x"
              :disabled="!repositoryConfigured"
              @input="persistDraft"
              @change="handleBranchChange"
            />
            <datalist id="versionCompareBranchOptions">
              <option v-for="branch in branchOptions" :key="branch" :value="branch" />
            </datalist>
            <button class="ghost-button small" type="button" :disabled="busy || !repositoryConfigured" @click="loadBranches()">刷新</button>
          </div>
          <div v-if="branchOptions.length" class="branch-suggestions" aria-label="分支建议">
            <button
              v-for="branch in branchOptions.slice(0, 8)"
              :key="branch"
              class="branch-chip"
              type="button"
              :class="{ active: branch === gitCompare.branch }"
              :disabled="!repositoryConfigured"
              @click="selectBranch(branch)"
            >
              {{ branch }}
            </button>
          </div>
          <small class="field-help">支持直接录入分支、tag 或 ref；下方建议来自应用当前分支、远端分支和已有版本记录。</small>
        </label>
        <label class="field">
          <span>旧 Commit</span>
          <div class="input-action">
            <input v-model.trim="gitCompare.oldCommit" class="text-input" type="text" :disabled="!repositoryConfigured" @input="persistDraft" />
            <button class="ghost-button small" type="button" :disabled="busy || !repositoryConfigured || !gitCompare.branch" @click="openCommitPicker('old')">选择</button>
          </div>
        </label>
        <label class="field">
          <span>新 Commit</span>
          <div class="input-action">
            <input v-model.trim="gitCompare.newCommit" class="text-input" type="text" :disabled="!repositoryConfigured" @input="persistDraft" />
            <button class="ghost-button small" type="button" :disabled="busy || !repositoryConfigured || !gitCompare.branch" @click="openCommitPicker('new')">选择</button>
            <button class="ghost-button small" type="button" :disabled="busy || !repositoryConfigured || !gitCompare.branch" @click="fillLatestCommit">最新</button>
          </div>
        </label>
      </div>

      <label class="field">
        <span>包范围</span>
        <input v-model.trim="packageName" class="text-input" type="text" placeholder="默认 *，例如 com.demo.order" @input="persistDraft" />
      </label>
      <p v-if="error" class="error-text">{{ error }}</p>
      <div class="action-row">
        <button class="primary-button" type="submit" :disabled="busy">{{ busy ? '处理中...' : '开始比对' }}</button>
        <button class="ghost-button" type="button" :disabled="busy" @click="restoreDraft">恢复上次填写</button>
        <button class="ghost-button" type="button" :disabled="busy" @click="clearDraft">清空填写</button>
      </div>
    </form>

    <section v-if="job" class="panel job-panel" aria-live="polite">
      <div class="panel-head job-head">
        <div>
          <h2>当前任务</h2>
          <p class="subtext">{{ job.name || (mode === 'git' ? 'Git 版本比对' : '制品版本比对') }}</p>
        </div>
        <div class="job-state-stack">
          <span :class="['job-state', job.error ? 'error' : job.finish ? 'done' : 'running']">{{ jobStateText }}</span>
          <strong>{{ jobProgress }}%</strong>
        </div>
      </div>

      <div class="progress-track" role="progressbar" :aria-valuenow="jobProgress" aria-valuemin="0" aria-valuemax="100">
        <span :style="{ width: `${jobProgress}%` }"></span>
      </div>

      <div class="job-summary-grid">
        <div class="job-summary-item">
          <span>当前阶段</span>
          <strong>{{ job.progressName || job.errorMessage || '-' }}</strong>
        </div>
        <div class="job-summary-item">
          <span>差异类</span>
          <strong>{{ classDiffCount }}</strong>
        </div>
        <div class="job-summary-item">
          <span>差异方法</span>
          <strong>{{ methodDiffCount }}</strong>
        </div>
        <div class="job-summary-item">
          <span>任务编号</span>
          <strong class="mono-text">{{ activeJobId || job.id }}</strong>
        </div>
      </div>

      <div v-if="job.error && job.errorMessage" class="job-error">{{ job.errorMessage }}</div>

      <div class="job-toolbar">
        <label class="check-inline"><input v-model="autoScrollLog" type="checkbox" /> 日志自动滚动</label>
        <button class="ghost-button small" type="button" :disabled="!activeJobId || jobPolling" @click="refreshJobOnce">刷新任务</button>
        <button class="ghost-button small" type="button" :disabled="!job.log" @click="copyJobLog">复制日志</button>
        <RouterLink v-if="job.finish && !job.error" class="primary-button small" :to="reportRoute">查看报告</RouterLink>
      </div>

      <div ref="jobLogRef" class="job-log" :class="{ empty: !jobLogLines.length }">
        <div v-if="!jobLogLines.length" class="log-placeholder">任务日志尚未输出，正在等待后端执行...</div>
        <article v-for="group in jobLogGroups" :key="group.name" class="job-log-group">
          <div class="job-log-group-head">
            <strong>{{ group.name }}</strong>
            <span>{{ group.lines.length }}</span>
          </div>
          <div class="job-log-group-body">
            <div v-for="(line, index) in group.lines" :key="`${group.name}-${index}-${line.raw}`" class="job-log-line">
              <span :class="['job-log-marker', line.type]"></span>
              <span class="job-log-time">{{ line.time }}</span>
              <span :class="['job-log-tag', line.type]">{{ jobLogTypeText(line.type) }}</span>
              <span class="job-log-text">{{ line.content }}</span>
            </div>
          </div>
        </article>
      </div>
      <p v-if="jobPollError" class="error-text">{{ jobPollError }}</p>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>比对报告</h2>
        <div class="panel-head-actions">
          <span class="report-count">{{ center?.compareReports.length || 0 }} 份</span>
          <button v-if="(center?.compareReports.length || 0) > reportPageSize" class="ghost-button small" type="button" @click="compareReportsCollapsed = !compareReportsCollapsed">
            {{ compareReportsCollapsed ? '展开' : '收起' }}
          </button>
        </div>
      </div>
      <div v-show="!compareReportsCollapsed" class="report-table-list">
        <article v-for="item in paginatedCompareReports" :key="item.id" class="report-row compare-row">
          <div class="report-main">
            <RouterLink class="report-title-link" :to="`/p/${projectId}/version/reports/${item.id}?appId=${appId}`">{{ item.name || item.id }}</RouterLink>
            <div class="record-meta">
              <span class="meta-chip">{{ item.gitBranch ? 'Git 比对' : '制品比对' }}</span>
              <span v-if="item.gitBranch" class="meta-chip">分支：{{ item.gitBranch }}</span>
              <span class="meta-chip old">旧：{{ shortText(item.gitOldCommit || item.sourceVersion) || '-' }}</span>
              <span class="meta-chip new">新：{{ shortText(item.gitNewCommit || item.targetVersion) || '-' }}</span>
            </div>
          </div>
          <div class="report-metrics">
            <span><b>{{ item.addClassCount + item.updateClassCount + item.deleteClassCount }}</b>类</span>
            <span><b>{{ item.addMethodCount + item.updateMethodCount + item.deleteMethodCount }}</b>方法</span>
            <span><b>{{ item.impactCaseCount }}</b>用例</span>
          </div>
          <div class="report-time">
            <strong>{{ item.createTimeRelativeText || '-' }}</strong>
            <small>{{ item.createTimeText || '-' }}</small>
          </div>
          <div class="row-actions">
            <RouterLink class="primary-button small" :to="`/p/${projectId}/version/reports/${item.id}?appId=${appId}`">查看</RouterLink>
            <button class="danger-button small" type="button" :disabled="busy" @click="removeCompareReport(item.id)">删除</button>
          </div>
        </article>
        <div v-if="!center?.compareReports.length" class="empty-card">暂无比对报告</div>
      </div>
      <AppPagination
        v-if="!compareReportsCollapsed && (center?.compareReports.length || 0) > reportPageSize"
        v-model:page="compareReportPage"
        v-model:page-size="reportPageSize"
        :total="center?.compareReports.length || 0"
        item-name="份比对报告"
      />
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>覆盖率报告</h2>
        <div class="panel-head-actions">
          <span class="report-count">{{ center?.coverageReports.length || 0 }} 份</span>
          <button v-if="(center?.coverageReports.length || 0) > coveragePageSize" class="ghost-button small" type="button" @click="coverageReportsCollapsed = !coverageReportsCollapsed">
            {{ coverageReportsCollapsed ? '展开' : '收起' }}
          </button>
        </div>
      </div>
      <div v-show="!coverageReportsCollapsed" class="report-table-list">
        <article v-for="item in paginatedCoverageReports" :key="item.id" class="report-row coverage-row">
          <div class="report-main">
            <RouterLink
              class="report-title-link"
              :to="{
                name: 'coverage-overview',
                params: { projectId, appId },
                query: { versionNumber: item.versionNumber, reportId: item.id, commitId: item.repoCommitId || undefined },
              }"
            >
              {{ item.versionNumber || '-' }}
            </RouterLink>
            <div class="record-meta">
              <span :class="['tag', item.reportType === 1 ? 'increment' : 'full']">
                {{ item.reportType === 1 ? '增量覆盖率' : '全量覆盖率' }}{{ item.hasNewerData ? ' · 需重算' : '' }}
              </span>
              <span v-if="item.repoBranch" class="meta-chip">分支：{{ item.repoBranch }}</span>
              <span v-if="item.repoCommitId" class="meta-chip">Commit：{{ shortText(item.repoCommitId) }}</span>
              <span v-if="item.baseVersionNumber" class="meta-chip old">基于：{{ item.baseVersionNumber }}</span>
            </div>
          </div>
          <div class="report-metrics coverage-metrics">
            <span><b>{{ coveragePercent(item.coveredClasses, item.totalClasses) }}</b>类</span>
            <span><b>{{ coveragePercent(item.coveredMethods, item.totalMethods) }}</b>方法</span>
            <span><b>{{ coveragePercent(item.coveredLines, item.totalLines) }}</b>行</span>
            <span><b>{{ item.snapshotCount || 0 }}</b>快照</span>
          </div>
          <div class="report-time">
            <strong>{{ item.createTimeRelativeText || '-' }}</strong>
            <small>{{ item.createTimeText || '-' }}</small>
          </div>
          <div class="row-actions">
            <RouterLink
              class="primary-button small"
              :to="{
                name: 'coverage-overview',
                params: { projectId, appId },
                query: { versionNumber: item.versionNumber, reportId: item.id, commitId: item.repoCommitId || undefined },
              }"
            >
              查看
            </RouterLink>
            <button class="danger-button small" type="button" :disabled="busy" @click="removeCoverage(item.id)">删除</button>
          </div>
        </article>
        <div v-if="!center?.coverageReports.length" class="empty-card">暂无覆盖率报告</div>
      </div>
      <AppPagination
        v-if="!coverageReportsCollapsed && (center?.coverageReports.length || 0) > coveragePageSize"
        v-model:page="coverageReportPage"
        v-model:page-size="coveragePageSize"
        :total="center?.coverageReports.length || 0"
        item-name="份覆盖率报告"
      />
    </section>

    <div v-if="commitPicker.visible" class="modal-mask" @click.self="closeCommitPicker">
      <section class="modal-card">
        <div class="panel-head">
          <h2>选择 {{ commitPicker.target === 'old' ? '旧' : '新' }} Commit</h2>
          <button class="text-danger" type="button" @click="closeCommitPicker">关闭</button>
        </div>
        <div class="input-action modal-tools">
          <input v-model.trim="commitPicker.keyword" class="text-input" type="search" placeholder="搜索 Commit、作者、说明" aria-label="搜索 Commit" />
          <label class="check-inline"><input v-model="commitPicker.onlySelectable" type="checkbox" /> 只看可选</label>
        </div>
        <div class="commit-list">
          <button v-for="item in paginatedCommits" :key="item.commitId" class="commit-item" type="button" @click="selectCommit(item.commitId)">
            <strong>{{ item.shortCommitId || item.commitId.slice(0, 10) }}</strong>
            <span>{{ item.message || '-' }}</span>
            <small>{{ item.author || '-' }}</small>
          </button>
          <div v-if="!filteredCommits.length" class="empty-card">未找到匹配 Commit</div>
        </div>
        <AppPagination
          v-if="filteredCommits.length > commitPageSize"
          v-model:page="commitPage"
          v-model:page-size="commitPageSize"
          :total="filteredCommits.length"
          item-name="个 Commit"
        />
      </section>
    </div>

    <div v-if="confirmDialog.visible" class="modal-mask" @click.self="cancelConfirm">
      <section class="modal-card confirm-card" role="dialog" aria-modal="true" aria-labelledby="versionConfirmTitle">
        <div class="confirm-icon">!</div>
        <div class="confirm-copy">
          <h2 id="versionConfirmTitle">{{ confirmDialog.title }}</h2>
          <p>{{ confirmDialog.message }}</p>
        </div>
        <div class="confirm-actions">
          <button class="ghost-button" type="button" @click="cancelConfirm">取消</button>
          <button :class="['danger-button', 'confirm-danger']" type="button" @click="acceptConfirm">确认删除</button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import {
  deleteCompareReport,
  deleteCoverageReport,
  deleteVersionFile,
  fetchCompareJob,
  fetchGitLatestCommit,
  fetchGitRecentCommits,
  fetchRepositoryBranches,
  fetchVersionCenter,
  startCompareJob,
  uploadResource,
} from '@/api/bootstrap'
import type { CompareJobSummary, GitCommitOption, VersionCenterPayload } from '@/api/types'
import AppPagination from '@/components/AppPagination.vue'

type PackageRole = 'source' | 'target'
type UploadedPackage = { value: string; label: string; uploaded: boolean }
type JobLogLine = { raw: string; time: string; content: string; type: string }
type JobLogGroup = { name: string; lines: JobLogLine[] }
type ConfirmDialogState = {
  visible: boolean
  title: string
  message: string
  resolve?: (confirmed: boolean) => void
}

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const initialJobId = computed(() => String(route.query.jobId || ''))
const storageKey = computed(() => `version-compare-draft:${projectId.value}:${appId.value}`)
const center = ref<VersionCenterPayload | null>(null)
const mode = ref<'package' | 'git'>('package')
const packageName = ref('')
const packageCompare = ref({ sourceFile: '', targetFile: '' })
const gitCompare = ref({ branch: '', oldCommit: '', newCommit: '' })
const uploadedPackages = ref<UploadedPackage[]>([])
const branches = ref<string[]>([])
const commits = ref<GitCommitOption[]>([])
const commitPicker = ref({ visible: false, target: 'old' as 'old' | 'new', keyword: '', onlySelectable: true })
const targetFileInput = ref<HTMLInputElement | null>(null)
const sourceFileInput = ref<HTMLInputElement | null>(null)
const jobLogRef = ref<HTMLElement | null>(null)
const job = ref<CompareJobSummary | null>(null)
const busy = ref(false)
const error = ref('')
const activeJobId = ref('')
const jobPolling = ref(false)
const jobPollError = ref('')
const autoScrollLog = ref(true)
const confirmDialog = ref<ConfirmDialogState>({ visible: false, title: '', message: '' })
const compareReportPage = ref(1)
const coverageReportPage = ref(1)
const reportPageSize = ref(10)
const coveragePageSize = ref(10)
const compareReportsCollapsed = ref(false)
const coverageReportsCollapsed = ref(false)
const commitPage = ref(1)
const commitPageSize = ref(12)
let pollTimer: number | undefined

const repositoryConfigured = computed(() => Boolean(center.value?.app.repoConfigured))
const currentAppBranch = computed(() => center.value?.app.currentBranch || '')
const branchOptions = computed(() => {
  const historyBranches = (center.value?.versions || [])
    .map((item) => item.repoBranch || '')
    .filter(Boolean)
  const ordered = [currentAppBranch.value, ...branches.value, ...historyBranches]
  return ordered.filter((branch, index, list) => branch && list.indexOf(branch) === index)
})
const packageOptions = computed(() => {
  const versionItems = (center.value?.packageVersions || []).map((item) => ({
    value: item.programFile || '',
    label: `${item.versionNumber} · ${item.programName || item.programFile || '-'}`,
    uploaded: false,
  })).filter((item) => item.value)
  const merged = [...uploadedPackages.value, ...versionItems]
  const seen = new Set<string>()
  return merged.filter((item) => {
    if (!item.value || seen.has(item.value)) return false
    seen.add(item.value)
    return true
  })
})
const filteredCommits = computed(() => {
  const needle = commitPicker.value.keyword.toLowerCase()
  return commits.value.filter((item) => {
    const selectable = !commitPicker.value.onlySelectable || Boolean(item.commitId)
    if (!selectable) return false
    if (!needle) return true
    return [item.commitId, item.shortCommitId, item.message, item.author].join(' ').toLowerCase().includes(needle)
  })
})
const paginatedCommits = computed(() => {
  const start = (commitPage.value - 1) * commitPageSize.value
  return filteredCommits.value.slice(start, start + commitPageSize.value)
})
const paginatedCompareReports = computed(() => {
  const reports = center.value?.compareReports || []
  const start = (compareReportPage.value - 1) * reportPageSize.value
  return reports.slice(start, start + reportPageSize.value)
})
const paginatedCoverageReports = computed(() => {
  const reports = center.value?.coverageReports || []
  const start = (coverageReportPage.value - 1) * coveragePageSize.value
  return reports.slice(start, start + coveragePageSize.value)
})
const jobProgress = computed(() => Math.max(0, Math.min(100, job.value?.progress || 0)))
const jobStateText = computed(() => {
  if (!job.value) return '未启动'
  if (job.value.error) return '失败'
  if (job.value.finish) return '已完成'
  return jobPolling.value ? '执行中' : '等待刷新'
})
const classDiffCount = computed(() => {
  const current = job.value
  if (!current) return 0
  return (current.addClassCount || 0) + (current.updateClassCount || 0) + (current.deleteClassCount || 0)
})
const methodDiffCount = computed(() => {
  const current = job.value
  if (!current) return 0
  return (current.addMethodCount || 0) + (current.updateMethodCount || 0) + (current.deleteMethodCount || 0)
})
const jobLogLines = computed(() => sanitizeJobLog(job.value?.log || ''))
const jobLogGroups = computed(() => groupJobLogLines(jobLogLines.value))
const reportRoute = computed(() => `/p/${projectId.value}/version/reports/${activeJobId.value || job.value?.id || ''}?appId=${appId.value}`)

watch(jobLogLines, async () => {
  if (!autoScrollLog.value) return
  await nextTick()
  if (jobLogRef.value) {
    jobLogRef.value.scrollTop = jobLogRef.value.scrollHeight
  }
})

watch([() => commitPicker.value.keyword, () => commitPicker.value.onlySelectable, commitPageSize], () => {
  commitPage.value = 1
})

watch(reportPageSize, () => {
  compareReportPage.value = 1
})

watch(coveragePageSize, () => {
  coverageReportPage.value = 1
})

async function load() {
  busy.value = true
  error.value = ''
  try {
    center.value = await fetchVersionCenter(projectId.value, appId.value)
    restoreDraft()
    if (repositoryConfigured.value) await loadBranches(false)
    if (initialJobId.value) {
      await startPollingJob(initialJobId.value)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载比对中心失败'
  } finally {
    busy.value = false
  }
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

function groupJobLogLines(lines: string[]) {
  const groups: JobLogGroup[] = []
  const groupMap = new Map<string, JobLogGroup>()
  for (const raw of lines) {
    const parsed = parseJobLogLine(raw)
    const groupName = detectJobLogGroup(parsed.content)
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

function parseJobLogLine(raw: string): JobLogLine {
  const match = raw.match(/^(\d{2}:\d{2}:\d{2})\s*(.*)$/)
  const content = match ? match[2] : raw
  return { raw, time: match ? match[1] : '日志', content, type: detectJobLogType(content) }
}

function detectJobLogType(line: string) {
  if (line.includes('新增')) return 'add'
  if (line.includes('修改')) return 'update'
  if (line.includes('删除')) return 'delete'
  if (line.includes('失败') || line.toLowerCase().includes('error')) return 'error'
  if (line.includes('比对完成') || line.includes('分析完成') || line.includes('报告已生成')) return 'done'
  if (line.includes('查找') || line.includes('检索') || line.includes('命中') || line.includes('影响')) return 'search'
  return 'default'
}

function detectJobLogGroup(line: string) {
  if (line.includes('发现 [新增]') || line.includes('发现 [修改]') || line.includes('发现 [删除]') || line.includes('新增方法')) return '变更发现'
  if (line.includes('比对完成') || line.includes('变更统计') || line.includes('当前应用快照数')) return '比对汇总'
  if (line.includes('开始分析用例影响') || line.includes('查找快照影响') || line.includes('查找影响用例') || line.includes('命中用例')) return '影响分析'
  return '运行日志'
}

function jobLogTypeText(type: string) {
  const labels: Record<string, string> = { add: '新增', update: '修改', delete: '删除', done: '完成', search: '分析', error: '错误', default: '日志' }
  return labels[type] || '日志'
}

function shortText(value?: string) {
  if (!value) return ''
  return value.length > 16 ? `${value.slice(0, 8)}...${value.slice(-6)}` : value
}

function coveragePercent(covered: number, total: number) {
  if (!total) return '0%'
  return `${Math.round((covered / total) * 100)}%`
}

function clearPollTimer() {
  if (pollTimer !== undefined) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

async function refreshJobOnce() {
  if (!activeJobId.value) return
  jobPollError.value = ''
  try {
    job.value = await fetchCompareJob(projectId.value, appId.value, activeJobId.value)
  } catch (err) {
    jobPollError.value = err instanceof Error ? err.message : '刷新任务状态失败'
  }
}

async function pollJobTick() {
  if (!activeJobId.value) return
  jobPolling.value = true
  await refreshJobOnce()
  const current = job.value
  if (current?.finish || current?.error) {
    jobPolling.value = false
    await loadReportsOnly()
    return
  }
  pollTimer = window.setTimeout(pollJobTick, 1500)
}

async function startPollingJob(jobId: string) {
  activeJobId.value = jobId
  clearPollTimer()
  await pollJobTick()
}

async function loadReportsOnly() {
  try {
    const latest = await fetchVersionCenter(projectId.value, appId.value)
    center.value = latest
  } catch {
    // Keep the visible job log even if refreshing report cards fails.
  }
}

async function copyJobLog() {
  if (!job.value?.log) return
  await navigator.clipboard?.writeText(jobLogLines.value.join('\n'))
}

function setMode(nextMode: 'package' | 'git') {
  if (nextMode === 'git' && !repositoryConfigured.value) return
  mode.value = nextMode
  persistDraft()
}

function persistDraft() {
  localStorage.setItem(storageKey.value, JSON.stringify({
    mode: mode.value,
    packageName: packageName.value,
    packageCompare: packageCompare.value,
    gitCompare: gitCompare.value,
    uploadedPackages: uploadedPackages.value,
  }))
}

function restoreDraft() {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (!raw) return
    const draft = JSON.parse(raw) as {
      mode?: 'package' | 'git'
      packageName?: string
      packageCompare?: { sourceFile?: string; targetFile?: string }
      gitCompare?: { branch?: string; oldCommit?: string; newCommit?: string }
      uploadedPackages?: UploadedPackage[]
    }
    mode.value = draft.mode === 'git' && repositoryConfigured.value ? 'git' : 'package'
    packageName.value = draft.packageName || ''
    packageCompare.value = { sourceFile: draft.packageCompare?.sourceFile || '', targetFile: draft.packageCompare?.targetFile || '' }
    gitCompare.value = {
      branch: currentAppBranch.value || draft.gitCompare?.branch || '',
      oldCommit: draft.gitCompare?.branch === (currentAppBranch.value || draft.gitCompare?.branch || '') ? draft.gitCompare?.oldCommit || '' : '',
      newCommit: draft.gitCompare?.branch === (currentAppBranch.value || draft.gitCompare?.branch || '') ? draft.gitCompare?.newCommit || '' : '',
    }
    uploadedPackages.value = draft.uploadedPackages || []
  } catch {
    clearDraft()
  }
}

function clearDraft() {
  packageName.value = ''
  packageCompare.value = { sourceFile: '', targetFile: '' }
  gitCompare.value = { branch: '', oldCommit: '', newCommit: '' }
  uploadedPackages.value = []
  localStorage.removeItem(storageKey.value)
}

async function uploadPackageFile(role: PackageRole, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  busy.value = true
  error.value = ''
  try {
    const path = await uploadResource(file)
    const item = { value: path, label: `上传 · ${file.name}`, uploaded: true }
    uploadedPackages.value = [item, ...uploadedPackages.value.filter((candidate) => candidate.value !== path)]
    if (role === 'source') packageCompare.value.sourceFile = path
    else packageCompare.value.targetFile = path
    persistDraft()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传制品失败'
  } finally {
    input.value = ''
    busy.value = false
  }
}

async function deleteSelectedPackage(role: PackageRole) {
  const filePath = role === 'source' ? packageCompare.value.sourceFile : packageCompare.value.targetFile
  if (!filePath) return
  const confirmed = await askConfirm('删除制品文件', `确认删除文件 ${filePath}？删除后本地缓存文件将不可恢复。`)
  if (!confirmed) return
  busy.value = true
  error.value = ''
  try {
    await deleteVersionFile(projectId.value, appId.value, filePath)
    uploadedPackages.value = uploadedPackages.value.filter((item) => item.value !== filePath)
    if (role === 'source') packageCompare.value.sourceFile = ''
    else packageCompare.value.targetFile = ''
    persistDraft()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除文件失败'
  } finally {
    busy.value = false
  }
}

async function loadBranches(showError = true) {
  if (!repositoryConfigured.value) return
  try {
    branches.value = await fetchRepositoryBranches(projectId.value, appId.value)
    if (!gitCompare.value.branch) {
      gitCompare.value.branch = currentAppBranch.value || branches.value[0] || ''
    }
    persistDraft()
  } catch (err) {
    if (showError) error.value = err instanceof Error ? err.message : '加载 Git 分支失败'
  }
}

function handleBranchChange() {
  gitCompare.value.oldCommit = ''
  gitCompare.value.newCommit = ''
  commits.value = []
  persistDraft()
}

function selectBranch(branch: string) {
  if (gitCompare.value.branch === branch) return
  gitCompare.value.branch = branch
  handleBranchChange()
}

async function openCommitPicker(target: 'old' | 'new') {
  if (!gitCompare.value.branch) {
    error.value = '请先选择 Git 分支'
    return
  }
  busy.value = true
  error.value = ''
  try {
    commits.value = await fetchGitRecentCommits(projectId.value, appId.value, gitCompare.value.branch, 80)
    commitPicker.value = { visible: true, target, keyword: '', onlySelectable: true }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 Commit 列表失败'
  } finally {
    busy.value = false
  }
}

function closeCommitPicker() {
  commitPicker.value.visible = false
}

function selectCommit(commitId: string) {
  if (commitPicker.value.target === 'old') gitCompare.value.oldCommit = commitId
  else gitCompare.value.newCommit = commitId
  persistDraft()
  closeCommitPicker()
}

async function fillLatestCommit() {
  if (!gitCompare.value.branch) return
  busy.value = true
  error.value = ''
  try {
    gitCompare.value.newCommit = await fetchGitLatestCommit(projectId.value, appId.value, gitCompare.value.branch)
    persistDraft()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '获取最新 Commit 失败'
  } finally {
    busy.value = false
  }
}

async function submitCompare() {
  busy.value = true
  error.value = ''
  jobPollError.value = ''
  try {
    if (mode.value === 'git' && !repositoryConfigured.value) throw new Error('当前应用未配置代码仓库')
    if (mode.value === 'git' && (!gitCompare.value.branch || !gitCompare.value.oldCommit || !gitCompare.value.newCommit)) {
      throw new Error('请完整填写 Git 分支、旧 Commit 和新 Commit')
    }
    if (mode.value === 'package' && (!packageCompare.value.sourceFile || !packageCompare.value.targetFile)) {
      throw new Error('请完整选择旧版本文件和新版本文件')
    }
    persistDraft()
    const payload =
      mode.value === 'git'
        ? {
            mode: 'git',
            branch: gitCompare.value.branch,
            oldCommit: gitCompare.value.oldCommit,
            newCommit: gitCompare.value.newCommit,
            packageName: packageName.value || undefined,
          }
        : {
            mode: 'package',
            sourceFile: packageCompare.value.sourceFile,
            targetFile: packageCompare.value.targetFile,
            packageName: packageName.value || undefined,
          }
    const result = await startCompareJob(projectId.value, appId.value, payload)
    job.value = result.job || null
    await startPollingJob(result.jobId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '启动比对失败'
  } finally {
    busy.value = false
  }
}

async function removeCompareReport(reportId: string) {
  const confirmed = await askConfirm('删除比对报告', '确认删除该比对报告？删除后历史比对结果和任务日志将不可恢复。')
  if (!confirmed) return
  busy.value = true
  error.value = ''
  try {
    await deleteCompareReport(projectId.value, appId.value, reportId)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除比对报告失败'
  } finally {
    busy.value = false
  }
}

async function removeCoverage(reportId: string) {
  const confirmed = await askConfirm('删除覆盖率报告', '确认删除该覆盖率报告？删除后需要重新生成才能查看。')
  if (!confirmed) return
  busy.value = true
  error.value = ''
  try {
    await deleteCoverageReport(projectId.value, appId.value, reportId)
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除覆盖率报告失败'
  } finally {
    busy.value = false
  }
}

function askConfirm(title: string, message: string) {
  return new Promise<boolean>((resolve) => {
    confirmDialog.value = { visible: true, title, message, resolve }
  })
}

function closeConfirm(confirmed: boolean) {
  const resolve = confirmDialog.value.resolve
  confirmDialog.value = { visible: false, title: '', message: '' }
  resolve?.(confirmed)
}

function cancelConfirm() {
  closeConfirm(false)
}

function acceptConfirm() {
  closeConfirm(true)
}

onBeforeUnmount(() => {
  clearPollTimer()
  if (confirmDialog.value.visible) {
    closeConfirm(false)
  }
})

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.tab-row,
.action-row,
.panel-head,
.card-top,
.meta-list,
.inline-actions,
.input-action,
.modal-tools,
.check-inline {
  display: flex;
  gap: 12px;
}

.page-header,
.panel-head,
.card-top {
  justify-content: space-between;
  align-items: center;
}

.panel-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.report-count {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(var(--oat-accent-rgb), .1);
  color: var(--oat-accent-hover);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.meta-list,
.inline-actions,
.input-action,
.modal-tools,
.check-inline {
  align-items: center;
  flex-wrap: wrap;
}

.page-header {
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.field span,
.meta-list,
.commit-item small {
  color: #64748b;
}

.secondary-link,
.table-link {
  color: #0f766e;
  font-weight: 700;
}

.tab-button,
.primary-button,
.ghost-button,
.danger-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  transition: transform .16s ease, box-shadow .16s ease, background .16s ease, color .16s ease;
}

.tab-button,
.ghost-button {
  background: rgba(15, 23, 42, 0.08);
}

.tab-button.active,
.primary-button {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  box-shadow: 0 12px 22px rgba(20, 184, 166, .22);
}

.danger-button {
  background: rgba(185, 28, 28, .12);
  color: #b91c1c;
}

.primary-button:hover:not(:disabled),
.tab-button.active:hover:not(:disabled) {
  background: linear-gradient(135deg, #0b5f59, #0f9f94);
  box-shadow: 0 14px 26px rgba(20, 184, 166, .30);
}

.ghost-button:hover:not(:disabled),
.tab-button:hover:not(:disabled) {
  background: rgba(15, 118, 110, .12);
  color: #0f766e;
}

.danger-button:hover:not(:disabled),
.text-danger:hover:not(:disabled) {
  background: #b91c1c;
  color: #fff;
  box-shadow: 0 12px 22px rgba(185, 28, 28, .18);
}

.primary-button:hover:not(:disabled),
.ghost-button:hover:not(:disabled),
.danger-button:hover:not(:disabled),
.tab-button:hover:not(:disabled),
.text-danger:hover:not(:disabled) {
  transform: translateY(-1px);
}

.small {
  padding: 7px 10px;
  font-size: 12px;
}

button:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.editor-card,
.panel,
.card,
.warning-card,
.empty-card,
.modal-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.warning-card {
  margin-bottom: 14px;
  background: rgba(245, 158, 11, .12);
  color: #92400e;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
}

.text-input {
  width: 100%;
  min-width: 180px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.branch-input {
  min-width: min(360px, 100%);
}

.branch-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.branch-chip {
  border: 1px solid rgba(37, 99, 235, .18);
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(37, 99, 235, .06);
  color: #1d4ed8;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
}

.branch-chip:hover:not(:disabled),
.branch-chip.active {
  border-color: rgba(15, 118, 110, .32);
  background: rgba(15, 118, 110, .12);
  color: #0f766e;
}

.field-help {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
}

.input-action .text-input {
  flex: 1;
}

.panel,
.action-row {
  margin-top: 18px;
  flex-wrap: wrap;
}

.job-panel {
  display: grid;
  gap: 14px;
}

.job-head {
  gap: 18px;
}

.job-state-stack {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.job-state-stack strong {
  min-width: 54px;
  color: #0f172a;
  font-size: 24px;
  text-align: right;
}

.job-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.job-state::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.job-state.running {
  background: rgba(37, 99, 235, .10);
  color: #1d4ed8;
}

.job-state.running::before {
  animation: pulse-dot 1s ease-in-out infinite;
}

.job-state.done {
  background: rgba(22, 163, 74, .12);
  color: #15803d;
}

.job-state.error {
  background: rgba(185, 28, 28, .12);
  color: #b91c1c;
}

.progress-track {
  overflow: hidden;
  height: 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, .08);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #0f766e, #14b8a6, #38bdf8);
  box-shadow: 0 8px 18px rgba(20, 184, 166, .24);
  transition: width .35s ease;
}

.job-summary-grid {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) repeat(3, minmax(120px, 1fr));
  gap: 10px;
}

.job-summary-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  background: rgba(248, 250, 252, .86);
}

.job-summary-item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.job-summary-item strong {
  overflow: hidden;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.job-error {
  padding: 10px 12px;
  border: 1px solid rgba(185, 28, 28, .18);
  border-radius: 14px;
  background: rgba(254, 242, 242, .82);
  color: #b91c1c;
  font-weight: 700;
}

.job-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.job-log {
  max-height: 300px;
  overflow: auto;
  padding: 14px;
  border: 1px solid rgba(15, 23, 42, .10);
  border-radius: 16px;
  background: #0b1220;
  color: #dbeafe;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .05);
}

.job-log.empty {
  display: grid;
  place-items: center;
  min-height: 128px;
}

.log-placeholder {
  color: #93a4bb;
}

.job-log-group {
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, .14);
  border-radius: 12px;
  background: rgba(255, 255, 255, .02);
}

.job-log-group + .job-log-group {
  margin-top: 12px;
}

.job-log-group-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  background: rgba(30, 41, 59, .88);
  color: #f8fafc;
}

.job-log-group-head span {
  color: #93c5fd;
  font-size: 12px;
}

.job-log-group-body {
  display: grid;
  padding: 6px 12px 8px;
}

.job-log-line {
  display: grid;
  grid-template-columns: 10px 64px auto 1fr;
  gap: 10px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(148, 163, 184, .14);
}

.job-log-line:last-child {
  border-bottom: none;
}

.job-log-marker {
  width: 10px;
  height: 10px;
  margin-top: 7px;
  border-radius: 999px;
  background: #64748b;
}

.job-log-marker.add { background: #22c55e; }
.job-log-marker.update { background: #f59e0b; }
.job-log-marker.delete { background: #ef4444; }
.job-log-marker.done { background: #38bdf8; }
.job-log-marker.search { background: #a78bfa; }
.job-log-marker.error { background: #ef4444; }

.job-log-time {
  color: #93c5fd;
}

.job-log-tag {
  width: fit-content;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.job-log-tag.add { background: rgba(34, 197, 94, .15); color: #86efac; }
.job-log-tag.update { background: rgba(245, 158, 11, .15); color: #fcd34d; }
.job-log-tag.delete,
.job-log-tag.error { background: rgba(239, 68, 68, .15); color: #fca5a5; }
.job-log-tag.done { background: rgba(56, 189, 248, .16); color: #7dd3fc; }
.job-log-tag.search { background: rgba(167, 139, 250, .16); color: #c4b5fd; }
.job-log-tag.default { background: rgba(100, 116, 139, .16); color: #cbd5e1; }

.job-log-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #e5eefc;
}

@keyframes pulse-dot {
  0%, 100% {
    opacity: .45;
    transform: scale(.9);
  }
  50% {
    opacity: 1;
    transform: scale(1.18);
  }
}

.report-table-list,
.commit-list {
  display: grid;
  gap: 12px;
}

.report-row {
  display: grid;
  grid-template-columns: minmax(260px, 1.5fr) minmax(260px, 1fr) minmax(128px, auto) auto;
  gap: 14px;
  align-items: center;
  padding: 14px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, .98), rgba(248, 250, 252, .92));
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease;
}

.report-row:hover {
  transform: translateY(-1px);
  border-color: rgba(15, 118, 110, .22);
  box-shadow: 0 12px 26px rgba(15, 23, 42, .08);
}

.report-main {
  min-width: 0;
}

.report-title-link {
  display: block;
  overflow: hidden;
  color: #172033;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-title-link:hover {
  color: #0f766e;
}

.record-meta,
.report-metrics,
.row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.record-meta {
  margin-top: 8px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 260px;
  overflow: hidden;
  padding: 4px 9px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 999px;
  background: rgba(248, 250, 252, .88);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-chip.old {
  border-color: rgba(234, 88, 12, .18);
  background: rgba(234, 88, 12, .08);
  color: #c2410c;
}

.meta-chip.new {
  border-color: rgba(33, 133, 208, .18);
  background: rgba(33, 133, 208, .08);
  color: #1f5f96;
}

.report-metrics span {
  display: grid;
  min-width: 62px;
  gap: 3px;
  justify-items: center;
  padding: 7px 9px;
  border-radius: 12px;
  background: rgba(15, 118, 110, .07);
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.report-metrics b {
  color: #172033;
  font-size: 17px;
}

.coverage-metrics span {
  background: rgba(33, 133, 208, .08);
  color: #1f5f96;
}

.report-time {
  display: grid;
  gap: 3px;
  color: #64748b;
  font-size: 12px;
  text-align: right;
}

.report-time strong {
  color: #172033;
}

.row-actions {
  justify-content: flex-end;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.tag.full {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.tag.increment {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.text-danger,
.error-text {
  color: #b91c1c;
}

.text-danger {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  font-weight: 700;
}

.hidden-input {
  display: none;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .44);
}

.modal-card {
  width: min(720px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
}

.confirm-card {
  width: min(460px, calc(100vw - 32px));
  display: grid;
  justify-items: center;
  gap: 14px;
  text-align: center;
  background:
    radial-gradient(circle at top, rgba(248, 113, 113, .14), transparent 38%),
    #fff;
}

.confirm-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 999px;
  background: rgba(185, 28, 28, .10);
  color: #b91c1c;
  font-size: 28px;
  font-weight: 900;
}

.confirm-copy h2 {
  margin: 0 0 8px;
  color: #172033;
}

.confirm-copy p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
  word-break: break-word;
}

.confirm-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  width: 100%;
}

.confirm-danger {
  min-width: 108px;
}

.commit-item {
  display: grid;
  grid-template-columns: 120px 1fr auto;
  gap: 12px;
  align-items: center;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  padding: 12px;
  background: #f8fbfb;
  text-align: left;
  cursor: pointer;
}

@media (max-width: 840px) {
  .grid-two,
  .commit-item,
  .job-summary-grid,
  .report-row {
    grid-template-columns: 1fr;
  }

  .report-time,
  .row-actions {
    justify-content: flex-start;
    text-align: left;
  }

  .job-head,
  .job-state-stack {
    align-items: flex-start;
  }

  .job-state-stack {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
