<template>
  <div class="selector-row">
    <label class="field grow">
      <span>选择应用</span>
      <select :value="appId" class="text-input" @change="$emit('update:appId', ($event.target as HTMLSelectElement).value)">
        <option value="">请选择应用</option>
        <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.name }}</option>
      </select>
    </label>
    <label class="field search-field">
      <span>搜索版本/报告</span>
      <input
        :value="keyword"
        class="text-input"
        type="search"
        placeholder="版本号、分支、Commit"
        aria-label="搜索覆盖率版本或报告"
        @input="$emit('update:keyword', ($event.target as HTMLInputElement).value.trim())"
      />
    </label>
    <label class="field source-field">
      <span>覆盖率类型</span>
      <select :value="sourceType" class="text-input" @change="$emit('update:sourceType', ($event.target as HTMLSelectElement).value)">
        <option value="ALL">全部</option>
        <option value="JAVA">Java</option>
        <option value="FRONTEND">前端 JS/TS</option>
        <option value="CPP">C/C++</option>
        <option value="GO">Go</option>
        <option value="PYTHON">Python</option>
      </select>
    </label>
    <button class="ghost-button" type="button" @click="$emit('refresh-apps')">刷新应用</button>
  </div>
</template>

<script setup lang="ts">
import type { AppSummary } from '@/api/types'

defineProps<{
  apps: AppSummary[]
  appId: string
  keyword: string
  sourceType: string
}>()

defineEmits<{
  (event: 'update:appId', value: string): void
  (event: 'update:keyword', value: string): void
  (event: 'update:sourceType', value: string): void
  (event: 'refresh-apps'): void
}>()
</script>

<style scoped>
.selector-row {
  position: sticky;
  top: 12px;
  z-index: 4;
  display: flex;
  align-items: end;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.search-field {
  min-width: min(360px, 100%);
}

.source-field {
  width: 160px;
}

.field span {
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

.ghost-button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

@media (max-width: 900px) {
  .selector-row {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }
}
</style>
