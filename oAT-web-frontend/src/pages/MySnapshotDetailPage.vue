<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">My Snapshot</div>
        <h1>{{ payload?.snapshot.name || '我的快照详情' }}</h1>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/my-snapshots/${snapshotId}/report`">
          查看覆盖率报告
        </RouterLink>
        <RouterLink class="ghost-button" :to="`/p/${projectId}/my-snapshots/${snapshotId}/graph`">
          查看链路图
        </RouterLink>
        <RouterLink class="secondary-link" :to="`/p/${projectId}/my-snapshots`">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载我的快照详情...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="detail-grid">
        <section class="panel">
          <div class="card-title">
            <h2>基本信息</h2>
            <button class="ghost-button" type="button" @click="saveBasic">保存</button>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>名称</span>
              <input v-model="form.name" class="text-input" type="text" />
            </label>
            <label class="field">
              <span>描述</span>
              <textarea v-model="form.describe" class="text-area" rows="4"></textarea>
            </label>
            <label class="field">
              <span>标签</span>
              <select v-model="form.labels" class="select" multiple>
                <option v-for="label in payload.labels" :key="label.name" :value="label.name">
                  {{ label.name }}
                </option>
              </select>
            </label>
          </div>
          <div class="utility-grid">
            <label class="switch-card">
              <span>共享链接</span>
              <input :checked="Boolean(payload.snapshot.share)" type="checkbox" @change="toggleShare" />
            </label>
            <RouterLink class="utility-link" :to="`/p/${projectId}/my-snapshots/${snapshotId}/report`">
              打开 SPA 覆盖率报告
            </RouterLink>
            <a
              v-if="payload.shareUrl && payload.snapshot.share"
              class="utility-link"
              :href="payload.shareUrl"
              target="_blank"
              rel="noreferrer"
            >
              打开分享链接
            </a>
          </div>
          <div v-if="payload.shareUrl && payload.snapshot.share" class="share-card">
            <span>分享地址</span>
            <input :value="absoluteShareUrl" class="text-input" readonly @focus="selectCurrentTarget" />
          </div>
        </section>

        <aside class="side-stack">
          <section class="panel">
            <div class="card-title">
              <h2>创建者</h2>
            </div>
            <div class="info-card">
              <strong>{{ payload.createUser?.nickname || payload.createUser?.name || '-' }}</strong>
              <span>{{ payload.createUser?.email || '-' }}</span>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>关联用例</h2>
              <button class="ghost-button small" type="button" @click="openUsecasePicker">更新关联</button>
            </div>
            <UsecasePicker
              v-model:open="usecasePickerOpen"
              title="更新关联用例"
              description="从项目用例中选择当前我的快照需要关联的条目。"
              :usecases="payload.allUsecases"
              :selected-ids="payload.usecases.map((usecase) => usecase.id)"
              :busy="loading"
              @submit="bindUsecases"
            />
            <div v-if="!payload.usecases.length" class="empty-card compact">暂无关联用例</div>
            <div v-else class="usecase-list">
              <RouterLink
                v-for="usecase in payload.usecases"
                :key="usecase.id"
                class="usecase-card"
                :to="`/p/${projectId}/usecases/${usecase.id}`"
              >
                <strong>{{ usecase.title }}</strong>
                <span>{{ usecase.updateTimeText || '-' }}</span>
              </RouterLink>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>危险操作</h2>
            </div>
            <button class="danger-button" type="button" @click="removeSnapshot">删除快照</button>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const payload = computed(() => projectStore.mySnapshotDetailByKey[storeKey.value])
const absoluteShareUrl = computed(() => {
  if (!payload.value?.shareUrl) {
    return ''
  }
  return `${window.location.origin}${payload.value.shareUrl}`
})
const loading = ref(false)
const error = ref('')
const usecasePickerOpen = ref(false)

const form = reactive({
  name: '',
  describe: '',
  labels: [] as string[],
})

function syncForm() {
  if (!payload.value) {
    return
  }
  form.name = payload.value.snapshot.name || ''
  form.describe = payload.value.snapshot.describe || ''
  form.labels = [...(payload.value.snapshot.labels || [])]
}

async function load() {
  if (!projectId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadMySnapshotDetail(projectId.value, snapshotId.value)
    syncForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照详情失败'
  } finally {
    loading.value = false
  }
}

async function saveBasic() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateMySnapshotBasicInfo(projectId.value, snapshotId.value, {
      name: form.name.trim(),
      describe: form.describe.trim(),
      labels: form.labels,
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新我的快照失败'
  } finally {
    loading.value = false
  }
}

function openUsecasePicker() {
  usecasePickerOpen.value = true
}

async function bindUsecases(usecaseIds: string[]) {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateMySnapshotUsecases(projectId.value, snapshotId.value, usecaseIds)
    usecasePickerOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新我的快照关联用例失败'
  } finally {
    loading.value = false
  }
}

async function toggleShare(event: Event) {
  const target = event.target as HTMLInputElement
  loading.value = true
  error.value = ''
  try {
    await projectStore.changeMySnapshotShare(projectId.value, snapshotId.value, target.checked)
    await load()
  } catch (err) {
    target.checked = !target.checked
    error.value = err instanceof Error ? err.message : '更新共享状态失败'
  } finally {
    loading.value = false
  }
}

function selectCurrentTarget(event: Event) {
  const target = event.target as HTMLInputElement
  target.select()
}

async function removeSnapshot() {
  const confirmed = window.confirm('确认删除该快照？')
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeMySnapshot(projectId.value, snapshotId.value)
    window.history.back()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除我的快照失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.header-actions,
.card-title {
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

.secondary-link,
.ghost-button,
.utility-link {
  color: #0f766e;
  font-weight: 700;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, 0.18);
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 118, 110, 0.06);
  cursor: pointer;
}

.ghost-button.small {
  padding: 8px 12px;
}

.danger-button {
  border: 1px solid rgba(185, 28, 28, 0.18);
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(185, 28, 28, 0.06);
  color: #b91c1c;
  cursor: pointer;
  font-weight: 700;
}

.status-card,
.panel {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 360px;
  gap: 18px;
}

.side-stack,
.usecase-list,
.utility-grid {
  display: grid;
  gap: 12px;
}

.form-grid {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.text-input,
.text-area,
.select {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.select[multiple] {
  min-height: 120px;
}

.info-card,
.usecase-card {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.switch-card,
.share-card {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.switch-card span,
.share-card span {
  color: #64748b;
}

.info-card span,
.usecase-card span {
  color: #64748b;
}

.empty-card {
  padding: 22px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

.empty-card.compact {
  padding: 14px;
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .card-title {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
