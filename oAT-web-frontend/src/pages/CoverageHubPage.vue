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
      <label class="field search-field">
        <span>搜索版本/报告</span>
        <input v-model.trim="keyword" class="text-input" type="search" placeholder="版本号、分支、Commit" aria-label="搜索覆盖率版本或报告" />
      </label>
      <button class="ghost-button" type="button" @click="loadApps">刷新应用</button>
    </div>

    <div v-if="error" class="status-card error">{{ error }}</div>

    <template v-if="selectedCenter">
      <section class="panel">
        <div class="panel-head">
          <h2>版本入口</h2>
          <span>{{ filteredVersions.length }} / {{ selectedCenter.versions.length }}</span>
        </div>
        <div class="card-grid">
          <article v-for="version in paginatedVersions" :key="version.id" class="card">
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
        <div v-if="!filteredVersions.length" class="empty-card compact">暂无匹配版本</div>
        <AppPagination
          v-if="filteredVersions.length > 0"
          v-model:page="versionPage"
          v-model:page-size="versionPageSize"
          :total="filteredVersions.length"
          item-name="版本"
          :page-sizes="[6, 12, 24, 48]"
        />
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>已生成报告</h2>
          <span>{{ filteredReports.length }} / {{ selectedCenter.coverageReports.length }}</span>
        </div>
        <div class="card-grid">
          <article v-for="report in paginatedReports" :key="report.id" class="card">
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
        <div v-if="!filteredReports.length" class="empty-card compact">暂无匹配报告</div>
        <AppPagination
          v-if="filteredReports.length > 0"
          v-model:page="reportPage"
          v-model:page-size="reportPageSize"
          :total="filteredReports.length"
          item-name="报告"
          :page-sizes="[6, 12, 24, 48]"
        />
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchProjectApps, fetchVersionCenter } from '@/api/bootstrap'
import AppPagination from '@/components/AppPagination.vue'
import type { AppSummary, VersionCenterPayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const apps = ref<AppSummary[]>([])
const centers = ref<Record<string, VersionCenterPayload>>({})
const selectedAppId = ref('')
const error = ref('')
const keyword = ref('')
const versionPage = ref(1)
const versionPageSize = ref(6)
const reportPage = ref(1)
const reportPageSize = ref(6)

const selectedCenter = computed(() => centers.value[selectedAppId.value])
const keywordTerm = computed(() => keyword.value.toLowerCase())
const filteredVersions = computed(() => {
  const versions = selectedCenter.value?.versions || []
  const term = keywordTerm.value
  if (!term) return versions
  return versions.filter((version) => [
    version.versionNumber,
    version.describe,
    version.repoBranch,
    version.repoCommitId,
    version.sourceType,
  ].some((value) => String(value || '').toLowerCase().includes(term)))
})
const filteredReports = computed(() => {
  const reports = selectedCenter.value?.coverageReports || []
  const term = keywordTerm.value
  if (!term) return reports
  return reports.filter((report) => [
    report.versionNumber,
    report.repoBranch,
    report.repoCommitId,
    report.reportType === 1 ? '增量' : '全量',
  ].some((value) => String(value || '').toLowerCase().includes(term)))
})
const paginatedVersions = computed(() => {
  const start = (versionPage.value - 1) * versionPageSize.value
  return filteredVersions.value.slice(start, start + versionPageSize.value)
})
const paginatedReports = computed(() => {
  const start = (reportPage.value - 1) * reportPageSize.value
  return filteredReports.value.slice(start, start + reportPageSize.value)
})

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
  versionPage.value = 1
  reportPage.value = 1
  void loadCenter(appId)
})

watch(keyword, () => {
  versionPage.value = 1
  reportPage.value = 1
})

watch([versionPageSize, () => filteredVersions.value.length], ([, total]) => {
  const totalPages = Math.max(1, Math.ceil(total / versionPageSize.value))
  if (versionPage.value > totalPages) {
    versionPage.value = totalPages
  }
})

watch([reportPageSize, () => filteredReports.value.length], ([, total]) => {
  const totalPages = Math.max(1, Math.ceil(total / reportPageSize.value))
  if (reportPage.value > totalPages) {
    reportPage.value = totalPages
  }
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

.selector-row {
  position: sticky;
  top: 12px;
  z-index: 4;
  align-items: end;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.search-field {
  min-width: min(360px, 100%);
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
.status-card,
.empty-card {
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
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  max-height: min(520px, calc(100vh - 300px));
  overflow: auto;
  padding-right: 4px;
}

.empty-card.compact {
  margin-top: 12px;
  padding: 14px;
  text-align: center;
  color: #64748b;
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
