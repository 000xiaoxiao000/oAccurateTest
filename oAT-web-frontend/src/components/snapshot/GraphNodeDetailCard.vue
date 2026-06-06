<template>
  <div v-if="detail" class="node-card detail-card">
    <div class="node-header">
      <div class="node-header-main">
        <strong class="node-title">{{ detail.title || detail.name || detail.id }}</strong>
        <div class="node-meta">
          <span v-if="detail.type" class="node-type-badge">{{ detail.type }}</span>
          <span v-if="detail.ip" class="node-ip">{{ detail.ip }}</span>
        </div>
      </div>
    </div>
    <div v-if="Object.keys(detail.stats || {}).length" class="stat-list">
      <div v-for="(value, key) in detail.stats" :key="key" class="stat-item">
        <span class="stat-key">{{ key }}</span>
        <strong class="stat-value">{{ String(value) }}</strong>
      </div>
    </div>

    <section v-for="section in detail.sections" :key="section.title" class="detail-section">
      <h3>{{ section.title }}</h3>
      <div v-if="section.fields?.length" class="field-grid">
        <div v-for="field in section.fields" :key="`${section.title}-${field.label}`" class="field-row">
          <span class="field-label">{{ field.label }}</span>
          <strong class="field-value" :title="displayValue(field.value)">{{ displayValue(field.value) }}</strong>
        </div>
      </div>
      <pre v-if="section.content" class="detail-pre">{{ section.content }}</pre>
    </section>

    <section v-if="hasList(detail.sqlGroups) || hasList(detail.sqlStatements)" class="detail-section">
      <h3>SQL访问</h3>
      <article v-for="sql in detail.sqlGroups" :key="`group-${sql.sql}-${sql.databaseName}`" class="trace-block sql-block">
        <div class="trace-head">
          <span class="pill">{{ sql.databaseType || sql.type || 'SQL' }}</span>
          <strong>{{ sql.databaseName || sql.jdbcUrl || sql.addressIp || '数据库' }}</strong>
          <small v-if="sql.count">{{ sql.count }} 次</small>
        </div>
        <div class="pre-wrap">
          <pre>{{ sql.sql }}</pre>
          <button class="copy-btn" :class="{ copied: copiedKey === `group-${sql.sql}` }" @click="copySql(sql.sql, `group-${sql.sql}`)">
            {{ copiedKey === `group-${sql.sql}` ? '已复制' : '复制' }}
          </button>
        </div>
        <div v-if="sql.params?.length" class="param-wrap">
          <p class="muted">参数：{{ sql.params.map((param) => param ? param.join(', ') : '').join(' | ') }}</p>
          <button class="copy-btn-small" :class="{ copied: copiedKey === `group-params-${sql.sql}` }" @click="copySql(sql.params.map((param) => param ? param.join(', ') : '').join(' | '), `group-params-${sql.sql}`)">
            {{ copiedKey === `group-params-${sql.sql}` ? '已复制' : '复制' }}
          </button>
        </div>
      </article>
      <article v-for="(sql, index) in detail.sqlStatements" :key="`sql-${index}-${sql.sql}`" class="trace-block sql-block">
        <div class="trace-head">
          <span class="pill">{{ sql.executes?.join('/') || sql.type || 'SQL' }}</span>
          <strong>{{ sql.databaseName || sql.jdbcUrl || 'SQL语句' }}</strong>
          <small v-if="sql.useTime !== undefined">{{ sql.useTime }} ms</small>
        </div>
        <div class="pre-wrap">
          <pre>{{ sql.sql }}</pre>
          <button class="copy-btn" :class="{ copied: copiedKey === `stmt-${index}-${sql.sql}` }" @click="copySql(sql.sql, `stmt-${index}-${sql.sql}`)">
            {{ copiedKey === `stmt-${index}-${sql.sql}` ? '已复制' : '复制' }}
          </button>
        </div>
        <div v-if="sql.params?.length" class="param-wrap">
          <p class="muted">参数：{{ sql.params.map((param) => param ? param.join(', ') : '').join(' | ') }}</p>
          <button class="copy-btn-small" :class="{ copied: copiedKey === `stmt-params-${index}-${sql.sql}` }" @click="copySql(sql.params.map((param) => param ? param.join(', ') : '').join(' | '), `stmt-params-${index}-${sql.sql}`)">
            {{ copiedKey === `stmt-params-${index}-${sql.sql}` ? '已复制' : '复制' }}
          </button>
        </div>
        <p v-if="sql.error" class="error-text">{{ sql.error.type || '异常' }}：{{ sql.error.message }}</p>
      </article>
    </section>

    <section v-if="hasList(detail.tableOperations)" class="detail-section">
      <h3>表操作统计</h3>
      <div class="operation-list">
        <div v-for="operation in detail.tableOperations" :key="`${operation.action}-${operation.tableName}-${operation.columns?.join(',')}`" class="operation-item">
          <div class="operation-row">
            <span class="operation-badge" :class="operationClass(operation.action)">{{ operation.action || operation.model || '-' }}</span>
            <strong class="table-name">{{ operation.tableName || '-' }}</strong>
          </div>
          <div class="operation-columns">
            <small>{{ operation.columns?.join(', ') || '无字段' }}</small>
          </div>
        </div>
      </div>
    </section>

    <section v-if="hasList(detail.remoteCalls)" class="detail-section">
      <h3>远程调用</h3>
      <article v-for="(call, index) in detail.remoteCalls" :key="`${call.type}-${call.traceNodeId}-${index}`" class="trace-block">
        <div class="trace-head">
          <span class="pill remote">{{ call.type || 'Remote' }}</span>
          <strong>{{ call.title || call.interfaceName || call.url || call.methodName || '远程调用' }}</strong>
          <small v-if="call.useTime !== undefined">{{ call.useTime }} ms</small>
        </div>
        <p v-if="call.methodName" class="muted">方法：{{ call.methodName }}</p>
        <p v-if="call.url" class="muted">地址：{{ call.url }}</p>
        <p v-if="call.remoteApp?.appName" class="muted">远程应用：{{ call.remoteApp.appName }}</p>
        <pre v-if="call.headers">{{ call.headers }}</pre>
        <pre v-if="call.request">{{ call.request }}</pre>
        <pre v-if="call.response">{{ call.response }}</pre>
        <p v-if="call.error" class="error-text">{{ call.error.type || '异常' }}：{{ call.error.message }}</p>
      </article>
    </section>

    <section v-if="hasList(detail.redisCommands)" class="detail-section">
      <h3>Redis访问</h3>
      <article v-for="(command, index) in detail.redisCommands" :key="`${command.type}-${index}-${command.command}`" class="trace-block">
        <div class="trace-head">
          <span class="pill" :class="{ danger: command.type === 'DEL', success: isMutatingRedis(command.type) }">{{ command.type || 'CMD' }}</span>
          <strong>{{ command.host || '-' }}:{{ command.port || '-' }}</strong>
          <small v-if="command.useTime !== undefined">{{ command.useTime }} ms</small>
        </div>
        <pre>{{ command.command }}</pre>
        <p v-if="command.error" class="error-text">{{ command.error.type || '异常' }}：{{ command.error.message }}</p>
      </article>
    </section>

    <section v-if="hasList(detail.errors)" class="detail-section">
      <h3>异常堆栈</h3>
      <article v-for="(errorItem, index) in detail.errors" :key="`${errorItem.type}-${index}-${errorItem.message}`" class="trace-block error-block">
        <strong>{{ errorItem.type || '异常' }}：{{ errorItem.message || errorItem.code || '-' }}</strong>
        <pre v-if="errorItem.errorStack">{{ errorItem.errorStack }}</pre>
      </article>
    </section>
  </div>
  <div v-else class="empty-card">{{ emptyText }}</div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { GraphNodeDetailPayload } from '@/api/types'

defineProps<{
  detail?: GraphNodeDetailPayload | null
  emptyText?: string
}>()

const copiedKey = ref<string | null>(null)

const hasList = (items?: unknown[]) => Array.isArray(items) && items.length > 0

const displayValue = (value?: unknown) => {
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  return String(value)
}

const operationClass = (action?: string) => ({
  success: action === '增',
  danger: action === '删',
  warning: action === '改',
  info: action === '查',
})

const isMutatingRedis = (type?: string) => ['SET', 'SETEX', 'HSET', 'HPUTALL', 'SADD', 'SETNX', 'HPUTALLEX'].includes(type || '')

const copySql = async (sql: string | undefined, key: string) => {
  try {
    await navigator.clipboard.writeText(sql || '')
    copiedKey.value = key
    setTimeout(() => {
      copiedKey.value = null
    }, 2000)
  } catch (error) {
    console.error('复制失败:', error)
  }
}
</script>

<style scoped>
.node-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  overflow-wrap: break-word;
  word-wrap: break-word;
  box-shadow: 0 2px 8px rgba(15, 23, 42, .05);
}

.detail-card {
  gap: 14px;
  align-content: start;
  max-width: 100%;
  min-height: 0;
  max-height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}

.node-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, .07);
}

.node-header-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.node-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
  word-break: break-word;
  overflow-wrap: break-word;
}

.node-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.node-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, .1);
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .04em;
  text-transform: uppercase;
}

.node-ip {
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  background: rgba(241, 245, 249, .9);
  padding: 2px 8px;
  border-radius: 6px;
}

.stat-list {
  display: grid;
  gap: 2px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, .07);
}

.stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  font-size: 13px;
  min-width: 0;
  background: #ffffff;
  transition: background 120ms ease;
}

.stat-item:nth-child(odd) {
  background: rgba(248, 250, 252, .8);
}

.stat-item:hover {
  background: rgba(236, 253, 245, .6);
}

.stat-key {
  color: #64748b;
  font-weight: 500;
  flex: 0 0 36%;
  min-width: 96px;
}

.stat-value {
  min-width: 0;
  flex: 1;
  overflow-wrap: break-word;
  word-wrap: break-word;
  word-break: break-word;
  text-align: right;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.detail-section {
  display: grid;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.detail-section h3 {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.4;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 8px;
}

.operation-list {
  display: grid;
  gap: 6px;
  max-height: 280px;
  overflow-y: auto;
}

.operation-item {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.field-row,
.operation-row,
.trace-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.field-row {
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(248, 250, 252, .86);
  border: 1px solid rgba(226, 232, 240, .72);
}

.field-row:hover {
  background: rgba(240, 253, 250, .72);
  border-color: rgba(15, 118, 110, .18);
}

.operation-row {
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
}

.table-name {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  overflow-wrap: break-word;
  word-break: break-all;
}

.operation-columns {
  padding-left: 2px;
}

.operation-columns small {
  color: #64748b;
  font-size: 11.5px;
  line-height: 1.5;
  word-break: break-all;
  overflow-wrap: break-word;
}

.field-label,
.muted {
  color: #64748b;
  min-width: 0;
  overflow-wrap: break-word;
  word-wrap: break-word;
}

.field-label {
  flex: 0 0 36%;
  min-width: 100px;
  font-size: 12px;
  font-weight: 700;
}

.field-value {
  min-width: 0;
  flex: 1;
  color: #0f172a;
  font-size: 13px;
  text-align: right;
  overflow-wrap: break-word;
  word-wrap: break-word;
  word-break: break-word;
  font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  line-height: 1.45;
}

.trace-block {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.trace-block p {
  min-width: 0;
  overflow-wrap: break-word;
  word-wrap: break-word;
  word-break: break-word;
}

.pre-wrap {
  position: relative;
}

.pre-wrap:hover .copy-btn {
  opacity: 1;
}

.param-wrap {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.6);
  border: 1px solid rgba(226, 232, 240, 0.5);
}

.param-wrap:hover .copy-btn-small {
  opacity: 1;
}

.param-wrap .muted {
  flex: 1;
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.copy-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  color: #475569;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  opacity: 0;
  transition: opacity 150ms ease, background 120ms ease, color 120ms ease;
  line-height: 1.4;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.copy-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
  border-color: rgba(15, 23, 42, 0.18);
}

.copy-btn.copied {
  opacity: 1;
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
  border-color: rgba(22, 163, 74, 0.24);
}

.copy-btn-small {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 5px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: #ffffff;
  color: #64748b;
  font-size: 10px;
  font-weight: 600;
  cursor: pointer;
  opacity: 0;
  transition: opacity 150ms ease, background 120ms ease, color 120ms ease;
  line-height: 1.4;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.copy-btn-small:hover {
  background: #f1f5f9;
  color: #0f172a;
  border-color: rgba(15, 23, 42, 0.15);
}

.copy-btn-small.copied {
  opacity: 1;
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
  border-color: rgba(22, 163, 74, 0.2);
}

.trace-block pre,
.detail-pre,
.log-preview {
  margin: 0;
  padding: 10px;
  padding-right: 70px;
  border-radius: 10px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  max-height: 180px;
  overflow: auto;
}

.log-preview {
  max-height: 280px;
}

.trace-head strong {
  min-width: 0;
  flex: 1;
  overflow-wrap: break-word;
  word-wrap: break-word;
  word-break: break-word;
}

.trace-head small {
  color: #64748b;
}

.pill,
.operation-badge {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
}

.pill.remote {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.pill.success,
.operation-badge.success {
  background: rgba(22, 163, 74, 0.1);
  color: #15803d;
}

.pill.danger,
.operation-badge.danger {
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
}

.operation-badge.warning {
  background: rgba(217, 119, 6, 0.12);
  color: #b45309;
}

.operation-badge.info {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.error-text {
  margin: 0;
  color: #b91c1c;
  font-size: 12px;
}

.error-block {
  border-color: rgba(185, 28, 28, 0.16);
  background: rgba(254, 242, 242, 0.72);
}

.empty-card {
  padding: 18px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

@media (max-width: 720px) {
  .field-grid {
    grid-template-columns: 1fr;
  }

  .field-row,
  .stat-item {
    flex-direction: column;
    gap: 6px;
  }

  .field-label,
  .field-value,
  .stat-key,
  .stat-value {
    flex: initial;
    max-width: 100%;
    width: 100%;
    text-align: left;
  }
}
</style>
