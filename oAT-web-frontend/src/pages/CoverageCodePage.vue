<template>
  <CoverageCodeView
    :eyebrow="'Coverage Code'"
    :title="payload?.className || className || '类源码详情'"
    :subtext="payload?.report?.versionNumber || '覆盖率源码视图'"
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
import { fetchCoverageCode } from '@/api/bootstrap'
import type { CoverageCodePayload } from '@/api/types'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const reportId = computed(() => String(route.query.reportId || ''))
const className = computed(() => String(route.query.className || ''))
const payload = ref<CoverageCodePayload | null>(null)
const loading = ref(false)
const error = ref('')
const reportRoute = computed(() => ({
  name: 'coverage-details',
  params: { projectId: projectId.value, appId: appId.value },
  query: { reportId: reportId.value },
}))

async function load() {
  if (!appId.value || !reportId.value || !className.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    payload.value = await fetchCoverageCode(projectId.value, {
      appId: appId.value,
      reportId: reportId.value,
      className: className.value,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载源码详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
