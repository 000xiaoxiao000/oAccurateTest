<template>
  <section class="login-page">
    <div class="login-mascots" aria-hidden="true">
      <MascotCanvas
        v-for="item in mascotItems"
        :key="item.seed"
        class="login-mascot"
        :style="{ left: item.left, top: item.top, animationDelay: item.delay }"
        :size="item.size"
        :color="item.color"
        :seed="item.seed"
        :mood="error ? 'error' : 'happy'"
      />
    </div>
    <div class="login-card" :class="{ 'shake-animation': Boolean(error), 'auth-submitting': submitting }">
      <div v-if="submitting" class="submit-overlay">{{ isRegisterMode ? '正在注册，请稍候...' : '正在登录，请稍候...' }}</div>
      <div class="login-label">Unified Entry</div>
      <h1>{{ isRegisterMode ? '注册 oAT 新前端账号' : '登录 oAT 新前端' }}</h1>
      <p>{{ isRegisterMode ? '创建账号后回到登录页，后续默认进入前后端分离工作台。' : '登录成功后会回到当前目标页面，后续已迁移模块默认进入前后端分离工作台。' }}</p>

      <form v-if="!isRegisterMode" class="login-form" @submit.prevent="submitLogin">
        <label class="field">
          <span>用户名或邮箱</span>
          <input v-model.trim="loginForm.nameOrEmail" class="text-input" type="text" autocomplete="username" />
        </label>
        <label class="field">
          <span>密码</span>
          <div class="password-row">
            <input
              v-model="loginForm.password"
              class="text-input"
              :type="showLoginPassword ? 'text' : 'password'"
              autocomplete="current-password"
            />
            <button class="password-toggle" type="button" @click="showLoginPassword = !showLoginPassword">
              {{ showLoginPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>
        <p v-if="route.query.registered" class="success-text">注册成功，请登录。</p>
        <p v-if="route.query.error" class="error-text">{{ String(route.query.error) }}</p>
        <p v-if="error" class="error-text">{{ error }}</p>
        <button class="login-button" type="submit" :disabled="submitting">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
        <RouterLink class="mode-link" to="/register">注册新账号</RouterLink>
      </form>

      <form v-else class="login-form" @submit.prevent="submitRegister">
        <label class="field">
          <span>用户名</span>
          <input v-model.trim="registerForm.name" class="text-input" type="text" autocomplete="username" pattern="^[A-Za-z0-9_]+$" />
        </label>
        <label class="field">
          <span>昵称</span>
          <input v-model.trim="registerForm.nickname" class="text-input" type="text" autocomplete="nickname" />
        </label>
        <label class="field">
          <span>邮箱</span>
          <input v-model.trim="registerForm.email" class="text-input" type="email" autocomplete="email" />
        </label>
        <label class="field">
          <span>密码</span>
          <div class="password-row">
            <input
              v-model="registerForm.password"
              class="text-input"
              :type="showRegisterPassword ? 'text' : 'password'"
              autocomplete="new-password"
            />
            <button class="password-toggle" type="button" @click="showRegisterPassword = !showRegisterPassword">
              {{ showRegisterPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>
        <label class="field">
          <span>确认密码</span>
          <div class="password-row">
            <input
              v-model="registerForm.againPassword"
              class="text-input"
              :type="showRegisterConfirmPassword ? 'text' : 'password'"
              autocomplete="new-password"
            />
            <button class="password-toggle" type="button" @click="showRegisterConfirmPassword = !showRegisterConfirmPassword">
              {{ showRegisterConfirmPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>
        <p v-if="error" class="error-text">{{ error }}</p>
        <button class="login-button" type="submit" :disabled="submitting">
          {{ submitting ? '注册中...' : '注册' }}
        </button>
        <RouterLink class="mode-link" to="/login">返回登录</RouterLink>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import MascotCanvas from '@/components/MascotCanvas.vue'

import { ApiError } from '@/api/http'
import { register } from '@/api/bootstrap'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const error = ref('')
const showLoginPassword = ref(false)
const showRegisterPassword = ref(false)
const showRegisterConfirmPassword = ref(false)
const loginForm = reactive({
  nameOrEmail: '',
  password: '',
})
const registerForm = reactive({
  name: '',
  nickname: '',
  email: '',
  password: '',
  againPassword: '',
})
const mascotItems = [
  { seed: 'login-a', left: '8%', top: '16%', size: 82, color: '#00b5ad', delay: '0s' },
  { seed: 'login-b', left: '18%', top: '68%', size: 68, color: '#f59e0b', delay: '-1.4s' },
  { seed: 'login-c', left: '78%', top: '18%', size: 78, color: '#2185d0', delay: '-.6s' },
  { seed: 'login-d', left: '84%', top: '72%', size: 72, color: '#21ba45', delay: '-2s' },
  { seed: 'login-e', left: '48%', top: '8%', size: 58, color: '#f2711c', delay: '-2.7s' },
]

const isRegisterMode = computed(() => route.name === 'register')

function resolveRedirect() {
  const redirect = route.query.redirect
  return normalizeRedirect(typeof redirect === 'string' ? redirect : '')
}

function normalizeRedirect(redirect: string) {
  let normalized = redirect.trim()
  for (let i = 0; i < 4; i += 1) {
    try {
      const decoded = decodeURIComponent(normalized)
      if (decoded === normalized) break
      normalized = decoded
    } catch {
      break
    }
  }

  if (!normalized || normalized.startsWith('http://') || normalized.startsWith('https://') || normalized.startsWith('//')) {
    return '/projects'
  }
  if (!normalized.startsWith('/') || normalized.startsWith('/login')) {
    return '/projects'
  }
  if (normalized.startsWith('/index.html')) {
    const queryIndex = normalized.indexOf('?')
    if (queryIndex >= 0) {
      const params = new URLSearchParams(normalized.slice(queryIndex + 1))
      return normalizeRedirect(params.get('redirect') || '')
    }
    return '/projects'
  }
  return normalized
}

async function submitLogin() {
  if (!loginForm.nameOrEmail || !loginForm.password) {
    error.value = '请输入用户名和密码'
    return
  }

  submitting.value = true
  error.value = ''
  try {
    await authStore.login(loginForm.nameOrEmail, loginForm.password)
    await router.replace(resolveRedirect())
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : err instanceof Error ? err.message : '登录失败'
  } finally {
    submitting.value = false
  }
}

async function submitRegister() {
  if (!registerForm.name || !registerForm.email || !registerForm.password || !registerForm.againPassword) {
    error.value = '请完整填写注册信息'
    return
  }
  if (!/^[A-Za-z0-9_]+$/.test(registerForm.name)) {
    error.value = '用户名只能包含数字、字母、下划线'
    return
  }
  if (registerForm.password.length < 6) {
    error.value = '密码至少需要 6 位'
    return
  }
  if (registerForm.password !== registerForm.againPassword) {
    error.value = '两次输入的密码不一致'
    return
  }

  submitting.value = true
  error.value = ''
  try {
    await register(registerForm)
    await router.replace('/login?registered=1')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : err instanceof Error ? err.message : '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: calc(100vh - 120px);
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
}

.login-mascots {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.login-mascot {
  position: absolute;
  opacity: .72;
  filter: drop-shadow(0 18px 24px rgba(15, 23, 42, .10));
  animation: mascot-drift 7s ease-in-out infinite alternate;
}

@keyframes mascot-drift {
  from { transform: translate3d(-8px, -6px, 0) rotate(-4deg); }
  to { transform: translate3d(10px, 8px, 0) rotate(5deg); }
}

.login-card {
  width: min(560px, 100%);
  padding: 32px;
  border-radius: 24px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background:
    radial-gradient(circle at top right, rgba(15, 118, 110, 0.16), transparent 38%),
    rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.08);
  position: relative;
  z-index: 1;
}

.submit-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: grid;
  place-items: center;
  border-radius: inherit;
  background: rgba(255, 255, 255, .72);
  color: #0f766e;
  font-weight: 900;
  backdrop-filter: blur(5px);
}

.shake-animation {
  animation: auth-shake .34s ease;
}

@keyframes auth-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-9px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-5px); }
  80% { transform: translateX(4px); }
}

.login-label {
  display: inline-block;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.login-form {
  display: grid;
  gap: 14px;
  margin-top: 20px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  font-size: 13px;
  color: #475569;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.14);
  border-radius: 16px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.96);
}

.password-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.password-row .text-input {
  flex: 1;
}

.password-toggle {
  flex: 0 0 auto;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 999px;
  padding: 10px 14px;
  background: #fff;
  color: #0f766e;
  cursor: pointer;
}

.login-button {
  border: none;
  border-radius: 999px;
  padding: 12px 18px;
  background: #0f766e;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.login-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.error-text,
.success-text {
  margin: 0;
}

.error-text {
  color: #b91c1c;
}

.success-text {
  color: #0f766e;
  font-weight: 700;
}

.mode-link {
  display: inline-block;
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
  text-align: center;
}
</style>
