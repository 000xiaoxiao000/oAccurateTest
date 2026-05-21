<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Coverage Center</div>
        <h1>覆盖率中心</h1>
        <p class="subtext">按应用查看版本、已生成报告，并进入新的覆盖率详情页。</p>
      </div>
    </div>

    <div class="selector-row">
      <label class="field grow">
        <span>选择应用</span>
        <select v-model="selectedAppId" class="text-input">
          <option value="">请选择应用</option>
          <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.name }}</option>
        </select>
      </label>
      <button class="ghost-button" type="button" @click="loadApps">刷新应用</button>
    </div>

    <div v-if="error" class="status-card error">{{ error }}</div>

    <template v-if="selectedCenter">
      <section class="panel">
        <div class="panel-head">
          <h2>版本入口</h2>
          <span>{{ selectedCenter.versions.length }}</span>
        </div>
        <div class="card-grid">
          <article v-for="version in selectedCenter.versions" :key="version.id" class="card">
            <div class="card-top">
              <strong>{{ version.versionNumber }}</strong>
              <span v-if="version.current" class="tag">当前版本</span>
            </div>
            <p class="subtext">{{ version.describe || '暂无描述' }}</p>
            <div class="meta-list">
              <span>{{ version.repoBranch || '-' }}</span>
              <span>{{ version.repoCommitId || '-' }}</span>
              <span>{{ version.createTimeRelativeText || version.createTimeText || '-' }}</span>
            </div>
            <RouterLink
              class="table-link"
              :to="{
                name: 'coverage-overview',
                params: { projectId, appId: selectedAppId },
                query: { versionNumber: version.versionNumber, commitId: version.repoCommitId || undefined },
              }"
            >
              查看覆盖率概览
            </RouterLink>
          </article>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>已生成报告</h2>
          <span>{{ selectedCenter.coverageReports.length }}</span>
        </div>
        <div class="card-grid">
          <article v-for="report in selectedCenter.coverageReports" :key="report.id" class="card">
            <div class="card-top">
              <strong>{{ report.versionNumber || '未命名版本' }}</strong>
              <span :class="['tag', report.reportType === 1 ? 'increment' : 'full']">
                {{ report.reportType === 1 ? '增量' : '全量' }}
              </span>
            </div>
            <div class="meta-list">
              <span>类 {{ report.coveredClasses }} / {{ report.totalClasses }}</span>
              <span>方法 {{ report.coveredMethods }} / {{ report.totalMethods }}</span>
              <span>代码行 {{ report.coveredLines }} / {{ report.totalLines }}</span>
            </div>
            <div class="meta-list">
              <span>{{ report.repoBranch || '-' }}</span>
              <span>{{ report.repoCommitId || '-' }}</span>
              <span>{{ report.createTimeRelativeText || report.createTimeText || '-' }}</span>
            </div>
            <div class="action-row">
              <RouterLink
                class="table-link"
                :to="{
                  name: 'coverage-overview',
                  params: { projectId, appId: selectedAppId },
                  query: {
                    versionNumber: report.versionNumber || undefined,
                    reportId: report.id,
                    commitId: report.repoCommitId || undefined,
                  },
                }"
              >
                打开报告
              </RouterLink>
              <span v-if="report.hasNewerData" class="warn-text">有新数据待重新生成</span>
            </div>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchProjectApps, fetchVersionCenter } from '@/api/bootstrap'
import type { AppSummary, VersionCenterPayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = ref<AppSummary[]>([])
const centers = ref<Record<string, VersionCenterPayload>>({})
const selectedAppId = ref('')
const error = ref('')

const selectedCenter = computed(() => centers.value[selectedAppId.value])

async function loadApps() {
  error.value = ''
  try {
    apps.value = await fetchProjectApps(projectId.value)
    if (!selectedAppId.value && apps.value.length) {
      selectedAppId.value = apps.value[0].id
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用失败'
  }
}

async function loadCenter(appId: string) {
  if (!appId || centers.value[appId]) {
    return
  }
  error.value = ''
  try {
    centers.value = {
      ...centers.value,
      [appId]: await fetchVersionCenter(projectId.value, appId),
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载覆盖率中心失败'
  }
}

watch(selectedAppId, (appId) => {
  void loadCenter(appId)
})

onMounted(loadApps)
</script>

<style scoped>
.page-header,
.selector-row,
.panel-head,
.card-top,
.meta-list,
.action-row {
  display: flex;
  gap: 12px;
}

.page-header,
.panel-head,
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
.field span,
.meta-list {
  color: #64748b;
}

.grow {
  flex: 1;
}

.field {
  display: grid;
  gap: 8px;
}

.text-input,
.ghost-button {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.ghost-button {
  background: rgba(15, 23, 42, 0.06);
  cursor: pointer;
}

.panel,
.card,
.status-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 18px;
}

.status-card.error,
.warn-text {
  color: #b91c1c;
}

.card-grid {
  display: grid;
  gap: 12px;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.tag.full {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.tag.increment {
  background: rgba(234, 88, 12, 0.12);
  color: #c2410c;
}

.table-link {
  color: #0f766e;
  font-weight: 700;
}
</style>
