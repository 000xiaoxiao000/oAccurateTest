import { computed, ref, type ComputedRef, type Ref } from 'vue'

import { apiGetRaw } from '@/api/http'
import type { CollectorSourceSummary, OnlineSessionSummary } from '@/api/types'
import type { useProjectStore } from '@/stores/project'

type ProjectStore = ReturnType<typeof useProjectStore>

type RawMonitorProbeSession = OnlineSessionSummary & {
  clientInfo?: {
    appKey?: string
    addressIp?: string
    agentVersion?: string
    systemDir?: string
    pid?: string
    jvmVersion?: string
    jvmOption?: string
  }
  application?: {
    appId?: string
    appName?: string
    projectSrcName?: string
  }
}

export function useMonitorSources(
  projectId: ComputedRef<string>,
  projectStore: ProjectStore,
  selectedAppIds: Ref<string[]>,
) {
  const probes = ref<OnlineSessionSummary[]>([])
  const probeKeyword = ref('')
  const collectorKeyword = ref('')
  const probesExpanded = ref(false)
  const probeLoading = ref(false)
  const collectorLoading = ref(false)
  const probeError = ref('')
  const collectorError = ref('')
  const probePreviewLimit = 4

  const collectorPayload = computed(() => projectId.value ? projectStore.collectorSourcesByProjectId[projectId.value] : undefined)
  const collectorSources = computed<CollectorSourceSummary[]>(() => collectorPayload.value?.sources || [])

  const filteredProbes = computed(() => {
    const needle = probeKeyword.value.toLowerCase()
    if (!needle) return probes.value
    return probes.value.filter((probe) => [probe.addressIp, probe.appName, probe.projectSrcName, probe.agentVersion, probe.systemDir]
      .join(' ')
      .toLowerCase()
      .includes(needle))
  })

  const visibleProbes = computed(() => probesExpanded.value ? filteredProbes.value : filteredProbes.value.slice(0, probePreviewLimit))

  const filteredCollectorSources = computed(() => {
    const needle = collectorKeyword.value.toLowerCase()
    return collectorSources.value.filter((source) => {
      const matchesApp = !selectedAppIds.value.length || selectedAppIds.value.includes(source.appId || '')
      const haystack = [
        source.appName,
        source.appId,
        source.language,
        source.collectorType,
        source.health,
        source.addressIp,
        source.pid,
        source.agentVersion,
        source.sourceId,
        source.sessionId,
      ].join(' ').toLowerCase()
      return matchesApp && (!needle || haystack.includes(needle))
    })
  })

  const availableClientIps = computed(() => Array.from(new Set(probes.value.map((probe) => probe.addressIp).filter((ip): ip is string => Boolean(ip)))))

  async function loadProbes() {
    probeLoading.value = true
    probeError.value = ''
    try {
      const items = await apiGetRaw<RawMonitorProbeSession[]>(`/api/projects/${projectId.value}/monitor/probeStatus`)
      probes.value = items.map(normalizeProbeSession)
    } catch (err) {
      probeError.value = err instanceof Error ? err.message : '加载探针失败'
    } finally {
      probeLoading.value = false
    }
  }

  async function loadCollectorSources() {
    if (!projectId.value) return
    collectorLoading.value = true
    collectorError.value = ''
    try {
      await projectStore.loadCollectorSources(projectId.value)
    } catch (err) {
      collectorError.value = err instanceof Error ? err.message : '加载采集源失败'
    } finally {
      collectorLoading.value = false
    }
  }

  return {
    probes,
    collectorSources,
    probeKeyword,
    collectorKeyword,
    probesExpanded,
    probePreviewLimit,
    probeLoading,
    collectorLoading,
    probeError,
    collectorError,
    filteredProbes,
    visibleProbes,
    filteredCollectorSources,
    availableClientIps,
    loadProbes,
    loadCollectorSources,
  }
}

function normalizeProbeSession(item: RawMonitorProbeSession): OnlineSessionSummary {
  const clientInfo = item.clientInfo
  const application = item.application
  const appId = item.appId || clientInfo?.appKey || application?.appId
  return {
    appId,
    addressIp: item.addressIp || clientInfo?.addressIp,
    agentVersion: item.agentVersion || clientInfo?.agentVersion,
    systemDir: item.systemDir || clientInfo?.systemDir,
    pid: item.pid || clientInfo?.pid,
    jvmVersion: item.jvmVersion || clientInfo?.jvmVersion,
    jvmOption: item.jvmOption || clientInfo?.jvmOption,
    onlineTime: item.onlineTime,
    appName: item.appName || application?.appName || (appId ? '未识别应用' : '未绑定应用'),
    projectSrcName: item.projectSrcName || application?.projectSrcName || '-',
  }
}
