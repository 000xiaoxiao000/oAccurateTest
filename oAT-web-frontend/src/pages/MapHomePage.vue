<template>
  <RelationBoard
    eyebrow="System Map"
    title="项目链路地图"
    subtext="展示项目下应用、快照和外部依赖的关系视图。"
    :loading="loading"
    :error="error"
    :nodes="nodes"
    :edges="edges"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import { fetchMapHome } from '@/api/bootstrap'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const loading = ref(false)
const error = ref('')
const elements = ref<Awaited<ReturnType<typeof fetchMapHome>>>([])

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

async function load() {
  loading.value = true
  error.value = ''
  try {
    elements.value = await fetchMapHome(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载地图失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
