<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Usecase Editor</div>
        <h1>{{ isEditMode ? '编辑用例' : '新建用例' }}</h1>
      </div>
      <div class="header-actions">
        <RouterLink class="secondary-link" :to="backLink">返回列表</RouterLink>
        <button class="action-button" type="button" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载编辑上下文...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <form class="editor-grid" @submit.prevent="save">
        <aside class="side-card">
          <div class="card-title">
            <h2>元信息</h2>
          </div>

          <label class="field">
            <span>用例标题</span>
            <input v-model="form.title" class="text-input" type="text" required />
          </label>

          <label class="field">
            <span>封面图地址</span>
            <input v-model="form.headImage" class="text-input" type="text" />
          </label>

          <label class="field">
            <span>目录</span>
            <input v-model="form.directory" class="text-input" type="text" required />
          </label>

          <label class="field">
            <span>标签</span>
            <select v-model="form.labels" class="select" multiple>
              <option v-for="label in payload.labels" :key="label.name" :value="label.name">
                {{ label.name }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>关联快照</span>
            <select v-model="form.snapshots" class="select tall" multiple>
              <option v-for="snapshot in payload.snapshots" :key="snapshot.id" :value="snapshot.id">
                {{ snapshot.title || snapshot.id }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>系统快照</span>
            <select v-model="form.systemSnapshots" class="select tall" multiple>
              <option v-for="item in payload.systemSnapshots" :key="item.id" :value="item.id">
                {{ item.name }}
              </option>
            </select>
          </label>

          <label class="field">
            <span>缺陷</span>
            <textarea
              v-model="form.defectsText"
              class="text-area"
              rows="4"
              placeholder="每行一个缺陷 ID 或链接"
            ></textarea>
          </label>

          <label class="field">
            <span>PRD 需求</span>
            <textarea
              v-model="form.prdRequirementsText"
              class="text-area"
              rows="4"
              placeholder="每行一个 PRD ID 或链接"
            ></textarea>
          </label>
        </aside>

        <section class="content-card">
          <div class="card-title">
            <h2>Markdown 内容</h2>
            <button class="submit-button" type="submit">保存</button>
          </div>
          <textarea
            v-model="form.content"
            class="markdown-input"
            rows="24"
            placeholder="请输入用例正文，支持 Markdown"
          ></textarea>
        </section>
      </form>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useProjectStore } from '@/stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const usecaseId = computed(() => String(route.params.usecaseId || ''))
const isEditMode = computed(() => Boolean(usecaseId.value))
const bootstrapKey = computed(() => `${projectId.value}:${usecaseId.value || 'new'}`)
const payload = computed(() => projectStore.usecaseBootstrapByKey[bootstrapKey.value])
const backLink = computed(() => `/p/${projectId.value}/usecases`)
const loading = ref(false)
const error = ref('')

const form = reactive({
  title: '',
  headImage: '',
  directory: '',
  content: '',
  labels: [] as string[],
  snapshots: [] as string[],
  systemSnapshots: [] as string[],
  defectsText: '',
  prdRequirementsText: '',
})

function syncForm() {
  if (!payload.value) {
    return
  }
  const source = payload.value.usecase
  form.title = source?.title || ''
  form.headImage = source?.headImage || ''
  form.directory = source?.directory || payload.value.currentDirectory || 'root'
  form.content = source?.content || ''
  form.labels = [...(payload.value.selectedLabelNames || [])]
  form.snapshots = [...(payload.value.selectedSnapshotIds || [])]
  form.systemSnapshots = [...(payload.value.selectedSystemSnapshotIds || [])]
  form.defectsText = payload.value.defectsText || ''
  form.prdRequirementsText = payload.value.prdRequirementsText || ''
}

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadUsecaseBootstrap(projectId.value, {
      directory: String(route.query.directory || 'root'),
      id: usecaseId.value || undefined,
    })
    syncForm()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载用例编辑上下文失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  loading.value = true
  error.value = ''
  try {
    const savedUsecaseId = await projectStore.persistUsecase(projectId.value, {
      id: usecaseId.value || undefined,
      title: form.title.trim(),
      headImage: form.headImage.trim() || undefined,
      content: form.content,
      directory: form.directory.trim(),
      labels: form.labels,
      snapshots: form.snapshots,
      systemSnapshots: form.systemSnapshots,
      defectsText: form.defectsText,
      prdRequirementsText: form.prdRequirementsText,
    })
    await router.push(`/p/${projectId.value}/usecases/${savedUsecaseId}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存用例失败'
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

.action-button,
.submit-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  color: #fff;
  cursor: pointer;
}

.action-button {
  background: #0f172a;
}

.submit-button {
  background: #0f766e;
}

.secondary-link {
  color: #0f766e;
  font-weight: 700;
}

.status-card,
.side-card,
.content-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.editor-grid {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
}

.field {
  display: grid;
  gap: 8px;
}

.field + .field {
  margin-top: 16px;
}

.text-input,
.text-area,
.select,
.markdown-input {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
  font: inherit;
}

.select[multiple] {
  min-height: 120px;
}

.select.tall {
  min-height: 160px;
}

.markdown-input {
  width: 100%;
  min-height: 640px;
  resize: vertical;
  line-height: 1.8;
}

@media (max-width: 960px) {
  .editor-grid {
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
