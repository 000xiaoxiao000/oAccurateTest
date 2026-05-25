<template>
  <section>
    <div class="home-map-toolbar">
      <div>
        <strong>{{ selectedNode ? selectedNode.label || selectedNode.id : '项目图谱操作' }}</strong>
        <span>{{ selectedNode ? selectedTypeText : '选择应用节点后可进入应用图谱或展开快照层' }}</span>
      </div>
      <div class="toolbar-actions">
        <button v-if="canUseAppActions" type="button" @click="openAppMap">查看应用图谱</button>
        <button v-if="canUseAppActions" type="button" :disabled="loadingLayer" @click="loadAppSnapshots">展开快照</button>
        <button v-if="extensionElements.length" type="button" class="danger" :disabled="loadingLayer" @click="clearExtensionLayers">清除扩展图层</button>
      </div>
      <div v-if="loadingLayer || activeExtensionLabel || layerError" :class="['layer-status', layerError && 'error']">
        {{ layerError || (loadingLayer ? `正在加载${activeExtensionLabel || '图层'}...` : `已加载：${activeExtensionLabel}`) }}
      </div>
    </div>
    <RelationBoard
      eyebrow="System Map"
      title="项目链路地图"
      subtext="展示项目下应用、快照和外部依赖的关系视图，可继续展开应用快照关系。"
      :loading="loading"
      :error="error"
      :nodes="nodes"
      :edges="edges"
      @node-select="handleNodeSelect"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import { fetchMapHome, fetchMapLayerAppSnapshots } from '@/api/bootstrap'
import type { MapElement, MapElementData } from '@/api/types'

interface RelationNodeSelection {
  id: string
  label?: string
  type?: string
  raw?: Record<string, unknown>
  classes?: string[]
}

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId || ''))
const loading = ref(false)
const loadingLayer = ref(false)
const error = ref('')
const layerError = ref('')
const activeExtensionLabel = ref('')
const elements = ref<MapElement[]>([])
const extensionElements = ref<MapElement[]>([])
const selectedNode = ref<RelationNodeSelection | null>(null)

const allElements = computed(() => mergeElements(elements.value, extensionElements.value))
const selectedClasses = computed(() => selectedNode.value?.classes || selectedNode.value?.type?.split(/\s+/).filter(Boolean) || [])
const selectedTypeText = computed(() => selectedClasses.value.length ? `类型：${selectedClasses.value.join(' / ')}` : '节点')
const canUseAppActions = computed(() => selectedClasses.value.some((item) => item === 'app' || item.includes('app')))

const nodes = computed(() =>
  allElements.value
    .filter((item) => item.group === 'nodes')
    .map((item) => ({
      id: item.data.id,
      label: item.data.name || item.data.id,
      type: item.classes?.join(' '),
      description: item.data.describe,
      raw: item.data as unknown as Record<string, unknown>,
      classes: item.classes,
      meta: [
        item.classes?.length ? `图层 ${item.classes.join(' / ')}` : '',
        typeof item.data.coverageRate === 'number' ? `覆盖率 ${item.data.coverageRate}%` : '',
        item.data.cyclo ? `复杂度 ${item.data.cyclo}` : '',
        item.data.appId ? `应用 ${item.data.appId}` : '',
      ].filter(Boolean),
    })),
)

const edges = computed(() => {
  const labelMap = new Map(nodes.value.map((node) => [node.id, node.label]))
  return allElements.value
    .filter((item) => item.group === 'edges' && item.data.source && item.data.target)
    .map((item) => ({
      id: item.data.id,
      source: item.data.source as string,
      target: item.data.target as string,
      label: item.data.hotName || item.data.methodName || item.data.name || edgeAction(item.data),
      action: edgeAction(item.data),
      sourceLabel: labelMap.get(item.data.source as string),
      targetLabel: labelMap.get(item.data.target as string),
    }))
})

function edgeAction(data: MapElementData) {
  return String(data.action || '')
}

function handleNodeSelect(node: RelationNodeSelection | null) {
  selectedNode.value = node
  layerError.value = ''
}

function openAppMap() {
  if (!selectedNode.value?.id) return
  router.push(`/p/${projectId.value}/map/app/${selectedNode.value.id}`)
}

async function loadAppSnapshots() {
  if (!selectedNode.value?.id) return
  loadingLayer.value = true
  layerError.value = ''
  activeExtensionLabel.value = '应用快照图层'
  try {
    const next = await fetchMapLayerAppSnapshots(projectId.value, selectedNode.value.id)
    extensionElements.value = mergeElements(extensionElements.value, next)
    if (!next.length) {
      layerError.value = '应用快照图层暂无数据'
    }
  } catch (err) {
    layerError.value = err instanceof Error ? err.message : '应用快照图层加载失败'
  } finally {
    loadingLayer.value = false
  }
}

function clearExtensionLayers() {
  extensionElements.value = []
  activeExtensionLabel.value = ''
  layerError.value = ''
}

function mergeElements(base: MapElement[], incoming: MapElement[]) {
  const map = new Map<string, MapElement>()
  ;[...base, ...incoming].forEach((item) => {
    const key = `${item.group || ''}:${item.data.id}:${item.data.source || ''}:${item.data.target || ''}`
    map.set(key, item)
  })
  return Array.from(map.values())
}

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

<style scoped>
.home-map-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, .45fr) 1fr;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.home-map-toolbar > div:first-child {
  display: grid;
  gap: 4px;
}

.home-map-toolbar span,
.layer-status {
  color: #64748b;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.toolbar-actions button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
  font-weight: 800;
}

.toolbar-actions button:hover:not(:disabled) {
  background: #0f172a;
  color: #fff;
}

.toolbar-actions button:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.toolbar-actions .danger {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}

.layer-status {
  grid-column: 1 / -1;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(15, 118, 110, 0.08);
  font-weight: 700;
}

.layer-status.error {
  color: #b91c1c;
  background: rgba(220, 38, 38, 0.08);
}

@media (max-width: 860px) {
  .home-map-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
