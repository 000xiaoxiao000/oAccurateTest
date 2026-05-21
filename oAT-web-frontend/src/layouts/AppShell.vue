<template>
  <div class="shell">
    <header class="shell-header">
      <div class="page-shell shell-header-inner">
        <div>
          <div class="shell-brand">oAT Frontend</div>
          <div class="shell-subtitle">前后端分离工作台</div>
        </div>
        <nav class="shell-nav">
          <RouterLink to="/projects">项目列表</RouterLink>
          <template v-if="currentUser">
            <RouterLink class="shell-user-link" to="/account">{{ currentUser.name }}</RouterLink>
            <button class="shell-logout" type="button" @click="handleLogout">退出</button>
          </template>
        </nav>
      </div>
    </header>
    <main class="page-shell shell-main">
      <RouterView />
    </main>
    <AiFloatingAssistant />
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useRouter, RouterLink, RouterView } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import AiFloatingAssistant from '@/components/AiFloatingAssistant.vue'

const router = useRouter()
const authStore = useAuthStore()
const { currentUser } = storeToRefs(authStore)

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

.shell-main {
  padding: 24px 0 40px;
}
</style>
