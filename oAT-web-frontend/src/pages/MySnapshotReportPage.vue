<template>
  <CoverageReportView
    :eyebrow="'My Snapshot Report'"
    :title="payload?.snapshot.name || '我的快照覆盖率报告'"
    :subtext="`${payload?.snapshot.appId || '未识别应用'} · 单次快照运行视角`"
    :graph-route="`/p/${projectId}/my-snapshots/${snapshotId}/graph`"
    :back-route="`/p/${projectId}/my-snapshots/${snapshotId}`"
    :back-label="'返回快照详情'"
    :loading="loading"
    :loading-text="'正在加载我的快照覆盖率报告...'"
    :error="error"
    :report="payload?.report"
    :class-stats="payload?.classStats"
    :info-items="infoItems"
    :build-code-route="buildCodeRoute"
    :empty-title="'当前快照暂无覆盖率汇总'"
    :empty-description="'这类快照的报告来自运行时链路聚合，若静态源码元数据不完整，部分统计会为空或偏少。'"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import CoverageReportView from '@/components/snapshot/CoverageReportView.vue'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const payload = computed(() => projectStore.mySnapshotReportByKey[storeKey.value])
const loading = ref(false)
const error = ref('')

const infoItems = computed(() => [
  { label: '应用', value: payload.value?.report?.appId || payload.value?.snapshot.appId || '-' },
  { label: '快照数', value: payload.value?.report?.snapshotCount ?? 1 },
  { label: '代码类数', value: payload.value?.report?.totalClasses ?? 0 },
  { label: '圈复杂度总和', value: payload.value?.report?.totalComplexity ?? 0 },
])

function buildCodeRoute(className: string) {
  return {
    name: 'my-snapshot-code',
    params: {
      projectId: projectId.value,
      snapshotId: snapshotId.value,
    },
    query: { className },
  }
}

async function load() {
  if (!projectId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadMySnapshotReport(projectId.value, snapshotId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载我的快照覆盖率报告失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
