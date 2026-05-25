<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Version Center</div>
        <h1>版本中心</h1>
        <p class="subtext">选择应用进入版本中心，管理版本、提交和覆盖率报告。</p>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载应用列表...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <div v-else class="card-grid">
      <article v-for="app in apps" :key="app.id" class="card">
        <div class="card-top">
          <strong>{{ app.name }}</strong>
          <span class="tag">{{ app.currentVersion || '未设当前版本' }}</span>
        </div>
        <p class="subtext">{{ app.describe || '暂无应用描述' }}</p>
        <div class="meta-list">
          <span>在线 {{ app.onlineCount }}</span>
          <span>{{ app.repoConfigured ? '已配置仓库' : '未配置仓库' }}</span>
        </div>
        <div class="action-row">
          <RouterLink class="table-link" :to="`/p/${projectId}/apps/${app.id}/versions`">版本列表</RouterLink>
          <RouterLink class="table-link" :to="`/p/${projectId}/apps/${app.id}/compare`">比对与报告</RouterLink>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchProjectApps } from '@/api/bootstrap'
import type { AppSummary } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = ref<AppSummary[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    apps.value = await fetchProjectApps(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用列表失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.card-top,
.meta-list,
.action-row {
  display: flex;
  gap: 12px;
}

.page-header,
.card-top,
.meta-list,
.action-row {
  justify-content: space-between;
  align-items: center;
}

.page-header {
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.meta-list {
  color: #64748b;
}

.status-card,
.card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.card-grid {
  display: grid;
  gap: 12px;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.table-link {
  color: #0f766e;
  font-weight: 700;
}
</style>
