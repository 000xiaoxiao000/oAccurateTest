<template>
  <nav v-if="items.length" class="breadcrumbs" aria-label="当前位置">
    <button class="back-button" type="button" @click="goBack">返回</button>
    <RouterLink
      v-for="(item, index) in items"
      :key="`${item.label}-${index}`"
      :to="item.to || route.fullPath"
      :class="{ current: index === items.length - 1 || !item.to }"
    >
      {{ item.label }}
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useProjectStore } from '@/stores/project'

type BreadcrumbItem = { label: string; to?: string }

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const appId = computed(() => typeof route.params.appId === 'string' ? route.params.appId : '')
const context = computed(() => projectId.value ? projectStore.contextByProjectId[projectId.value] : undefined)
const currentApp = computed(() => context.value?.apps.find((app) => app.id === appId.value))
const backTarget = computed(() => {
  const name = String(route.name || '')
  const projectHome = projectId.value ? `/p/${projectId.value}/home` : '/projects'
  const projectApps = projectId.value ? `/p/${projectId.value}/apps` : '/projects'
  const projectCoverage = projectId.value ? `/p/${projectId.value}/coverage` : '/projects'
  const appSettings = projectId.value && appId.value ? `/p/${projectId.value}/apps/${appId.value}/settings` : projectHome
  const appList = projectId.value ? `/p/${projectId.value}/apps` : '/projects'

  const routeBackTargets: Record<string, string> = {
    projects: '/projects',
    'project-home': '/projects',
    'project-ai': projectHome,
    'project-apps': projectHome,
    'online-apps': appList,
    'app-settings': appList,
    'app-probe-alerts': appSettings,
    'app-repository': appSettings,
    'app-api-endpoints': appSettings,
    monitor: projectHome,
    'version-apps': projectHome,
    'version-list': `/p/${projectId.value}/version/apps`,
    'version-create': `/p/${projectId.value}/apps/${appId.value}/versions`,
    'version-compare': `/p/${projectId.value}/apps/${appId.value}/versions`,
    'version-report-detail': `/p/${projectId.value}/apps/${appId.value}/compare`,
    'coverage-hub': projectHome,
    'coverage-overview': projectCoverage,
    'coverage-details': projectCoverage,
    'coverage-code': projectCoverage,
    'map-home': projectHome,
    'map-app': `/p/${projectId.value}/map/home`,
    'map-code': `/p/${projectId.value}/map/home`,
    'search-center': `/p/${projectId.value}/map/home`,
    'system-snapshot-list': appList,
    'system-snapshot-detail': `/p/${projectId.value}/apps/${appId.value}/snapshots`,
    'system-snapshot-report': `/p/${projectId.value}/apps/${appId.value}/snapshots/${route.params.snapshotId}`,
    'system-snapshot-code': `/p/${projectId.value}/apps/${appId.value}/snapshots/${route.params.snapshotId}/report`,
    'system-snapshot-graph': `/p/${projectId.value}/apps/${appId.value}/snapshots/${route.params.snapshotId}`,
    'my-snapshot-list': projectHome,
    'my-snapshot-code-report': `/p/${projectId.value}/my-snapshots`,
    'my-snapshot-aggregate-code': `/p/${projectId.value}/my-snapshots`,
    'my-snapshot-detail': `/p/${projectId.value}/my-snapshots`,
    'my-snapshot-report': `/p/${projectId.value}/my-snapshots/${route.params.snapshotId}`,
    'my-snapshot-code': `/p/${projectId.value}/my-snapshots/${route.params.snapshotId}`,
    'my-snapshot-graph': `/p/${projectId.value}/my-snapshots/${route.params.snapshotId}`,
    'usecase-list': projectHome,
    'usecase-new': `/p/${projectId.value}/usecases`,
    'usecase-edit': `/p/${projectId.value}/usecases/${route.params.usecaseId}`,
    'usecase-detail': `/p/${projectId.value}/usecases`,
    'project-members': projectHome,
    'project-labels': projectHome,
  }

  return routeBackTargets[name] || projectHome
})

const routeLabels: Record<string, string> = {
  projects: '项目列表',
  'account-settings': '用户设置',
  'project-home': '项目首页',
  'project-ai': 'AI Interactive',
  'project-apps': '应用管理',
  'online-apps': '在线应用',
  'app-settings': '应用设置',
  'app-probe-alerts': '探针告警',
  'app-repository': '仓库配置',
  'app-api-endpoints': '接口扫描',
  monitor: '实时监控',
  'version-apps': '版本中心',
  'version-list': '版本列表',
  'version-create': '新建版本',
  'version-compare': '版本比对',
  'version-report-detail': '比对报告',
  'coverage-hub': '覆盖率中心',
  'coverage-overview': '覆盖率概览',
  'coverage-details': '覆盖率详情',
  'coverage-code': '源码覆盖',
  'map-home': '链路地图',
  'map-app': '应用地图',
  'map-code': '代码地图',
  'search-center': '搜索中心',
  'system-snapshot-list': '系统快照',
  'system-snapshot-detail': '系统快照详情',
  'system-snapshot-report': '系统快照报告',
  'system-snapshot-code': '系统快照源码',
  'system-snapshot-graph': '系统快照链路图',
  'my-snapshot-list': '我的快照',
  'my-snapshot-code-report': '我的快照代码报告',
  'my-snapshot-aggregate-code': '我的快照源码',
  'my-snapshot-detail': '我的快照详情',
  'my-snapshot-report': '我的快照报告',
  'my-snapshot-code': '我的快照源码',
  'my-snapshot-graph': '我的快照链路图',
  'usecase-list': '测试用例',
  'usecase-new': '新建用例',
  'usecase-edit': '编辑用例',
  'usecase-detail': '用例详情',
  'project-members': '项目成员',
  'project-labels': '标签管理',
}

const items = computed<BreadcrumbItem[]>(() => {
  const name = String(route.name || '')
  if (!name || name === 'not-found') return []
  if (!projectId.value) return [{ label: routeLabels[name] || '当前位置' }]

  const result: BreadcrumbItem[] = [
    { label: '项目列表', to: '/projects' },
    { label: context.value?.project.name || '项目', to: `/p/${projectId.value}/home` },
  ]

  if (appId.value) {
    result.push({ label: currentApp.value?.name || '应用', to: `/p/${projectId.value}/apps/${appId.value}/settings` })
  }

  const label = routeLabels[name] || String(route.meta.title || '当前位置')
  if (result[result.length - 1]?.label !== label) {
    result.push({ label })
  }
  return result
})

function goBack() {
  router.push(backTarget.value)
}
</script>

<style scoped>
.breadcrumbs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  color: #64748b;
  font-size: 13px;
}

.breadcrumbs a,
.back-button {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(255, 255, 255, .82);
  color: #0f766e;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
}

.breadcrumbs a::after {
  content: '/';
  margin-left: 8px;
  color: #cbd5e1;
}

.breadcrumbs a.current {
  color: #334155;
  pointer-events: none;
}

.breadcrumbs a.current::after {
  content: '';
  margin: 0;
}
</style>
