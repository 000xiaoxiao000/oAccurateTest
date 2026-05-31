<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Applications</div>
        <h1>应用列表</h1>
      </div>
      <div class="header-actions">
        <button class="ghost-button" type="button" @click="creating = !creating">
          {{ creating ? '收起创建' : '新建应用' }}
        </button>
        <button class="action-button" type="button" @click="load">刷新</button>
      </div>
    </div>

    <form v-if="creating" class="editor-card" @submit.prevent="submitCreate">
      <h2>创建应用</h2>
      <div class="editor-grid">
        <label class="field">
          <span>应用名称</span>
          <input v-model.trim="form.name" class="text-input" type="text" />
        </label>
        <label class="field">
          <span>源码工程名</span>
          <input v-model.trim="form.srcName" class="text-input" type="text" />
        </label>
        <label class="field">
          <span>作用范围</span>
          <input v-model.trim="form.range" class="text-input" type="text" />
        </label>
        <label class="field wide">
          <span>应用描述</span>
          <textarea v-model.trim="form.describe" class="text-area" rows="3" />
        </label>
        <label class="field wide">
          <span>应用参数</span>
          <textarea v-model.trim="form.properties" class="text-area" rows="4" />
        </label>
        <label class="field">
          <span>当前版本</span>
          <input v-model.trim="form.currentVersion" class="text-input" type="text" />
        </label>
        <label class="field">
          <span>当前分支</span>
          <input v-model.trim="form.currentBranch" class="text-input" type="text" />
        </label>
        <label class="field">
          <span>当前 CommitId</span>
          <input v-model.trim="form.currentCommitId" class="text-input" type="text" />
        </label>
      </div>
      <div class="subsection">
        <h2>探针上下线告警</h2>
        <label class="checkbox-row">
          <input v-model="form.probeAlertEnabled" type="checkbox" />
          <span>启用探针实例上下线告警</span>
        </label>
        <div class="editor-grid">
          <label>
            <span>下线阈值（秒）</span>
            <input v-model.number="form.probeOfflineThresholdSeconds" class="text-input" type="number" min="30" />
          </label>
          <label>
            <span>Webhook 地址</span>
            <input v-model.trim="form.probeWebhookUrl" class="text-input" type="text" />
          </label>
        </div>
        <div class="checkbox-group">
          <label class="checkbox-row"><input v-model="form.probeAlertOnOffline" type="checkbox" /> <span>下线</span></label>
          <label class="checkbox-row"><input v-model="form.probeAlertOnRecovered" type="checkbox" /> <span>恢复上线</span></label>
          <label class="checkbox-row"><input v-model="form.probeAlertOnOnline" type="checkbox" /> <span>首次上线</span></label>
        </div>
      </div>
      <p v-if="createError" class="error-text">{{ createError }}</p>
      <div class="editor-actions">
        <button class="primary-button" type="submit" :disabled="submittingCreate">
          {{ submittingCreate ? '创建中...' : '创建应用' }}
        </button>
      </div>
    </form>

    <div class="summary-grid">
      <div class="summary-card">
        <span>应用总数</span>
        <strong>{{ apps.length }}</strong>
      </div>
      <div class="summary-card">
        <span>在线实例</span>
        <strong>{{ totalOnlineCount }}</strong>
      </div>
      <div class="summary-card">
        <span>已配置仓库</span>
        <strong>{{ repoConfiguredCount }}</strong>
      </div>
      <div class="summary-card">
        <span>项目编号</span>
        <strong class="small-text">{{ projectId }}</strong>
      </div>
    </div>

    <div class="toolbar-card app-toolbar">
      <div>
        <h2>应用清单</h2>
        <p>搜索和主要结果保持在首屏，分页控件固定在列表底部。</p>
      </div>
      <input v-model.trim="keyword" class="text-input" type="search" placeholder="搜索应用 ID、名称、版本、工程或类型" aria-label="搜索应用" />
    </div>

    <div v-if="loading" class="status-card">正在加载应用列表...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else>
      <div v-if="filteredApps.length === 0" class="status-card">没有匹配的应用</div>
      <div v-else class="card-grid">
      <article v-for="app in paginatedApps" :key="app.id" class="card">
        <div class="card-top">
          <div>
            <strong>{{ app.name }}</strong>
            <div class="app-id">{{ app.id }}</div>
            <div class="card-sub">{{ app.srcName || app.range || '未配置源工程信息' }}</div>
          </div>
          <RouterLink :class="app.onlineCount > 0 ? 'badge online' : 'badge offline'" :to="`/p/${projectId}/apps/online?appId=${app.id}`">
            {{ app.onlineCount > 0 ? `${app.onlineCount} 在线` : '离线' }}
          </RouterLink>
        </div>
        <div class="card-desc">{{ app.describe || '暂无应用描述' }}</div>
        <div class="meta-list">
          <span>版本 {{ app.currentVersion || '-' }}</span>
          <span>分支 {{ app.currentBranch || '-' }}</span>
          <span>{{ app.repoConfigured ? '已配置仓库' : '未配置仓库' }}</span>
        </div>
        <div class="card-actions">
          <RouterLink :to="`/p/${projectId}/apps/${app.id}/settings`">应用设置</RouterLink>
          <RouterLink :to="`/p/${projectId}/apps/${app.id}/snapshots`">系统快照</RouterLink>
          <RouterLink :to="`/p/${projectId}/apps/${app.id}/api-endpoints`">接口扫描</RouterLink>
          <RouterLink :to="`/p/${projectId}/apps/${app.id}/probe-alerts`">探针告警</RouterLink>
          <RouterLink :to="`/p/${projectId}/apps/${app.id}/repository`">仓库配置</RouterLink>
          <RouterLink :to="`/p/${projectId}/apps/${app.id}/versions`">版本中心</RouterLink>
          <a :href="backendApiUrl(`/p/${projectId}/app/${app.id}/oAT.key`)" download>下载注册文件</a>
          <button class="text-danger" type="button" @click="startDelete(app.id)">删除应用</button>
        </div>

        <form v-if="deletingId === app.id" class="delete-form" @submit.prevent="submitDelete(app.id)">
          <label class="field">
            <span>输入登录密码以删除应用</span>
            <input v-model="deletePassword" class="text-input" type="password" autocomplete="current-password" />
          </label>
          <p v-if="deleteError" class="error-text">{{ deleteError }}</p>
          <div class="editor-actions">
            <button class="danger-button" type="submit" :disabled="submittingDelete">
              {{ submittingDelete ? '删除中...' : '确认删除' }}
            </button>
            <button class="ghost-button" type="button" @click="cancelDelete">取消</button>
          </div>
        </form>
      </article>
      </div>
      <AppPagination
        v-if="filteredApps.length > 0"
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :total="filteredApps.length"
        item-name="个应用"
        :page-sizes="[10, 20, 50, 100]"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { backendApiUrl } from '@/api/http'
import AppPagination from '@/components/AppPagination.vue'
import { useProjectStore } from '@/stores/project'

const DEFAULT_APP_PROPERTIES = `#代码追踪范围包括
#codeStack.include=`

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = computed(() => projectStore.appsByProjectId[projectId.value] || [])
const loading = ref(false)
const error = ref('')
const creating = ref(false)
const createError = ref('')
const deleteError = ref('')
const deletingId = ref('')
const deletePassword = ref('')
const submittingCreate = ref(false)
const submittingDelete = ref(false)
const keyword = ref('')
const pageSize = ref(10)
const currentPage = ref(1)
const form = reactive({
  name: '',
  srcName: '',
  range: '',
  describe: '',
  properties: DEFAULT_APP_PROPERTIES,
  currentVersion: '',
  currentBranch: '',
  currentCommitId: '',
  probeAlertEnabled: false,
  probeOfflineThresholdSeconds: 90,
  probeWebhookUrl: '',
  probeAlertOnOnline: false,
  probeAlertOnOffline: false,
  probeAlertOnRecovered: false,
})

const filteredApps = computed(() => {
  const needle = keyword.value.toLowerCase()
  if (!needle) return apps.value
  return apps.value.filter((app) => [app.id, app.name, app.srcName, app.range, app.currentVersion, app.currentBranch, app.currentCommitId]
    .join(' ')
    .toLowerCase()
    .includes(needle))
})

const paginatedApps = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredApps.value.slice(start, start + pageSize.value)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredApps.value.length / pageSize.value)))

const totalOnlineCount = computed(() => apps.value.reduce((sum, app) => sum + (app.onlineCount || 0), 0))

const repoConfiguredCount = computed(() => apps.value.filter((app) => app.repoConfigured).length)

function applyRouteIntent() {
  const create = route.query.create
  if (create === '1' || create === 'true') {
    creating.value = true
    createError.value = ''
  }
}

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadProjectApps(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用列表失败'
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!form.name) {
    createError.value = '应用名称不能为空'
    return
  }
  submittingCreate.value = true
  createError.value = ''
  try {
    const created = await projectStore.createManagedApp(projectId.value, { ...form })
    form.name = ''
    form.srcName = ''
    form.range = ''
    form.describe = ''
    form.properties = DEFAULT_APP_PROPERTIES
    form.currentVersion = ''
    form.currentBranch = ''
    form.currentCommitId = ''
    form.probeAlertEnabled = false
    form.probeOfflineThresholdSeconds = 90
    form.probeWebhookUrl = ''
    form.probeAlertOnOnline = false
    form.probeAlertOnOffline = false
    form.probeAlertOnRecovered = false
    creating.value = false
    await router.push(`/p/${projectId.value}/apps/${created.id}/settings`)
  } catch (err) {
    createError.value = err instanceof Error ? err.message : '创建应用失败'
  } finally {
    submittingCreate.value = false
  }
}

function startDelete(appId: string) {
  deletingId.value = appId
  deletePassword.value = ''
  deleteError.value = ''
}

function cancelDelete() {
  deletingId.value = ''
  deletePassword.value = ''
  deleteError.value = ''
}

async function submitDelete(appId: string) {
  if (!deletePassword.value) {
    deleteError.value = '请输入登录密码'
    return
  }
  submittingDelete.value = true
  deleteError.value = ''
  try {
    await projectStore.removeManagedApp(projectId.value, appId, deletePassword.value)
    cancelDelete()
  } catch (err) {
    deleteError.value = err instanceof Error ? err.message : '删除应用失败'
  } finally {
    submittingDelete.value = false
  }
}

onMounted(load)

watch(
  () => route.query.create,
  () => {
    applyRouteIntent()
  },
  { immediate: true },
)

watch([keyword, pageSize], () => {
  currentPage.value = 1
})

watch(totalPages, (pages) => {
  if (currentPage.value > pages) currentPage.value = pages
})
</script>

<style scoped>
.page-header,
.header-actions,
.editor-actions,
.card-actions {
  display: flex;
  gap: 12px;
}

.page-header {
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-actions,
.card-actions,
.editor-actions {
  flex-wrap: wrap;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.action-button,
.primary-button,
.ghost-button,
.danger-button {
  border: none;
  border-radius: 999px;
  min-height: var(--oat-min-target);
  padding: 10px 16px;
  cursor: pointer;
  font-weight: 800;
}

.action-button,
.primary-button {
  background: linear-gradient(135deg, var(--oat-primary), var(--oat-primary-hover));
  color: #fff;
}

.ghost-button {
  border: 1px solid rgba(var(--oat-primary-rgb), .16);
  background: rgba(var(--oat-primary-rgb), 0.08);
  color: var(--oat-primary-dark);
}

.danger-button {
  background: linear-gradient(135deg, var(--oat-danger), #ef4444);
  color: #fff;
}

.editor-card,
.status-card,
.summary-card,
.toolbar-card {
  padding: 18px;
  border-radius: var(--oat-radius-lg);
  background: var(--oat-surface-raised);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.summary-card {
  background:
    radial-gradient(circle at top right, rgba(15, 118, 110, 0.13), transparent 44%),
    rgba(255, 255, 255, 0.94);
}

.summary-card span {
  color: #64748b;
  font-weight: 700;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 28px;
  word-break: break-word;
}

.summary-card .small-text {
  font-size: 13px;
}

.app-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, .8fr) minmax(260px, 1.2fr);
  gap: 12px;
  align-items: end;
  margin-bottom: 18px;
  position: sticky;
  top: 82px;
  z-index: 8;
  backdrop-filter: saturate(180%) blur(14px);
}

.app-toolbar h2,
.app-toolbar p {
  margin: 0;
}

.app-toolbar h2 {
  margin-bottom: 6px;
  color: #0f172a;
  font-size: 18px;
}

.app-toolbar p {
  color: #64748b;
}

.editor-card {
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
}

.field.wide {
  grid-column: 1 / -1;
}

.field span {
  color: #475569;
  font-size: 13px;
}

.text-input,
.text-area {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: var(--oat-radius-md);
  min-height: var(--oat-min-target);
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.96);
}

.status-card.error,
.error-text,
.text-danger {
  color: #b91c1c;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 520px));
  justify-content: start;
  align-items: stretch;
  gap: 16px;
  min-height: min(520px, calc(100vh - 360px));
}

.card {
  padding: 20px;
  border-radius: var(--oat-radius-xl);
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-sub,
.card-desc,
.meta-list {
  color: #64748b;
}

.card-sub {
  margin-top: 6px;
  font-size: 13px;
}

.app-id {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
  word-break: break-all;
}

.card-desc {
  margin-top: 14px;
  min-height: 42px;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
  font-size: 13px;
}

.card-actions {
  margin-top: 14px;
}

.card-actions a,
.card-actions button {
  color: #0f766e;
  font-weight: 700;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}

.card-actions .text-danger {
  color: #dc2626;
}

.card-actions .text-danger:hover:not(:disabled) {
  color: #b91c1c;
}

.delete-form {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(185, 28, 28, 0.12);
}

.badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  text-decoration: none;
}

.badge.online {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.badge.offline {
  background: rgba(148, 163, 184, 0.18);
  color: #475569;
}

@media (max-width: 720px) {
  .editor-grid,
  .summary-grid,
  .app-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
