<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Repository</div>
        <h1>仓库配置</h1>
      </div>
      <button class="action-button" type="button" @click="load">刷新</button>
    </div>

    <div v-if="loading" class="status-card">正在加载仓库配置...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <form class="form-card" @submit.prevent="save">
        <label class="block">
          <span>仓库地址</span>
          <input v-model="form.repoAddress" class="text-input" type="text" />
        </label>
        <div class="form-grid">
          <label>
            <span>用户名 / Token 名称</span>
            <input v-model="form.repoUserName" class="text-input" type="text" />
          </label>
          <label>
            <span>密码 / Token</span>
            <input v-model="form.repoPassword" class="text-input" type="password" />
          </label>
        </div>

        <div class="actions">
          <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '处理中...' : '保存' }}</button>
          <button class="secondary-button" type="button" :disabled="loading || !form.repoAddress.trim()" @click="loadBranches">
            {{ loading ? '读取中...' : '读取远端分支' }}
          </button>
        </div>

        <div v-if="branches.length" class="branch-card">
          <h2>远端分支</h2>
          <div class="branch-list">
            <span v-for="branch in branches" :key="branch" class="branch-chip">{{ branch }}</span>
          </div>
        </div>
      </form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { fetchRepositoryBranchesPreview } from '@/api/bootstrap'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const storeKey = computed(() => `${projectId.value}:${appId.value}`)
const payload = computed(() => projectStore.repositoryByKey[storeKey.value])
const loading = ref(false)
const error = ref('')
const branches = ref<string[]>([])

const form = reactive({
  repoAddress: '',
  repoUserName: '',
  repoPassword: '',
})

function syncForm() {
  if (!payload.value) {
    return
  }
  form.repoAddress = payload.value.app.repoAddress || ''
  form.repoUserName = payload.value.app.repoUserName || ''
  form.repoPassword = payload.value.app.repoPassword || ''
}

async function load() {
  if (!projectId.value || !appId.value) {
    error.value = '缺少 projectId 或 appId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadRepository(projectId.value, appId.value)
    syncForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载仓库配置失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateRepository(projectId.value, appId.value, { ...form })
    syncForm()
    await dialog.alert({ title: '仓库配置已保存', message: '仓库地址和认证信息已更新，可继续读取远端分支或进行 Git 版本比对。', tone: 'success' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存仓库配置失败'
  } finally {
    loading.value = false
  }
}

async function loadBranches() {
  if (!form.repoAddress.trim()) {
    error.value = '请先填写仓库地址'
    return
  }
  loading.value = true
  error.value = ''
  try {
    branches.value = await fetchRepositoryBranchesPreview(projectId.value, {
      repoUrl: form.repoAddress.trim(),
      username: form.repoUserName.trim() || undefined,
      password: form.repoPassword || undefined,
    })
    if (!branches.value.length) {
      error.value = '远端仓库未返回分支，请检查仓库地址或认证信息'
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '读取远端分支失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.action-button,
.primary-button,
.secondary-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  color: #fff;
  cursor: pointer;
}

.action-button {
  background: #0f172a;
}

.primary-button {
  background: #0f766e;
}

.secondary-button {
  background: #2563eb;
}

.status-card,
.form-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.block,
.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.text-input {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.actions {
  display: flex;
  gap: 14px;
  margin-top: 20px;
}

.branch-card {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.branch-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.branch-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .actions,
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
