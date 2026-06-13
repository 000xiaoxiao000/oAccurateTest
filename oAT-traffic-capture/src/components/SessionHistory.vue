<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { TrafficRecord } from '../types/traffic'

interface SessionItem {
  id: string
  case_name: string
  start_time: number
  end_time: number | null
  record_count: number
}

const emit = defineEmits<{ load: [records: TrafficRecord[]] }>()

const sessions = ref<SessionItem[]>([])
const loadingId = ref('')

onMounted(loadSessions)

async function loadSessions() {
  sessions.value = (await window.electronAPI?.listSessions()) ?? []
}

async function loadSession(id: string) {
  loadingId.value = id
  try {
    const records = (await window.electronAPI?.loadSession(id)) ?? []
    emit('load', records)
  } finally {
    loadingId.value = ''
  }
}

async function removeSession(id: string) {
  if (!confirm('删除该历史会话？')) return

  const result = await window.electronAPI?.deleteSession(id)
  if (result?.success) {
    sessions.value = sessions.value.filter((session) => session.id !== id)
  }
}

function formatDate(timestamp: number) {
  return new Date(timestamp).toLocaleString('zh-CN')
}
</script>

<template>
  <section class="session-history">
    <div class="section-title">
      <span>历史会话</span>
      <button class="refresh-btn" @click="loadSessions">刷新</button>
    </div>
    <div v-if="sessions.length === 0" class="empty">暂无历史记录</div>
    <div v-for="session in sessions" :key="session.id" class="session-item">
      <div class="session-info">
        <span class="case-name">{{ session.case_name }}</span>
        <span class="meta">{{ formatDate(session.start_time) }} · {{ session.record_count }} 条</span>
      </div>
      <div class="actions">
        <button @click="loadSession(session.id)" :disabled="loadingId === session.id">
          {{ loadingId === session.id ? '加载中' : '加载' }}
        </button>
        <button class="danger" @click="removeSession(session.id)">删除</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.session-history {
  padding: 0 30px 18px;
  background: #f0f2f5;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 10px;
}

.refresh-btn {
  padding: 3px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  color: #595959;
  font-size: 12px;
  cursor: pointer;
}

.empty {
  padding: 10px 12px;
  background: white;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-size: 13px;
  color: #8c8c8c;
}

.session-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 9px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  margin-bottom: 6px;
  background: white;
  gap: 12px;
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.case-name {
  font-size: 13px;
  font-weight: 500;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 12px;
  color: #8c8c8c;
}

.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.actions button {
  padding: 3px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: white;
  color: #595959;
  font-size: 12px;
  cursor: pointer;
}

.actions button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.actions button:hover:not(:disabled),
.refresh-btn:hover {
  border-color: #667eea;
  color: #667eea;
}

.actions button.danger:hover {
  border-color: #ff4d4f;
  color: #ff4d4f;
}
</style>
