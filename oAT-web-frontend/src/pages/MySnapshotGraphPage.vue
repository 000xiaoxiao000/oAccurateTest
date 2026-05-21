<template>
  <GraphView
    :eyebrow="'My Snapshot Graph'"
    :fallback-title="'我的快照链路图'"
    :back-route="detailRoute"
    :back-label="'返回快照详情'"
    :loading="loading"
    :loading-text="'正在加载链路图...'"
    :error="error"
    :graph="graph"
    :selected-node-id="selectedNodeId"
    :selected-node-detail="selectedNodeDetail"
    :arrow-marker-id="'graph-arrow-my'"
    @select-node="selectNode"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import GraphView from '@/components/snapshot/GraphView.vue'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const detailRoute = computed(() => `/p/${projectId.value}/my-snapshots/${snapshotId.value}`)
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const graph = computed(() => projectStore.mySnapshotGraphByKey[storeKey.value])
const selectedNodeId = ref('')
const selectedNodeKey = computed(() => `${projectId.value}:${snapshotId.value}:graph:${selectedNodeId.value}`)
const selectedNodeDetail = computed(() => projectStore.mySnapshotGraphNodeByKey[selectedNodeKey.value])
const loading = ref(false)
const error = ref('')

async function load() {
  if (!projectId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadMySnapshotGraph(projectId.value, snapshotId.value)
    if (graph.value?.showDefaultNode?.id) {
      await selectNode(graph.value.showDefaultNode.id)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照链路图失败'
  } finally {
    loading.value = false
  }
}

async function selectNode(nodeId: string) {
  if (!nodeId) {
    return
  }
  selectedNodeId.value = nodeId
  try {
    await projectStore.loadMySnapshotGraphNode(projectId.value, snapshotId.value, nodeId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载图节点详情失败'
  }
}

onMounted(load)
</script>
