<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTrafficStore } from './stores/traffic'
import TrafficTable from './components/TrafficTable.vue'
import DetailModal from './components/DetailModal.vue'
import MqInputModal from './components/MqInputModal.vue'
import ProxyControl from './components/ProxyControl.vue'
import SessionHistory from './components/SessionHistory.vue'
import type { TrafficRecord } from './types/traffic'

const store = useTrafficStore()
const showDetail = ref(false)
const showMqInput = ref(false)
const selectedRecord = ref<TrafficRecord | null>(null)
const proxyInfoVisible = ref(false)
const selectedRecordIds = ref<string[]>([])

onMounted(() => {
  window.electronAPI?.onTrafficCaptured((record: TrafficRecord) => {
    store.addRecord(record)
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
</script>

<template>
  <div class="app">
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

    <div class="toolbar">
      <div class="search-box">
        <input
          v-model="store.searchQuery"
          type="text"
          placeholder="搜索 URL、用例名称或方法..."
        />
      </div>
      <div class="btn-group">
        <button class="btn btn-primary" @click="exportData('excel')">导出 Excel</button>
        <button class="btn btn-outline" @click="exportData('csv')">导出 CSV</button>
        <button class="btn btn-outline" @click="exportData('json')">导出 JSON</button>
      </div>
    </div>

    <TrafficTable
      :records="store.filteredRecords"
      v-model:selected-ids="selectedRecordIds"
      @delete="handleDelete"
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
.app {
  min-height: 100vh;
  background: #f0f2f5;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 24px 30px;
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
}

.header-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 4px;
}

.header-subtitle {
  font-size: 13px;
  opacity: 0.85;
  margin: 0;
}

.control-panel {
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
  padding: 18px 30px;
}

.control-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.input-group {
  flex: 1;
  min-width: 280px;
}

.input-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 500;
  color: #262626;
  font-size: 13px;
}

.input-group input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
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
  gap: 10px;
  flex-wrap: wrap;
}

.btn {
  padding: 9px 18px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
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
  margin-top: 12px;
  padding: 8px 14px;
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
  padding: 12px 30px;
  background: #f9f9f9;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats {
  display: flex;
  gap: 28px;
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
  font-size: 13px;
}

.stat-value {
  font-weight: 600;
  color: #262626;
  font-size: 15px;
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

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.toolbar {
  padding: 12px 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  background: white;
  gap: 16px;
}

.search-box input {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  width: 280px;
  transition: border-color 0.2s;
}

.search-box input:focus {
  outline: none;
  border-color: #667eea;
}
</style>
