<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Create Version</div>
        <h1>{{ center?.app.name || appId }}</h1>
        <p class="subtext">支持 Git 拉取与文件上传两种方式创建新版本。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/versions`">返回版本列表</RouterLink>
      </div>
    </div>

    <div v-if="center && !center.app.repoConfigured" class="warning-card">
      当前应用尚未配置代码仓库，Git 拉取不可用；请先进入代码仓库配置，或切换到文件上传。
    </div>

    <div class="tab-row">
      <button type="button" :class="['tab-button', sourceType === 'git' && 'active']" :disabled="!center?.app.repoConfigured" @click="sourceType = 'git'">Git 拉取</button>
      <button type="button" :class="['tab-button', sourceType === 'upload' && 'active']" @click="sourceType = 'upload'">文件上传</button>
    </div>

    <form class="editor-card" @submit.prevent="submit">
      <div class="grid-two">
        <label class="field">
          <span>版本号</span>
          <input v-model.trim="form.versionNumber" class="text-input" type="text" />
        </label>
        <label class="field">
          <span>设为当前版本</span>
          <select v-model="form.setAsCurrent" class="text-input">
            <option value="">否</option>
            <option value="on">是</option>
          </select>
        </label>
        <label class="field wide">
          <span>版本描述</span>
          <textarea v-model.trim="form.describe" class="text-area" rows="3" />
        </label>
      </div>

      <template v-if="sourceType === 'git'">
        <div class="grid-two">
          <label class="field">
            <span>分支</span>
            <input v-model.trim="form.repoBranch" class="text-input" list="branch-options" type="text" @change="fetchLatestCommitForBranch" />
            <datalist id="branch-options">
              <option v-for="branch in branches" :key="branch" :value="branch" />
            </datalist>
          </label>
          <label class="field">
            <span>Commit ID</span>
            <input v-model.trim="form.repoCommitId" class="text-input" type="text" />
          </label>
          <label class="field wide">
            <span>排除路径</span>
            <input v-model.trim="excludePaths" class="text-input" type="text" placeholder="多个路径用逗号分隔" />
          </label>
        </div>
        <div class="action-row">
          <button class="ghost-button" type="button" :disabled="busy || !center?.app.repoConfigured" @click="loadBranches">刷新分支</button>
          <button class="ghost-button" type="button" :disabled="busy || !center?.app.repoConfigured || !form.repoBranch" @click="fetchLatestCommitForBranch">获取最新 Commit</button>
          <button class="ghost-button" type="button" :disabled="busy || !center?.app.repoConfigured || !form.repoBranch" @click="checkGit">检测可拉取性</button>
          <button class="ghost-button" type="button" :disabled="busy || !center?.app.repoConfigured || !form.repoBranch" @click="pullGit">执行拉取</button>
          <button class="ghost-button" type="button" :disabled="busy || !gitPulledPath" @click="removePulledCode">删除拉取文件</button>
        </div>
        <div v-if="gitEstimate" class="panel">
          <div class="panel-head">
            <h2>拉取预估</h2>
          </div>
          <div class="meta-list">
            <span>分支 {{ gitEstimate.branch || '-' }}</span>
            <span>Commit {{ gitEstimate.commitId || '-' }}</span>
            <span>时长 {{ formatDuration(gitEstimate.estimatedDurationMs) }}</span>
            <span>包大小 {{ formatBytes(gitEstimate.estimatedPackageSizeBytes) }}</span>
          </div>
          <p v-if="gitEstimate.packageCommitVerify?.unavailableReason" class="subtext">
            {{ gitEstimate.packageCommitVerify.unavailableReason }}
          </p>
        </div>
        <div v-if="gitJob" class="panel">
          <div class="panel-head">
            <h2>拉取任务</h2>
            <span>{{ gitJob.progress }}%</span>
          </div>
          <p class="subtext">{{ gitJob.progressName || gitJob.message || '-' }}</p>
        </div>
      </template>

      <template v-else>
        <label class="field">
          <span>程序文件</span>
          <input class="text-input" type="file" @change="onFileChange" />
        </label>
        <div class="action-row">
          <button class="ghost-button" type="button" :disabled="busy || !selectedFile" @click="uploadFile">上传文件</button>
        </div>
      </template>

      <div class="panel">
        <div class="panel-head">
          <h2>已选择程序文件</h2>
        </div>
        <p class="subtext">{{ uploadedPath || gitPulledPath || '尚未准备程序文件' }}</p>
      </div>

      <p v-if="notice" class="notice-text">{{ notice }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <div class="action-row">
        <button class="primary-button" type="submit" :disabled="busy">{{ busy ? '处理中...' : '创建版本' }}</button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import {
  createVersion,
  deleteGitCode,
  fetchGitPullEstimate,
  fetchGitLatestCommit,
  fetchGitPullStatus,
  fetchRepositoryBranches,
  fetchVersionCenter,
  startGitPull,
  uploadResource,
} from '@/api/bootstrap'
import type { GitJobSummary, GitPullEstimate, VersionCenterPayload } from '@/api/types'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const center = ref<VersionCenterPayload | null>(null)
const sourceType = ref<'git' | 'upload'>('git')
const branches = ref<string[]>([])
const selectedFile = ref<File | null>(null)
const uploadedPath = ref('')
const gitPulledPath = ref('')
const excludePaths = ref('')
const gitEstimate = ref<GitPullEstimate | null>(null)
const gitJob = ref<GitJobSummary | null>(null)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const form = ref({
  versionNumber: '',
  describe: '',
  setAsCurrent: '',
  repoBranch: '',
  repoCommitId: '',
})

function formatBytes(value?: number) {
  if (!value) return '-'
  if (value > 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MB`
  if (value > 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${value} B`
}

function formatDuration(value?: number) {
  if (!value) return '-'
  return `${Math.ceil(value / 1000)} 秒`
}

async function load() {
  busy.value = true
  error.value = ''
  try {
    center.value = await fetchVersionCenter(projectId.value, appId.value)
    form.value.setAsCurrent = center.value.versions.length ? '' : 'on'
    if (!center.value.app.repoConfigured) sourceType.value = 'upload'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载版本中心失败'
  } finally {
    busy.value = false
  }
}

async function loadBranches() {
  busy.value = true
  error.value = ''
  try {
    branches.value = await fetchRepositoryBranches(projectId.value, appId.value)
    if (!form.value.repoBranch && branches.value.length) {
      form.value.repoBranch = branches.value[0]
      await fetchLatestCommitForBranch()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载分支失败'
  } finally {
    busy.value = false
  }
}

async function fetchLatestCommitForBranch() {
  if (!form.value.repoBranch || !center.value?.app.repoConfigured) return
  busy.value = true
  error.value = ''
  try {
    form.value.repoCommitId = await fetchGitLatestCommit(projectId.value, appId.value, form.value.repoBranch)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '获取最新 Commit 失败'
  } finally {
    busy.value = false
  }
}

async function checkGit() {
  busy.value = true
  error.value = ''
  try {
    gitEstimate.value = await fetchGitPullEstimate(projectId.value, appId.value, {
      branch: form.value.repoBranch,
      commitId: form.value.repoCommitId || undefined,
      versionNumber: form.value.versionNumber || undefined,
      excludePaths: excludePaths.value || undefined,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Git 检测失败'
  } finally {
    busy.value = false
  }
}

async function pollGit(jobId: string) {
  while (true) {
    gitJob.value = await fetchGitPullStatus(projectId.value, appId.value, jobId)
    if (gitJob.value.finish) break
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
  if (gitJob.value.success) {
    gitPulledPath.value = gitJob.value.cachePath || ''
    form.value.repoCommitId = gitJob.value.repoCommitId || form.value.repoCommitId
  } else {
    throw new Error(gitJob.value.message || 'Git 拉取失败')
  }
}

async function pullGit() {
  busy.value = true
  error.value = ''
  try {
    const jobId = await startGitPull(projectId.value, appId.value, {
      branch: form.value.repoBranch,
      commitId: form.value.repoCommitId || undefined,
      excludePaths: excludePaths.value || undefined,
      versionNumber: form.value.versionNumber || undefined,
    })
    await pollGit(jobId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Git 拉取失败'
  } finally {
    busy.value = false
  }
}

async function removePulledCode() {
  if (!gitPulledPath.value) return
  busy.value = true
  error.value = ''
  try {
    await deleteGitCode(projectId.value, appId.value, gitPulledPath.value)
    gitPulledPath.value = ''
    gitJob.value = null
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除拉取文件失败'
  } finally {
    busy.value = false
  }
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  selectedFile.value = target.files?.[0] || null
}

async function uploadFile() {
  if (!selectedFile.value) {
    error.value = '请先选择文件'
    return
  }
  busy.value = true
  error.value = ''
  try {
    uploadedPath.value = await uploadResource(selectedFile.value)
    notice.value = `文件已上传：${selectedFile.value.name}`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传文件失败'
  } finally {
    busy.value = false
  }
}

async function submit() {
  if (!form.value.versionNumber) {
    error.value = '版本号不能为空'
    return
  }
  const programFile = sourceType.value === 'git' ? gitPulledPath.value : uploadedPath.value
  if (!programFile) {
    error.value = '请先准备程序文件'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await createVersion(projectId.value, appId.value, {
      versionNumber: form.value.versionNumber,
      describe: form.value.describe,
      programFile,
      sourceType: sourceType.value,
      repoBranch: form.value.repoBranch,
      repoCommitId: form.value.repoCommitId,
      setAsCurrent: form.value.setAsCurrent,
    })
    gitPulledPath.value = ''
    await router.push(`/p/${projectId.value}/apps/${appId.value}/versions`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建版本失败'
  } finally {
    busy.value = false
  }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!gitPulledPath.value) return
  event.preventDefault()
  event.returnValue = 'Git 拉取的临时代码尚未创建版本或删除，离开页面前建议先删除拉取文件。'
}

onMounted(() => {
  load()
  window.addEventListener('beforeunload', beforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<style scoped>
.page-header,
.header-actions,
.tab-row,
.action-row,
.panel-head,
.meta-list {
  display: flex;
  gap: 12px;
}

.page-header,
.panel-head,
.meta-list {
  justify-content: space-between;
  align-items: center;
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
.meta-list {
  color: #64748b;
}

.secondary-link {
  color: #0f766e;
  font-weight: 700;
}

.tab-button,
.primary-button,
.ghost-button {
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

.editor-card,
.panel,
.warning-card {
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

button:disabled {
  opacity: .55;
  cursor: not-allowed;
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

.wide {
  grid-column: 1 / -1;
}

.text-input,
.text-area {
  width: 100%;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.action-row,
.panel {
  margin-top: 16px;
  flex-wrap: wrap;
}

.notice-text {
  color: #0f766e;
  margin-top: 14px;
}

.error-text {
  color: #b91c1c;
  margin-top: 14px;
}

@media (max-width: 840px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
