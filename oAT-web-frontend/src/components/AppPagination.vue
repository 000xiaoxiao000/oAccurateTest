<template>
  <nav v-if="total > 0" class="app-pagination" aria-label="分页">
    <div class="page-summary">
      <strong>{{ total }}</strong>
      <span>{{ itemName }}</span>
      <small>{{ firstItem }}-{{ lastItem }} / 第 {{ safePage }} / {{ totalPages }} 页</small>
    </div>
    <div class="page-controls">
      <label v-if="pageSizes.length" class="page-size-control">
        <span>每页</span>
        <select :value="pageSize" @change="changePageSize">
          <option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option>
        </select>
      </label>
      <button type="button" :disabled="safePage <= 1" @click="setPage(safePage - 1)">上一页</button>
      <button
        v-for="pageNumber in pageNumbers"
        :key="pageNumber"
        type="button"
        :class="{ active: pageNumber === safePage }"
        @click="setPage(pageNumber)"
      >
        {{ pageNumber }}
      </button>
      <button type="button" :disabled="safePage >= totalPages" @click="setPage(safePage + 1)">下一页</button>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'

const props = withDefaults(defineProps<{
  page: number
  pageSize: number
  total: number
  itemName?: string
  pageSizes?: number[]
  maxButtons?: number
}>(), {
  itemName: '条数据',
  pageSizes: () => [10, 20, 50],
  maxButtons: 5,
})

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
}>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.pageSize))))
const safePage = computed(() => Math.min(Math.max(1, props.page), totalPages.value))
const firstItem = computed(() => (props.total === 0 ? 0 : (safePage.value - 1) * props.pageSize + 1))
const lastItem = computed(() => Math.min(props.total, safePage.value * props.pageSize))

const pageNumbers = computed(() => {
  const maxButtons = Math.max(3, props.maxButtons)
  const half = Math.floor(maxButtons / 2)
  let from = Math.max(1, safePage.value - half)
  let to = Math.min(totalPages.value, from + maxButtons - 1)
  from = Math.max(1, to - maxButtons + 1)
  const pages: number[] = []
  for (let page = from; page <= to; page += 1) {
    pages.push(page)
  }
  return pages
})

watch([() => props.page, totalPages], () => {
  if (props.page !== safePage.value) {
    emit('update:page', safePage.value)
  }
})

function setPage(page: number) {
  emit('update:page', Math.min(Math.max(1, page), totalPages.value))
}

function changePageSize(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  emit('update:pageSize', value > 0 ? value : props.pageSize)
  emit('update:page', 1)
}
</script>

<style scoped>
.app-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding: 10px 12px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background: rgba(255, 255, 255, .88);
}

.page-summary,
.page-controls,
.page-size-control {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.page-summary {
  color: #64748b;
  font-size: 13px;
}

.page-summary strong {
  color: #0f172a;
  font-size: 16px;
}

.page-summary small {
  color: #94a3b8;
}

.page-controls button,
.page-size-control select {
  min-height: 34px;
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
  font-weight: 800;
}

.page-controls button {
  padding: 6px 11px;
  transition: transform .16s ease, background .16s ease, color .16s ease, box-shadow .16s ease;
}

.page-controls button:hover:not(:disabled),
.page-controls button.active {
  transform: translateY(-1px);
  background: #0f766e;
  color: #fff;
  box-shadow: 0 8px 18px rgba(15, 118, 110, .16);
}

.page-controls button:disabled {
  opacity: .45;
}

.page-size-control {
  color: #64748b;
  font-size: 13px;
}

.page-size-control select {
  padding: 0 28px 0 10px;
  background-color: #fff;
}

@media (max-width: 720px) {
  .app-pagination,
  .page-controls {
    align-items: stretch;
  }

  .page-controls {
    width: 100%;
  }

  .page-controls button {
    flex: 1;
  }
}
</style>
