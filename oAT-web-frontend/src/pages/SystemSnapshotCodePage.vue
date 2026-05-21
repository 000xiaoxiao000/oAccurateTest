<template>
  <CoverageCodeView
    :eyebrow="'Snapshot Code'"
    :title="payload?.className || className || '类源码详情'"
    :subtext="payload?.snapshot.title || '系统快照源码覆盖视图'"
    :report-route="reportRoute"
    :loading="loading"
    :loading-text="'正在加载源码详情...'"
    :error="error"
    :methods="payload?.methods"
    :colored-source-html="payload?.coloredSourceHtml"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import CoverageCodeView from '@/components/snapshot/CoverageCodeView.vue'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const className = computed(() => String(route.query.className || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}:${className.value}`)
const payload = computed(() => projectStore.systemSnapshotCodeByKey[storeKey.value])
const reportRoute = computed(() => `/p/${projectId.value}/apps/${appId.value}/snapshots/${snapshotId.value}/report`)
const loading = ref(false)
const error = ref('')

async function load() {
  if (!projectId.value || !appId.value || !snapshotId.value || !className.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadSystemSnapshotCode(projectId.value, appId.value, snapshotId.value, className.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载系统快照源码详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
