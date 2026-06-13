<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TrafficRecord } from '../types/traffic'

const props = defineProps<{
  records: TrafficRecord[]
}>()

const emit = defineEmits<{
  delete: [id: string]
  viewDetail: [record: TrafficRecord]
}>()

const selectedIds = ref<Set<string>>(new Set())
const allSelected = computed({
  get: () => props.records.length > 0 && selectedIds.value.size === props.records.length,
  set: (val: boolean) => {
    if (val) {
      props.records.forEach(r => selectedIds.value.add(r.id))
    } else {
      selectedIds.value.clear()
    }
  }
})

function getMethodClass(method: string): string {
  const map: Record<string, string> = {
    GET: 'm-get',
    POST: 'm-post',
    PUT: 'm-put',
    DELETE: 'm-del',
    SEND: 'm-post'
  }
  return map[method] || 'm-get'
}

function getStatusClass(status: number | string): string {
  const code = Number(status)
  if (isNaN(code)) return 's-2xx'
  if (code >= 500) return 's-5xx'
  if (code >= 400) return 's-4xx'
  return 's-2xx'
}

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}
</script>

<template>
  <div class="table-wrap">
    <table>
      <thead>
        <tr>
          <th style="width: 40px">
            <input type="checkbox" v-model="allSelected" />
          </th>
          <th style="width: 160px">用例名称</th>
          <th style="width: 90px">方法</th>
          <th>请求 URL</th>
          <th style="width: 90px">状态码</th>
          <th style="width: 90px">耗时</th>
          <th style="width: 155px">时间</th>
          <th style="width: 110px">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="records.length === 0">
          <td colspan="8" class="empty-state">
            <div class="empty-icon">📭</div>
            <div>暂无流量记录</div>
            <div class="empty-hint">点击"开始捕获"后，系统将自动记录经过代理的流量</div>
          </td>
        </tr>
        <tr v-for="record in records" :key="record.id">
          <td>
            <input type="checkbox" :value="record.id" v-model="selectedIds" />
          </td>
          <td>
            <span class="case-tag">{{ record.caseName || '未命名' }}</span>
          </td>
          <td>
            <span class="badge" :class="getMethodClass(record.method)">
              {{ record.method }}
            </span>
          </td>
          <td class="url-cell" :title="record.url" @click="emit('viewDetail', record)">
            {{ record.url }}
            <span class="proto">{{ record.protocol }}</span>
          </td>
          <td>
            <span class="badge" :class="getStatusClass(record.statusCode)">
              {{ record.statusCode }}
            </span>
          </td>
          <td>{{ record.duration }}ms</td>
          <td>{{ formatTime(record.timestamp) }}</td>
          <td>
            <button class="action-btn" @click="emit('viewDetail', record)">详情</button>
            <button class="action-btn danger" @click="emit('delete', record.id)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-wrap {
  padding: 0 30px 30px;
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 16px;
}

thead {
  background: #fafafa;
}

th {
  padding: 11px 12px;
  text-align: left;
  font-weight: 600;
  color: #262626;
  font-size: 13px;
  border-bottom: 2px solid #f0f0f0;
}

td {
  padding: 14px 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  color: #595959;
}

tbody tr:hover {
  background: #fafafa;
}

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
  min-width: 56px;
}

.m-get { background: #e6f7ff; color: #1890ff; }
.m-post { background: #f6ffed; color: #52c41a; }
.m-put { background: #fff7e6; color: #fa8c16; }
.m-del { background: #fff1f0; color: #ff4d4f; }

.s-2xx { background: #f6ffed; color: #52c41a; }
.s-4xx { background: #fff7e6; color: #fa8c16; }
.s-5xx { background: #fff1f0; color: #ff4d4f; }

.url-cell {
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.url-cell:hover {
  color: #667eea;
}

.proto {
  font-size: 11px;
  padding: 2px 5px;
  background: #f0f0f0;
  border-radius: 3px;
  margin-left: 6px;
  color: #8c8c8c;
}

.case-tag {
  display: inline-block;
  padding: 3px 7px;
  background: #f0f0f0;
  border-radius: 3px;
  font-size: 12px;
  color: #595959;
}

.action-btn {
  background: none;
  border: none;
  color: #1890ff;
  cursor: pointer;
  padding: 3px 6px;
  font-size: 13px;
}

.action-btn:hover {
  text-decoration: underline;
}

.action-btn.danger {
  color: #ff4d4f;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #8c8c8c;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.4;
}

.empty-hint {
  font-size: 12px;
  margin-top: 8px;
  color: #bfbfbf;
}
</style>
