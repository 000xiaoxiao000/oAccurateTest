import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TrafficRecord } from '../types/traffic'

export const useTrafficStore = defineStore('traffic', () => {
  const records = ref<TrafficRecord[]>([])
  const isCapturing = ref(false)
  const currentCaseName = ref('')
  const searchQuery = ref('')
  const statusFilter = ref<'all' | 'success' | 'failed'>('all')
  const proxyPort = ref(8888)

  function isSuccessRecord(record: TrafficRecord): boolean {
    const code = Number(record.statusCode)
    return !isNaN(code) && code >= 200 && code < 400
  }

  const filteredRecords = computed(() => {
    const q = searchQuery.value.toLowerCase()
    return records.value.filter(r => {
      if (statusFilter.value === 'success' && !isSuccessRecord(r)) return false
      if (statusFilter.value === 'failed' && isSuccessRecord(r)) return false
      if (!q) return true
      return r.url.toLowerCase().includes(q) ||
        r.caseName.toLowerCase().includes(q) ||
        r.method.toLowerCase().includes(q) ||
        String(r.statusCode).includes(q)
    })
  })

  const stats = computed(() => {
    const total = records.value.length
    const success = records.value.filter(isSuccessRecord).length
    const failed = total - success
    const avgDuration = total > 0
      ? Math.round(records.value.reduce((acc, r) => acc + r.duration, 0) / total)
      : 0
    return { total, success, failed, avgDuration }
  })

  function addRecord(record: TrafficRecord) {
    records.value.unshift(record)
  }

  function deleteRecord(id: string) {
    const idx = records.value.findIndex(r => r.id === id)
    if (idx !== -1) records.value.splice(idx, 1)
    window.electronAPI?.deleteTrafficRecord(id)
  }

  function clearRecords() {
    records.value = []
    window.electronAPI?.clearTrafficRecords()
  }

  function replaceRecords(nextRecords: TrafficRecord[]) {
    records.value = nextRecords
  }

  function setStatusFilter(filter: 'all' | 'success' | 'failed') {
    statusFilter.value = filter
  }

  async function startCapture(caseName: string) {
    currentCaseName.value = caseName
    const result = await window.electronAPI?.startCapture(caseName)
    if (result?.success) {
      isCapturing.value = true
      proxyPort.value = result.port ?? 8888
    }
    return result
  }

  async function stopCapture() {
    await window.electronAPI?.stopCapture()
    isCapturing.value = false
  }

  return {
    records,
    isCapturing,
    currentCaseName,
    searchQuery,
    statusFilter,
    proxyPort,
    filteredRecords,
    stats,
    addRecord,
    deleteRecord,
    clearRecords,
    replaceRecords,
    setStatusFilter,
    startCapture,
    stopCapture
  }
})
