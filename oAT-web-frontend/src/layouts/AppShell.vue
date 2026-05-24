<template>
  <div ref="shellRef" class="shell">
    <header class="shell-header">
      <div class="page-shell shell-header-inner">
        <RouterLink class="brand-block" :to="projectId ? `/p/${projectId}/home` : '/projects'" aria-label="oAccurateTest" @click="closeMenus">
          <img class="brand-logo-image" src="/images/logo.png" alt="oAccurateTest" />
        </RouterLink>
        <nav class="shell-nav">
          <RouterLink v-if="projectId" :to="`/p/${projectId}/search`">搜索</RouterLink>
          <div v-if="projectId" class="nav-dropdown" :class="{ open: openMenu === 'monitor' }">
            <button class="nav-dropdown-trigger" type="button" @click.stop="toggleMenu('monitor')">监控台 <span class="menu-caret">⌄</span></button>
            <div class="nav-menu compact">
              <RouterLink :to="`/p/${projectId}/monitor`" @click="closeMenus">实时监控</RouterLink>
              <RouterLink :to="`/p/${projectId}/my-snapshots`" @click="closeMenus">我的快照</RouterLink>
              <RouterLink :to="`/p/${projectId}/apps`" @click="closeMenus">系统快照</RouterLink>
              <RouterLink :to="`/p/${projectId}/map/home`" @click="closeMenus">链路地图</RouterLink>
            </div>
          </div>
          <div v-if="projectId" class="nav-dropdown app-center" :class="{ open: openMenu === 'app' }">
            <button class="nav-dropdown-trigger" type="button" @click.stop="toggleMenu('app')">应用中心 <span class="menu-caret">⌄</span></button>
            <div class="nav-menu app-menu">
              <RouterLink class="menu-entry" :to="`/p/${projectId}/apps`" @click="closeMenus">应用总览</RouterLink>
              <input v-model.trim="appKeyword" class="menu-search" type="text" placeholder="搜索应用..." />
              <RouterLink :to="`/p/${projectId}/apps/online`" @click="closeMenus">在线应用</RouterLink>
              <div class="menu-divider"></div>
              <div v-for="app in filteredApps" :key="app.id" class="app-menu-item">
                <RouterLink class="app-main-link" :to="`/p/${projectId}/apps/${app.id}/snapshots`" :title="appTitle(app)" @click="closeMenus">
                  {{ app.name }} <small>({{ appVersionText(app) }})</small>
                </RouterLink>
                <span class="sub-menu-actions">
                  <RouterLink title="系统快照" :to="`/p/${projectId}/apps/${app.id}/snapshots`" @click="closeMenus">📷</RouterLink>
                  <RouterLink title="版本" :to="`/p/${projectId}/apps/${app.id}/versions`" @click="closeMenus">V</RouterLink>
                  <RouterLink title="版本比对" :to="`/p/${projectId}/apps/${app.id}/compare`" @click="closeMenus">⚖</RouterLink>
                  <RouterLink title="覆盖率报告" :to="`/p/${projectId}/apps/${app.id}/coverage`" @click="closeMenus">📊</RouterLink>
                  <RouterLink title="接口扫描" :to="`/p/${projectId}/apps/${app.id}/api-endpoints`" @click="closeMenus">API</RouterLink>
                  <RouterLink title="应用设置" :to="`/p/${projectId}/apps/${app.id}/settings`" @click="closeMenus">⚙</RouterLink>
                </span>
              </div>
              <div v-if="!filteredApps.length" class="empty-menu-item">暂无应用</div>
            </div>
          </div>
          <div v-if="projectId" class="nav-dropdown" :class="{ open: openMenu === 'assets' }">
            <button class="nav-dropdown-trigger" type="button" @click.stop="toggleMenu('assets')">测试资产 <span class="menu-caret">⌄</span></button>
            <div class="nav-menu compact">
              <RouterLink :to="`/p/${projectId}/version/apps`" @click="closeMenus">版本中心</RouterLink>
              <RouterLink :to="`/p/${projectId}/coverage`" @click="closeMenus">覆盖率中心</RouterLink>
              <RouterLink :to="`/p/${projectId}/usecases`" @click="closeMenus">测试用例</RouterLink>
              <RouterLink :to="`/p/${projectId}/members`" @click="closeMenus">项目成员</RouterLink>
              <RouterLink :to="`/p/${projectId}/labels`" @click="closeMenus">标签管理</RouterLink>
            </div>
          </div>
          <RouterLink v-if="projectId && aiEnabled" :to="`/p/${projectId}/ai`">AI 助手</RouterLink>
          <RouterLink to="/projects">项目列表</RouterLink>
          <template v-if="currentUser">
            <div v-if="projectId" class="nav-dropdown create-menu" :class="{ open: openMenu === 'create' }">
              <button class="icon-trigger" type="button" @click.stop="toggleMenu('create')">＋</button>
              <div class="nav-menu compact right-aligned">
                <RouterLink to="/projects?create=1" @click="closeMenus">创建新项目</RouterLink>
                <RouterLink :to="`/p/${projectId}/apps?create=1`" @click="closeMenus">添加应用</RouterLink>
                <RouterLink :to="`/p/${projectId}/version/apps`" @click="closeMenus">添加版本</RouterLink>
                <RouterLink :to="`/p/${projectId}/usecases/new`" @click="closeMenus">新建用例</RouterLink>
              </div>
            </div>
            <RouterLink v-if="projectId" class="icon-trigger" :to="`/projects?edit=${projectId}`" title="设置" @click="closeMenus">⚙</RouterLink>
            <div class="nav-dropdown project-switcher" :class="{ open: openMenu === 'project' }">
              <button class="project-trigger" type="button" :aria-expanded="openMenu === 'project'" @click.stop="toggleMenu('project')">{{ currentProjectName }} <span>⌄</span></button>
              <div class="nav-menu project-menu right-aligned">
                <input v-model.trim="projectKeyword" class="menu-search" type="text" placeholder="搜索项目..." />
                <RouterLink v-for="project in filteredProjects" :key="project.id" :to="`/p/${project.id}/home`" @click="closeMenus">
                  {{ project.name }}
                </RouterLink>
                <div v-if="!filteredProjects.length" class="empty-menu-item">暂无项目</div>
              </div>
            </div>
            <div class="nav-dropdown user-menu" :class="{ open: openMenu === 'user' }">
              <button class="icon-trigger" type="button" @click.stop="toggleMenu('user')">👤</button>
              <div class="nav-menu compact right-aligned">
                <RouterLink class="shell-user-link" to="/account" @click="closeMenus">用户设置</RouterLink>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
const shellRef = ref<HTMLElement | null>(null)
const openMenu = ref<'monitor' | 'app' | 'assets' | 'create' | 'project' | 'user' | ''>('')
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

watch(() => route.fullPath, () => {
  closeMenus()
})

function toggleMenu(name: typeof openMenu.value) {
  openMenu.value = openMenu.value === name ? '' : name
}

function closeMenus() {
  openMenu.value = ''
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target as Node | null
  if (shellRef.value && target && !shellRef.value.contains(target)) {
    closeMenus()
  }
}

onMounted(() => {
  if (currentUser.value && projects.value.length === 0) {
    projectStore.loadProjects().catch(() => undefined)
  }
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
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
  z-index: 80;
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

.brand-block {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  min-width: 145px;
  padding: 0;
}

.brand-logo-image {
  display: block;
  width: 145px;
  max-width: 145px;
  height: 26px;
  object-fit: contain;
}

.shell-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.shell-nav > a.router-link-active,
.shell-nav > a.router-link-exact-active,
.nav-dropdown.open .nav-dropdown-trigger {
  color: #0f766e;
}

.menu-caret {
  margin-left: 2px;
  color: #64748b;
  font-size: 13px;
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
  max-width: 220px;
  border-radius: 999px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, .72);
  color: #5b6b79;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, .06);
}

.nav-menu {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  z-index: 160;
  min-width: 220px;
  display: none;
  padding: 10px;
  border: 1px solid rgba(15, 23, 42, .10);
  border-radius: 18px;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 24px 60px rgba(15, 23, 42, .18);
  backdrop-filter: blur(18px);
}

.nav-dropdown:hover .nav-menu,
.nav-dropdown:focus-within .nav-menu,
.nav-dropdown.open .nav-menu {
  display: grid;
  gap: 4px;
}

.nav-dropdown.open .nav-dropdown-trigger,
.nav-dropdown.open .project-trigger,
.nav-dropdown.open .icon-trigger {
  color: #0f766e;
}

.nav-menu.right-aligned {
  left: auto;
  right: 0;
}

.nav-menu a,
.nav-menu button,
.disabled-menu-item,
.empty-menu-item,
.menu-entry {
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
  width: min(340px, calc(100vw - 24px));
  min-width: 280px;
  max-height: min(420px, calc(100vh - 112px));
  overflow: auto;
  overscroll-behavior: contain;
}

.project-menu a {
  display: flex;
  align-items: center;
  min-height: 38px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-switcher .menu-search {
  position: sticky;
  top: 0;
  z-index: 1;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 8px 14px rgba(255, 255, 255, .85);
}

.project-switcher::after {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: none;
  content: '';
  pointer-events: none;
}

.project-switcher.open::after {
  display: block;
}

.app-menu {
  width: 520px;
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
  font: inherit;
  color: #172033;
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
  padding-right: 244px !important;
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
  display: inline-flex;
  align-items: center;
  gap: 2px;
}


.sub-menu-actions a {
  width: auto;
  min-width: 28px;
  padding: 4px 6px;
  border-radius: 8px;
  text-align: center;
  background: rgba(15, 118, 110, .06);
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
