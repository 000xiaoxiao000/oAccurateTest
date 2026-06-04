<template>
  <div ref="rootRef" class="ai-floating" :class="{ open: panelOpen, hidden: mascotHidden }" :style="floatingStyle">
    <button v-if="mascotHidden" class="restore-button" type="button" @pointerdown="startDrag" @click="showMascot">显示 AI 助手</button>

    <button v-else class="launcher" type="button" title="打开 AI 助手" data-tooltip="打开 AI 助手" @pointerdown="startDrag" @click="togglePanel">
      <MascotCanvas :size="88" :color="mascotColor" :seed="projectId" :mood="asking ? 'thinking' : mood" :interactive="true" />
      <span v-if="!panelOpen" class="launcher-bubble">{{ launcherHint }}</span>
    </button>

    <section
      v-if="!mascotHidden && panelOpen"
      ref="panelRef"
      class="assistant-panel"
      :class="{ 'layout-locked': layoutLocked, 'custom-layout': Object.keys(panelLayout).length }"
      :style="panelStyle"
      @dblclick="resetPanelSizeFromDoubleClick"
    >
      <div class="resize-handle" title="拖拽放大" @pointerdown.stop.prevent="startResize"></div>
      <header class="panel-header" @pointerdown="startDrag">
        <div>
          <div class="eyebrow">AI Interactive</div>
          <h2>项目悬浮助手</h2>
        </div>
        <div class="panel-tools">
          <button type="button" title="恢复默认内部布局" @click="resetLayout">↺</button>
          <button type="button" title="撤销上一步内部布局调整" @click="undoLayout">↶</button>
          <button type="button" :title="layoutLocked ? '解锁内部布局拖动' : '锁定内部布局'" @click="toggleLayoutLock">{{ layoutLocked ? '🔒' : '🔓' }}</button>
          <button type="button" class="danger-tool" title="清空当前助手对话" @click="clearConversation">清</button>
          <RouterLink :to="`/p/${projectId}/ai`">工作台</RouterLink>
          <button type="button" title="隐藏小人" @click="hideMascot">-</button>
          <button type="button" title="收起助手" @click="setPanelOpen(false)">x</button>
        </div>
      </header>

      <div
        class="section-box messages-box layout-item"
        :class="{ collapsed: isSectionCollapsed('messages') }"
        :style="layoutItemStyle('messages')"
        @pointerdown="startLayoutDrag($event, 'messages')"
      >
        <span
          v-for="direction in layoutResizeDirections"
          :key="direction"
          :class="['layout-resizer', direction]"
          title="拖拽调整区域大小"
          @pointerdown.stop.prevent="startLayoutResize($event, 'messages', direction)"
        ></span>
        <button class="section-title section-toggle" type="button" @click="toggleSection('messages')">
          <span>对话</span>
          <span>{{ isSectionCollapsed('messages') ? '展开' : '收起' }}</span>
        </button>
        <div v-show="!isSectionCollapsed('messages')" class="message-list">
          <article v-for="item in messages" :key="item.id" class="message" :class="item.role">
            <div class="message-avatar">{{ item.role === 'user' ? '我' : 'AI' }}</div>
            <div class="message-body">
              <div class="message-name">{{ item.role === 'user' ? '你' : 'AI 助手' }}</div>
              <div class="message-card">
                <button class="copy-button" type="button" :class="{ copied: copiedMessageId === item.id }" :aria-label="item.role === 'assistant' ? '复制回复内容' : '复制提问内容'" @pointerdown.stop @click="copyMessage(item.text, item.id)">
                  {{ copiedMessageId === item.id ? '已复制' : '复制' }}
                </button>
                <div v-if="item.role === 'assistant'" class="message-text markdown-message" v-html="renderMarkdown(item.text)"></div>
                <div v-else class="message-text">{{ item.text }}</div>
                
                <div v-if="item.role === 'assistant'" class="feedback-actions">
                  <button 
                    class="feedback-btn" 
                    type="button" 
                    :disabled="feedbackSubmitting" 
                    :class="{ active: getMessageFeedback(item.id) === 'helpful' }"
                    :title="getMessageFeedback(item.id) === 'helpful' ? '已标记有帮助' : '有帮助'"
                    @click.stop="submitMessageFeedback(item.id, item.text, 'helpful', 5)"
                  >
                    <span class="feedback-icon">👍</span>
                  </button>
                  <button 
                    class="feedback-btn" 
                    type="button" 
                    :disabled="feedbackSubmitting" 
                    :class="{ active: getMessageFeedback(item.id) === 'not_helpful' }"
                    :title="getMessageFeedback(item.id) === 'not_helpful' ? '已标记没帮助' : '没帮助'"
                    @click.stop="submitMessageFeedback(item.id, item.text, 'not_helpful', 1)"
                  >
                    <span class="feedback-icon">👎</span>
                  </button>
                  <button 
                    class="feedback-btn" 
                    type="button" 
                    :disabled="feedbackSubmitting"
                    title="标记为不正确"
                    @click.stop="submitMessageFeedback(item.id, item.text, 'incorrect', 1, true)"
                  >
                    <span class="feedback-icon">⚠️</span>
                  </button>
                </div>
                
                <div v-if="item.suggestions?.length" class="message-actions">
                  <button v-for="suggestion in item.suggestions" :key="suggestion" class="message-action" type="button" @click="sendPresetQuestion(suggestion)">{{ suggestion }}</button>
                </div>
                <div v-if="item.actions?.length" class="message-actions">
                  <button v-for="action in item.actions" :key="action.title + action.type" class="message-action exec-action" type="button" @click="executeAction(action)">{{ action.title || '执行操作' }}</button>
                </div>
              </div>
            </div>
          </article>
          <article v-if="!messages.length" class="message assistant">
            <div class="message-avatar">AI</div>
            <div class="message-body">
              <div class="message-name">AI 助手</div>
              <div class="message-card">
                <div class="message-text markdown-message" v-html="renderMarkdown(assistantContext.mascotHint || assistantContext.welcomeMessage)"></div>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div
        v-if="contextChips.length"
        class="context-status layout-item"
        :style="layoutItemStyle('context')"
        @pointerdown="startLayoutDrag($event, 'context')"
      >
        <span
          v-for="direction in layoutResizeDirections"
          :key="direction"
          :class="['layout-resizer', direction]"
          title="拖拽调整区域大小"
          @pointerdown.stop.prevent="startLayoutResize($event, 'context', direction)"
        ></span>
        <div v-for="chip in contextChips" :key="chip.id" class="context-chip">
          {{ chip.label }}：<span>{{ chip.value }}</span>
          <button type="button" title="清除" @click="removeContextChip(chip.id)">x</button>
        </div>
      </div>

      <div
        class="section-box layout-item"
        :class="{ collapsed: isSectionCollapsed('links') }"
        :style="layoutItemStyle('links')"
        @pointerdown="startLayoutDrag($event, 'links')"
      >
        <span
          v-for="direction in layoutResizeDirections"
          :key="direction"
          :class="['layout-resizer', direction]"
          title="拖拽调整区域大小"
          @pointerdown.stop.prevent="startLayoutResize($event, 'links', direction)"
        ></span>
        <button class="section-title section-toggle" type="button" @click="toggleSection('links')">
          <span>快捷入口</span>
          <span>{{ isSectionCollapsed('links') ? '展开' : '收起' }}</span>
        </button>
        <div v-show="!isSectionCollapsed('links')" class="quick-links">
          <button v-for="link in normalizedQuickLinks" :key="link.title + link.url" type="button" @click="openQuickLink(link)">
            <span class="quick-link-icon">{{ quickLinkIcon(link.title) }}</span>
            <span class="quick-link-copy">
              <strong>{{ link.title }}</strong>
              <small>{{ link.description }}</small>
            </span>
          </button>
          <div v-if="!normalizedQuickLinks.length" class="quick-links-empty">暂无快捷入口，请先在 AI 工作台或当前页面产生上下文。</div>
        </div>
      </div>

      <div
        class="section-box layout-item"
        :class="{ collapsed: isSectionCollapsed('starters') }"
        :style="layoutItemStyle('starters')"
        @pointerdown="startLayoutDrag($event, 'starters')"
      >
        <span
          v-for="direction in layoutResizeDirections"
          :key="direction"
          :class="['layout-resizer', direction]"
          title="拖拽调整区域大小"
          @pointerdown.stop.prevent="startLayoutResize($event, 'starters', direction)"
        ></span>
        <button class="section-title section-toggle" type="button" @click="toggleSection('starters')">
          <span>快捷提问</span>
          <span>{{ isSectionCollapsed('starters') ? '展开' : '收起' }}</span>
        </button>
        <div v-show="!isSectionCollapsed('starters')" class="starters">
          <button v-for="item in starterQuestions" :key="item" type="button" @click="sendPresetQuestion(item)">{{ item }}</button>
        </div>
      </div>

      <form class="compose layout-item" :style="layoutItemStyle('compose')" @pointerdown="startLayoutDrag($event, 'compose')" @submit.prevent="sendQuestion">
        <span
          v-for="direction in layoutResizeDirections"
          :key="direction"
          :class="['layout-resizer', direction]"
          title="拖拽调整区域大小"
          @pointerdown.stop.prevent="startLayoutResize($event, 'compose', direction)"
        ></span>
        <textarea v-model="question" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看" @keydown.enter.exact="handleQuestionEnter"></textarea>
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
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { useProjectStore } from '@/stores/project'
import { useAuthStore } from '@/stores/auth'
import { useDialog } from '@/composables/useDialog'
import MascotCanvas from '@/components/MascotCanvas.vue'
import { renderMarkdown } from '@/utils/markdown'
import { submitAiFeedback } from '@/api/bootstrap'
import type { AIAction, AIFeedbackPayload, AIInteractivePagePayload, AIQuickLink } from '@/api/types'

type Message = { id: string; role: 'user' | 'assistant'; text: string; suggestions?: string[]; actions?: AIAction[] }
type ContextChipId = 'route' | 'apps' | 'online' | 'topic' | 'image' | 'filter' | 'hover' | 'selection'
type ContextChip = { id: ContextChipId; label: string; value: string }
type FloatingPosition = { left: number; top: number }
type PanelSize = { width: number; height: number }
type SectionName = 'messages' | 'links' | 'starters'
type LayoutItemName = 'messages' | 'context' | 'links' | 'starters' | 'compose'
type LayoutRect = { left: number; top: number; width: number; height: number }
type LayoutBounds = { left: number; top: number; width: number; height: number }
type LayoutResizeDirection = 'n' | 'e' | 's' | 'w' | 'ne' | 'nw' | 'se' | 'sw'
type LiveSignals = { filters: string[]; tableHover: string; tableSelection: string }
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

const LAYOUT_VERSION = '2026-05-26-compact-internal-content-ai'
const DEFAULT_COLLAPSED_SECTIONS: SectionName[] = ['links', 'starters']
const layoutResizeDirections: LayoutResizeDirection[] = ['n', 'e', 's', 'w', 'ne', 'nw', 'se', 'sw']
const LAYOUT_ITEM_META: Record<LayoutItemName, { minWidth: number; minHeight: number; preferredHeight: number }> = {
  messages: { minWidth: 180, minHeight: 120, preferredHeight: 190 },
  context: { minWidth: 160, minHeight: 48, preferredHeight: 68 },
  links: { minWidth: 180, minHeight: 92, preferredHeight: 150 },
  starters: { minWidth: 180, minHeight: 86, preferredHeight: 114 },
  compose: { minWidth: 220, minHeight: 122, preferredHeight: 144 },
}

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const authStore = useAuthStore()
const dialog = useDialog()
const question = ref('')
const messages = ref<Message[]>([])
const panelOpen = ref(false)
const mascotHidden = ref(false)
const asking = ref(false)
const error = ref('')
const copiedMessageId = ref('')
const feedbackSubmitting = ref(false)
const messageFeedbacks = ref<Record<string, string>>({})
const imageInput = ref<HTMLInputElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)
const panelRef = ref<HTMLElement | null>(null)
const imageData = ref('')
const recording = ref(false)
const hiddenContextChips = ref<ContextChip['id'][]>([])
const position = ref<FloatingPosition | null>(null)
const panelSize = ref<PanelSize | null>(null)
const collapsedSections = ref<SectionName[]>([])
const layoutLocked = ref(false)
const panelLayout = ref<Partial<Record<LayoutItemName, LayoutRect>>>({})
const liveSignals = ref<LiveSignals>({ filters: [], tableHover: '', tableSelection: '' })
const dragMoved = ref(false)
const layoutDragMoved = ref(false)
let layoutUndoSnapshot: { position: FloatingPosition | null; panelSize: PanelSize | null; collapsedSections: SectionName[]; panelLayout: Partial<Record<LayoutItemName, LayoutRect>> } | null = null
let resizeObserver: ResizeObserver | null = null
let recognition: SpeechRecognitionLike | null = null
let hoveredRowEl: HTMLElement | null = null
let selectedRowEl: HTMLElement | null = null
let panelResizeActive = false
let lastPanelBounds: LayoutBounds | null = null
let panelResizeStartLayout: Partial<Record<LayoutItemName, LayoutRect>> | null = null
let copiedMessageTimer: number | undefined

const projectId = computed(() => typeof route.params.projectId === 'string' ? route.params.projectId : '')
const context = computed(() => projectId.value ? projectStore.aiContextByProjectId[projectId.value] : undefined)
const fallbackContext = computed<AIInteractivePagePayload>(() => ({
  projectId: projectId.value,
  projectName: '当前项目',
  projectSummary: 'AI 助手正在准备项目上下文',
  welcomeMessage: '我是 AI 助手，可以帮你分析当前页面、跳转常用功能或排查测试风险。',
  mascotHint: '点我打开 AI 助手',
  onlineAppCount: 0,
  appCount: 0,
  appNames: [],
  starterQuestions: [],
  abilityCards: [],
  quickLinks: [],
  mascot: {
    mascotName: 'AI 助手',
    mascotPrimary: '#b6d900',
  },
  aiTimeout: 120,
}))
const assistantContext = computed(() => context.value || fallbackContext.value)
const mascotColor = computed(() => assistantContext.value.mascot?.mascotPrimary || '#b6d900')
const launcherHint = computed(() => {
  if (asking.value) return '分析中...'
  if (error.value) return '需要处理'
  return assistantContext.value.mascot?.mascotName || '点我提问'
})
const mood = computed(() => error.value ? 'error' : 'happy')
const stateText = computed(() => error.value || (asking.value ? '生成中...' : imageData.value ? '已附加图片' : '就绪'))
const storagePrefix = computed(() => projectId.value ? `spa-ai-floating:${projectId.value}` : '')
const floatingStyle = computed(() => position.value ? { left: `${position.value.left}px`, top: `${position.value.top}px`, right: 'auto', bottom: 'auto' } : {})
const panelStyle = computed(() => panelSize.value ? { width: `${panelSize.value.width}px`, height: `${panelSize.value.height}px` } : {})

const normalizedQuickLinks = computed(() => normalizeLinks(buildAdaptiveQuickLinks(dynamicQuickLinks.value.length ? dynamicQuickLinks.value : assistantContext.value.quickLinks || [])))
const lastReply = computed(() => projectId.value ? projectStore.aiAssistantLastReplyByProjectId[projectId.value] : undefined)
const dynamicQuickLinks = ref<AIQuickLink[]>([])
const contextChips = computed(() => buildContextChips().filter((chip) => !hiddenContextChips.value.includes(chip.id)))
const starterQuestions = computed(() => {
  const routeSpecific = routeStarters(route.path)
  return buildAdaptiveStarters([...routeSpecific, ...(assistantContext.value.starterQuestions || [])])
})

watch(projectId, async (value) => {
  if (!value) return
  await loadContext(value)
  restoreState()
}, { immediate: true })

watch(messages, () => saveHistory(), { deep: true })
watch(panelOpen, (value) => {
  if (storagePrefix.value) sessionStorage.setItem(`${storagePrefix.value}:panel`, value ? '1' : '0')
  if (!value) {
    teardownResizeObserver()
    return
  }
  if (value) nextTick(() => {
    installResizeObserver()
    applyDefaultPanelLayout()
  })
})

watch(() => route.fullPath, () => {
  clearLiveRowState()
  nextTick(collectLiveFilterState)
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
  const layoutVersionKey = `${storagePrefix.value}:layout-version`
  const isCurrentLayout = localStorage.getItem(layoutVersionKey) === LAYOUT_VERSION
  if (!isCurrentLayout) {
    localStorage.removeItem(`${storagePrefix.value}:position`)
    localStorage.removeItem(`${storagePrefix.value}:panel-size`)
    localStorage.removeItem(`${storagePrefix.value}:sections`)
    localStorage.setItem(layoutVersionKey, LAYOUT_VERSION)
  }
  try {
    messages.value = sanitizeMessages(JSON.parse(localStorage.getItem(`${storagePrefix.value}:history`) || '[]'))
  } catch {
    messages.value = []
  }
  mascotHidden.value = false
  localStorage.removeItem(`${storagePrefix.value}:hidden`)
  panelOpen.value = sessionStorage.getItem(`${storagePrefix.value}:panel`) === '1'
  position.value = null
  localStorage.removeItem(`${storagePrefix.value}:position`)
  panelSize.value = isCurrentLayout ? normalizePanelSize(readJson<PanelSize | null>(`${storagePrefix.value}:panel-size`, null)) : null
  collapsedSections.value = normalizeCollapsedSections(
    isCurrentLayout ? readJson<SectionName[]>(`${storagePrefix.value}:sections`, DEFAULT_COLLAPSED_SECTIONS) : DEFAULT_COLLAPSED_SECTIONS,
  )
  hiddenContextChips.value = readJson<ContextChip['id'][]>(`${storagePrefix.value}:hidden-context-chips`, [])
  dynamicQuickLinks.value = readJson<AIQuickLink[]>(`${storagePrefix.value}:reply-links`, [])
  panelLayout.value = isCurrentLayout ? readJson<Partial<Record<LayoutItemName, LayoutRect>>>(`${storagePrefix.value}:panel-layout`, {}) : {}
  layoutLocked.value = localStorage.getItem(`${storagePrefix.value}:layout-locked`) === '1'
}

function sanitizeMessages(value: unknown): Message[] {
  if (!Array.isArray(value)) return []
  return value
    .filter((item): item is Message => Boolean(item && typeof item === 'object' && 'role' in item && 'text' in item))
    .map((item) => ({
      id: typeof item.id === 'string' && item.id ? item.id : uid(),
      role: (item.role === 'user' ? 'user' : 'assistant') as Message['role'],
      text: sanitizeMessageText(item.text),
      suggestions: Array.isArray(item.suggestions) ? item.suggestions.filter((entry): entry is string => typeof entry === 'string' && Boolean(entry.trim())) : undefined,
      actions: Array.isArray(item.actions) ? item.actions : undefined,
    }))
    .filter((item) => item.text)
}

function sanitizeMessageText(value: unknown) {
  const text = typeof value === 'string' ? value.trim() : String(value || '').trim()
  if (!text) return ''
  if (text.includes('INTERNAL_SERVER_ERROR') || text.includes('NullPointerException')) {
    return 'AI 助手接口发生服务端异常，请稍后重试；后端日志中会记录具体原因。'
  }
  return text
}

function normalizeCollapsedSections(value: SectionName[]) {
  const allowed: SectionName[] = ['messages', 'links', 'starters']
  const next = value.filter((item): item is SectionName => allowed.includes(item))
  return next.length ? Array.from(new Set(next)) : [...DEFAULT_COLLAPSED_SECTIONS]
}

function buildContextChips(): ContextChip[] {
  const chips: ContextChip[] = [
    { id: 'route', label: '当前页面', value: route.fullPath },
    { id: 'apps', label: '应用', value: `${assistantContext.value.appCount} 个` },
    { id: 'online', label: '在线', value: `${assistantContext.value.onlineAppCount} 个` },
  ]
  if (liveSignals.value.filters.length) chips.push({ id: 'filter', label: '当前筛选', value: liveSignals.value.filters.join('、') })
  if (liveSignals.value.tableHover) chips.push({ id: 'hover', label: '当前悬停', value: liveSignals.value.tableHover })
  if (liveSignals.value.tableSelection) chips.push({ id: 'selection', label: '当前选中', value: liveSignals.value.tableSelection })
  if (lastReply.value?.topic) chips.push({ id: 'topic', label: '主题', value: lastReply.value.topic })
  if (imageData.value) chips.push({ id: 'image', label: '图片', value: '已附加' })
  return chips
}

function removeContextChip(id: ContextChip['id']) {
  if (id === 'filter') {
    liveSignals.value = { ...liveSignals.value, filters: [] }
    return
  }
  if (id === 'hover') {
    hoveredRowEl?.classList.remove('ai-floating-row-hover')
    hoveredRowEl = null
    liveSignals.value = { ...liveSignals.value, tableHover: '' }
    return
  }
  if (id === 'selection') {
    selectedRowEl?.classList.remove('ai-floating-row-selected', 'ai-floating-row-hover')
    selectedRowEl = null
    liveSignals.value = { ...liveSignals.value, tableSelection: '' }
    return
  }
  hiddenContextChips.value = Array.from(new Set([...hiddenContextChips.value, id]))
  if (storagePrefix.value) localStorage.setItem(`${storagePrefix.value}:hidden-context-chips`, JSON.stringify(hiddenContextChips.value))
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) as T : fallback
  } catch {
    return fallback
  }
}

function normalizePanelSize(size: PanelSize | null): PanelSize | null {
  if (!size) return null
  const bounds = getPanelBounds()
  return {
    width: Math.min(Math.max(size.width, bounds.minWidth), bounds.maxWidth),
    height: Math.min(Math.max(size.height, bounds.minHeight), bounds.maxHeight),
  }
}

function getPanelBounds() {
  const launcherWidth = 96
  const maxWidth = Math.max(420, window.innerWidth - launcherWidth - 32)
  const maxHeight = Math.max(460, window.innerHeight - 32)
  return {
    minWidth: Math.min(420, maxWidth),
    maxWidth,
    minHeight: Math.min(460, maxHeight),
    maxHeight,
  }
}

function saveHistory() {
  if (!storagePrefix.value) return
  localStorage.setItem(`${storagePrefix.value}:history`, JSON.stringify(sanitizeMessages(messages.value).slice(-30)))
}

function togglePanel() {
  if (dragMoved.value) {
    dragMoved.value = false
    return
  }
  setPanelOpen(!panelOpen.value)
}

function setPanelOpen(value: boolean) {
  if (panelOpen.value === value) return
  const anchor = getFloatingAnchor()
  panelOpen.value = value
  preserveFloatingAnchor(anchor)
}

function hideMascot() {
  const anchor = getFloatingAnchor()
  mascotHidden.value = true
  panelOpen.value = false
  preserveFloatingAnchor(anchor)
}

function showMascot() {
  if (dragMoved.value) {
    dragMoved.value = false
    return
  }
  const anchor = getFloatingAnchor()
  mascotHidden.value = false
  panelOpen.value = true
  preserveFloatingAnchor(anchor)
}

function getFloatingAnchor() {
  if (!position.value) return null
  const rect = rootRef.value?.getBoundingClientRect()
  return rect ? { right: rect.right, bottom: rect.bottom } : null
}

function preserveFloatingAnchor(anchor: { right: number; bottom: number } | null) {
  if (!anchor) return
  nextTick(() => {
    const root = rootRef.value
    if (!root) return
    position.value = clampPosition({
      left: anchor.right - root.offsetWidth,
      top: anchor.bottom - root.offsetHeight,
    })
    if (storagePrefix.value && position.value) localStorage.setItem(`${storagePrefix.value}:position`, JSON.stringify(position.value))
  })
}

async function clearConversation() {
  const confirmed = await dialog.confirm({
    title: '清空 AI 对话',
    message: '确认清空当前项目的悬浮助手对话和后端 AI 助手记忆？AI 工作台会话不会受影响。',
    confirmText: '确认清空',
    tone: 'danger',
  })
  if (!confirmed) return
  messages.value = []
  error.value = ''
  question.value = ''
  imageData.value = ''
  try {
    await projectStore.resetAiSessionState(projectId.value, 'assistant')
  } catch (err) {
    error.value = friendlyAiError(err)
  }
}

function isSectionCollapsed(section: SectionName) {
  return collapsedSections.value.includes(section)
}

function toggleSection(section: SectionName) {
  if (layoutDragMoved.value) return
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
  panelLayout.value = {}
  if (!storagePrefix.value) return
  localStorage.removeItem(`${storagePrefix.value}:position`)
  localStorage.removeItem(`${storagePrefix.value}:panel-size`)
  localStorage.removeItem(`${storagePrefix.value}:sections`)
  localStorage.removeItem(`${storagePrefix.value}:panel-layout`)
  lastPanelBounds = null
  nextTick(applyDefaultPanelLayout)
}

function resetPanelSizeFromDoubleClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  if (target?.closest('.panel-tools, textarea, button, a, input, .section-body, .message-list, .quick-links, .starters')) return
  snapshotLayout()
  panelSize.value = null
  panelLayout.value = {}
  if (!storagePrefix.value) return
  localStorage.removeItem(`${storagePrefix.value}:panel-size`)
  localStorage.removeItem(`${storagePrefix.value}:panel-layout`)
  lastPanelBounds = null
  nextTick(applyDefaultPanelLayout)
}

function snapshotLayout() {
  layoutUndoSnapshot = {
    position: position.value ? { ...position.value } : null,
    panelSize: panelSize.value ? { ...panelSize.value } : null,
    collapsedSections: [...collapsedSections.value],
    panelLayout: clonePanelLayout(panelLayout.value),
  }
}

function undoLayout() {
  if (!layoutUndoSnapshot) return
  position.value = layoutUndoSnapshot.position
  panelSize.value = layoutUndoSnapshot.panelSize
  collapsedSections.value = layoutUndoSnapshot.collapsedSections
  panelLayout.value = clonePanelLayout(layoutUndoSnapshot.panelLayout)
  if (!storagePrefix.value) return
  if (position.value) localStorage.setItem(`${storagePrefix.value}:position`, JSON.stringify(position.value))
  else localStorage.removeItem(`${storagePrefix.value}:position`)
  if (panelSize.value) localStorage.setItem(`${storagePrefix.value}:panel-size`, JSON.stringify(panelSize.value))
  else localStorage.removeItem(`${storagePrefix.value}:panel-size`)
  localStorage.setItem(`${storagePrefix.value}:sections`, JSON.stringify(collapsedSections.value))
  localStorage.setItem(`${storagePrefix.value}:panel-layout`, JSON.stringify(panelLayout.value))
}

function clonePanelLayout(layout: Partial<Record<LayoutItemName, LayoutRect>>) {
  return Object.fromEntries(Object.entries(layout).map(([key, rect]) => [key, rect ? { ...rect } : rect])) as Partial<Record<LayoutItemName, LayoutRect>>
}

function toggleLayoutLock() {
  layoutLocked.value = !layoutLocked.value
  if (storagePrefix.value) localStorage.setItem(`${storagePrefix.value}:layout-locked`, layoutLocked.value ? '1' : '0')
}

function startDrag(event: PointerEvent) {
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

function startResize(event: PointerEvent) {
  const panel = panelRef.value
  if (!panel) return
  const startX = event.clientX
  const startY = event.clientY
  const rect = panel.getBoundingClientRect()
  const startWidth = rect.width
  const startHeight = rect.height
  const startBounds = layoutBounds(panel)
  snapshotLayout()
  panelResizeActive = true
  panelResizeStartLayout = clonePanelLayout(panelLayout.value)
  const move = (moveEvent: PointerEvent) => {
    const bounds = getPanelBounds()
    const nextWidth = Math.min(Math.max(startWidth + moveEvent.clientX - startX, bounds.minWidth), bounds.maxWidth)
    const nextHeight = Math.min(Math.max(startHeight + moveEvent.clientY - startY, bounds.minHeight), bounds.maxHeight)
    panelSize.value = { width: Math.round(nextWidth), height: Math.round(nextHeight) }
    nextTick(() => scalePanelLayout(startBounds, layoutBounds(panel)))
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    panelResizeActive = false
    scalePanelLayout(startBounds, layoutBounds(panel))
    panelResizeStartLayout = null
    if (storagePrefix.value && panelSize.value) {
      localStorage.setItem(`${storagePrefix.value}:panel-size`, JSON.stringify(panelSize.value))
    }
    savePanelLayout()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function installResizeObserver() {
  if (!panelRef.value || resizeObserver) return
  resizeObserver = new ResizeObserver((entries) => {
    const entry = entries[0]
    const panel = panelRef.value
    if (!entry || !panel || panelResizeActive) return
    lastPanelBounds = layoutBounds(panel)
  })
  resizeObserver.observe(panelRef.value)
}

function teardownResizeObserver() {
  resizeObserver?.disconnect()
  resizeObserver = null
  lastPanelBounds = null
  panelResizeStartLayout = null
  panelResizeActive = false
}

function layoutItemStyle(item: LayoutItemName) {
  const rect = panelLayout.value[item]
  return rect ? { left: `${rect.left}px`, top: `${rect.top}px`, width: `${rect.width}px`, height: `${rect.height}px` } : {}
}

function startLayoutDrag(event: PointerEvent, item: LayoutItemName) {
  if (layoutLocked.value || !panelLayout.value[item]) return
  const target = event.target as HTMLElement | null
  const isSectionTitle = Boolean(target?.closest('.section-title'))
  if (target?.closest('button, a, textarea, input, select, .section-body, .message-list, .quick-links, .starters, .layout-resizer') && !isSectionTitle) return
  const panel = panelRef.value
  const rect = panelLayout.value[item]
  if (!panel || !rect) return
  if (!isSectionTitle) event.preventDefault()
  event.stopPropagation()
  snapshotLayout()
  const bounds = layoutBounds(panel)
  const startX = event.clientX
  const startY = event.clientY
  layoutDragMoved.value = false
  const move = (moveEvent: PointerEvent) => {
    moveEvent.preventDefault()
    if (Math.abs(moveEvent.clientX - startX) + Math.abs(moveEvent.clientY - startY) > 4) layoutDragMoved.value = true
    const next = {
      ...rect,
      left: rect.left + moveEvent.clientX - startX,
      top: rect.top + moveEvent.clientY - startY,
    }
    panelLayout.value = {
      ...panelLayout.value,
      [item]: clampLayoutRect(next, bounds),
    }
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    savePanelLayout()
    window.setTimeout(() => { layoutDragMoved.value = false }, 0)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function startLayoutResize(event: PointerEvent, item: LayoutItemName, direction: LayoutResizeDirection) {
  if (layoutLocked.value || !panelLayout.value[item]) return
  const panel = panelRef.value
  const rect = panelLayout.value[item]
  if (!panel || !rect) return
  event.preventDefault()
  event.stopPropagation()
  snapshotLayout()
  const bounds = layoutBounds(panel)
  const startX = event.clientX
  const startY = event.clientY
  const move = (moveEvent: PointerEvent) => {
    const deltaX = moveEvent.clientX - startX
    const deltaY = moveEvent.clientY - startY
    const next = resizeLayoutRect(rect, direction, deltaX, deltaY)
    panelLayout.value = {
      ...panelLayout.value,
      [item]: clampLayoutRect(next, bounds, item),
    }
  }
  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    savePanelLayout()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function resizeLayoutRect(rect: LayoutRect, direction: LayoutResizeDirection, deltaX: number, deltaY: number): LayoutRect {
  const next = { ...rect }
  if (direction.includes('e')) next.width = rect.width + deltaX
  if (direction.includes('s')) next.height = rect.height + deltaY
  if (direction.includes('w')) {
    next.width = rect.width - deltaX
    next.left = rect.left + deltaX
  }
  if (direction.includes('n')) {
    next.height = rect.height - deltaY
    next.top = rect.top + deltaY
  }
  return next
}

function layoutBounds(panel: HTMLElement) {
  const header = panel.querySelector('.panel-header') as HTMLElement | null
  const top = (header?.offsetHeight || 0) + 8
  return {
    left: 10,
    top,
    width: Math.max(0, panel.clientWidth - 20),
    height: Math.max(0, panel.clientHeight - top - 10),
  }
}

function clampLayoutRect(rect: LayoutRect, bounds: ReturnType<typeof layoutBounds>, item?: LayoutItemName) {
  const meta = item ? LAYOUT_ITEM_META[item] : undefined
  const minWidth = Math.min(meta?.minWidth || 160, bounds.width)
  const minHeight = Math.min(meta?.minHeight || 48, bounds.height)
  const width = Math.min(Math.max(rect.width, minWidth), bounds.width)
  const height = Math.min(Math.max(rect.height, minHeight), bounds.height)
  return {
    width,
    height,
    left: Math.min(Math.max(rect.left, bounds.left), bounds.left + bounds.width - width),
    top: Math.min(Math.max(rect.top, bounds.top), bounds.top + bounds.height - height),
  }
}

function scalePanelLayout(previousBounds: LayoutBounds | null, nextBounds: LayoutBounds | null) {
  if (!previousBounds || !nextBounds || !Object.keys(panelLayout.value).length) return
  const widthRatio = nextBounds.width / Math.max(1, previousBounds.width)
  const heightRatio = nextBounds.height / Math.max(1, previousBounds.height)
  const scaled: Partial<Record<LayoutItemName, LayoutRect>> = {}
  const sourceLayout = panelResizeStartLayout || panelLayout.value
  for (const item of Object.keys(sourceLayout) as LayoutItemName[]) {
    const rect = sourceLayout[item]
    if (!rect) continue
    scaled[item] = clampLayoutRect({
      left: nextBounds.left + (rect.left - previousBounds.left) * widthRatio,
      top: nextBounds.top + (rect.top - previousBounds.top) * heightRatio,
      width: rect.width * widthRatio,
      height: rect.height * heightRatio,
    }, nextBounds, item)
  }
  panelLayout.value = scaled
}

function applyDefaultPanelLayout() {
  const panel = panelRef.value
  if (!panel || Object.keys(panelLayout.value).length) return
  const bounds = layoutBounds(panel)
  const gap = 10
  const sideWidth = Math.max(180, Math.round(bounds.width * 0.34))
  const mainWidth = Math.max(200, bounds.width - sideWidth - gap)
  const composeHeight = 142
  const contextHeight = contextChips.value.length ? 68 : 0
  const rightBottom = bounds.top + bounds.height - composeHeight - gap
  const quickRows = Math.max(1, Math.min(normalizedQuickLinks.value.length || 2, 3))
  const starterRows = Math.max(1, Math.min(starterQuestions.value.length || 2, 4))
  const quickHeight = isSectionCollapsed('links') ? 38 : Math.min(210, 46 + quickRows * 52)
  const starterHeight = isSectionCollapsed('starters') ? 38 : Math.min(210, 42 + starterRows * 34)
  const messageHeight = Math.max(180, bounds.height - composeHeight - contextHeight - gap * (contextHeight ? 2 : 1))
  const contextTop = bounds.top + messageHeight + gap
  panelLayout.value = {
    messages: { left: bounds.left, top: bounds.top, width: mainWidth, height: messageHeight },
    ...(contextHeight ? { context: { left: bounds.left, top: contextTop, width: mainWidth, height: contextHeight } } : {}),
    links: { left: bounds.left + mainWidth + gap, top: bounds.top, width: sideWidth, height: quickHeight },
    starters: { left: bounds.left + mainWidth + gap, top: bounds.top + quickHeight + gap, width: sideWidth, height: starterHeight },
    compose: { left: bounds.left, top: rightBottom, width: bounds.width, height: composeHeight },
  }
  lastPanelBounds = bounds
  savePanelLayout()
}

function savePanelLayout() {
  if (storagePrefix.value) localStorage.setItem(`${storagePrefix.value}:panel-layout`, JSON.stringify(panelLayout.value))
}

async function sendQuestion() {
  if (asking.value) return
  const text = question.value.trim()
  if (!projectId.value) {
    error.value = '请先进入或选择一个项目后再使用 AI 助手'
    return
  }
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
      pageContext: buildPageContext(text),
      imageData: imageData.value || undefined,
      sessionState: JSON.stringify({ messages: messages.value.slice(-20) }),
      memoryScope: 'assistant',
    })
    const links = normalizeLinks(result.quickLinks || [], false)
    if (links.length) {
      dynamicQuickLinks.value = links
      if (storagePrefix.value) localStorage.setItem(`${storagePrefix.value}:reply-links`, JSON.stringify(links))
      collapsedSections.value = collapsedSections.value.filter((section) => section !== 'links')
    }
    messages.value.push({
      id: uid(),
      role: 'assistant',
      text: sanitizeMessageText(result.answer || '暂无回答'),
      suggestions: result.suggestions || [],
      actions: result.actions || [],
    })
    await executeAutoAction(result.actions)
    question.value = ''
    imageData.value = ''
  } catch (err) {
    error.value = friendlyAiError(err)
    messages.value.push({ id: uid(), role: 'assistant', text: error.value })
  } finally {
    asking.value = false
  }
}

async function handleQuestionEnter(event: KeyboardEvent) {
  if (event.isComposing) return
  event.preventDefault()
  await sendQuestion()
}

async function sendPresetQuestion(text: string) {
  question.value = text
  await nextTick()
  await sendQuestion()
}

async function copyMessage(text: string, messageId: string) {
  try {
    await writeClipboardText(getSelectedMessageText() || text)
    copiedMessageId.value = messageId
    if (copiedMessageTimer) window.clearTimeout(copiedMessageTimer)
    copiedMessageTimer = window.setTimeout(() => {
      if (copiedMessageId.value === messageId) copiedMessageId.value = ''
    }, 1400)
    error.value = ''
  } catch {
    error.value = '复制失败，请手动选择文本复制'
  }
}

function getMessageFeedback(messageId: string): string {
  return messageFeedbacks.value[messageId] || ''
}

async function submitMessageFeedback(
  messageId: string,
  answerText: string,
  feedbackType: 'helpful' | 'not_helpful' | 'incorrect' | 'incomplete',
  rating: number,
  requireComment = false,
) {
  if (getMessageFeedback(messageId) === feedbackType) return
  let comment = ''
  if (requireComment) {
    comment = window.prompt('请简单说明哪里需要改进，便于 AI 后续学习。\n例如：回答不准确、信息过时...') || ''
    if (!comment.trim()) return
  }
  feedbackSubmitting.value = true
  try {
    const question = messages.value
      .slice(0, messages.value.findIndex((m) => m.id === messageId))
      .reverse()
      .find((m) => m.role === 'user')?.text || ''
    await submitAiFeedback({
      projectId: projectId.value,
      question,
      answer: answerText,
      rating,
      feedbackType,
      comment,
    })
    messageFeedbacks.value = { ...messageFeedbacks.value, [messageId]: feedbackType }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '反馈提交失败'
  } finally {
    feedbackSubmitting.value = false
  }
}

async function writeClipboardText(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) throw new Error('copy failed')
}

function getSelectedMessageText() {
  const selection = window.getSelection()
  const panel = panelRef.value
  if (!selection || !panel || selection.isCollapsed || selection.rangeCount === 0) return ''
  const range = selection.getRangeAt(0)
  if (!isNodeInside(panel, range.startContainer) || !isNodeInside(panel, range.endContainer)) return ''
  return selection.toString().trim()
}

function isNodeInside(container: HTMLElement, node: Node) {
  const element = node.nodeType === Node.ELEMENT_NODE ? node as Element : node.parentElement
  return Boolean(element && container.contains(element))
}

function quickLinkIcon(title?: string) {
  const text = title || ''
  if (text.includes('覆盖率')) return '覆'
  if (text.includes('快照')) return '照'
  if (text.includes('监控') || text.includes('链路')) return '链'
  if (text.includes('AI')) return 'AI'
  if (text.includes('应用')) return '用'
  return '↗'
}

function friendlyAiError(err: unknown) {
  const message = err instanceof Error ? err.message : ''
  if (message === 'Failed to fetch' || message.includes('无法连接后端')) {
    return '无法连接后端 AI 服务，请确认后端已启动，并检查前端代理或跨域配置。'
  }
  if (message.includes('INTERNAL_SERVER_ERROR') || message.includes('500') || message.includes('NullPointerException')) {
    return 'AI 助手接口发生服务端异常，请稍后重试；后端日志中会记录具体原因。'
  }
  return message || 'AI 提问失败'
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

function normalizeLinks(links: AIQuickLink[], includeRouteLinks = true) {
  const routeLinks = includeRouteLinks ? routeQuickLinks(route.path) : []
  const merged = [...routeLinks, ...links]
  const seen = new Set<string>()
  return merged
    .map((link) => ({ ...link, priority: quickLinkPriority(link) }))
    .sort((a, b) => b.priority - a.priority)
    .filter((link) => {
    if (!link.url || seen.has(link.url)) return false
    seen.add(link.url)
    return true
  }).slice(0, 6)
}

function quickLinkPriority(link: AIQuickLink) {
  const text = `${link.title || ''} ${link.description || ''} ${link.url || ''}`
  if (text.includes('选中')) return 100
  if (text.includes('悬停')) return 92
  if (text.includes('筛选')) return 80
  if (text.includes('/monitor') || text.includes('监控')) return 76
  if (text.includes('/search') || text.includes('搜索')) return 72
  if (text.includes('快照')) return 66
  if (text.includes('覆盖率')) return 60
  if (text.includes('AI')) return 50
  return 40
}

function buildAdaptiveQuickLinks(links: AIQuickLink[]) {
  if (!projectId.value) {
    return [
      { title: '项目列表', description: '选择一个项目后使用完整 AI 助手能力', url: '/projects' },
    ]
  }
  const base = `/p/${projectId.value}`
  const next = [...links]
  if (liveSignals.value.tableSelection) {
    next.unshift({ title: '围绕选中数据进入监控台', description: `结合当前选中行查看实时请求：${liveSignals.value.tableSelection}`, url: `${base}/monitor` })
    next.unshift({ title: '围绕选中数据继续搜索', description: `带着当前选中行继续定位：${liveSignals.value.tableSelection}`, url: `${base}/search` })
  } else if (liveSignals.value.tableHover) {
    next.unshift({ title: '围绕悬停数据继续搜索', description: `以当前悬停行为线索继续排查：${liveSignals.value.tableHover}`, url: `${base}/search` })
    next.unshift({ title: '围绕悬停数据查看快照', description: '把当前悬停数据映射到历史快照入口', url: `${base}/my-snapshots` })
  }
  if (liveSignals.value.filters.length) {
    next.push({ title: '保留当前筛选去项目地图', description: `带着筛选条件继续缩小范围：${liveSignals.value.filters.join('、')}`, url: `${base}/map/home` })
  }
  return next
}

function buildAdaptiveStarters(starters: string[]) {
  const next = [...starters]
  if (liveSignals.value.tableSelection) {
    next.unshift(`帮我分析这条数据：${liveSignals.value.tableSelection}`)
    next.unshift('这条选中数据我该重点看什么')
  } else if (liveSignals.value.tableHover) {
    next.unshift('帮我看看这条悬停数据代表什么')
    next.unshift('这条悬停数据下一步应该怎么排查')
  }
  if (liveSignals.value.filters.length) next.push('结合当前筛选条件，建议我下一步操作')
  return Array.from(new Set(next.filter(Boolean))).slice(0, 8)
}

function collectLiveFilterState() {
  const filters: string[] = []
  const selector = 'input[type="text"], input[type="search"], input:not([type]), select, textarea'
  for (const element of Array.from(document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(selector))) {
    if (rootRef.value?.contains(element)) continue
    const value = String(element.value || '').trim()
    if (!value || value.length > 24) continue
    const placeholder = element.getAttribute('placeholder') || element.getAttribute('name') || element.getAttribute('aria-label') || ''
    const item = placeholder.trim() ? `${placeholder.trim()}=${value}` : value
    if (!filters.includes(item)) filters.push(item)
    if (filters.length >= 4) break
  }
  liveSignals.value = { ...liveSignals.value, filters }
}

function summarizeRow(row: HTMLElement) {
  const cells: string[] = []
  for (const cell of Array.from(row.querySelectorAll('td'))) {
    const text = (cell.textContent || '').replace(/\s+/g, ' ').trim()
    if (text && !cells.includes(text)) cells.push(text)
    if (cells.length >= 4) break
  }
  return cells.join(' | ')
}

function findTableRow(target: EventTarget | null) {
  const element = target instanceof HTMLElement ? target : null
  const row = element?.closest('table tbody tr, .ui.table tbody tr') as HTMLElement | null
  if (!row || rootRef.value?.contains(row)) return null
  return row
}

function onDocumentInput(event: Event) {
  if (rootRef.value?.contains(event.target as Node)) return
  collectLiveFilterState()
}

function onDocumentMouseOver(event: MouseEvent) {
  const row = findTableRow(event.target)
  if (!row || row === hoveredRowEl) return
  if (hoveredRowEl && hoveredRowEl !== selectedRowEl) hoveredRowEl.classList.remove('ai-floating-row-hover')
  hoveredRowEl = row
  if (hoveredRowEl !== selectedRowEl) hoveredRowEl.classList.add('ai-floating-row-hover')
  liveSignals.value = { ...liveSignals.value, tableHover: summarizeRow(row) }
}

function onDocumentMouseOut(event: MouseEvent) {
  const row = findTableRow(event.target)
  if (!row) return
  const related = event.relatedTarget instanceof Node ? event.relatedTarget : null
  if (related && row.contains(related)) return
  if (row !== selectedRowEl) row.classList.remove('ai-floating-row-hover')
  if (hoveredRowEl === row) {
    hoveredRowEl = null
    liveSignals.value = { ...liveSignals.value, tableHover: '' }
  }
}

function onDocumentClick(event: MouseEvent) {
  const row = findTableRow(event.target)
  if (!row) return
  selectedRowEl?.classList.remove('ai-floating-row-selected', 'ai-floating-row-hover')
  selectedRowEl = row
  selectedRowEl.classList.add('ai-floating-row-selected')
  liveSignals.value = { ...liveSignals.value, tableSelection: summarizeRow(row) }
}

function clearLiveRowState() {
  hoveredRowEl?.classList.remove('ai-floating-row-hover')
  selectedRowEl?.classList.remove('ai-floating-row-selected', 'ai-floating-row-hover')
  hoveredRowEl = null
  selectedRowEl = null
  liveSignals.value = { ...liveSignals.value, tableHover: '', tableSelection: '' }
}

function bindLivePageSignals() {
  document.addEventListener('input', onDocumentInput, true)
  document.addEventListener('change', onDocumentInput, true)
  document.addEventListener('mouseover', onDocumentMouseOver, true)
  document.addEventListener('mouseout', onDocumentMouseOut, true)
  document.addEventListener('click', onDocumentClick, true)
  collectLiveFilterState()
}

function unbindLivePageSignals() {
  document.removeEventListener('input', onDocumentInput, true)
  document.removeEventListener('change', onDocumentInput, true)
  document.removeEventListener('mouseover', onDocumentMouseOver, true)
  document.removeEventListener('mouseout', onDocumentMouseOut, true)
  document.removeEventListener('click', onDocumentClick, true)
}

function collectTexts(selector: string, limit: number) {
  const values: string[] = []
  for (const element of Array.from(document.querySelectorAll(selector))) {
    if (rootRef.value?.contains(element)) continue
    const text = ((element.getAttribute('placeholder') || element.textContent || '') as string).replace(/\s+/g, ' ').trim()
    if (text && !values.includes(text)) values.push(text.slice(0, 48))
    if (values.length >= limit) break
  }
  return values
}

function collectCoverageSourceContext(currentQuestion: string) {
  const source = document.querySelector<HTMLElement>('.source-container')
  if (!source || rootRef.value?.contains(source)) return ''

  const sourceText = normalizeContextText(source.innerText || source.textContent || '')
  if (!sourceText) return ''

  const methodNames = collectSourceMethodNames()
  const targetMethod = findTargetMethodName(currentQuestion, methodNames)
  const snippet = buildSourceSnippet(sourceText, targetMethod)
  const className = collectCoverageClassName()
  const selectedMethodSummary = collectSelectedMethodSummary(targetMethod)
  const parts = ['页面类型:覆盖率源码页']
  if (className) parts.push(`当前类:${className}`)
  if (targetMethod) parts.push(`目标方法:${targetMethod}`)
  if (selectedMethodSummary) parts.push(`方法覆盖信息:${selectedMethodSummary}`)
  if (snippet) parts.push(`当前源码片段:\n${snippet}`)
  return parts.join('；')
}

function normalizeContextText(text: string) {
  return text
    .replace(/\u00a0/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function collectSourceMethodNames() {
  const names: string[] = []
  for (const button of Array.from(document.querySelectorAll<HTMLElement>('.method-jump'))) {
    if (rootRef.value?.contains(button)) continue
    const raw = normalizeContextText(button.textContent || '')
    const name = raw.split('(')[0]?.trim()
    if (name && !names.includes(name)) names.push(name)
  }
  return names
}

function findTargetMethodName(question: string, methodNames: string[]) {
  const normalizedQuestion = question.toLowerCase()
  const selected = normalizeContextText(selectedRowEl?.textContent || '')
  return methodNames.find((name) => normalizedQuestion.includes(name.toLowerCase()))
    || methodNames.find((name) => selected.toLowerCase().includes(name.toLowerCase()))
    || ''
}

function buildSourceSnippet(sourceText: string, methodName: string) {
  const lines = sourceText.split('\n').map((line) => line.trimEnd()).filter((line) => line.trim())
  if (!lines.length) return ''
  const methodLineIndex = methodName
    ? lines.findIndex((line) => new RegExp(`\\b${escapeRegExp(methodName)}\\s*\\(`).test(line))
    : -1
  const start = methodLineIndex >= 0 ? Math.max(0, methodLineIndex - 4) : 0
  const end = methodLineIndex >= 0 ? Math.min(lines.length, methodLineIndex + 42) : Math.min(lines.length, 52)
  return lines.slice(start, end).join('\n').slice(0, 6000)
}

function collectCoverageClassName() {
  const title = document.querySelector<HTMLElement>('.coverage-code-view h1')?.textContent
    || document.querySelector<HTMLElement>('h1')?.textContent
    || ''
  return normalizeContextText(title)
}

function collectSelectedMethodSummary(targetMethod: string) {
  const rows = Array.from(document.querySelectorAll<HTMLElement>('.method-table tbody tr'))
  const targetRow = rows.find((row) => targetMethod && normalizeContextText(row.textContent || '').includes(targetMethod))
    || (selectedRowEl && selectedRowEl.closest('.method-table') ? selectedRowEl : null)
  return targetRow ? summarizeRow(targetRow).slice(0, 300) : ''
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function buildPageContext(currentQuestion: string) {
  const parts = [`route=${route.fullPath}`]
  const pageTitle = document.title.trim()
  if (pageTitle) parts.push(`页面标题:${pageTitle}`)
  const headers = collectTexts('th, .ui.table thead th', 6)
  if (headers.length) parts.push(`表格字段:${headers.join('、')}`)
  const filterLabels = collectTexts('input[placeholder], textarea[placeholder], select, .search input[placeholder]', 6)
  if (filterLabels.length) parts.push(`筛选线索:${filterLabels.join('、')}`)
  const actionTexts = collectTexts('button, a, .menu .item', 8)
  if (actionTexts.length) parts.push(`可操作项:${actionTexts.join('、')}`)
  if (liveSignals.value.filters.length) parts.push(`当前筛选:${liveSignals.value.filters.join('、')}`)
  if (liveSignals.value.tableSelection) parts.push(`当前选中行:${liveSignals.value.tableSelection}`)
  if (liveSignals.value.tableHover) parts.push(`当前悬停行:${liveSignals.value.tableHover}`)
  const coverageSourceContext = collectCoverageSourceContext(currentQuestion)
  if (coverageSourceContext) parts.push(coverageSourceContext)
  if (currentQuestion) parts.push(`当前问题:${currentQuestion}`)
  return parts.join('；')
}

function normalizeSpaUrl(url?: string) {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    if (parsed.origin === window.location.origin) {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`
    }
    return url
  } catch {
    return url.startsWith('/') ? url : `/${url}`
  }
}

async function openQuickLink(link: AIQuickLink) {
  const target = normalizeSpaUrl(link.url)
  if (!target) return
  if (/^https?:\/\//.test(target)) {
    window.open(target, '_blank', 'noopener,noreferrer')
    return
  }
  await router.push(target)
  setPanelOpen(false)
}

async function executeAction(action: AIAction) {
  if (action.requireConfirm) {
    const confirmed = await dialog.confirm({
      title: action.title || '执行 AI 建议动作',
      message: action.confirmText || `确认执行${action.title}？`,
      confirmText: '确认执行',
      tone: action.type === 'logout' ? 'danger' : 'warning',
    })
    if (!confirmed) return
  }
  if (action.type === 'logout') {
    await authStore.logout()
    await router.replace('/login')
    setPanelOpen(false)
    return
  }
  if (action.type === 'monitorPageAction') {
    const event = new CustomEvent('oat:monitor-action', { detail: action.payload || {} })
    window.dispatchEvent(event)
    const target = `/p/${projectId.value}/monitor`
    if (route.path !== target) await router.push(target)
    setPanelOpen(false)
    return
  }
  const target = normalizeSpaUrl(action.url)
  if (!target) return
  if (/^https?:\/\//.test(target)) {
    window.open(target, '_blank', 'noopener,noreferrer')
    return
  }
  await router.push(target)
  setPanelOpen(false)
}

async function executeAutoAction(actions?: AIAction[]) {
  const action = actions?.find((item) => item.payload?.autoExecute === true && !item.requireConfirm && item.type !== 'logout')
  if (!action) return
  messages.value.push({ id: uid(), role: 'assistant', text: `已按建议执行：${action.title}` })
  await executeAction(action)
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
  bindLivePageSignals()
  nextTick(() => {
    installResizeObserver()
    applyDefaultPanelLayout()
  })
})

onBeforeUnmount(() => {
  teardownResizeObserver()
  unbindLivePageSignals()
  clearLiveRowState()
  recognition?.stop()
  if (copiedMessageTimer) window.clearTimeout(copiedMessageTimer)
})
</script>

<style scoped>
.ai-floating {
  position: fixed;
  right: 22px;
  bottom: 24px;
  z-index: 4500;
  display: flex;
  flex-direction: row-reverse;
  align-items: flex-end;
  gap: 12px;
  max-width: calc(100vw - 44px);
  color: #111827;
  user-select: none;
  --ai-primary: #0f766e;
  --ai-primary-soft: rgba(20, 184, 166, .10);
  --ai-border: rgba(203, 213, 225, .82);
}

.launcher,
.restore-button {
  border: none;
  cursor: grab;
  touch-action: none;
}

.launcher {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 82px;
  height: 82px;
  padding: 0;
  border-radius: 999px;
  background: transparent;
}

.launcher :deep(.mascot-canvas) {
  width: 76px;
  height: 76px;
  display: block;
  filter: drop-shadow(0 12px 24px rgba(15, 23, 42, .16));
  transition: transform .16s ease;
}

.launcher:hover :deep(.mascot-canvas) {
  transform: translateY(-2px) scale(1.03);
}

.launcher-bubble {
  position: absolute;
  right: 82px;
  bottom: 46px;
  width: max-content;
  max-width: 136px;
  padding: 6px 9px;
  border: 1px solid rgba(20, 184, 166, .18);
  border-radius: 14px 14px 4px;
  background: rgba(255, 255, 255, .98);
  color: #334155;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  box-shadow: 0 12px 26px rgba(15, 23, 42, .12);
}

.launcher-bubble::after {
  position: absolute;
  right: -6px;
  bottom: 9px;
  width: 10px;
  height: 10px;
  content: '';
  background: rgba(255, 255, 255, .98);
  border-right: 1px solid rgba(20, 184, 166, .18);
  border-bottom: 1px solid rgba(20, 184, 166, .18);
  transform: rotate(-45deg);
}

.restore-button {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 10px 14px;
  background: #0f766e;
  color: #fff;
  box-shadow: 0 12px 26px rgba(15, 118, 110, .24);
}

.assistant-panel {
  position: relative;
  width: clamp(560px, 54vw, calc(100vw - 132px));
  height: min(760px, calc(100vh - 48px));
  min-width: min(420px, calc(100vw - 132px));
  min-height: min(460px, calc(100vh - 48px));
  max-width: calc(100vw - 132px);
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ai-border);
  border-radius: 20px;
  background:
    radial-gradient(circle at 18% 0%, rgba(182, 217, 0, .10), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, .99), rgba(248, 250, 252, .98));
  box-shadow: 0 18px 46px rgba(15, 23, 42, .16);
  cursor: default;
}

.assistant-panel.custom-layout {
  display: block;
}

.resize-handle {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 3;
  width: 18px;
  height: 18px;
  border: 1px solid rgba(20, 184, 166, .24);
  border-radius: 6px;
  background:
    linear-gradient(135deg, transparent 0 32%, rgba(15, 118, 110, .22) 32% 40%, transparent 40% 54%, rgba(15, 118, 110, .34) 54% 62%, transparent 62% 76%, rgba(15, 118, 110, .5) 76% 84%, transparent 84% 100%),
    rgba(240, 253, 250, .92);
  box-shadow: 0 4px 10px rgba(15, 23, 42, .08);
  cursor: nwse-resize;
}

.panel-header {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px 9px;
  border-bottom: 1px solid rgba(226, 232, 240, .74);
  background: rgba(255, 255, 255, .68);
  backdrop-filter: blur(12px);
  cursor: grab;
  touch-action: none;
}

.eyebrow {
  color: #64748b;
  font-size: 11px;
  line-height: 1.2;
  letter-spacing: .7px;
  text-transform: uppercase;
}

.panel-header h2 {
  margin: 2px 0 0;
  color: #111827;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.3;
}

.panel-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  flex-wrap: wrap;
}

.panel-tools button,
.panel-tools a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  min-width: 26px;
  border: none;
  border-radius: 999px;
  padding: 0 8px;
  background: rgba(241, 245, 249, .9);
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  text-decoration: none;
  cursor: pointer;
  transition: background .16s ease, color .16s ease, transform .16s ease;
}

.panel-tools a {
  color: #0f766e;
  background: transparent;
}

.panel-tools button:hover,
.panel-tools a:hover {
  background: rgba(20, 184, 166, .12);
  color: #0f766e;
  transform: translateY(-1px);
}

.panel-tools .danger-tool {
  background: rgba(254, 242, 242, .96);
  color: #b91c1c;
}

.section-box {
  display: flex;
  flex: 0 0 auto;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
}

.layout-item {
  position: absolute;
  min-height: 0;
  border: 1px solid transparent;
  border-radius: 16px;
  transition: border-color .14s ease, box-shadow .14s ease;
}

.layout-item::before {
  position: absolute;
  top: 7px;
  right: 26px;
  z-index: 4;
  content: '拖拽移动';
  border: 1px solid rgba(203, 213, 225, .7);
  border-radius: 999px;
  padding: 2px 7px;
  background: rgba(255, 255, 255, .92);
  color: rgba(100, 116, 139, .86);
  font-size: 10px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-2px);
  transition: opacity .16s ease, transform .16s ease;
}

.layout-item::after {
  position: absolute;
  top: 7px;
  right: 10px;
  z-index: 4;
  content: '⋮⋮';
  color: rgba(100, 116, 139, .78);
  font-size: 12px;
  letter-spacing: -1px;
  pointer-events: none;
}

.layout-item:hover {
  border-color: rgba(20, 184, 166, .42);
  box-shadow: 0 10px 24px rgba(15, 23, 42, .08);
}

.layout-item:hover::before {
  opacity: 1;
  transform: translateY(0);
}

.layout-resizer {
  position: absolute;
  z-index: 6;
  opacity: 0;
  pointer-events: auto;
  transition: opacity .14s ease, background .14s ease;
}

.layout-item:hover .layout-resizer {
  opacity: 1;
}

.layout-resizer.n,
.layout-resizer.s {
  left: 18px;
  right: 18px;
  height: 7px;
  cursor: ns-resize;
}

.layout-resizer.n { top: -3px; }

.layout-resizer.s { bottom: -3px; }

.layout-resizer.e,
.layout-resizer.w {
  top: 18px;
  bottom: 18px;
  width: 7px;
  cursor: ew-resize;
}

.layout-resizer.e { right: -3px; }

.layout-resizer.w { left: -3px; }

.layout-resizer.ne,
.layout-resizer.nw,
.layout-resizer.se,
.layout-resizer.sw {
  width: 18px;
  height: 18px;
  border-radius: 7px;
  background:
    linear-gradient(135deg, transparent 0 38%, rgba(15, 118, 110, .25) 38% 50%, transparent 50% 64%, rgba(15, 118, 110, .45) 64% 78%, transparent 78% 100%),
    rgba(255, 255, 255, .9);
  box-shadow: 0 4px 12px rgba(15, 23, 42, .10);
}

.layout-resizer.ne { top: -5px; right: -5px; cursor: nesw-resize; }

.layout-resizer.nw { top: -5px; left: -5px; cursor: nwse-resize; }

.layout-resizer.se { right: 6px; bottom: 6px; cursor: nwse-resize; }

.layout-resizer.sw { left: -5px; bottom: -5px; cursor: nesw-resize; }

.layout-resizer.n:hover,
.layout-resizer.s:hover {
  background: rgba(20, 184, 166, .18);
}

.layout-resizer.e:hover,
.layout-resizer.w:hover {
  background: rgba(20, 184, 166, .18);
}

.layout-resizer.se::before {
  position: absolute;
  right: 18px;
  bottom: -2px;
  content: '缩放';
  border: 1px solid rgba(203, 213, 225, .7);
  border-radius: 999px;
  padding: 2px 6px;
  background: rgba(255, 255, 255, .92);
  color: rgba(100, 116, 139, .86);
  font-size: 10px;
  white-space: nowrap;
  pointer-events: none;
}

.assistant-panel.layout-locked .layout-item,
.assistant-panel.layout-locked .layout-item .section-title,
.assistant-panel.layout-locked .context-status,
.assistant-panel.layout-locked .compose {
  cursor: default;
}

.assistant-panel.layout-locked .layout-item::before,
.assistant-panel.layout-locked .layout-item::after,
.assistant-panel.layout-locked .layout-resizer {
  display: none;
}

.messages-box {
  border-bottom: 1px solid rgba(226, 232, 240, .62);
}

.section-title {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: none;
  padding: 7px 13px 4px;
  background: transparent;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .35px;
  cursor: pointer;
}

.layout-item .section-title {
  cursor: move;
  padding-right: 44px;
}

.section-title span:last-child {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
}

.section-box.collapsed {
  flex: 0 0 auto;
}

.message-list {
  display: grid;
  align-content: start;
  gap: 7px;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 13px 10px;
  scrollbar-gutter: stable;
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 1px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #dff7f5;
  color: #0f172a;
  font-size: 11px;
  font-weight: 800;
}

.message.user .message-avatar {
  background: #e5e7eb;
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-name {
  margin-bottom: 3px;
  color: #6b7280;
  font-size: 11px;
  line-height: 1.2;
}

.message.user .message-name {
  text-align: right;
}

.message-card {
  position: relative;
  border: 1px solid rgba(226, 232, 240, .78);
  border-radius: 12px;
  padding: 8px 10px;
  background: rgba(248, 250, 252, .86);
  color: #1f2937;
  font-size: 12.5px;
  line-height: 1.55;
  word-break: break-word;
  overflow-wrap: anywhere;
  user-select: text;
}

.message.assistant .message-card {
  padding-right: 58px;
}

.message.user .message-card {
  background: rgba(238, 242, 255, .9);
}

.message-text {
  color: inherit;
  white-space: pre-wrap;
  user-select: text;
}

.markdown-message {
  white-space: normal;
  user-select: text;
}

.markdown-message :deep(h1),
.markdown-message :deep(h2),
.markdown-message :deep(h3),
.markdown-message :deep(h4) {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.45;
}

.markdown-message :deep(p),
.markdown-message :deep(ul),
.markdown-message :deep(ol),
.markdown-message :deep(blockquote),
.markdown-message :deep(pre),
.markdown-message :deep(.markdown-table-scroll) {
  margin: 8px 0 0;
}

.markdown-message :deep(ul),
.markdown-message :deep(ol) {
  padding-left: 20px;
}

.markdown-message :deep(code) {
  padding: 1px 5px;
  border-radius: 6px;
  background: rgba(15, 118, 110, .10);
  color: #0f766e;
  font-size: .92em;
}

.markdown-message :deep(pre) {
  overflow: auto;
  padding: 10px;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
}

.markdown-message :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-message :deep(blockquote) {
  padding: 8px 10px;
  border-left: 3px solid rgba(15, 118, 110, .35);
  border-radius: 8px;
  background: rgba(240, 253, 250, .74);
}

.markdown-message :deep(.markdown-table-scroll) {
  overflow-x: auto;
}

.markdown-message :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.markdown-message :deep(th),
.markdown-message :deep(td) {
  padding: 6px 8px;
  border: 1px solid rgba(15, 118, 110, .16);
  text-align: left;
  vertical-align: top;
}

.markdown-message :deep(th) {
  background: rgba(240, 253, 250, .92);
  color: #0f766e;
}

.markdown-message :deep(a) {
  color: #0f766e;
  font-weight: 700;
}

.copy-button {
  position: absolute;
  top: 7px;
  right: 8px;
  min-width: 42px;
  height: 22px;
  border: 1px solid rgba(20, 184, 166, .20);
  border-radius: 999px;
  background: rgba(255, 255, 255, .94);
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  box-shadow: 0 6px 16px rgba(15, 23, 42, .08);
  transform: translateY(-2px);
  transition: opacity .18s ease, color .16s ease, background .16s ease, transform .16s ease;
}

.message-card:hover .copy-button {
  opacity: 1;
  transform: translateY(0);
}

.copy-button:hover,
.copy-button.copied {
  opacity: 1;
  transform: translateY(0);
  background: #0f766e;
  color: #fff;
}

.feedback-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid rgba(15, 23, 42, .06);
  opacity: 0;
  transition: opacity .18s ease;
}

.message-card:hover .feedback-actions,
.message-card:focus-within .feedback-actions {
  opacity: 1;
}

.feedback-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  transition: background .14s ease, transform .1s ease;
  padding: 0;
}

.feedback-btn:hover {
  background: rgba(15, 23, 42, .06);
  transform: scale(1.1);
}

.feedback-btn:active {
  transform: scale(.94);
}

.feedback-btn.active {
  background: rgba(15, 118, 110, .12);
}

.feedback-btn:disabled {
  opacity: .4;
  cursor: not-allowed;
  transform: none;
}

.feedback-icon {
  font-size: 13px;
  line-height: 1;
  pointer-events: none;
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 7px;
}

.message-action {
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 5px 8px;
  background: #fff;
  color: #0f766e;
  font-size: 11px;
  line-height: 1.2;
  cursor: pointer;
}

.message-action:hover {
  border-color: rgba(15, 118, 110, .36);
  background: rgba(20, 184, 166, .08);
}

.message-action.exec-action {
  background: rgba(20, 184, 166, .10);
  font-weight: 800;
}

.context-status {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 0 0 auto;
  padding: 7px 13px;
  border-bottom: 1px solid rgba(226, 232, 240, .62);
  background: rgba(248, 250, 252, .82);
  overflow: auto;
  cursor: move;
}

.context-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  border: 1px solid rgba(20, 184, 166, .22);
  border-radius: 12px;
  padding: 6px 8px;
  background: rgba(240, 253, 250, .92);
  color: #0f766e;
  font-size: 11.5px;
  line-height: 1.4;
}

.context-chip span {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-chip button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-left: 5px;
  border: none;
  background: transparent;
  color: rgba(15, 118, 110, .46);
  cursor: pointer;
}

.context-chip button:hover {
  color: rgba(220, 38, 68, .8);
}

.quick-links,
.starters {
  display: grid;
  flex: 1 1 auto;
  gap: 6px;
  overflow-y: auto;
  padding: 6px 13px 9px;
}

.quick-links {
  max-height: none;
}

.starters {
  max-height: none;
  grid-template-columns: 1fr;
}

.quick-links-empty {
  border: 1px dashed rgba(20, 184, 166, .26);
  border-radius: 12px;
  padding: 10px;
  background: rgba(240, 253, 250, .58);
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.quick-links button,
.starters button,
.tool-button,
.send-button {
  border-radius: 12px;
  border: 1px solid rgba(20, 184, 166, .22);
  background: rgba(20, 184, 166, .06);
  color: #0f766e;
  cursor: pointer;
}

.quick-links button {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 8px 10px;
  text-decoration: none;
  text-align: left;
}

.quick-links button:hover,
.starters button:hover,
.tool-button:hover,
.send-button:hover:not(:disabled) {
  border-color: rgba(15, 118, 110, .42);
  background: rgba(20, 184, 166, .14);
  transform: translateY(-1px);
}

.quick-link-icon {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(20, 184, 166, .12);
  color: #0f766e;
  font-size: 10px;
  font-weight: 800;
}

.quick-link-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.quick-links strong {
  color: #111827;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
}

.quick-links small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.starters button {
  border-radius: 999px;
  padding: 7px 9px;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
  text-align: left;
}

.compose {
  padding: 9px 13px 12px;
  border-top: 1px solid rgba(226, 232, 240, .78);
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 -10px 24px rgba(15, 23, 42, .04);
  cursor: move;
}

.compose textarea {
  width: 100%;
  min-height: 64px;
  max-height: 108px;
  border: 1px solid #dbe5ea;
  border-radius: 14px;
  padding: 9px 10px;
  resize: vertical;
  color: #0f172a;
  font: inherit;
  font-size: 13px;
  line-height: 1.45;
  outline: none;
}

.compose textarea:focus {
  border-color: rgba(20, 184, 166, .52);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, .12);
}

.compose-actions,
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.compose-actions {
  justify-content: space-between;
  margin-top: 7px;
}

.state {
  color: #6b7280;
  font-size: 11px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar {
  justify-content: flex-end;
}

.tool-button {
  position: relative;
  min-width: 30px;
  height: 30px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 700;
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
  min-width: 52px;
  height: 30px;
  padding: 0 12px;
  border-color: rgba(15, 118, 110, .35);
  background: #0f766e;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.send-button:disabled {
  opacity: .55;
  cursor: wait;
}

.hidden-input {
  display: none;
}

:global(.ai-floating-row-hover) {
  outline: 2px solid rgba(20, 184, 166, .42) !important;
  outline-offset: -2px;
  background: rgba(240, 253, 250, .64) !important;
}

:global(.ai-floating-row-selected) {
  outline: 2px solid rgba(15, 118, 110, .62) !important;
  outline-offset: -2px;
  background: rgba(204, 251, 241, .72) !important;
}

@media (max-width: 768px) {
  .ai-floating {
    right: 12px;
    bottom: 12px;
    gap: 8px;
  }

  .assistant-panel {
    width: calc(100vw - 24px);
    min-width: 0;
    max-width: calc(100vw - 24px);
    height: min(78vh, 520px);
    max-height: min(78vh, 520px);
  }

  .ai-floating.open {
    display: block;
  }

  .ai-floating.open .launcher {
    position: fixed;
    right: 12px;
    bottom: 12px;
  }

  .ai-floating.open .assistant-panel {
    margin-bottom: 94px;
  }

  .panel-header {
    padding: 12px 12px 8px;
  }

  .panel-tools {
    gap: 4px;
  }

  .launcher-bubble {
    display: none;
  }

  .starters {
    grid-template-columns: 1fr;
  }
}
</style>
