<template>
  <CoverageCodeView
    :eyebrow="'Coverage Code'"
    :title="sourceCoverage?.sourcePath || className || '源码详情'"
    :subtext="reportVersionText"
    :report-route="reportRoute"
    :loading="loading"
    :loading-text="'正在加载源码详情...'"
    :error="error"
    :methods="methodsPayload?.methods"
    :source-coverage="sourceCoverage || undefined"
    :language="sourceCoverage?.language || methodsPayload?.language || reportMetadata?.report?.language || reportMetadata?.app?.language"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import CoverageCodeView from '@/components/snapshot/CoverageCodeView.vue'
import { fetchCoverageMethods, fetchCoverageReportMetadata, fetchSourceCoverage } from '@/features/coverage/api/core'
import type { CoverageMethodsPayload, CoverageReportMetadata, SourceCoveragePayload } from '@/entities/coverage/model'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const reportId = computed(() => String(route.query.reportId || ''))
const className = computed(() => String(route.query.className || ''))
const methodsPayload = ref<CoverageMethodsPayload | null>(null)
const sourceCoverage = ref<SourceCoveragePayload | null>(null)
const reportMetadata = ref<CoverageReportMetadata | null>(null)
const loading = ref(false)
const error = ref('')
const reportVersionText = computed(() => reportMetadata.value?.report?.versionNumber || '覆盖率源码视图')
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
  sourceCoverage.value = null
  methodsPayload.value = null
  try {
    const [metadata, sourcePayload, nextMethodsPayload] = await Promise.all([
      fetchCoverageReportMetadata(projectId.value, reportId.value).catch(() => null),
      fetchSourceCoverage(projectId.value, reportId.value, className.value),
      fetchCoverageMethods(projectId.value, reportId.value, className.value),
    ])
    reportMetadata.value = metadata
    methodsPayload.value = nextMethodsPayload
    sourceCoverage.value = sourcePayload
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载源码详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
