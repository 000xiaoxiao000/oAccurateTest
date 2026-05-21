<template>
  <CoverageCodeView
    :eyebrow="'My Snapshot Code'"
    :title="payload?.className || className || '聚合类源码详情'"
    :subtext="payload?.app?.name || appId || '按个人快照聚合的类源码覆盖视图'"
    :report-route="`/p/${projectId}/my-snapshots/code-report`"
    :loading="loading"
    :loading-text="'正在加载聚合类源码详情...'"
    :error="error"
    :methods="payload?.methods"
    :colored-source-html="payload?.coloredSourceHtml"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import CoverageCodeView from '@/components/snapshot/CoverageCodeView.vue'
import { fetchMySnapshotAggregateCode } from '@/api/bootstrap'
import type { MySnapshotAggregateCodePayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.query.appId || ''))
const className = computed(() => String(route.query.className || ''))
const payload = ref<MySnapshotAggregateCodePayload | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  if (!projectId.value || !appId.value || !className.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchMySnapshotAggregateCode(projectId.value, {
      appId: appId.value,
      className: className.value,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载聚合类源码详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
