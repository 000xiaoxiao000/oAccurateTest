<template>
  <div v-if="open" class="modal-mask" @click.self="$emit('close')">
    <section class="modal-card comparison-modal">
      <div class="modal-head">
        <div>
          <div class="eyebrow">Coverage Diff Methods</div>
          <h2>{{ title }}</h2>
          <p class="subtext">{{ description }}</p>
        </div>
        <button class="modal-close" type="button" aria-label="关闭覆盖变化方法弹窗" @click="$emit('close')">×</button>
      </div>

      <div class="comparison-modal-toolbar">
        <span :class="['comparison-tone-badge', type]">{{ label }}</span>
        <span class="count-badge">{{ filteredMethods.length }} / {{ methods.length }} 个方法</span>
        <input v-model.trim="keyword" class="text-input comparison-search" type="search" placeholder="搜索类名、方法名或签名" aria-label="搜索覆盖变化方法" />
      </div>

      <div v-if="filteredMethods.length" class="method-list comparison-method-list">
        <article v-for="method in filteredMethods" :key="`${method.className}-${method.methodName}-${method.methodDesc}`" class="method-item comparison-method-item">
          <div class="method-main">
            <strong>{{ method.className }}</strong>
            <span>{{ method.methodName }}{{ method.methodDesc || '' }}</span>
          </div>
          <RouterLink
            v-if="comparisonReportId"
            class="report-action-link"
            :to="{ name: 'coverage-code', params: { projectId, appId }, query: { reportId: comparisonReportId, className: method.className } }"
          >
            定位源码
          </RouterLink>
        </article>
      </div>
      <div v-else class="base-report-empty">暂无匹配方法</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { CoverageComparisonMethod } from '@/api/types'

const props = defineProps<{
  open: boolean
  type: 'added' | 'stable' | 'decreased'
  title: string
  label: string
  description: string
  methods: CoverageComparisonMethod[]
  comparisonReportId: string
  projectId: string
  appId: string
}>()

defineEmits<{
  (event: 'close'): void
}>()

const keyword = ref('')
const filteredMethods = computed(() => {
  const needle = keyword.value.toLowerCase()
  if (!needle) return props.methods
  return props.methods.filter((method) => [method.className, method.methodName, method.methodDesc]
    .join(' ')
    .toLowerCase()
    .includes(needle))
})

watch(() => props.open, (open) => {
  if (open) keyword.value = ''
})
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .52);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(780px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 48px));
  overflow: auto;
  display: grid;
  gap: 14px;
  padding: 22px;
  border-radius: 24px;
  background: rgba(255, 255, 255, .98);
  border: 1px solid rgba(15, 23, 42, .08);
  box-shadow: 0 28px 70px rgba(15, 23, 42, .24);
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(15, 23, 42, .08);
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.modal-head h2,
.modal-head .subtext {
  margin: 0;
}

.modal-head h2 {
  margin-top: 4px;
}

.subtext,
.base-report-empty {
  color: #64748b;
}

.modal-close {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, .06);
  color: #334155;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.modal-close:hover {
  background: rgba(220, 38, 38, .10);
  color: #dc2626;
}

.comparison-modal-toolbar {
  display: grid;
  grid-template-columns: auto auto minmax(220px, 1fr);
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 18px;
  background: rgba(248, 250, 252, .78);
  border: 1px solid rgba(15, 23, 42, .07);
}

.comparison-tone-badge,
.count-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.comparison-tone-badge {
  gap: 6px;
  padding: 6px 11px;
}

.comparison-tone-badge::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: currentColor;
}

.comparison-tone-badge.added {
  background: rgba(22, 163, 74, .12);
  color: #15803d;
}

.comparison-tone-badge.stable {
  background: rgba(37, 99, 235, .10);
  color: #2563eb;
}

.comparison-tone-badge.decreased {
  background: rgba(220, 38, 38, .10);
  color: #dc2626;
}

.count-badge {
  flex: 0 0 auto;
  padding: 4px 10px;
  background: rgba(37, 99, 235, .08);
  color: #2563eb;
}

.text-input {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 14px;
  padding: 10px 12px;
}

.method-list {
  display: grid;
  gap: 10px;
}

.comparison-method-list {
  max-height: min(520px, calc(100vh - 360px));
  overflow: auto;
  padding-right: 4px;
}

.method-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, .92);
  border: 1px solid rgba(15, 23, 42, .08);
}

.method-main {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.method-main strong,
.method-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.method-main span {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.report-action-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  border: 1px solid rgba(15, 118, 110, .16);
  border-radius: 999px;
  padding: 6px 12px;
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  text-decoration: none;
}

.report-action-link:hover {
  border-color: rgba(15, 118, 110, .28);
  background: rgba(15, 118, 110, .1);
}

.base-report-empty {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(241, 245, 249, .9);
  text-align: center;
}

@media (max-width: 700px) {
  .comparison-modal-toolbar,
  .method-item {
    grid-template-columns: 1fr;
  }
}
</style>
