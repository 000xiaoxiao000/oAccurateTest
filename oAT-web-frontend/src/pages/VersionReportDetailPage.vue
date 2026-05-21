<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Compare Report</div>
        <h1>{{ payload?.report?.jobName || reportId }}</h1>
        <p class="subtext">{{ payload?.app?.name || '版本比对报告' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink v-if="appId" class="secondary-link" :to="`/p/${projectId}/apps/${appId}/compare`">返回比对中心</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载比对报告...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <div v-else-if="payload?.state === 'pending'" class="status-card">{{ payload.retryMessage || '报告生成中...' }}</div>
    <template v-else-if="payload?.report">
      <div class="hero-grid">
        <article class="hero-card">
          <span>类变更</span>
          <strong>{{ payload.report.addClassCount + payload.report.updateClassCount + payload.report.deleteClassCount }}</strong>
        </article>
        <article class="hero-card">
          <span>方法变更</span>
          <strong>{{ payload.report.addMethodCount + payload.report.updateMethodCount + payload.report.deleteMethodCount }}</strong>
        </article>
        <article class="hero-card">
          <span>影响用例</span>
          <strong>{{ payload.report.impactCaseCount }}</strong>
        </article>
      </div>

      <section class="panel">
        <div class="panel-head">
          <h2>报告元信息</h2>
        </div>
        <div class="info-grid">
          <div class="info-item"><span>旧版本</span><strong>{{ payload.report.targetVersion || '-' }}</strong></div>
          <div class="info-item"><span>新版本</span><strong>{{ payload.report.sourceVersion || '-' }}</strong></div>
          <div class="info-item"><span>分支</span><strong>{{ payload.report.gitBranch || '-' }}</strong></div>
          <div class="info-item"><span>旧 Commit</span><strong>{{ payload.report.gitOldCommit || '-' }}</strong></div>
          <div class="info-item"><span>新 Commit</span><strong>{{ payload.report.gitNewCommit || '-' }}</strong></div>
          <div class="info-item"><span>生成时间</span><strong>{{ payload.report.createTimeText || '-' }}</strong></div>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>差异类与方法</h2>
          <span>{{ payload.differences?.length || 0 }}</span>
        </div>
        <div class="diff-list">
          <article v-for="item in payload.differences || []" :key="item.className" class="diff-card">
            <div class="diff-top">
              <strong>{{ item.className }}</strong>
              <span class="tag">{{ item.model || 'update' }}</span>
            </div>
            <div v-if="item.methods.length" class="method-list">
              <div v-for="method in item.methods" :key="`${method.methodName}-${method.methodDesc}`" class="method-item">
                <span>{{ method.methodName }}</span>
                <small>{{ method.methodDesc || '-' }}</small>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>影响用例</h2>
          <span>{{ payload.usecases?.length || 0 }}</span>
        </div>
        <div class="diff-list">
          <article v-for="item in payload.usecases || []" :key="item.id" class="diff-card">
            <div class="diff-top">
              <RouterLink class="result-link" :to="`/p/${projectId}/usecases/${item.id}`">{{ item.title }}</RouterLink>
              <span class="tag">{{ item.directoryPath || 'ROOT' }}</span>
            </div>
            <div class="meta-list">
              <span v-for="difference in item.differences" :key="difference">{{ difference }}</span>
            </div>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchCompareReport } from '@/api/bootstrap'
import type { VersionReportDetailPayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const reportId = computed(() => String(route.params.reportId || ''))
const appId = computed(() => String(route.query.appId || ''))
const payload = ref<VersionReportDetailPayload | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchCompareReport(projectId.value, reportId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载比对报告失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.panel-head,
.diff-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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
.info-item span,
.meta-list,
.method-item small {
  color: #64748b;
}

.secondary-link,
.result-link {
  color: #0f766e;
  font-weight: 700;
}

.status-card,
.hero-card,
.panel,
.diff-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.hero-grid,
.info-grid,
.diff-list {
  display: grid;
  gap: 14px;
}

.hero-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.panel {
  margin-top: 18px;
}

.info-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.hero-card strong,
.info-item strong {
  display: block;
  margin-top: 6px;
}

.method-list,
.meta-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.method-item {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #f8fbfb;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 960px) {
  .hero-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
