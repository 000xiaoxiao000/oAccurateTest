<template>
  <RelationBoard
    eyebrow="Code Map"
    title="源码链路图"
    subtext="按 traceId 展示调用链中的代码节点。"
    :loading="loading"
    :error="error"
    :nodes="nodes"
    :edges="edges"
    :back-route="`/p/${projectId}/search`"
    back-label="返回搜索中心"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import { fetchMapCode } from '@/api/bootstrap'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const traceId = computed(() => String(route.query.traceId || ''))
const loading = ref(false)
const error = ref('')
const elements = ref<Awaited<ReturnType<typeof fetchMapCode>>>([])

const nodes = computed(() =>
  elements.value
    .filter((item) => item.group === 'nodes')
    .map((item) => ({
      id: item.data.id,
      label: item.data.name || item.data.id,
      type: item.classes?.join(','),
      description: item.data.describe,
      meta: [
        item.data.packageAndClassName ? `类 ${item.data.packageAndClassName}` : '',
        item.data.methodName ? `方法 ${item.data.methodName}` : '',
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
      label: item.data.methodName || item.data.name,
      sourceLabel: labelMap.get(item.data.source as string),
      targetLabel: labelMap.get(item.data.target as string),
    }))
})

async function load() {
  if (!traceId.value) {
    error.value = '缺少 traceId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    elements.value = await fetchMapCode(projectId.value, traceId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载源码地图失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
