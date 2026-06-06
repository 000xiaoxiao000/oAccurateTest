<template>
  <main class="share-shell">
    <header class="share-header">
      <div class="brand-block">
        <MascotCanvas :size="58" color="#0f766e" seed="share-snapshot" />
        <div>
          <div class="eyebrow">Shared Snapshot</div>
          <h1>{{ payload?.snapshot.name || payload?.snapshot.title || '共享快照' }}</h1>
        </div>
      </div>
      <nav class="share-nav">
        <RouterLink to="/login">登录</RouterLink>
        <RouterLink to="/register">注册</RouterLink>
      </nav>
    </header>

    <div v-if="loading" class="status-card">正在加载共享快照...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <section class="hero-card">
        <div>
          <div class="label-row" v-if="payload.labels?.length">
            <span
              v-for="label in payload.labels"
              :key="label.name"
              class="label-chip"
              :style="labelStyle(label.color)"
            >
              {{ label.name }}
            </span>
          </div>
          <p class="description">{{ payload.snapshot.describe || '暂无描述' }}</p>
        </div>
        <div class="meta-grid">
          <div class="meta-item">
            <span>创建者</span>
            <strong>{{ payload.createUser?.nickname || payload.createUser?.name || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>更新时间</span>
            <strong :title="payload.snapshot.updateTimeText || undefined">{{ payload.snapshot.updateTimeRelativeText || payload.snapshot.updateTimeText || '-' }}</strong>
          </div>
          <div class="meta-item">
            <span>关联用例</span>
            <strong>{{ payload.usecases?.length || 0 }}</strong>
          </div>
        </div>
      </section>

      <section v-if="payload.usecases?.length" class="panel related-panel">
        <div class="card-title">
          <h2>关联用例</h2>
        </div>
        <div class="usecase-list">
          <RouterLink
            v-for="usecase in payload.usecases"
            :key="usecase.id"
            class="usecase-card"
            :to="`/share/usecase/${usecase.id}`"
          >
            <strong>{{ usecase.title }}</strong>
            <span>{{ usecase.updateTimeText || '-' }}</span>
          </RouterLink>
        </div>
      </section>

      <GraphView
        eyebrow="Shared Trace"
        fallback-title="共享链路图"
        back-route="/login"
        back-label="登录系统"
        :loading="graphLoading"
        loading-text="正在加载共享链路图..."
        :error="graphError"
        :graph="graph"
        :selected-node-id="selectedNodeId"
        :selected-node-detail="selectedNodeDetail"
        arrow-marker-id="shareSnapshotArrow"
        @select-node="selectNode"
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchShareSnapshot, fetchShareSnapshotGraph, fetchShareSnapshotGraphNode } from '@/api/bootstrap'
import type { GraphNodeDetailPayload, GraphViewPayload, PublicSnapshotPayload } from '@/api/types'
import MascotCanvas from '@/components/MascotCanvas.vue'
import GraphView from '@/components/snapshot/GraphView.vue'

const route = useRoute()
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const payload = ref<PublicSnapshotPayload>()
const graph = ref<GraphViewPayload>()
const selectedNodeId = ref('')
const selectedNodeDetail = ref<GraphNodeDetailPayload>()
const loading = ref(false)
const graphLoading = ref(false)
const error = ref('')
const graphError = ref('')

function labelStyle(color?: string) {
  const labelColor = color || '#0f766e'
  return {
    color: labelColor,
    backgroundColor: labelColorBackground(labelColor),
    borderColor: labelColorBorder(labelColor),
  }
}

function labelColorBackground(color: string) {
  return labelColorWithAlpha(color, 0.14)
}

function labelColorBorder(color: string) {
  return labelColorWithAlpha(color, 0.20)
}

function labelColorWithAlpha(color: string, alpha: number) {
  const hex = color.trim().replace(/^#/, '')
  const normalized = hex.length === 3 ? hex.split('').map((char) => `${char}${char}`).join('') : hex
  if (!/^[0-9a-fA-F]{6}$/.test(normalized)) return 'rgba(15, 118, 110, .14)'
  const red = Number.parseInt(normalized.slice(0, 2), 16)
  const green = Number.parseInt(normalized.slice(2, 4), 16)
  const blue = Number.parseInt(normalized.slice(4, 6), 16)
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}

async function load() {
  if (!snapshotId.value) {
    error.value = '缺少 snapshotId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchShareSnapshot(snapshotId.value)
    await loadGraph()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载共享快照失败'
  } finally {
    loading.value = false
  }
}

async function loadGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    graph.value = await fetchShareSnapshotGraph(snapshotId.value)
    const defaultNodeId = graph.value.showDefaultNode?.id || graph.value.nodes[0]?.id || ''
    if (defaultNodeId) {
      await selectNode(defaultNodeId)
    }
  } catch (err) {
    graphError.value = err instanceof Error ? err.message : '加载共享链路图失败'
  } finally {
    graphLoading.value = false
  }
}

async function selectNode(nodeId: string) {
  selectedNodeId.value = nodeId
  try {
    selectedNodeDetail.value = await fetchShareSnapshotGraphNode(snapshotId.value, nodeId)
  } catch (err) {
    graphError.value = err instanceof Error ? err.message : '加载节点详情失败'
  }
}

onMounted(load)
</script>

<style scoped>
.share-shell {
  min-height: 100vh;
  padding: 28px min(5vw, 56px) 48px;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.16), transparent 34%),
    linear-gradient(135deg, #f7fbf8 0%, #eef7f4 42%, #f8fafc 100%);
}

.share-header,
.brand-block,
.share-nav,
.card-title,
.label-row,
.usecase-list {
  display: flex;
  align-items: center;
  gap: 14px;
}

.share-header {
  justify-content: space-between;
  margin: 0 auto 24px;
  max-width: 1180px;
}

.brand-block h1 {
  margin: 2px 0 0;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.share-nav a {
  border-radius: 999px;
  padding: 9px 14px;
  background: rgba(255, 255, 255, 0.8);
  color: #0f766e;
  font-weight: 800;
}

.status-card,
.hero-card,
.panel {
  max-width: 1180px;
  margin: 0 auto 18px;
  padding: 18px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 18px;
}

.description {
  color: #475569;
  line-height: 1.8;
}

.label-row,
.usecase-list {
  flex-wrap: wrap;
}

.label-chip {
  border: 1px solid rgba(15, 118, 110, .16);
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.meta-item,
.usecase-card {
  border-radius: 18px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.meta-item span,
.usecase-card span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.usecase-card {
  color: #0f172a;
  min-width: 220px;
}

@media (max-width: 860px) {
  .share-header,
  .hero-card {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
