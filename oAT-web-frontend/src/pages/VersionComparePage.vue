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
            <select v-model="gitCompare.branch" class="text-input branch-select" :disabled="!repositoryConfigured" @change="handleBranchChange">
              <option value="">请选择分支</option>
              <option v-for="branch in branchOptions" :key="branch" :value="branch">{{ branch }}</option>
            </select>
            <button class="ghost-button small" type="button" :disabled="busy || !repositoryConfigured" @click="loadBranches()">刷新</button>
          </div>
          <small class="field-help">优先使用应用当前分支；切换分支会清空已选择的 Commit，避免跨分支误比对。</small>
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
        <div v-for="(line, index) in jobLogLines" :key="`${index}-${line}`" :class="['log-line', line.includes('error') || line.includes('失败') ? 'error' : '']">
          {{ line }}
        </div>
      </div>
      <p v-if="jobPollError" class="error-text">{{ jobPollError }}</p>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>比对报告</h2>
        <span>{{ center?.compareReports.length || 0 }}</span>
      </div>
      <div class="card-grid">
        <article v-for="item in center?.compareReports || []" :key="item.id" class="card">
          <div class="card-top">
            <strong>{{ item.name || item.id }}</strong>
            <span class="tag">{{ item.createTimeRelativeText || item.createTimeText || '-' }}</span>
          </div>
          <div class="meta-list">
            <span>源 {{ item.sourceVersion || item.gitNewCommit || '-' }}</span>
            <span>目标 {{ item.targetVersion || item.gitOldCommit || '-' }}</span>
            <span>类 {{ item.addClassCount + item.updateClassCount + item.deleteClassCount }}</span>
            <span>方法 {{ item.addMethodCount + item.updateMethodCount + item.deleteMethodCount }}</span>
            <span>用例 {{ item.impactCaseCount }}</span>
          </div>
          <div class="action-row">
            <RouterLink class="table-link" :to="`/p/${projectId}/version/reports/${item.id}?appId=${appId}`">查看报告</RouterLink>
            <button class="text-danger" type="button" :disabled="busy" @click="removeCompareReport(item.id)">删除</button>
          </div>
        </article>
        <div v-if="!center?.compareReports.length" class="empty-card">暂无比对报告</div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>覆盖率报告</h2>
        <span>{{ center?.coverageReports.length || 0 }}</span>
      </div>
      <div class="card-grid">
        <article v-for="item in center?.coverageReports || []" :key="item.id" class="card">
          <div class="card-top">
            <strong>{{ item.versionNumber || '-' }}</strong>
            <span :class="['tag', item.reportType === 1 ? 'increment' : 'full']">
              {{ item.reportType === 1 ? '增量' : '全量' }}{{ item.hasNewerData ? ' · 需重算' : '' }}
            </span>
          </div>
          <div class="meta-list">
            <span>类 {{ item.coveredClasses }} / {{ item.totalClasses }}</span>
            <span>方法 {{ item.coveredMethods }} / {{ item.totalMethods }}</span>
            <span>代码行 {{ item.coveredLines }} / {{ item.totalLines }}</span>
            <span>快照 {{ item.snapshotCount || 0 }}</span>
          </div>
          <div class="action-row">
            <RouterLink
              class="table-link"
              :to="{
                name: 'coverage-overview',
                params: { projectId, appId },
                query: { versionNumber: item.versionNumber, reportId: item.id, commitId: item.repoCommitId || undefined },
              }"
            >
              打开报告
            </RouterLink>
            <button class="text-danger" type="button" :disabled="busy" @click="removeCoverage(item.id)">删除</button>
          </div>
        </article>
        <div v-if="!center?.coverageReports.length" class="empty-card">暂无覆盖率报告</div>
      </div>
    </section>

    <div v-if="commitPicker.visible" class="modal-mask" @click.self="closeCommitPicker">
      <section class="modal-card">
        <div class="panel-head">
          <h2>选择 {{ commitPicker.target === 'old' ? '旧' : '新' }} Commit</h2>
          <button class="text-danger" type="button" @click="closeCommitPicker">关闭</button>
        </div>
        <div class="input-action modal-tools">
          <input v-model.trim="commitPicker.keyword" class="text-input" type="text" placeholder="搜索 Commit、作者、说明" />
          <label class="check-inline"><input v-model="commitPicker.onlySelectable" type="checkbox" /> 只看可选</label>
        </div>
        <div class="commit-list">
          <button v-for="item in filteredCommits" :key="item.commitId" class="commit-item" type="button" @click="selectCommit(item.commitId)">
            <strong>{{ item.shortCommitId || item.commitId.slice(0, 10) }}</strong>
            <span>{{ item.message || '-' }}</span>
            <small>{{ item.author || '-' }}</small>
          </button>
          <div v-if="!filteredCommits.length" class="empty-card">未找到匹配 Commit</div>
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

type PackageRole = 'source' | 'target'
type UploadedPackage = { value: string; label: string; uploaded: boolean }

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
let pollTimer: number | undefined

const repositoryConfigured = computed(() => Boolean(center.value?.app.repoConfigured))
const currentAppBranch = computed(() => center.value?.app.currentBranch || '')
const branchOptions = computed(() => {
  const ordered = [currentAppBranch.value, ...branches.value]
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
const reportRoute = computed(() => `/p/${projectId.value}/version/reports/${activeJobId.value || job.value?.id || ''}?appId=${appId.value}`)

watch(jobLogLines, async () => {
  if (!autoScrollLog.value) return
  await nextTick()
  if (jobLogRef.value) {
    jobLogRef.value.scrollTop = jobLogRef.value.scrollHeight
  }
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
  if (!filePath || !window.confirm(`确认删除文件 ${filePath}？`)) return
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
    if (!gitCompare.value.branch) gitCompare.value.branch = currentAppBranch.value || branches.value[0] || ''
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
  if (!window.confirm('确认删除该比对报告？')) return
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
  if (!window.confirm('确认删除该覆盖率报告？')) return
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

onBeforeUnmount(() => {
  clearPollTimer()
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
}

.tab-button,
.ghost-button {
  background: rgba(15, 23, 42, 0.08);
}

.tab-button.active,
.primary-button {
  background: #0f172a;
  color: #fff;
}

.danger-button {
  background: rgba(185, 28, 28, .1);
  color: #b91c1c;
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

.branch-select {
  min-width: min(360px, 100%);
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

.log-line {
  white-space: pre-wrap;
  word-break: break-word;
}

.log-line.error {
  color: #fecaca;
}

.log-placeholder {
  color: #93a4bb;
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

.card-grid,
.commit-list {
  display: grid;
  gap: 12px;
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
  .job-summary-grid {
    grid-template-columns: 1fr;
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
