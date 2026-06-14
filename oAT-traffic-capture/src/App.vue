<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useTrafficStore } from './stores/traffic'
import TrafficTable from './components/TrafficTable.vue'
import DetailModal from './components/DetailModal.vue'
import FilterRulesPanel from './components/FilterRulesPanel.vue'
import MqInputModal from './components/MqInputModal.vue'
import PluginPanel from './components/PluginPanel.vue'
import ProxyControl from './components/ProxyControl.vue'
import SessionHistory from './components/SessionHistory.vue'
import TrafficStatsPanel from './components/TrafficStatsPanel.vue'
import type { TrafficRecord } from './types/traffic'

const store = useTrafficStore()
const showDetail = ref(false)
const showMqInput = ref(false)
const selectedRecord = ref<TrafficRecord | null>(null)
const proxyInfoVisible = ref(false)
const selectedRecordIds = ref<string[]>([])
const activePanel = ref<'none' | 'stats' | 'rules' | 'plugins'>('none')
const pluginsPath = ref('')
const coverageIntervalSeconds = ref(30)
const coverageServiceBaseUrl = ref('')
const coverageProjectId = ref('')
const coverageAppId = ref('')
const relayNotice = ref<{ text: string; status: 'success' | 'failed' | 'skipped' } | null>(null)
const hasCoverageRelayPlugin = computed(() => store.plugins.some(plugin => plugin.id === 'oat-coverage-relay'))
const isFloatingMode = new URLSearchParams(window.location.search).get('floating') === '1'
let floatingClickTimer: number | null = null
let relayNoticeTimer: number | null = null

onMounted(() => {
  window.electronAPI?.onTrafficCaptured((record: TrafficRecord) => {
    store.addRecord(record)
    if (record.coverageRelay && hasCoverageRelayPlugin.value) {
      showCoverageRelayNotice(record)
    }
  })
  window.electronAPI?.onCaptureStateChanged((state) => {
    store.syncCaptureState(state)
  })
  window.electronAPI?.getCaptureState().then((state) => {
    if (state) store.syncCaptureState(state)
  })
  store.loadFilterRules()
  store.loadPlugins()
  store.loadCoverageRelayConfig().then(() => {
    syncCoverageRelayForm()
  })
  window.electronAPI?.getPluginsPath().then((path) => {
    pluginsPath.value = path ?? ''
  })
})

async function toggleCapture() {
  if (store.isCapturing) {
    await store.stopCapture()
  } else {
    if (!store.currentCaseName.trim()) {
      alert('请先输入用例名称')
      return
    }
    const result = await store.startCapture(store.currentCaseName)
    if (result?.success) {
      proxyInfoVisible.value = true
      setTimeout(() => { proxyInfoVisible.value = false }, 6000)
    } else {
      alert(`启动代理失败: ${result?.error ?? '主进程未返回错误信息，请查看终端日志'}`)
    }
  }
}

function handleViewDetail(record: TrafficRecord) {
  selectedRecord.value = record
  showDetail.value = true
}

function handleDelete(id: string) {
  if (confirm('确定删除这条记录？')) {
    store.deleteRecord(id)
  }
}

function handleClear() {
  if (store.records.length === 0) return
  if (confirm('确定清空所有记录？')) {
    store.clearRecords()
  }
}

async function exportData(format: 'excel' | 'csv' | 'json') {
  const selectedIds = new Set(selectedRecordIds.value)
  const records = selectedIds.size > 0
    ? store.filteredRecords.filter(record => selectedIds.has(record.id))
    : store.filteredRecords

  if (records.length === 0) {
    alert('没有可导出的记录')
    return
  }

  try {
    const exportRecords = JSON.parse(JSON.stringify(records)) as TrafficRecord[]
    const result = await window.electronAPI?.exportRecords(format, exportRecords)
    if (result?.success) {
      alert(`成功导出 ${exportRecords.length} 条数据：${result.filePath}`)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    alert(`导出失败：${message}`)
  }
}

function addMqRecord(record: TrafficRecord) {
  record.caseName = store.currentCaseName || '未命名'
  store.addRecord(record)
}

function handleLoadSession(records: TrafficRecord[]) {
  store.replaceRecords([...records].reverse())
}

function toggleStatusFilter(filter: 'success' | 'failed') {
  store.setStatusFilter(store.statusFilter === filter ? 'all' : filter)
}

function formatDuration(ms: number) {
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.round(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  return `${Math.round(seconds / 60)}min`
}

function formatClock(timestamp?: number) {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

function coverageRelayStatusText(status?: 'success' | 'failed' | 'skipped') {
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  if (status === 'skipped') return '跳过'
  return '未知'
}

function showCoverageRelayNotice(record: TrafficRecord) {
  const relay = record.coverageRelay
  if (!relay) return
  const statusText = coverageRelayStatusText(relay.status)
  const detail = relay.status === 'success'
    ? relay.targetUrl ? `，目标 ${relay.targetUrl}` : ''
    : relay.error ? `：${relay.error}` : ''
  relayNotice.value = {
    status: relay.status,
    text: `覆盖率上送${statusText}${detail}`
  }
  if (relayNoticeTimer) {
    window.clearTimeout(relayNoticeTimer)
  }
  relayNoticeTimer = window.setTimeout(() => {
    relayNotice.value = null
    relayNoticeTimer = null
  }, 5000)
}

function syncCoverageRelayForm() {
  coverageIntervalSeconds.value = Math.round(store.coverageRelayConfig.intervalMs / 1000)
  coverageServiceBaseUrl.value = store.coverageRelayConfig.serviceBaseUrl ?? ''
  coverageProjectId.value = store.coverageRelayConfig.projectId ?? ''
  coverageAppId.value = store.coverageRelayConfig.appId ?? ''
}

async function saveCoverageRelayConfig() {
  const seconds = Number(coverageIntervalSeconds.value)
  if (!Number.isFinite(seconds) || seconds < 1) {
    alert('覆盖率上送间隔不能小于 1 秒')
    syncCoverageRelayForm()
    return
  }
  await store.saveCoverageRelayConfig({
    intervalMs: Math.round(seconds * 1000),
    serviceBaseUrl: coverageServiceBaseUrl.value,
    projectId: coverageProjectId.value,
    appId: coverageAppId.value
  })
  syncCoverageRelayForm()
  relayNotice.value = {
    status: 'success',
    text: `覆盖率上送配置已保存，默认目标 ${coverageServiceBaseUrl.value || '-'} / ${coverageProjectId.value || '-'} / ${coverageAppId.value || '-'}`
  }
  if (relayNoticeTimer) window.clearTimeout(relayNoticeTimer)
  relayNoticeTimer = window.setTimeout(() => {
    relayNotice.value = null
    relayNoticeTimer = null
  }, 3000)
}

async function replayRecord(record: TrafficRecord) {
  const result = await store.replay(record)
  if (!result?.success && result?.error) {
    alert(`重放失败：${result.error}`)
  }
}

async function replaySelected() {
  if (selectedRecordIds.value.length === 0) {
    alert('请先选择要重放的记录')
    return
  }
  const results = await store.replayMany(selectedRecordIds.value)
  const failed = results?.filter(result => !result.success).length ?? 0
  if (failed > 0) {
    alert(`重放完成，其中 ${failed} 条失败或不支持`)
  }
}

function togglePanel(panel: 'stats' | 'rules' | 'plugins') {
  activePanel.value = activePanel.value === panel ? 'none' : panel
}

async function openPluginsFolder() {
  await window.electronAPI?.openPluginsFolder()
}

async function uninstallPlugin(pluginId: string) {
  await store.uninstallPlugin(pluginId)
}

async function showFloatingWindow() {
  await window.electronAPI?.showFloatingWindow()
}

async function restoreMainWindow() {
  if (floatingClickTimer) {
    window.clearTimeout(floatingClickTimer)
    floatingClickTimer = null
  }
  await window.electronAPI?.restoreMainWindow()
}

function handleFloatingClick() {
  if (floatingClickTimer) return
  floatingClickTimer = window.setTimeout(() => {
    floatingClickTimer = null
    toggleCapture()
  }, 220)
}
</script>

<template>
  <div v-if="isFloatingMode" class="floating-capture">
    <input
      v-model="store.currentCaseName"
      class="floating-case-input"
      type="text"
      placeholder="用例名称 / 流量描述"
      :disabled="store.isCapturing"
      @dblclick.stop
    />
    <div class="floating-stats" :class="{ active: store.isCapturing }">
      <span class="floating-status-dot"></span>
      <span>{{ store.isCapturing ? '捕获中' : '未捕获' }}</span>
      <strong>{{ store.capturedCount }}</strong>
      <span>条流量</span>
    </div>
    <button
      class="floating-capture-button"
      :class="{ active: store.isCapturing }"
      type="button"
      title="单击开始/停止，双击恢复主窗口"
      @click.stop="handleFloatingClick"
      @dblclick.stop="restoreMainWindow"
    >
      <span class="floating-dot"></span>
      {{ store.isCapturing ? '停止捕获' : '开始捕获' }}
    </button>
    <button class="floating-restore-button" type="button" @click="restoreMainWindow">
      恢复
    </button>
  </div>

  <div v-else class="app">
    <header class="app-header">
      <div class="header-content">
        <div>
          <h1 class="header-title">oAT 流量采集器</h1>
          <p class="header-subtitle">实时捕获并管理 HTTP、HTTPS、MQ 等协议的网络请求流量</p>
        </div>
        <ProxyControl :port="store.proxyPort" />
      </div>
    </header>

    <div class="control-panel">
      <div class="control-row">
        <div class="input-group">
          <label for="caseName">用例名称 / 流量描述</label>
          <input
            id="caseName"
            v-model="store.currentCaseName"
            type="text"
            placeholder="例如：用户登录流程、订单创建接口测试..."
            :disabled="store.isCapturing"
          />
        </div>
        <div class="btn-group">
          <button
            class="btn"
            :class="store.isCapturing ? 'btn-danger' : 'btn-success'"
            @click="toggleCapture"
          >
            {{ store.isCapturing ? '⏹ 停止捕获' : '▶ 开始捕获' }}
          </button>
          <button class="btn btn-outline" @click="showFloatingWindow">
            悬浮最小化
          </button>
          <button class="btn btn-outline" @click="showMqInput = true">
            + 手动录入 MQ
          </button>
          <button class="btn btn-outline" @click="handleClear">清空记录</button>
        </div>
      </div>

      <div v-if="proxyInfoVisible" class="proxy-tip">
        <span class="tip-icon">ℹ️</span>
        代理已启动在端口 <strong>{{ store.proxyPort }}</strong>，请在浏览器或系统网络设置中配置 HTTP 代理为
        <strong>127.0.0.1:{{ store.proxyPort }}</strong>
      </div>
    </div>

    <div class="stats-bar">
      <div class="stats">
        <button
          type="button"
          class="stat-item"
          :class="{ active: store.statusFilter === 'all' }"
          @click="store.setStatusFilter('all')"
        >
          <span class="stat-label">总请求数</span>
          <span class="stat-value">{{ store.stats.total }}</span>
        </button>
        <button
          type="button"
          class="stat-item"
          :class="{ active: store.statusFilter === 'success' }"
          @click="toggleStatusFilter('success')"
        >
          <span class="stat-label">成功</span>
          <span class="stat-value success">{{ store.stats.success }}</span>
        </button>
        <button
          type="button"
          class="stat-item"
          :class="{ active: store.statusFilter === 'failed' }"
          @click="toggleStatusFilter('failed')"
        >
          <span class="stat-label">失败</span>
          <span class="stat-value danger">{{ store.stats.failed }}</span>
        </button>
        <div class="stat-item">
          <span class="stat-label">平均耗时</span>
          <span class="stat-value">{{ store.stats.avgDuration }}ms</span>
        </div>
      </div>
      <div v-if="store.isCapturing" class="recording-indicator">
        <span class="dot"></span>
        正在捕获中 · 端口 {{ store.proxyPort }}
      </div>
    </div>

    <div
      v-if="hasCoverageRelayPlugin"
      class="coverage-relay-bar"
      :class="{ ok: store.coverageRelayStats.latestRelay?.status === 'success', failed: store.coverageRelayStats.latestRelay?.status === 'failed' }"
    >
      <div class="coverage-relay-main">
        <span class="coverage-dot"></span>
        <strong>覆盖率上送</strong>
        <span v-if="store.coverageRelayStats.total === 0">暂无上送记录</span>
        <span v-else>
          最近一次 {{ coverageRelayStatusText(store.coverageRelayStats.latestRelay?.status) }}
          · 成功 {{ store.coverageRelayStats.success }} / 失败 {{ store.coverageRelayStats.failed }}
        </span>
      </div>
      <div class="coverage-relay-meta">
        <label class="coverage-interval-control">
          <span>服务</span>
          <input
            v-model.trim="coverageServiceBaseUrl"
            class="wide"
            type="text"
            placeholder="http://localhost:8080"
            @keyup.enter="saveCoverageRelayConfig"
          />
          <span>项目</span>
          <input
            v-model.trim="coverageProjectId"
            type="text"
            placeholder="projectId"
            @keyup.enter="saveCoverageRelayConfig"
          />
          <span>应用</span>
          <input
            v-model.trim="coverageAppId"
            type="text"
            placeholder="appId"
            @keyup.enter="saveCoverageRelayConfig"
          />
          <span>预计间隔</span>
          <input
            v-model.number="coverageIntervalSeconds"
            type="number"
            min="1"
            step="1"
            @keyup.enter="saveCoverageRelayConfig"
          />
          <span>秒</span>
          <button type="button" @click="saveCoverageRelayConfig">保存</button>
        </label>
        <span>预计间隔约 {{ formatDuration(store.coverageRelayStats.intervalMs) }}</span>
        <span>下次约 {{ formatClock(store.coverageRelayStats.nextReportAt) }}</span>
        <span v-if="store.coverageRelayStats.latestRelay?.httpStatus">HTTP {{ store.coverageRelayStats.latestRelay.httpStatus }}</span>
        <span v-if="store.coverageRelayStats.latestRelay?.error" class="coverage-error" :title="store.coverageRelayStats.latestRelay.error">
          {{ store.coverageRelayStats.latestRelay.error }}
        </span>
      </div>
    </div>

    <div v-if="hasCoverageRelayPlugin && relayNotice" class="coverage-relay-notice" :class="relayNotice.status">
      {{ relayNotice.text }}
    </div>

    <div class="toolbar">
      <div class="search-box">
        <input
          v-model="store.searchQuery"
          type="text"
          placeholder="搜索 URL、用例名称或方法..."
        />
      </div>
      <div class="btn-group">
        <button class="btn btn-outline" @click="togglePanel('stats')">统计图表</button>
        <button class="btn btn-outline" @click="togglePanel('rules')">过滤规则</button>
        <button class="btn btn-outline" @click="togglePanel('plugins')">插件扩展</button>
        <button class="btn btn-outline" @click="replaySelected">重放选中</button>
        <button class="btn btn-primary" @click="exportData('excel')">导出 Excel</button>
        <button class="btn btn-outline" @click="exportData('csv')">导出 CSV</button>
        <button class="btn btn-outline" @click="exportData('json')">导出 JSON</button>
      </div>
    </div>

    <TrafficStatsPanel v-if="activePanel === 'stats'" :stats="store.chartStats" />
    <FilterRulesPanel v-if="activePanel === 'rules'" :rules="store.filterRules" @save="store.saveRules" />
    <PluginPanel
      v-if="activePanel === 'plugins'"
      :plugins="store.plugins"
      :plugins-path="pluginsPath"
      @reload="store.reloadPlugins"
      @open-folder="openPluginsFolder"
      @install-builtin="store.installBuiltinPlugin"
      @uninstall-builtin="store.uninstallBuiltinPlugin"
      @uninstall-plugin="(plugin) => uninstallPlugin(plugin.id)"
    />

    <TrafficTable
      :records="store.filteredRecords"
      v-model:selected-ids="selectedRecordIds"
      @delete="handleDelete"
      @replay="replayRecord"
      @view-detail="handleViewDetail"
    />

    <SessionHistory @load="handleLoadSession" />

    <DetailModal
      :record="selectedRecord"
      :show="showDetail"
      @close="showDetail = false"
    />

    <MqInputModal
      :show="showMqInput"
      @close="showMqInput = false"
      @add="addMqRecord"
    />
  </div>
</template>

<style scoped>
.floating-capture {
  width: 100vw;
  height: 100vh;
  padding: 12px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  -webkit-app-region: drag;
  user-select: none;
}

.floating-case-input {
  width: 100%;
  height: 34px;
  box-sizing: border-box;
  border: 1px solid #d9d9d9;
  border-radius: 5px;
  padding: 0 10px;
  font-size: 13px;
  color: #262626;
  outline: none;
  -webkit-app-region: no-drag;
}

.floating-case-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.14);
}

.floating-case-input:disabled {
  background: #f5f5f5;
  color: #8c8c8c;
}

.floating-stats {
  width: 100%;
  height: 24px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 5px;
  background: #f7f7f7;
  color: #595959;
  font-size: 12px;
  -webkit-app-region: no-drag;
}

.floating-stats.active {
  background: #fff1f0;
  color: #cf1322;
}

.floating-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #bfbfbf;
}

.floating-stats.active .floating-status-dot {
  background: #ff4d4f;
  animation: pulse 1.5s ease-in-out infinite;
}

.floating-stats strong {
  color: #262626;
  font-size: 13px;
}

.floating-capture-button {
  width: 100%;
  height: 50px;
  border: 0;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #52c41a;
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  -webkit-app-region: no-drag;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.16);
}

.floating-capture-button.active {
  background: #ff4d4f;
}

.floating-capture-button:hover {
  filter: brightness(0.96);
}

.floating-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.95;
}

.floating-capture-button.active .floating-dot {
  animation: pulse 1.5s ease-in-out infinite;
}

.floating-restore-button {
  width: 100%;
  height: 26px;
  border: 1px solid #d9d9d9;
  border-radius: 5px;
  background: #ffffff;
  color: #595959;
  font-size: 12px;
  cursor: pointer;
  -webkit-app-region: no-drag;
}

.floating-restore-button:hover {
  background: #f5f5f5;
}

.app {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 12px 18px;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
}

.header-title {
  font-size: 19px;
  font-weight: 600;
  margin: 0 0 4px;
}

.header-subtitle {
  font-size: 12px;
  opacity: 0.85;
  margin: 0;
}

.control-panel {
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  padding: 8px 18px;
}

.control-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.input-group {
  flex: 1;
  min-width: 280px;
}

.input-group label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
  color: #262626;
  font-size: 12px;
}

.input-group input {
  width: 100%;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  transition: border-color 0.2s;
}

.input-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.15);
}

.input-group input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.btn-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.btn {
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  font-weight: 500;
  transition: opacity 0.2s;
  white-space: nowrap;
}

.btn:hover {
  opacity: 0.85;
}

.btn-success { background: #52c41a; color: white; }
.btn-danger { background: #ff4d4f; color: white; }
.btn-primary { background: #667eea; color: white; }
.btn-outline {
  background: white;
  color: #595959;
  border: 1px solid #d9d9d9;
}

.proxy-tip {
  margin-top: 8px;
  padding: 6px 12px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  font-size: 13px;
  color: #096dd9;
}

.tip-icon {
  margin-right: 6px;
}

.stats-bar {
  padding: 6px 18px;
  background: #f9f9f9;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats {
  display: flex;
  gap: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  font: inherit;
}

button.stat-item {
  cursor: pointer;
}

button.stat-item:hover {
  background: #f0f2ff;
}

.stat-item.active {
  background: #eef2ff;
  border-color: #aebcff;
}

.stat-label {
  color: #8c8c8c;
  font-size: 12px;
}

.stat-value {
  font-weight: 600;
  color: #262626;
  font-size: 14px;
}

.stat-value.success { color: #52c41a; }
.stat-value.danger { color: #ff4d4f; }

.recording-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 4px;
  color: #cf1322;
  font-size: 13px;
}

.dot {
  width: 8px;
  height: 8px;
  background: #ff4d4f;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

.coverage-relay-bar {
  min-height: 34px;
  padding: 6px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
  color: #595959;
  font-size: 12px;
}

.coverage-relay-bar.ok {
  background: #f6ffed;
  color: #237804;
}

.coverage-relay-bar.failed {
  background: #fff1f0;
  color: #cf1322;
}

.coverage-relay-main,
.coverage-relay-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.coverage-relay-main strong {
  color: #262626;
}

.coverage-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #bfbfbf;
}

.coverage-relay-bar.ok .coverage-dot {
  background: #52c41a;
}

.coverage-relay-bar.failed .coverage-dot {
  background: #ff4d4f;
}

.coverage-relay-meta {
  justify-content: flex-end;
}

.coverage-relay-meta span {
  white-space: nowrap;
}

.coverage-interval-control {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: inherit;
  white-space: nowrap;
}

.coverage-interval-control input {
  width: 88px;
  height: 24px;
  box-sizing: border-box;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 0 6px;
  color: #262626;
  background: #fff;
}

.coverage-interval-control input.wide {
  width: 180px;
}

.coverage-interval-control button {
  height: 24px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 0 8px;
  background: #fff;
  color: #595959;
  cursor: pointer;
}

.coverage-relay-notice {
  margin: 8px 18px 0;
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid #d9d9d9;
  background: #fafafa;
  color: #595959;
  font-size: 13px;
}

.coverage-relay-notice.success {
  border-color: #b7eb8f;
  background: #f6ffed;
  color: #237804;
}

.coverage-relay-notice.failed {
  border-color: #ffa39e;
  background: #fff1f0;
  color: #cf1322;
}

.coverage-relay-notice.skipped {
  border-color: #ffe58f;
  background: #fffbe6;
  color: #ad6800;
}

.coverage-error {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.toolbar {
  padding: 7px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  background: white;
  gap: 16px;
}

@media (max-width: 900px) {
  .coverage-relay-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .coverage-relay-meta {
    justify-content: flex-start;
  }
}

.search-box input {
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
  width: 220px;
  transition: border-color 0.2s;
}

.search-box input:focus {
  outline: none;
  border-color: #667eea;
}
</style>
