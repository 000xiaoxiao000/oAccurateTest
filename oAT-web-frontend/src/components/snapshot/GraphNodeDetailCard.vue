<template>
  <div v-if="detail" class="node-card detail-card">
    <strong>{{ detail.title || detail.name || detail.id }}</strong>
    <span>{{ detail.type || '-' }}</span>
    <small>{{ detail.ip || '-' }}</small>
    <div class="stat-list">
      <div v-for="(value, key) in detail.stats" :key="key" class="stat-item">
        <span>{{ key }}</span>
        <strong>{{ String(value) }}</strong>
      </div>
    </div>

    <section v-for="section in detail.sections" :key="section.title" class="detail-section">
      <h3>{{ section.title }}</h3>
      <div v-if="section.fields?.length" class="field-grid">
        <div v-for="field in section.fields" :key="`${section.title}-${field.label}`" class="field-row">
          <span>{{ field.label }}</span>
          <strong>{{ displayValue(field.value) }}</strong>
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
        <pre>{{ sql.sql }}</pre>
        <p v-if="sql.params?.length" class="muted">参数：{{ sql.params.map((param) => param.join(', ')).join(' | ') }}</p>
      </article>
      <article v-for="(sql, index) in detail.sqlStatements" :key="`sql-${index}-${sql.sql}`" class="trace-block sql-block">
        <div class="trace-head">
          <span class="pill">{{ sql.executes?.join('/') || sql.type || 'SQL' }}</span>
          <strong>{{ sql.databaseName || sql.jdbcUrl || 'SQL语句' }}</strong>
          <small v-if="sql.useTime !== undefined">{{ sql.useTime }} ms</small>
        </div>
        <pre>{{ sql.sql }}</pre>
        <p v-if="sql.params?.length" class="muted">参数：{{ sql.params.map((param) => param.join(', ')).join(' | ') }}</p>
        <p v-if="sql.error" class="error-text">{{ sql.error.type || '异常' }}：{{ sql.error.message }}</p>
      </article>
    </section>

    <section v-if="hasList(detail.tableOperations)" class="detail-section">
      <h3>表操作统计</h3>
      <div class="operation-list">
        <div v-for="operation in detail.tableOperations" :key="`${operation.action}-${operation.tableName}-${operation.columns?.join(',')}`" class="operation-row">
          <span class="operation-badge" :class="operationClass(operation.action)">{{ operation.action || operation.model || '-' }}</span>
          <strong>{{ operation.tableName || '-' }}</strong>
          <small>{{ operation.columns?.join(', ') || '无字段' }}</small>
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

    <pre v-if="detail.logPreview" class="log-preview">{{ detail.logPreview }}</pre>
  </div>
  <div v-else class="empty-card">{{ emptyText }}</div>
</template>

<script setup lang="ts">
import type { GraphNodeDetailPayload } from '@/api/types'

defineProps<{
  detail?: GraphNodeDetailPayload | null
  emptyText?: string
}>()

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
</script>

<style scoped>
.node-card {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.node-card span,
.node-card small {
  color: #64748b;
}

.detail-card {
  gap: 10px;
}

.stat-list {
  display: grid;
  gap: 8px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.stat-item span {
  color: #64748b;
}

.detail-section {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.detail-section h3 {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
}

.field-grid,
.operation-list {
  display: grid;
  gap: 8px;
}

.field-row,
.operation-row,
.trace-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.field-row span,
.operation-row small,
.muted {
  color: #64748b;
}

.field-row strong,
.operation-row strong {
  min-width: 0;
  max-width: 65%;
  color: #0f172a;
  font-size: 13px;
  text-align: right;
  word-break: break-word;
}

.trace-block {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.trace-block pre,
.detail-pre,
.log-preview {
  margin: 0;
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.trace-head strong {
  min-width: 0;
  flex: 1;
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
</style>
