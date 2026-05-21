<template>
  <section>
    <div class="toolbar">
      <div class="chip-group">
        <button
          v-for="item in layerOptions"
          :key="item.value"
          type="button"
          :class="['chip-button', selectedLayers.includes(item.value) && 'active']"
          @click="toggleLayer(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
    <RelationBoard
      eyebrow="Application Map"
      :title="`应用链路图 · ${appId}`"
      subtext="可切换代码层、数据表层等扩展信息。"
      :loading="loading"
      :error="error"
      :nodes="nodes"
      :edges="edges"
      :back-route="`/p/${projectId}/apps`"
      back-label="返回应用列表"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import { fetchMapApp } from '@/api/bootstrap'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const loading = ref(false)
const error = ref('')
const elements = ref<Awaited<ReturnType<typeof fetchMapApp>>>([])
const selectedLayers = ref<string[]>(['code'])

const layerOptions = [
  { label: '代码层', value: 'code' },
  { label: '表结构层', value: 'table' },
]

const nodes = computed(() =>
  elements.value
    .filter((item) => item.group === 'nodes')
    .map((item) => ({
      id: item.data.id,
      label: item.data.name || item.data.id,
      type: item.classes?.join(','),
      description: item.data.describe,
      meta: [
        item.data.coverageRate ? `覆盖率 ${item.data.coverageRate}%` : '',
        item.data.cyclo ? `复杂度 ${item.data.cyclo}` : '',
      ].filter(Boolean),
    })),
)

const edges = computed(() => {
  const labelMap = new Map(nodes.value.map((node) => [node.id, node.label]))
  return elements.value
    .filter((item) => item.group === 'edges' && item.data.source && item.data.target)
    .map((item) => ({
      id: item.data.id,
      source: item.data.source as string,
      target: item.data.target as string,
      label: item.data.hotName || item.data.methodName || item.data.name,
      sourceLabel: labelMap.get(item.data.source as string),
      targetLabel: labelMap.get(item.data.target as string),
    }))
})

function toggleLayer(layer: string) {
  selectedLayers.value = selectedLayers.value.includes(layer)
    ? selectedLayers.value.filter((item) => item !== layer)
    : [...selectedLayers.value, layer]
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    elements.value = await fetchMapApp(projectId.value, appId.value, selectedLayers.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载应用地图失败'
  } finally {
    loading.value = false
  }
}

watch(selectedLayers, load, { deep: true })
onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 14px;
}

.chip-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.chip-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
}

.chip-button.active {
  background: #0f172a;
  color: #fff;
}
</style>
