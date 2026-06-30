import { ref, type ComputedRef, type Ref } from 'vue'

import { autoSaveMonitorSystemSnapshot, fetchMonitorSnapshotContext, saveMonitorMySnapshot, saveMonitorSystemSnapshot, uploadResource } from '@/api/bootstrap'
import { apiPost } from '@/api/http'
import type { GraphViewPayload, MonitorSnapshotContextPayload, TraceItemSummary } from '@/api/types'
import type { useToast } from '@/composables/useToast'
import type { MonitorSnapshotDialogMode, MonitorSnapshotFormState } from '@/features/monitor/components/MonitorSnapshotDialog.vue'

type ToastApi = ReturnType<typeof useToast>

export function useMonitorSnapshots(
  projectId: ComputedRef<string>,
  traces: Ref<TraceItemSummary[]>,
  graph: Ref<GraphViewPayload | null>,
  selectedTraceId: Ref<string>,
  autoSaveMySnapshot: Ref<boolean>,
  autoSaveSystemSnapshot: Ref<boolean>,
  toast: ToastApi,
) {
  const savingSnapshot = ref(false)
  const snapshotDialogOpen = ref(false)
  const snapshotDialogMode = ref<MonitorSnapshotDialogMode>('system')
  const snapshotMenuOpen = ref(false)
  const snapshotContextLoading = ref(false)
  const topicImageUploading = ref(false)
  const snapshotNotice = ref('')
  const snapshotFormError = ref('')
  const snapshotContext = ref<MonitorSnapshotContextPayload | null>(null)
  const snapshotForm = ref<MonitorSnapshotFormState>({
    traceId: '',
    appId: '',
    directory: 'root',
    title: '',
    topicImage: '',
    describe: '',
    versionCycle: 30,
    labels: [] as string[],
    principals: [] as string[],
  })
  const autoSavedTraceIds = {
    my: new Set<string>(),
    system: new Set<string>(),
  }

  async function autoSaveSnapshot() {
    if (!selectedTraceId.value) return
    savingSnapshot.value = true
    snapshotNotice.value = ''
    try {
      const selectedTrace = traces.value.find((trace) => trace.traceId === selectedTraceId.value)
      const result = await autoSaveMonitorSystemSnapshot(projectId.value, {
        traceId: selectedTraceId.value,
        appId: selectedTrace?.appId,
        title: graph.value?.title || selectedTrace?.title || '实时监控自动快照',
      })
      const message = result || '系统快照已自动保存'
      snapshotNotice.value = message
      toast.success(message)
    } catch (err) {
      const message = err instanceof Error ? err.message : '保存系统快照失败'
      snapshotNotice.value = message
      toast.error(message)
    } finally {
      savingSnapshot.value = false
    }
  }

  async function saveMySnapshot() {
    if (!selectedTraceId.value) return
    savingSnapshot.value = true
    snapshotNotice.value = ''
    try {
      const selectedTrace = traces.value.find((trace) => trace.traceId === selectedTraceId.value)
      const body = new URLSearchParams()
      body.set('traceId', selectedTraceId.value)
      body.set('autoSave', 'true')
      body.set('name', (selectedTrace?.title || graph.value?.title || selectedTraceId.value).trim().slice(0, 200))
      body.set('describe', '实时监控自动保存')
      body.append('labels', '自动保存')
      body.append('labels', '实时监控')
      const result = await apiPost<unknown>(`/api/projects/${projectId.value}/snapshots/my/save`, body.toString(), 'application/x-www-form-urlencoded;charset=UTF-8')
      const message = extractResultMessage(result, '我的快照已自动保存')
      snapshotNotice.value = message
      toast.success(message)
    } catch (err) {
      const message = err instanceof Error ? err.message : '保存我的快照失败'
      snapshotNotice.value = message
      toast.error(message)
    } finally {
      savingSnapshot.value = false
    }
  }

  async function autoSaveCurrentTraceSnapshots() {
    if (!selectedTraceId.value) return
    await saveMySnapshot()
    await autoSaveSnapshot()
  }

  async function runAutoSaveForNewTraces(items: TraceItemSummary[]) {
    for (const item of items.slice(0, 5)) {
      if (!item.traceId) continue
      if (autoSaveMySnapshot.value && !autoSavedTraceIds.my.has(item.traceId)) {
        autoSavedTraceIds.my.add(item.traceId)
        selectedTraceId.value = item.traceId
        await saveMySnapshot()
      }
      if (autoSaveSystemSnapshot.value && !autoSavedTraceIds.system.has(item.traceId)) {
        autoSavedTraceIds.system.add(item.traceId)
        selectedTraceId.value = item.traceId
        await autoSaveSnapshot()
      }
    }
  }

  async function batchSaveMySnapshots(items: TraceItemSummary[]) {
    for (const trace of items.slice(0, 10)) {
      if (!trace.traceId) continue
      selectedTraceId.value = trace.traceId
      await saveMySnapshot()
    }
  }

  async function batchSaveSystemSnapshots(items: TraceItemSummary[]) {
    for (const trace of items.slice(0, 10)) {
      if (!trace.traceId) continue
      selectedTraceId.value = trace.traceId
      await autoSaveSnapshot()
    }
  }

  function toggleSnapshotMenu() {
    if (!selectedTraceId.value || savingSnapshot.value) return
    snapshotMenuOpen.value = !snapshotMenuOpen.value
  }

  async function openSnapshotDialog(mode: MonitorSnapshotDialogMode = 'system') {
    if (!selectedTraceId.value) return
    snapshotMenuOpen.value = false
    snapshotDialogMode.value = mode
    snapshotDialogOpen.value = true
    snapshotContextLoading.value = true
    snapshotFormError.value = ''
    try {
      const selectedTrace = traces.value.find((trace) => trace.traceId === selectedTraceId.value)
      const context = await fetchMonitorSnapshotContext(projectId.value, selectedTraceId.value, selectedTrace?.appId)
      snapshotContext.value = context
      snapshotForm.value = {
        traceId: context.traceId,
        appId: context.appId,
        directory: 'root',
        title: (context.defaultTitle || graph.value?.title || (mode === 'my' ? '实时监控我的快照' : '实时监控系统快照')).slice(0, 50),
        topicImage: '',
        describe: '',
        versionCycle: 30,
        labels: mode === 'my' ? ['实时监控'] : [],
        principals: context.currentUserId ? [context.currentUserId] : [],
      }
    } catch (err) {
      snapshotFormError.value = err instanceof Error ? err.message : `加载${mode === 'my' ? '我的' : '系统'}快照保存上下文失败`
    } finally {
      snapshotContextLoading.value = false
    }
  }

  function closeSnapshotDialog() {
    if (savingSnapshot.value || topicImageUploading.value) return
    snapshotDialogOpen.value = false
    snapshotFormError.value = ''
  }

  async function handleTopicImageUpload(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    topicImageUploading.value = true
    snapshotFormError.value = ''
    try {
      snapshotForm.value.topicImage = await uploadResource(file)
    } catch (err) {
      snapshotFormError.value = err instanceof Error ? err.message : '图片上传失败'
    } finally {
      input.value = ''
      topicImageUploading.value = false
    }
  }

  async function submitSnapshotForm() {
    const form = snapshotForm.value
    const error = validateSnapshotForm(form, snapshotDialogMode.value)
    if (error) {
      snapshotFormError.value = error
      return
    }
    savingSnapshot.value = true
    snapshotFormError.value = ''
    snapshotNotice.value = ''
    try {
      if (snapshotDialogMode.value === 'my') {
        await saveMonitorMySnapshot(projectId.value, {
          traceId: form.traceId,
          appId: form.appId,
          name: form.title,
          describe: form.describe,
          labels: form.labels,
        })
        snapshotNotice.value = '我的快照已保存'
        toast.success(snapshotNotice.value)
      } else {
        const result = await saveMonitorSystemSnapshot(projectId.value, form)
        snapshotNotice.value = result || '系统快照已保存'
        toast.success(snapshotNotice.value)
      }
      snapshotDialogOpen.value = false
    } catch (err) {
      const message = err instanceof Error ? err.message : `${snapshotDialogMode.value === 'my' ? '我的' : '系统'}快照保存失败`
      snapshotFormError.value = message
      toast.error(message)
    } finally {
      savingSnapshot.value = false
    }
  }

  return {
    savingSnapshot,
    snapshotDialogOpen,
    snapshotDialogMode,
    snapshotMenuOpen,
    snapshotContextLoading,
    topicImageUploading,
    snapshotFormError,
    snapshotContext,
    snapshotForm,
    autoSaveCurrentTraceSnapshots,
    runAutoSaveForNewTraces,
    batchSaveMySnapshots,
    batchSaveSystemSnapshots,
    toggleSnapshotMenu,
    openSnapshotDialog,
    closeSnapshotDialog,
    handleTopicImageUpload,
    submitSnapshotForm,
  }
}

function validateSnapshotForm(form: MonitorSnapshotFormState, mode: MonitorSnapshotDialogMode) {
  if (!form.title || form.title.length < 4) return '名称至少包含4个字符'
  if (form.title.length > 50) return '名称不能超过50个字符'
  if (form.describe.length > 512) return '描述不能超过512个字符'
  if (mode === 'system' && !form.principals.length) return '请至少选择一个负责人'
  return ''
}

function extractResultMessage(result: unknown, fallback: string) {
  if (typeof result === 'string') return result || fallback
  if (result && typeof result === 'object' && 'message' in result) {
    return String((result as { message?: unknown }).message || fallback)
  }
  return fallback
}
