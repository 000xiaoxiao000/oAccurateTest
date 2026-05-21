<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Application</div>
        <h1>应用设置</h1>
      </div>
      <button class="action-button" type="button" @click="load">刷新</button>
    </div>

    <div v-if="loading" class="status-card">正在加载应用设置...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <form class="form-card" @submit.prevent="save">
        <div class="form-grid">
          <label>
            <span>应用名称</span>
            <input v-model="form.name" class="text-input" type="text" />
          </label>
          <label>
            <span>工程名称</span>
            <input v-model="form.srcName" class="text-input" type="text" />
          </label>
        </div>

        <label class="block">
          <span>作用范围</span>
          <select v-model="form.range" class="select">
            <option value="only">仅当前项目</option>
            <option value="all">所有项目</option>
          </select>
        </label>

        <label class="block">
          <span>应用描述</span>
          <textarea v-model="form.describe" class="text-area" rows="3"></textarea>
        </label>

        <label class="block">
          <span>应用参数</span>
          <textarea v-model="form.properties" class="text-area" rows="5"></textarea>
        </label>

        <div class="form-grid">
          <label>
            <span>当前版本</span>
            <input v-model="form.currentVersion" class="text-input" type="text" />
          </label>
          <label>
            <span>当前分支</span>
            <input v-model="form.currentBranch" class="text-input" type="text" />
          </label>
        </div>

        <label class="block">
          <span>当前 CommitId</span>
          <input v-model="form.currentCommitId" class="text-input" type="text" />
        </label>

        <section class="subsection">
          <h2>探针上下线告警</h2>
          <label class="checkbox-row">
            <input v-model="form.probeAlertEnabled" type="checkbox" />
            <span>启用探针实例上下线告警</span>
          </label>

          <div class="form-grid">
            <label>
              <span>下线阈值（秒）</span>
              <input v-model.number="form.probeOfflineThresholdSeconds" class="text-input" type="number" min="30" />
            </label>
            <label>
              <span>Webhook 地址</span>
              <input v-model="form.probeWebhookUrl" class="text-input" type="text" />
            </label>
          </div>

          <div class="checkbox-group">
            <label class="checkbox-row"><input v-model="form.probeAlertOnOffline" type="checkbox" /> <span>下线</span></label>
            <label class="checkbox-row"><input v-model="form.probeAlertOnRecovered" type="checkbox" /> <span>恢复上线</span></label>
            <label class="checkbox-row"><input v-model="form.probeAlertOnOnline" type="checkbox" /> <span>首次上线</span></label>
          </div>

          <div class="dashboard" v-if="payload.probeAlertDashboard">
            <div class="metric">
              <strong>{{ payload.probeAlertDashboard.onlineCount }}</strong>
              <span>在线探针</span>
            </div>
            <div class="metric">
              <strong>{{ payload.probeAlertDashboard.offlineCount }}</strong>
              <span>离线探针</span>
            </div>
            <div class="metric">
              <strong>{{ payload.probeAlertDashboard.recentEventCount }}</strong>
              <span>最近告警</span>
            </div>
            <div class="metric">
              <strong>{{ payload.probeAlertDashboard.failedNotifyCount }}</strong>
              <span>通知失败</span>
            </div>
          </div>
        </section>

        <div class="actions">
          <button class="primary-button" type="submit">保存</button>
          <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/repository`">
            仓库配置
          </RouterLink>
          <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/api-endpoints`">
            接口扫描
          </RouterLink>
          <RouterLink class="secondary-link" :to="`/p/${projectId}/monitor`">
            实时监控
          </RouterLink>
        </div>
      </form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const storeKey = computed(() => `${projectId.value}:${appId.value}`)
const payload = computed(() => projectStore.appSettingsByKey[storeKey.value])
const loading = ref(false)
const error = ref('')

const form = reactive({
  name: '',
  srcName: '',
  range: 'only',
  describe: '',
  properties: '',
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

function syncForm() {
  if (!payload.value) {
    return
  }
  const app = payload.value.app
  form.name = app.name || ''
  form.srcName = app.srcName || ''
  form.range = app.range || 'only'
  form.describe = app.describe || ''
  form.properties = app.properties || ''
  form.currentVersion = app.currentVersion || ''
  form.currentBranch = app.currentBranch || ''
  form.currentCommitId = app.currentCommitId || ''
  form.probeAlertEnabled = app.probeAlertEnabled
  form.probeOfflineThresholdSeconds = app.probeOfflineThresholdSeconds || 90
  form.probeWebhookUrl = app.probeWebhookUrl || ''
  form.probeAlertOnOnline = app.probeAlertOnOnline
  form.probeAlertOnOffline = app.probeAlertOnOffline
  form.probeAlertOnRecovered = app.probeAlertOnRecovered
}

async function load() {
  if (!projectId.value || !appId.value) {
    error.value = '缺少 projectId 或 appId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadAppSettings(projectId.value, appId.value)
    syncForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用设置失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateAppSettings(projectId.value, appId.value, { ...form })
    syncForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存应用设置失败'
  } finally {
    loading.value = false
  }
}

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

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.action-button,
.primary-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  color: #fff;
  cursor: pointer;
}

.action-button {
  background: #0f172a;
}

.primary-button {
  background: #0f766e;
}

.secondary-link {
  color: #0f766e;
  font-weight: 700;
}

.status-card,
.form-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.block,
.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.block {
  margin-top: 16px;
}

.text-input,
.text-area,
.select {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.subsection {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.checkbox-row,
.checkbox-group {
  display: flex;
  gap: 12px;
  align-items: center;
}

.checkbox-group {
  flex-wrap: wrap;
  margin-top: 14px;
}

.dashboard {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.metric {
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.metric strong {
  display: block;
  font-size: 22px;
}

.metric span {
  color: #64748b;
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-top: 20px;
}

@media (max-width: 900px) {
  .form-grid,
  .dashboard {
    grid-template-columns: 1fr;
  }
}
</style>
