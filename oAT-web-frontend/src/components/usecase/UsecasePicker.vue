<template>
  <section v-if="open" class="picker-card">
    <div class="picker-head">
      <div>
        <h3>{{ title }}</h3>
        <p>{{ description }}</p>
      </div>
      <button class="icon-button" type="button" @click="close">x</button>
    </div>

    <div class="picker-tools">
      <input v-model="keyword" class="text-input" type="search" placeholder="搜索用例标题、ID 或内容..." aria-label="搜索用例" />
      <button class="ghost-button small" type="button" @click="selectAll">全选当前结果</button>
      <button class="ghost-button small" type="button" @click="clearSelection">清空</button>
      <span class="selected-badge">已选 {{ draftIds.length }} 个</span>
    </div>

    <div v-if="!filteredUsecases.length" class="empty-card compact">没有匹配的用例</div>
    <div v-else class="usecase-options">
      <label v-for="usecase in filteredUsecases" :key="usecase.id" class="usecase-option">
        <input v-model="draftIds" type="checkbox" :value="usecase.id" />
        <span>
          <strong>{{ usecase.title || usecase.id }}</strong>
          <small>{{ usecase.updateTimeText || usecase.id }}</small>
        </span>
      </label>
    </div>

    <div class="picker-footer">
      <button class="submit-button" type="button" :disabled="busy" @click="submit">
        {{ busy ? '保存中...' : '保存关联' }}
      </button>
      <button class="ghost-button" type="button" :disabled="busy" @click="close">取消</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { UsecaseSummary } from '@/api/types'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    usecases: UsecaseSummary[]
    selectedIds?: string[]
    busy?: boolean
  }>(),
  {
    description: '从项目用例中选择需要关联的条目。',
    selectedIds: () => [],
    busy: false,
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  submit: [ids: string[]]
}>()

const keyword = ref('')
const draftIds = ref<string[]>([])

const filteredUsecases = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return props.usecases
  }
  return props.usecases.filter((usecase) => {
    return [usecase.id, usecase.title, usecase.content]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(kw))
  })
})

watch(
  () => [props.open, props.selectedIds] as const,
  () => {
    if (props.open) {
      keyword.value = ''
      draftIds.value = [...props.selectedIds]
    }
  },
  { immediate: true },
)

function selectAll() {
  draftIds.value = Array.from(new Set([...draftIds.value, ...filteredUsecases.value.map((usecase) => usecase.id)]))
}

function clearSelection() {
  draftIds.value = []
}

function close() {
  emit('update:open', false)
}

function submit() {
  emit('submit', [...draftIds.value])
}
</script>

<style scoped>
.picker-card {
  display: grid;
  gap: 14px;
  margin: 14px 0;
  padding: 16px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(240, 253, 250, 0.96), rgba(255, 255, 255, 0.96));
}

.picker-head,
.picker-tools,
.picker-footer,
.usecase-option {
  display: flex;
  align-items: center;
  gap: 10px;
}

.picker-head {
  justify-content: space-between;
}

.picker-head h3,
.picker-head p {
  margin: 0;
}

.picker-head p,
.usecase-option small {
  color: #64748b;
}

.picker-tools {
  flex-wrap: wrap;
}

.text-input {
  min-width: min(360px, 100%);
  flex: 1;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.ghost-button,
.submit-button,
.icon-button {
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  font-weight: 700;
}

.ghost-button,
.icon-button {
  border: 1px solid rgba(15, 118, 110, 0.18);
  background: rgba(15, 118, 110, 0.06);
  color: #0f766e;
}

.ghost-button.small {
  padding: 8px 12px;
}

.submit-button {
  border: none;
  background: #0f766e;
  color: #fff;
}

.submit-button:disabled,
.ghost-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.selected-badge {
  padding: 8px 11px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: #0f766e;
  font-weight: 800;
}

.usecase-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 10px;
  max-height: 360px;
  overflow: auto;
}

.usecase-option {
  align-items: flex-start;
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  background: #fff;
}

.usecase-option span {
  display: grid;
  gap: 4px;
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

@media (max-width: 720px) {
  .picker-head,
  .picker-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
