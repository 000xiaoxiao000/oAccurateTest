<template>
  <section class="panel build-session-panel">
    <div class="panel-head typed-panel-head">
      <div>
        <h2>构建会话</h2>
        <p class="subtext">按 buildId 聚合覆盖率报告，区分不同测试阶段。</p>
      </div>
      <span class="count-badge">{{ sessions.length }}</span>
    </div>
    <div v-if="sessions.length" class="build-session-list">
      <article v-for="session in visibleSessions" :key="session.buildId || `${session.versionNumber}-${session.repoCommitId}`" class="build-session-item">
        <div class="build-session-main">
          <strong>{{ session.buildId || '历史构建' }}</strong>
          <span>{{ session.versionNumber || '-' }} · {{ shortHash(session.repoCommitId) }}</span>
        </div>
        <div class="build-session-meta">
          <span>{{ session.totalReports || 0 }} 报告</span>
          <span>阶段 {{ session.testStages?.join(', ') || '-' }}</span>
          <span>行 {{ coverageRate(session.coveredLines, session.totalLines) }}</span>
          <span>分支 {{ coverageRate(session.coveredBranchTargets, session.totalBranchTargets) }}</span>
        </div>
      </article>
    </div>
    <div v-else class="empty-card">暂无构建会话数据</div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BuildSession } from '@/entities/coverage/model'

const props = defineProps<{
  sessions: BuildSession[]
}>()

const visibleSessions = computed(() => props.sessions.slice(0, 6))

function coverageRate(covered?: number, total?: number) {
  if (!total) return '0%'
  return `${(((covered || 0) / total) * 100).toFixed(1)}%`
}

function shortHash(value?: string) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 12)}...${value.slice(-6)}` : value
}
</script>

<style scoped>
.panel,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.typed-panel-head {
  align-items: flex-start;
}

.typed-panel-head h2,
.typed-panel-head .subtext {
  margin: 0;
}

.typed-panel-head .subtext {
  margin-top: 4px;
}

.subtext {
  color: #64748b;
}

.count-badge {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, .08);
  color: #2563eb;
  font-size: 13px;
  font-weight: 800;
}

.build-session-panel,
.build-session-list,
.build-session-main {
  display: grid;
  gap: 12px;
}

.build-session-list,
.build-session-main {
  gap: 10px;
}

.build-session-item {
  display: grid;
  grid-template-columns: minmax(180px, .9fr) minmax(260px, 1.4fr);
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: var(--oat-radius-md);
  background: rgba(248, 250, 252, 0.74);
}

.build-session-main,
.build-session-meta {
  min-width: 0;
}

.build-session-main {
  gap: 4px;
}

.build-session-main strong,
.build-session-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.build-session-main strong {
  color: #0f172a;
}

.build-session-main span {
  color: #64748b;
  font-size: 13px;
}

.build-session-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.build-session-meta span {
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 960px) {
  .typed-panel-head,
  .build-session-meta {
    justify-content: flex-start;
  }

  .build-session-item {
    grid-template-columns: 1fr;
  }
}
</style>
