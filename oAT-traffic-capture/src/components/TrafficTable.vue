<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { TrafficRecord } from '../types/traffic'

const props = defineProps<{
  records: TrafficRecord[]
}>()

const emit = defineEmits<{
  delete: [id: string]
  viewDetail: [record: TrafficRecord]
}>()

const selectedIds = ref<Set<string>>(new Set())
const currentPage = ref(1)
const pageSize = ref(10)
const pageSizeOptions = [10, 20, 50]

const totalPages = computed(() => Math.max(1, Math.ceil(props.records.length / pageSize.value)))
const pageStart = computed(() => (currentPage.value - 1) * pageSize.value)
const pageEnd = computed(() => Math.min(pageStart.value + pageSize.value, props.records.length))
const pagedRecords = computed(() => props.records.slice(pageStart.value, pageEnd.value))

const allSelected = computed({
  get: () => pagedRecords.value.length > 0 && pagedRecords.value.every(r => selectedIds.value.has(r.id)),
  set: (val: boolean) => {
    if (val) {
      pagedRecords.value.forEach(r => selectedIds.value.add(r.id))
    } else {
      pagedRecords.value.forEach(r => selectedIds.value.delete(r.id))
    }
  }
})

const visiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(1, currentPage.value - 2)
  const end = Math.min(totalPages.value, currentPage.value + 2)
  for (let page = start; page <= end; page += 1) {
    pages.push(page)
  }
  return pages
})

watch(
  () => [props.records.length, pageSize.value],
  () => {
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
    }
    selectedIds.value = new Set([...selectedIds.value].filter(id => props.records.some(record => record.id === id)))
  },
  { flush: 'sync' }
)

function goToPage(page: number) {
  currentPage.value = Math.min(Math.max(page, 1), totalPages.value)
}

function changePageSize(event: Event) {
  pageSize.value = Number((event.target as HTMLSelectElement).value)
  currentPage.value = 1
}

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
        <tr v-for="record in pagedRecords" :key="record.id">
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
    <div v-if="records.length > 0" class="pagination">
      <div class="page-summary">
        共 {{ records.length }} 条，第 {{ pageStart + 1 }}-{{ pageEnd }} 条
      </div>
      <div class="page-controls">
        <select :value="pageSize" @change="changePageSize" aria-label="每页条数">
          <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条/页</option>
        </select>
        <button type="button" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">上一页</button>
        <button
          v-for="page in visiblePages"
          :key="page"
          type="button"
          :class="{ active: page === currentPage }"
          @click="goToPage(page)"
        >
          {{ page }}
        </button>
        <button type="button" :disabled="currentPage === totalPages" @click="goToPage(currentPage + 1)">下一页</button>
      </div>
    </div>
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

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0 0;
  font-size: 13px;
  color: #8c8c8c;
}

.page-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.page-controls select,
.page-controls button {
  height: 30px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  color: #595959;
  font-size: 13px;
}

.page-controls select {
  padding: 0 8px;
}

.page-controls button {
  min-width: 30px;
  padding: 0 10px;
}

.page-controls button.active {
  border-color: #667eea;
  background: #667eea;
  color: white;
}

.page-controls button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .page-controls {
    flex-wrap: wrap;
  }
}
</style>
