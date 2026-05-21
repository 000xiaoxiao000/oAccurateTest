<template>
  <CoverageReportView
    :eyebrow="'My Snapshot Code Report'"
    :title="'我的快照代码报告'"
    :subtext="payload?.report?.appId || payload?.appId || '按接口聚合的快照代码关系视图'"
    :graph-route="`/p/${projectId}/my-snapshots`"
    :back-route="`/p/${projectId}/my-snapshots`"
    :back-label="'返回我的快照'"
    :loading="loading"
    :loading-text="'正在加载我的快照代码报告...'"
    :error="error"
    :report="payload?.report"
    :class-stats="payload?.classStats"
    :code-relationships="payload?.codeRelationships"
    :info-items="infoItems"
    :build-code-route="buildCodeRoute"
    :empty-title="'当前暂无可展示的代码报告'"
    :empty-description="'还没有任何快照命中到可汇总的代码关系。'"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import CoverageReportView from '@/components/snapshot/CoverageReportView.vue'
import { fetchMySnapshotCodeReport } from '@/api/bootstrap'
import type { MySnapshotCodeReportPayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = ref<MySnapshotCodeReportPayload | null>(null)
const loading = ref(false)
const error = ref('')

const infoItems = computed(() => [
  { label: '应用', value: payload.value?.report?.appId || payload.value?.appId || '-' },
  { label: '快照数', value: payload.value?.report?.snapshotCount ?? 0 },
  { label: '代码类数', value: payload.value?.report?.totalClasses ?? 0 },
  { label: '请求入口', value: payload.value?.codeRelationships?.length ?? 0 },
])

function buildCodeRoute(className: string) {
  return {
    path: `/p/${projectId.value}/my-snapshots/code`,
    query: {
      appId: payload.value?.appId || payload.value?.report?.appId || '',
      className,
    },
  }
}

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchMySnapshotCodeReport(projectId.value, {
      sort: typeof route.query.sort === 'string' ? route.query.sort : undefined,
      keyword: typeof route.query.keyword === 'string' ? route.query.keyword : undefined,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照代码报告失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
