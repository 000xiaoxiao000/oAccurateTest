<template>
  <div class="shell">
    <header class="shell-header">
      <div class="page-shell shell-header-inner">
        <div>
          <div class="shell-brand">oAT Frontend</div>
          <div class="shell-subtitle">前后端分离工作台</div>
        </div>
        <nav class="shell-nav">
          <RouterLink v-if="projectId" :to="`/p/${projectId}/map/home`">搜索</RouterLink>
          <div v-if="projectId" class="nav-dropdown">
            <RouterLink class="nav-dropdown-trigger" :to="`/p/${projectId}/monitor`">监控台</RouterLink>
            <div class="nav-menu compact">
              <RouterLink :to="`/p/${projectId}/monitor`">实时监控</RouterLink>
              <RouterLink :to="`/p/${projectId}/my-snapshots`">我的快照</RouterLink>
            </div>
          </div>
          <div v-if="projectId" class="nav-dropdown app-center">
            <RouterLink class="nav-dropdown-trigger" :to="`/p/${projectId}/apps`">应用中心</RouterLink>
            <div class="nav-menu app-menu">
              <input v-model.trim="appKeyword" class="menu-search" type="text" placeholder="搜索应用..." />
              <RouterLink :to="`/p/${projectId}/apps/online`">在线应用</RouterLink>
              <div class="menu-divider"></div>
              <div v-for="app in filteredApps" :key="app.id" class="app-menu-item">
                <RouterLink class="app-main-link" :to="`/p/${projectId}/apps/${app.id}/snapshots`" :title="appTitle(app)">
                  {{ app.name }} <small>({{ appVersionText(app) }})</small>
                </RouterLink>
                <span class="sub-menu-actions">
                  <RouterLink title="系统快照" :to="`/p/${projectId}/apps/${app.id}/snapshots`">📷</RouterLink>
                  <RouterLink title="版本比对" :to="`/p/${projectId}/apps/${app.id}/compare`">⚖</RouterLink>
                  <RouterLink title="覆盖率报告" :to="`/p/${projectId}/apps/${app.id}/coverage`">📊</RouterLink>
                </span>
              </div>
              <div v-if="!filteredApps.length" class="empty-menu-item">暂无应用</div>
            </div>
          </div>
          <RouterLink v-if="projectId && aiEnabled" :to="`/p/${projectId}/ai`">AI Interactive</RouterLink>
          <RouterLink to="/projects">项目列表</RouterLink>
          <template v-if="currentUser">
            <div class="nav-dropdown create-menu" v-if="projectId">
              <button class="icon-trigger" type="button">＋</button>
              <div class="nav-menu compact right-aligned">
                <RouterLink to="/projects?create=1">创建新项目</RouterLink>
                <RouterLink :to="`/p/${projectId}/apps?create=1`">添加应用</RouterLink>
              </div>
            </div>
            <RouterLink v-if="projectId" class="icon-trigger" :to="`/projects?edit=${projectId}`" title="设置">⚙</RouterLink>
            <div class="nav-dropdown project-switcher">
              <button class="project-trigger" type="button">{{ currentProjectName }} <span>⌄</span></button>
              <div class="nav-menu project-menu right-aligned">
                <input v-model.trim="projectKeyword" class="menu-search" type="text" placeholder="搜索项目..." />
                <RouterLink v-for="project in filteredProjects" :key="project.id" :to="`/p/${project.id}/home`">
                  {{ project.name }}
                </RouterLink>
                <div v-if="!filteredProjects.length" class="empty-menu-item">暂无项目</div>
              </div>
            </div>
            <div class="nav-dropdown user-menu">
              <button class="icon-trigger" type="button">👤</button>
              <div class="nav-menu compact right-aligned">
                <RouterLink class="shell-user-link" to="/account">用户设置</RouterLink>
                <span class="disabled-menu-item">管理面板</span>
                <button type="button" @click="handleLogout">注销退出</button>
              </div>
            </div>
          </template>
        </nav>
      </div>
    </header>
    <main class="page-shell shell-main">
      <AppBreadcrumbs />
      <RouterView />
    </main>
    <AiFloatingAssistant v-if="route.name !== 'project-ai'" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/project'
import AiFloatingAssistant from '@/components/AiFloatingAssistant.vue'
import AppBreadcrumbs from '@/components/AppBreadcrumbs.vue'
import type { AppSummary } from '@/api/types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const projectStore = useProjectStore()
const { currentUser } = storeToRefs(authStore)
const { projects } = storeToRefs(projectStore)
const projectKeyword = ref('')
const appKeyword = ref('')
const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const context = computed(() => projectId.value ? projectStore.contextByProjectId[projectId.value] : undefined)
const apps = computed(() => context.value?.apps || projectStore.appsByProjectId[projectId.value] || [])
const aiEnabled = computed(() => context.value?.ai.enabled !== false)
const currentProjectName = computed(() => context.value?.project.name || projects.value.find((project) => project.id === projectId.value)?.name || '项目列表')
const filteredProjects = computed(() => {
  const keyword = projectKeyword.value.toLowerCase()
  return projects.value.filter((project) => !keyword || `${project.name} ${project.describe || ''}`.toLowerCase().includes(keyword))
})
const filteredApps = computed(() => {
  const keyword = appKeyword.value.toLowerCase()
  return apps.value.filter((app) => !keyword || `${app.name} ${app.srcName || ''} ${app.currentVersion || ''}`.toLowerCase().includes(keyword)).slice(0, 12)
})

watch(projectId, async (value) => {
  if (!value) return
  await projectStore.loadProjectContext(value)
}, { immediate: true })

onMounted(() => {
  if (currentUser.value && projects.value.length === 0) {
    projectStore.loadProjects().catch(() => undefined)
  }
})

function appVersionText(app: AppSummary) {
  const version = app.currentVersion || '未设置'
  return app.currentBranch ? `${version}/${app.currentBranch}` : version
}

function appTitle(app: AppSummary) {
  return `${app.name}\n版本: ${app.currentVersion || '未设置'}${app.currentBranch ? `\n分支: ${app.currentBranch}` : ''}${app.currentCommitId ? `\nCommit: ${app.currentCommitId}` : ''}`
}

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.shell-header {
  position: sticky;
  top: 0;
  z-index: 10;
  backdrop-filter: blur(18px);
  background: rgba(245, 250, 251, 0.85);
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.shell-header-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
}

.shell-brand {
  font-size: 20px;
  font-weight: 800;
}

.shell-subtitle {
  color: #5b6b79;
  font-size: 13px;
}

.shell-nav {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.shell-user-link {
  color: #5b6b79;
  font-weight: 600;
}

.shell-logout {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  background: #0f766e;
  color: #fff;
  cursor: pointer;
}

.nav-dropdown {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.nav-dropdown-trigger,
.project-trigger,
.icon-trigger {
  border: none;
  background: transparent;
  color: #172033;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.icon-trigger {
  display: inline-grid;
  place-items: center;
  min-width: 34px;
  min-height: 34px;
  border-radius: 999px;
  color: #0f766e;
  background: rgba(15, 118, 110, .08);
}

.project-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 180px;
  color: #5b6b79;
}

.nav-menu {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 30;
  min-width: 220px;
  display: none;
  padding: 10px;
  border: 1px solid rgba(15, 23, 42, .10);
  border-radius: 16px;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 22px 48px rgba(15, 23, 42, .14);
}

.nav-dropdown:hover .nav-menu,
.nav-dropdown:focus-within .nav-menu {
  display: grid;
  gap: 4px;
}

.nav-menu.right-aligned {
  left: auto;
  right: 0;
}

.nav-menu a,
.nav-menu button,
.disabled-menu-item,
.empty-menu-item {
  display: block;
  width: 100%;
  border: none;
  border-radius: 10px;
  padding: 9px 10px;
  background: transparent;
  color: #172033;
  text-align: left;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.nav-menu a:hover,
.nav-menu button:hover,
.app-menu-item:hover {
  background: rgba(15, 118, 110, .08);
  color: #0f766e;
}

.nav-menu.compact {
  min-width: 160px;
}

.project-menu {
  min-width: 250px;
  max-height: min(360px, calc(100vh - 120px));
  overflow: auto;
}

.app-menu {
  width: 390px;
  max-width: calc(100vw - 24px);
  max-height: min(480px, calc(100vh - 120px));
  overflow: auto;
}

.menu-search {
  width: 100%;
  margin-bottom: 6px;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 999px;
  padding: 9px 12px;
  outline: none;
}

.menu-divider {
  height: 1px;
  margin: 5px 0;
  background: rgba(15, 23, 42, .08);
}

.app-menu-item {
  position: relative;
  display: flex !important;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  border-radius: 10px;
  padding-right: 110px !important;
}

.app-menu-item small {
  color: #64748b;
  font-size: 12px;
}

.app-main-link {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-menu-actions {
  position: absolute;
  right: 8px;
  display: none;
  align-items: center;
  gap: 2px;
}

.app-menu-item:hover .sub-menu-actions {
  display: inline-flex;
}

.sub-menu-actions a {
  width: auto;
  padding: 4px 5px;
}

.disabled-menu-item,
.empty-menu-item {
  color: #94a3b8;
  cursor: default;
}

.shell-main {
  padding: 24px 0 40px;
}

@media (max-width: 900px) {
  .shell-header-inner {
    align-items: flex-start;
  }

  .shell-nav {
    gap: 10px;
  }
}
</style>
