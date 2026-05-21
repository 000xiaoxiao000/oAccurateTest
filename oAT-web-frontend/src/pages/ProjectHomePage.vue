<template>
  <section class="project-home">
    <div v-if="loading" class="status-card">正在加载项目上下文...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="context">
      <div class="hero" :style="{ '--hero-accent': context.ai.mascotPrimary }">
        <div>
          <div class="eyebrow">Project Context</div>
          <h1>{{ context.project.name }}</h1>
          <p>{{ context.project.describe || '暂无项目描述' }}</p>
        </div>
        <div class="hero-stats">
          <div class="hero-stat">
            <strong>{{ context.appCount }}</strong>
            <span>应用总数</span>
          </div>
          <div class="hero-stat">
            <strong>{{ context.onlineAppCount }}</strong>
            <span>在线应用</span>
          </div>
          <div class="hero-stat">
            <strong>{{ context.currentUserRole }}</strong>
            <span>当前角色</span>
          </div>
        </div>
      </div>

      <div class="panel-grid">
        <section class="panel">
          <div class="panel-head">
            <h2>应用上下文</h2>
            <span>{{ context.apps.length }} 个应用</span>
          </div>
          <div class="app-list">
            <article v-for="app in context.apps" :key="app.id" class="app-card">
              <div class="app-card-top">
                <RouterLink class="app-link" :to="`/p/${projectId}/apps/${app.id}/settings`">{{ app.name }}</RouterLink>
                <span :class="app.onlineCount > 0 ? 'tag online' : 'tag offline'">
                  {{ app.onlineCount > 0 ? '在线' : '离线' }}
                </span>
              </div>
              <div class="app-card-meta">
                <span>实例 {{ app.onlineCount }}</span>
                <span>{{ app.currentVersion || '未设置版本' }}</span>
              </div>
              <div class="app-card-actions">
                <RouterLink :to="`/p/${projectId}/apps/${app.id}/snapshots`">系统快照</RouterLink>
                <RouterLink :to="`/p/${projectId}/apps/${app.id}/api-endpoints`">接口扫描</RouterLink>
                <RouterLink :to="`/p/${projectId}/apps/${app.id}/probe-alerts`">探针告警</RouterLink>
                <RouterLink :to="`/p/${projectId}/apps/${app.id}/repository`">仓库配置</RouterLink>
                <RouterLink :to="`/p/${projectId}/monitor?appId=${app.id}`">实时监控</RouterLink>
              </div>
            </article>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>AI 能力</h2>
            <RouterLink class="ai-entry" :to="`/p/${projectId}/ai`">
              {{ context.ai.enabled ? '进入工作台' : '未启用' }}
            </RouterLink>
          </div>
          <ul class="feature-list">
            <li>工作台入口：{{ context.ai.interactivePath }}</li>
            <li>问答接口：{{ context.ai.askApiPath }}</li>
            <li>反馈接口：{{ context.ai.feedbackApiBasePath }}</li>
            <li>超时时间：{{ context.ai.timeout }} 秒</li>
          </ul>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>项目导航</h2>
            <span>已迁移模块</span>
          </div>
          <div class="quick-grid">
            <RouterLink :to="`/p/${projectId}/apps`">应用管理</RouterLink>
            <RouterLink :to="`/p/${projectId}/my-snapshots`">我的快照</RouterLink>
            <RouterLink :to="`/p/${projectId}/usecases`">测试用例</RouterLink>
            <RouterLink :to="`/p/${projectId}/members`">项目成员</RouterLink>
            <RouterLink :to="`/p/${projectId}/labels`">标签管理</RouterLink>
            <RouterLink :to="`/p/${projectId}/apps/online`">在线实例</RouterLink>
            <RouterLink :to="`/p/${projectId}/monitor`">实时监控</RouterLink>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>分析中心</h2>
            <span>已切换到新前端</span>
          </div>
          <div class="quick-grid">
            <RouterLink :to="`/p/${projectId}/version/apps`">版本中心</RouterLink>
            <RouterLink :to="`/p/${projectId}/coverage`">覆盖率概览</RouterLink>
            <RouterLink :to="`/p/${projectId}/map/home`">链路地图</RouterLink>
            <RouterLink :to="`/p/${projectId}/search`">搜索中心</RouterLink>
          </div>
        </section>
      </div>

      <section class="legacy-grid">
        <article class="panel legacy-panel">
          <div class="panel-head">
            <h2>项目动态</h2>
            <span>{{ context.recentLogs?.length || 0 }} 条</span>
          </div>
          <div v-if="context.recentLogs?.length" class="activity-list">
            <article v-for="log in context.recentLogs" :key="log.id || `${log.title}-${log.createTime}`" class="activity-item">
              <div class="activity-title" v-html="log.title || '-'"></div>
              <p>{{ log.message || '暂无描述' }}</p>
              <span>{{ formatTime(log.createTime) }}</span>
            </article>
          </div>
          <div v-else class="empty-card subtle">暂无项目动态</div>
        </article>

        <article class="panel legacy-panel">
          <div class="panel-head">
            <h2>我最近的快照</h2>
            <RouterLink class="legacy-link" :to="`/p/${projectId}/my-snapshots`">更多</RouterLink>
          </div>
          <div v-if="context.recentSnapshots?.length" class="snapshot-list">
            <article v-for="snapshot in context.recentSnapshots" :key="snapshot.id" class="legacy-snapshot">
              <RouterLink class="snapshot-link" :to="`/p/${projectId}/my-snapshots/${snapshot.id}`">
                {{ snapshot.name || snapshot.id }}
              </RouterLink>
              <span>{{ snapshot.createTimeText || snapshot.updateTimeText || snapshot.updateTimeRelativeText || '-' }}</span>
            </article>
          </div>
          <div v-else class="empty-card subtle">暂无快照</div>
        </article>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const loading = ref(false)
const error = ref('')

const projectId = computed(() => String(route.params.projectId || ''))
const context = computed(() => projectStore.contextByProjectId[projectId.value])

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleString('zh-CN', { hour12: false })
}

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadProjectContext(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载项目上下文失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.status-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 18px;
  padding: 26px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--hero-accent) 22%, white) 0%, transparent 36%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(239, 247, 248, 0.94));
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 22px 48px rgba(15, 23, 42, 0.08);
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.hero p {
  color: #5b6b79;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-stat {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.hero-stat strong {
  display: block;
  font-size: 22px;
}

.hero-stat span {
  color: #6b7280;
  font-size: 13px;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.legacy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.panel {
  padding: 22px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.app-list {
  display: grid;
  gap: 12px;
}

.app-card {
  padding: 16px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.app-card-top,
.app-card-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.app-card-meta {
  margin-top: 8px;
  color: #6b7280;
  font-size: 13px;
}

.app-card-actions {
  display: flex;
  gap: 12px;
  margin-top: 10px;
}

.app-link,
.app-card-actions a,
.ai-entry {
  color: #0f766e;
  font-weight: 700;
}

.tag {
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
}

.tag.online {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.tag.offline {
  background: rgba(148, 163, 184, 0.18);
  color: #475569;
}

.feature-list {
  margin: 0;
  padding-left: 18px;
  color: #4b5563;
}

.feature-list li + li {
  margin-top: 10px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-grid a {
  padding: 14px 16px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
  color: #0f766e;
  font-weight: 700;
}

.legacy-panel {
  min-height: 220px;
}

.legacy-link {
  color: #9a3412;
  font-weight: 700;
}

.snapshot-link {
  color: #0f766e;
  font-weight: 700;
}

.activity-list,
.snapshot-list {
  display: grid;
  gap: 12px;
}

.activity-item,
.legacy-snapshot {
  padding: 14px 16px;
  border-radius: 18px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.activity-item p {
  margin: 8px 0 0;
  color: #475569;
}

.activity-item span,
.legacy-snapshot span {
  display: block;
  margin-top: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.activity-title :deep(a) {
  color: #0f766e;
  font-weight: 700;
}

.subtle {
  color: #94a3b8;
}

@media (max-width: 960px) {
  .hero,
  .panel-grid,
  .legacy-grid {
    grid-template-columns: 1fr;
  }

  .hero-stats {
    grid-template-columns: 1fr;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
