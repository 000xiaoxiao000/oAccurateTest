<template>
  <div v-if="projectId && context" ref="rootRef" class="ai-floating" :class="{ open: panelOpen, hidden: mascotHidden }" :style="floatingStyle">
    <button v-if="mascotHidden" class="restore-button" type="button" @pointerdown="startDrag" @click="showMascot">显示AI助手</button>

    <button v-else class="launcher" type="button" title="打开 AI 助手" @pointerdown="startDrag" @click="togglePanel">
      <MascotCanvas :size="88" :color="mascotColor" :seed="projectId" :mood="asking ? 'thinking' : mood" />
      <span>AI 助手</span>
    </button>

    <section v-if="!mascotHidden && panelOpen" ref="panelRef" class="assistant-panel" :style="panelStyle">
      <header class="panel-header" @pointerdown="startDrag">
        <div>
          <div class="eyebrow">AI Interactive</div>
          <h2>项目悬浮助手</h2>
        </div>
        <div class="panel-tools">
          <button type="button" title="恢复默认位置和尺寸" @click="resetLayout">↺</button>
          <button type="button" title="清空当前助手对话" @click="clearConversation">清空</button>
          <RouterLink :to="`/p/${projectId}/ai`">工作台</RouterLink>
          <button type="button" title="隐藏小人" @click="hideMascot">-</button>
          <button type="button" title="收起助手" @click="panelOpen = false">x</button>
        </div>
      </header>

      <div class="section-box messages-box" :class="{ collapsed: isSectionCollapsed('messages') }">
        <button class="section-title section-toggle" type="button" @click="toggleSection('messages')">
          <span>对话</span>
          <span>{{ isSectionCollapsed('messages') ? '展开' : '收起' }}</span>
        </button>
        <div v-show="!isSectionCollapsed('messages')" class="message-list">
          <article v-for="item in messages" :key="item.id" class="message" :class="item.role">
            <strong>{{ item.role === 'user' ? '你' : 'AI' }}</strong>
            <span>{{ item.text }}</span>
          </article>
          <article v-if="!messages.length" class="message assistant">
            <strong>AI</strong>
            <span>{{ context.mascotHint || context.welcomeMessage }}</span>
          </article>
        </div>
      </div>

      <div class="context-status">
        当前页面：{{ route.fullPath }} · {{ context.appCount }} 个应用 · {{ context.onlineAppCount }} 个在线
      </div>

      <div class="section-box" :class="{ collapsed: isSectionCollapsed('links') }">
        <button class="section-title section-toggle" type="button" @click="toggleSection('links')">
          <span>快捷入口</span>
          <span>{{ isSectionCollapsed('links') ? '展开' : '收起' }}</span>
        </button>
        <div v-show="!isSectionCollapsed('links')" class="quick-links">
          <RouterLink v-for="link in normalizedQuickLinks" :key="link.title + link.url" :to="link.url">
            <strong>{{ link.title }}</strong>
            <span>{{ link.description }}</span>
          </RouterLink>
        </div>
      </div>

      <div class="section-box" :class="{ collapsed: isSectionCollapsed('starters') }">
        <button class="section-title section-toggle" type="button" @click="toggleSection('starters')">
          <span>快捷提问</span>
          <span>{{ isSectionCollapsed('starters') ? '展开' : '收起' }}</span>
        </button>
        <div v-show="!isSectionCollapsed('starters')" class="starters">
          <button v-for="item in starterQuestions" :key="item" type="button" @click="question = item">{{ item }}</button>
        </div>
      </div>

      <form class="compose" @submit.prevent="sendQuestion">
        <textarea v-model="question" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看"></textarea>
        <input ref="imageInput" type="file" accept="image/*" class="hidden-input" @change="handleImageChange" />
        <div class="compose-actions">
          <span class="state">{{ stateText }}</span>
          <div class="toolbar">
            <button type="button" :class="['tool-button', imageData && 'active']" title="上传图片提问" @click="selectImage">
              图
              <span v-if="imageData" class="remove-image" @click.stop="clearImage">x</span>
            </button>
            <button type="button" :class="['tool-button', recording && 'active']" title="语音输入" @click="toggleVoiceInput">声</button>
            <button class="send-button" type="submit" :disabled="asking">{{ asking ? '生成中' : '发送' }}</button>
          </div>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { useProjectStore } from '@/stores/project'
import MascotCanvas from '@/components/MascotCanvas.vue'
import type { AIQuickLink } from '@/api/types'

type Message = { id: string; role: 'user' | 'assistant'; text: string }
type FloatingPosition = { left: number; top: number }
type PanelSize = { width: number; height: number }
type SectionName = 'messages' | 'links' | 'starters'
type SpeechRecognitionLike = {
  lang: string
  continuous: boolean
  interimResults: boolean
  start: () => void
  stop: () => void
  onresult: ((event: { results: ArrayLike<{ 0: { transcript: string } }> }) => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

const route = useRoute()
const projectStore = useProjectStore()
const question = ref('')
const messages = ref<Message[]>([])
const panelOpen = ref(false)
const mascotHidden = ref(false)
const asking = ref(false)
const error = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const imageData = ref('')
const recording = ref(false)
const position = ref<FloatingPosition | null>(null)
const panelSize = ref<PanelSize | null>(null)
const collapsedSections = ref<SectionName[]>([])
const layoutLocked = ref(false)
const dragMoved = ref(false)
let layoutUndoSnapshot: { position: FloatingPosition | null; panelSize: PanelSize | null; collapsedSections: SectionName[] } | null = null
let resizeObserver: ResizeObserver | null = null
let recognition: SpeechRecognitionLike | null = null

const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const context = computed(() => projectId.value ? projectStore.aiContextByProjectId[projectId.value] : undefined)
const mascotColor = computed(() => context.value?.mascot?.mascotPrimary || '#0f766e')
const mood = computed(() => error.value ? 'error' : 'happy')
const stateText = computed(() => error.value || (asking.value ? '生成中...' : imageData.value ? '已附加图片' : '就绪'))
const storagePrefix = computed(() => projectId.value ? `spa-ai-floating:${projectId.value}` : '')
const floatingStyle = computed(() => position.value ? { left: `${position.value.left}px`, top: `${position.value.top}px`, right: 'auto', bottom: 'auto' } : {})
const panelStyle = computed(() => panelSize.value ? { width: `${panelSize.value.width}px`, height: `${panelSize.value.height}px` } : {})

const normalizedQuickLinks = computed(() => normalizeLinks(context.value?.quickLinks || []))
const starterQuestions = computed(() => {
  const routeSpecific = routeStarters(route.path)
  return [...routeSpecific, ...(context.value?.starterQuestions || [])].slice(0, 8)
})

watch(projectId, async (value) => {
  if (!value) return
  await loadContext(value)
  restoreState()
}, { immediate: true })

watch(messages, () => saveHistory(), { deep: true })
watch(panelOpen, (value) => {
  if (storagePrefix.value) sessionStorage.setItem(`${storagePrefix.value}:panel`, value ? '1' : '0')
  if (value) nextTick(installResizeObserver)
})

async function loadContext(value: string) {
  try {
    await projectStore.loadAiContext(value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 AI 助手失败'
  }
}

function uid() {
  return `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

function restoreState() {
  if (!storagePrefix.value) return
  try {
    messages.value = JSON.parse(localStorage.getItem(`${storagePrefix.value}:history`) || '[]')
  } catch {
    messages.value = []
  }
  mascotHidden.value = localStorage.getItem(`${storagePrefix.value}:hidden`) === '1'
  panelOpen.value = sessionStorage.getItem(`${storagePrefix.value}:panel`) === '1'
  position.value = readJson<FloatingPosition | null>(`${storagePrefix.value}:position`, null)
  panelSize.value = readJson<PanelSize | null>(`${storagePrefix.value}:panel-size`, null)
  collapsedSections.value = readJson<SectionName[]>(`${storagePrefix.value}:sections`, [])
  layoutLocked.value = localStorage.getItem(`${storagePrefix.value}:layout-locked`) === '1'
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) as T : fallback
  } catch {
    return fallback
  }
}

function saveHistory() {
  if (!storagePrefix.value) return
  localStorage.setItem(`${storagePrefix.value}:history`, JSON.stringify(messages.value.slice(-30)))
}

function togglePanel() {
  if (dragMoved.value) {
    dragMoved.value = false
    return
  }
  panelOpen.value = !panelOpen.value
}

function hideMascot() {
  mascotHidden.value = true
  panelOpen.value = false
  localStorage.setItem(`${storagePrefix.value}:hidden`, '1')
}

function showMascot() {
  mascotHidden.value = false
  panelOpen.value = true
  localStorage.setItem(`${storagePrefix.value}:hidden`, '0')
}

function clearConversation() {
  messages.value = []
  error.value = ''
}

function isSectionCollapsed(section: SectionName) {
  return collapsedSections.value.includes(section)
}

function toggleSection(section: SectionName) {
  snapshotLayout()
  const next = new Set(collapsedSections.value)
  if (next.has(section)) next.delete(section)
  else next.add(section)
  collapsedSections.value = Array.from(next)
  if (storagePrefix.value) localStorage.setItem(`${storagePrefix.value}:sections`, JSON.stringify(collapsedSections.value))
}

function resetLayout() {
  snapshotLayout()
  position.value = null
  panelSize.value = null
  collapsedSections.value = []
  if (!storagePrefix.value) return
  localStorage.removeItem(`${storagePrefix.value}:position`)
  localStorage.removeItem(`${storagePrefix.value}:panel-size`)
  localStorage.removeItem(`${storagePrefix.value}:sections`)
}

function snapshotLayout() {
  layoutUndoSnapshot = {
    position: position.value ? { ...position.value } : null,
    panelSize: panelSize.value ? { ...panelSize.value } : null,
    collapsedSections: [...collapsedSections.value],
  }
}

function undoLayout() {
  if (!layoutUndoSnapshot) return
  position.value = layoutUndoSnapshot.position
  panelSize.value = layoutUndoSnapshot.panelSize
  collapsedSections.value = layoutUndoSnapshot.collapsedSections
  if (!storagePrefix.value) return
  if (position.value) localStorage.setItem(`${storagePrefix.value}:position`, JSON.stringify(position.value))
  else localStorage.removeItem(`${storagePrefix.value}:position`)
  if (panelSize.value) localStorage.setItem(`${storagePrefix.value}:panel-size`, JSON.stringify(panelSize.value))
  else localStorage.removeItem(`${storagePrefix.value}:panel-size`)
  localStorage.setItem(`${storagePrefix.value}:sections`, JSON.stringify(collapsedSections.value))
}

function toggleLayoutLock() {
  layoutLocked.value = !layoutLocked.value
  if (storagePrefix.value) localStorage.setItem(`${storagePrefix.value}:layout-locked`, layoutLocked.value ? '1' : '0')
}

function startDrag(event: PointerEvent) {
  if (layoutLocked.value) return
  const target = event.target as HTMLElement | null
  if (target?.closest('button, a, textarea, input, select') && !target.closest('.launcher, .restore-button')) return
  const root = rootRef.value
  if (!root) return
  const rect = root.getBoundingClientRect()
  const startX = event.clientX
  const startY = event.clientY
  const startLeft = rect.left
  const startTop = rect.top
  snapshotLayout()
  dragMoved.value = false
  const move = (moveEvent: PointerEvent) => {
    const deltaX = moveEvent.clientX - startX
    const deltaY = moveEvent.clientY - startY
    if (Math.abs(deltaX) + Math.abs(deltaY) > 4) dragMoved.value = true
    position.value = clampPosition({ left: startLeft + deltaX, top: startTop + deltaY })
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    if (storagePrefix.value && position.value) localStorage.setItem(`${storagePrefix.value}:position`, JSON.stringify(position.value))
    window.setTimeout(() => { dragMoved.value = false }, 0)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function clampPosition(value: FloatingPosition): FloatingPosition {
  const width = rootRef.value?.offsetWidth || (panelOpen.value ? 480 : 120)
  const height = rootRef.value?.offsetHeight || (panelOpen.value ? 640 : 120)
  const margin = 8
  return {
    left: Math.min(Math.max(value.left, margin), Math.max(margin, window.innerWidth - width - margin)),
    top: Math.min(Math.max(value.top, margin), Math.max(margin, window.innerHeight - height - margin)),
  }
}

function installResizeObserver() {
  if (!panelRef.value || resizeObserver) return
  resizeObserver = new ResizeObserver((entries) => {
    const entry = entries[0]
    if (!entry || !storagePrefix.value) return
    const { width, height } = entry.contentRect
    if (layoutLocked.value) return
    if (width > 320 && height > 360) {
      if (!panelSize.value || Math.abs(panelSize.value.width - width) > 6 || Math.abs(panelSize.value.height - height) > 6) {
        snapshotLayout()
      }
      panelSize.value = { width: Math.round(width), height: Math.round(height) }
      localStorage.setItem(`${storagePrefix.value}:panel-size`, JSON.stringify(panelSize.value))
    }
  })
  resizeObserver.observe(panelRef.value)
}

async function sendQuestion() {
  const text = question.value.trim()
  if (!text && !imageData.value) {
    error.value = '请输入问题或上传图片'
    return
  }
  asking.value = true
  error.value = ''
  messages.value.push({ id: uid(), role: 'user', text: text || '[图片提问]' })
  try {
    const result = await projectStore.askAi(projectId.value, {
      question: text,
      pageContext: `route=${route.fullPath}`,
      imageData: imageData.value || undefined,
      sessionState: JSON.stringify({ messages: messages.value.slice(-20) }),
    })
    messages.value.push({ id: uid(), role: 'assistant', text: result.answer || '暂无回答' })
    question.value = ''
    imageData.value = ''
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'AI 提问失败'
    messages.value.push({ id: uid(), role: 'assistant', text: error.value })
  } finally {
    asking.value = false
  }
}

function selectImage() {
  imageInput.value?.click()
}

function handleImageChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = '仅支持图片文件'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    error.value = '图片不能超过 10MB'
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    imageData.value = String(reader.result || '')
  }
  reader.readAsDataURL(file)
  input.value = ''
}

function clearImage() {
  imageData.value = ''
}

function toggleVoiceInput() {
  const api = (window as unknown as { SpeechRecognition?: SpeechRecognitionConstructor; webkitSpeechRecognition?: SpeechRecognitionConstructor })
  const SpeechRecognition = api.SpeechRecognition || api.webkitSpeechRecognition
  if (!SpeechRecognition) {
    error.value = '当前浏览器不支持语音输入'
    return
  }
  if (recording.value) {
    recognition?.stop()
    recording.value = false
    return
  }
  recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.continuous = false
  recognition.interimResults = false
  recognition.onresult = (event) => {
    const transcript = event.results[0]?.[0]?.transcript || ''
    question.value = `${question.value}${question.value ? ' ' : ''}${transcript}`
  }
  recognition.onend = () => {
    recording.value = false
  }
  recognition.onerror = () => {
    error.value = '语音识别失败'
    recording.value = false
  }
  recording.value = true
  recognition.start()
}

function normalizeLinks(links: AIQuickLink[]) {
  const routeLinks = routeQuickLinks(route.path)
  const merged = [...routeLinks, ...links]
  const seen = new Set<string>()
  return merged.filter((link) => {
    if (!link.url || seen.has(link.url)) return false
    seen.add(link.url)
    return true
  }).slice(0, 6)
}

function routeQuickLinks(path: string): AIQuickLink[] {
  const base = `/p/${projectId.value}`
  if (path.includes('/monitor')) {
    return [
      { title: '实时监控', description: '查看最新调用链路', url: `${base}/monitor` },
      { title: '我的快照', description: '查看已保存快照', url: `${base}/my-snapshots` },
    ]
  }
  if (path.includes('/coverage')) {
    return [
      { title: '覆盖率中心', description: '返回覆盖率总览', url: `${base}/coverage` },
      { title: '版本中心', description: '查看版本和报告', url: `${base}/version/apps` },
    ]
  }
  return [
    { title: '项目首页', description: '返回项目上下文', url: `${base}/home` },
    { title: 'AI 工作台', description: '打开完整对话页面', url: `${base}/ai` },
  ]
}

function routeStarters(path: string) {
  if (path.includes('/monitor')) return ['当前监控页应该先看哪些请求', '如果线上有异常，排查顺序是什么']
  if (path.includes('/api-endpoints')) return ['哪些接口还没有覆盖用例', '如何提升当前接口覆盖率']
  if (path.includes('/coverage')) return ['这个页面的覆盖率风险在哪里', '帮我按风险排序低覆盖方法']
  if (path.includes('/usecases')) return ['帮我改进这个用例', '这个用例应该关联哪些快照']
  return ['当前页面我应该先看什么', '帮我总结项目当前测试风险']
}

onMounted(() => {
  if (projectId.value && !context.value) loadContext(projectId.value)
  nextTick(installResizeObserver)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.ai-floating {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 80;
  color: #0f172a;
}

.launcher,
.restore-button {
  border: none;
  cursor: grab;
  touch-action: none;
  box-shadow: 0 18px 42px rgba(15, 23, 42, .18);
}

.launcher {
  display: grid;
  place-items: center;
  width: 108px;
  min-height: 118px;
  border-radius: 28px;
  background: rgba(255, 255, 255, .92);
  border: 1px solid rgba(15, 23, 42, .08);
}

.launcher span {
  margin-top: -6px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.restore-button {
  border-radius: 999px;
  padding: 12px 16px;
  background: #0f766e;
  color: #fff;
}

.assistant-panel {
  position: absolute;
  right: 0;
  bottom: 126px;
  width: min(460px, calc(100vw - 32px));
  height: min(650px, calc(100vh - 160px));
  max-height: min(720px, calc(100vh - 160px));
  min-width: min(340px, calc(100vw - 32px));
  min-height: 460px;
  display: grid;
  grid-template-rows: auto minmax(150px, 1fr) auto auto auto;
  gap: 10px;
  padding: 14px;
  overflow: auto;
  resize: both;
  resize: v-bind(layoutLocked ? 'none' : 'both');
  border-radius: 26px;
  border: 1px solid rgba(15, 23, 42, .10);
  background:
    radial-gradient(circle at top right, color-mix(in srgb, v-bind(mascotColor) 18%, white), transparent 34%),
    rgba(255, 255, 255, .96);
  box-shadow: 0 30px 76px rgba(15, 23, 42, .22);
}

.panel-header,
.panel-tools,
.compose-actions,
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-header {
  justify-content: space-between;
  cursor: grab;
  touch-action: none;
}

.eyebrow {
  color: #0f766e;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: .12em;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 2px 0 0;
  font-size: 17px;
}

.panel-tools button,
.panel-tools a,
.starters button,
.tool-button,
.send-button {
  border: none;
  border-radius: 999px;
  padding: 7px 10px;
  background: #eef7f7;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.section-box,
.context-status,
.compose {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 18px;
  background: rgba(248, 250, 252, .78);
}

.section-box,
.context-status {
  padding: 10px;
}

.section-title {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 900;
}

.section-toggle {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: none;
  background: transparent;
  padding: 0 0 8px;
  cursor: pointer;
}

.section-box.collapsed {
  padding-bottom: 2px;
}

.message-list {
  display: grid;
  gap: 8px;
  max-height: 210px;
  overflow: auto;
}

.message {
  display: grid;
  gap: 3px;
  padding: 9px 10px;
  border-radius: 14px;
  background: #fff;
}

.message.user {
  background: #ecfdf5;
}

.message strong {
  font-size: 12px;
}

.message span,
.context-status,
.quick-links span {
  color: #64748b;
  font-size: 12px;
  white-space: pre-wrap;
}

.quick-links,
.starters {
  display: grid;
  gap: 8px;
}

.quick-links a {
  display: grid;
  gap: 2px;
  padding: 9px 10px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, .06);
}

.starters {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.compose {
  padding: 10px;
}

.compose textarea {
  width: 100%;
  min-height: 78px;
  border: 1px solid #d9e5ea;
  border-radius: 14px;
  padding: 10px;
  resize: vertical;
  font: inherit;
}

.compose-actions {
  justify-content: space-between;
  margin-top: 8px;
}

.state {
  color: #64748b;
  font-size: 12px;
}

.tool-button.active {
  background: #0f766e;
  color: #fff;
}

.remove-image {
  margin-left: 5px;
  color: inherit;
}

.send-button {
  background: #0f172a;
  color: #fff;
}

.send-button:disabled {
  opacity: .55;
  cursor: wait;
}

.hidden-input {
  display: none;
}

@media (max-width: 640px) {
  .ai-floating {
    right: 12px;
    bottom: 12px;
  }

  .assistant-panel {
    right: -4px;
    bottom: 116px;
  }

  .starters {
    grid-template-columns: 1fr;
  }
}
</style>
