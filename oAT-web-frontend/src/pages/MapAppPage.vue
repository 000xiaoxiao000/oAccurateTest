<template>
  <section>
    <div class="toolbar map-toolbar">
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
        <button type="button" :class="['chip-button', showHotLabels && 'active']" @click="showHotLabels = !showHotLabels">关联热度</button>
        <button type="button" :class="['chip-button', highlightRelated && 'active']" @click="highlightRelated = !highlightRelated">高亮关联</button>
      </div>

      <div class="layer-actions">
        <div>
          <strong>{{ selectedNode ? selectedNode.label || selectedNode.id : '图层操作' }}</strong>
          <span>{{ selectedNode ? selectedTypeText : '先在图谱中选择应用、快照、表或远程服务节点' }}</span>
        </div>
        <div class="action-buttons">
          <button v-if="canExpandAppSnapshots" type="button" :disabled="loadingLayer" @click="loadAppSnapshots">展开快照</button>
          <button v-if="canLoadSnapshotLayer" type="button" :disabled="loadingLayer" @click="loadSnapshotTables">表结构图层</button>
          <button v-if="canLoadSnapshotLayer" type="button" :disabled="loadingLayer" @click="loadSnapshotRemote">远程服务图层</button>
          <button v-if="canLoadSnapshotLayer" type="button" :disabled="loadingLayer" @click="loadSnapshotCode">源码关联图谱</button>
          <button v-if="canOpenSnapshot" type="button" :disabled="loadingLayer" @click="openSnapshotDetail">打开快照详情</button>
          <button v-if="canExpandTableSnapshots" type="button" :disabled="loadingLayer" @click="loadTableSnapshots">展开关联快照</button>
          <button v-if="canExpandRemoteSnapshots" type="button" :disabled="loadingLayer" @click="loadDubboSnapshots">展开关联快照</button>
          <button v-if="extensionElements.length" type="button" class="danger" :disabled="loadingLayer" @click="clearExtensionLayers">清除扩展图层</button>
        </div>
      </div>
      <div v-if="loadingLayer || activeExtensionLabel || layerError" :class="['layer-status', layerError && 'error']">
        {{ layerError || (loadingLayer ? `正在加载${activeExtensionLabel || '图层'}...` : `已加载：${activeExtensionLabel}`) }}
      </div>
    </div>
    <RelationBoard
      eyebrow="Application Map"
      :title="`应用链路图 · ${appId}`"
      subtext="支持代码层、表结构层、快照表/远程服务/源码关联图谱等扩展图层。"
      :loading="loading"
      :error="error"
      :nodes="nodes"
      :edges="edges"
      :back-route="`/p/${projectId}/apps`"
      back-label="返回应用列表"
      :context-actions="contextActions"
      :show-edge-labels="showHotLabels"
      :highlight-related="highlightRelated"
      @node-select="handleNodeSelect"
      @context-action="handleContextAction"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import {
  fetchMapApp,
  fetchMapLayerAppSnapshots,
  fetchMapLayerDubboSnapshots,
  fetchMapLayerSnapshotCode,
  fetchMapLayerSnapshotRemote,
  fetchMapLayerSnapshotTables,
  fetchMapLayerTableSnapshots,
} from '@/api/bootstrap'
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
const appId = computed(() => String(route.params.appId || ''))
const loading = ref(false)
const loadingLayer = ref(false)
const error = ref('')
const layerError = ref('')
const activeExtensionLabel = ref('')
const elements = ref<MapElement[]>([])
const extensionElements = ref<MapElement[]>([])
const selectedLayers = ref<string[]>(['code'])
const selectedNode = ref<RelationNodeSelection | null>(null)
const showHotLabels = ref(false)
const highlightRelated = ref(true)

const layerOptions = [
  { label: '代码层', value: 'code' },
  { label: '表结构层', value: 'table' },
]

const allElements = computed(() => mergeElements(elements.value, extensionElements.value))

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
      meta: buildNodeMeta(item.data, item.classes),
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

const selectedRaw = computed(() => selectedNode.value?.raw as Partial<MapElementData> | undefined)
const selectedClasses = computed(() => selectedNode.value?.classes || selectedNode.value?.type?.split(/\s+/).filter(Boolean) || [])
const selectedTypeText = computed(() => selectedClasses.value.length ? `类型：${selectedClasses.value.join(' / ')}` : '节点')
const canExpandAppSnapshots = computed(() => hasSelectedClass('app'))
const canLoadSnapshotLayer = computed(() => hasSelectedClass('snapshot'))
const canOpenSnapshot = computed(() => hasSelectedClass('snapshot'))
const canExpandTableSnapshots = computed(() => hasSelectedClass('table') && Boolean(selectedRaw.value?.database && selectedRaw.value?.name))
const canExpandRemoteSnapshots = computed(() => hasSelectedClass('dubbo') && Boolean(selectedRaw.value?.interfaceName && selectedRaw.value?.methodName))
const contextActions = computed(() => [
  { id: 'refresh-map', label: '刷新图谱', target: 'canvas' as const, disabled: loading.value || loadingLayer.value },
  { id: 'load-app-snapshots', label: '展开快照', target: 'app' as const, disabled: loadingLayer.value },
  { id: 'open-snapshot-detail', label: '打开快照详情', target: 'snapshot' as const, disabled: loadingLayer.value },
  { id: 'load-snapshot-tables', label: '表结构图层', target: 'snapshot' as const, disabled: loadingLayer.value },
  { id: 'load-snapshot-remote', label: '远程服务图层', target: 'snapshot' as const, disabled: loadingLayer.value },
  { id: 'load-snapshot-code', label: '源码关联图谱', target: 'snapshot' as const, disabled: loadingLayer.value },
  { id: 'load-table-snapshots', label: '展开关联快照', target: 'table' as const, disabled: loadingLayer.value },
  { id: 'load-remote-snapshots', label: '展开关联快照', target: 'remote' as const, disabled: loadingLayer.value },
  ...(extensionElements.value.length
    ? [
        { id: 'clear-extension-layers', label: '清除扩展图层', target: 'canvas' as const, danger: true, disabled: loadingLayer.value },
        { id: 'clear-extension-layers', label: '清除扩展图层', target: 'snapshot' as const, danger: true, disabled: loadingLayer.value },
      ]
    : []),
])

function buildNodeMeta(data: MapElementData, classes?: string[]) {
  return [
    classes?.length ? `图层 ${classes.join(' / ')}` : '',
    typeof data.coverageRate === 'number' ? `覆盖率 ${data.coverageRate}%` : '',
    data.cyclo ? `复杂度 ${data.cyclo}` : '',
    data.appId ? `应用 ${data.appId}` : '',
    data.database ? `库 ${data.database}` : '',
    data.methodName ? `方法 ${data.methodName}` : '',
  ].filter(Boolean)
}

function edgeAction(data: MapElementData) {
  return String((data as MapElementData & { action?: string }).action || '')
}

function hasSelectedClass(className: string) {
  return selectedClasses.value.some((item) => item === className || item.includes(className))
}

function toggleLayer(layer: string) {
  selectedLayers.value = selectedLayers.value.includes(layer)
    ? selectedLayers.value.filter((item) => item !== layer)
    : [...selectedLayers.value, layer]
}

function handleNodeSelect(node: RelationNodeSelection | null) {
  selectedNode.value = node
  layerError.value = ''
}

function handleContextAction(actionId: string, node: RelationNodeSelection | null) {
  selectedNode.value = node
  layerError.value = ''
  if (actionId === 'load-app-snapshots') {
    loadAppSnapshots()
    return
  }
  if (actionId === 'open-snapshot-detail') {
    openSnapshotDetail()
    return
  }
  if (actionId === 'load-snapshot-tables') {
    loadSnapshotTables()
    return
  }
  if (actionId === 'load-snapshot-remote') {
    loadSnapshotRemote()
    return
  }
  if (actionId === 'load-snapshot-code') {
    loadSnapshotCode()
    return
  }
  if (actionId === 'load-table-snapshots') {
    loadTableSnapshots()
    return
  }
  if (actionId === 'load-remote-snapshots') {
    loadDubboSnapshots()
    return
  }
  if (actionId === 'refresh-map') {
    load()
    return
  }
  if (actionId === 'clear-extension-layers') {
    clearExtensionLayers()
  }
}

async function loadAppSnapshots() {
  const targetAppId = String(selectedRaw.value?.id || appId.value)
  await loadExtensionLayer('应用快照图层', () => fetchMapLayerAppSnapshots(projectId.value, targetAppId), false)
}

async function loadSnapshotTables() {
  if (!selectedNode.value?.id) return
  await loadExtensionLayer('快照表结构图层', () => fetchMapLayerSnapshotTables(projectId.value, selectedNode.value!.id), true)
}

async function loadSnapshotRemote() {
  if (!selectedNode.value?.id) return
  await loadExtensionLayer('快照远程服务图层', () => fetchMapLayerSnapshotRemote(projectId.value, selectedNode.value!.id), true)
}

async function loadSnapshotCode() {
  if (!selectedNode.value?.id) return
  await loadExtensionLayer('源码关联图谱', () => fetchMapLayerSnapshotCode(projectId.value, selectedNode.value!.id), true)
}

async function loadTableSnapshots() {
  const database = selectedRaw.value?.database
  const table = selectedRaw.value?.name
  if (!database || !table) return
  await loadExtensionLayer('表关联快照图层', () => fetchMapLayerTableSnapshots(projectId.value, database, table), false)
}

async function loadDubboSnapshots() {
  const interfaceName = selectedRaw.value?.interfaceName
  const methodName = selectedRaw.value?.methodName
  if (!interfaceName || !methodName) return
  await loadExtensionLayer('远程服务关联快照图层', () => fetchMapLayerDubboSnapshots(projectId.value, interfaceName, methodName), false)
}

async function loadExtensionLayer(label: string, loader: () => Promise<MapElement[]>, replaceSecondary: boolean) {
  loadingLayer.value = true
  layerError.value = ''
  activeExtensionLabel.value = label
  try {
    const next = await loader()
    extensionElements.value = replaceSecondary ? next : mergeElements(extensionElements.value, next)
    if (!next.length) {
      layerError.value = `${label}暂无数据`
    }
  } catch (err) {
    layerError.value = err instanceof Error ? err.message : `${label}加载失败`
  } finally {
    loadingLayer.value = false
  }
}

function clearExtensionLayers() {
  extensionElements.value = []
  activeExtensionLabel.value = ''
  layerError.value = ''
}

function openSnapshotDetail() {
  if (!selectedNode.value?.id) return
  const snapshotAppId = String(selectedRaw.value?.appId || appId.value)
  router.push(`/p/${projectId.value}/apps/${snapshotAppId}/snapshots/${selectedNode.value.id}`)
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
  layerError.value = ''
  activeExtensionLabel.value = ''
  extensionElements.value = []
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

.map-toolbar {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.chip-group,
.action-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.chip-button,
.action-buttons button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.08);
  cursor: pointer;
  font-weight: 800;
  transition: transform .12s ease, background .12s ease, color .12s ease, box-shadow .12s ease;
}

.chip-button.active,
.action-buttons button:hover:not(:disabled) {
  background: #0f172a;
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(15, 23, 42, .16);
}

.action-buttons button:active:not(:disabled),
.chip-button:active:not(:disabled) {
  transform: translateY(0);
}

.action-buttons button:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.action-buttons .danger {
  background: rgba(220, 38, 38, 0.12);
  color: #b91c1c;
}

.layer-actions {
  display: grid;
  grid-template-columns: minmax(220px, .4fr) 1fr;
  gap: 12px;
  align-items: center;
}

.layer-actions div:first-child {
  display: grid;
  gap: 4px;
}

.layer-actions span,
.layer-status {
  color: #64748b;
}

.layer-status {
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
  .layer-actions {
    grid-template-columns: 1fr;
  }
}
</style>
