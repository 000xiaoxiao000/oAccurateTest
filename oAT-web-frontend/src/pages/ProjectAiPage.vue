<template>
  <section class="ai-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">AI Workspace</div>
        <h1>{{ context?.projectName || 'AI 工作台' }}</h1>
        <p class="subtext">{{ context?.projectSummary || '正在加载项目 AI 上下文...' }}</p>
      </div>
      <div class="header-actions">
        <button class="secondary-button" type="button" @click="clearMemory">清空记忆</button>
        <button class="action-button" type="button" @click="load">刷新</button>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载 AI 工作台...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="context">
      <div class="hero-card" :style="{ '--hero-accent': context.mascot?.mascotPrimary || '#0f766e' }">
        <div>
          <div class="hero-kicker">智能协作</div>
          <h2>{{ context.welcomeMessage }}</h2>
          <p>{{ context.mascotHint }}</p>
          <div class="hero-actions">
            <button class="primary-button" type="button" @click="useQuestion('帮我总结一下当前项目概况')">启动概览</button>
            <button class="ghost-button" type="button" @click="useQuestion('如果线上有异常，排查顺序是什么')">开始排查</button>
            <RouterLink class="ghost-link" :to="`/p/${projectId}/monitor`">打开监控台</RouterLink>
          </div>
        </div>
        <div class="hero-mascot">
          <div class="stage-ring one"></div>
          <div class="stage-ring two"></div>
          <MascotCanvas :size="170" :color="context.mascot?.mascotPrimary || '#0f766e'" :seed="projectId" :mood="asking ? 'thinking' : error ? 'error' : 'happy'" :interactive="true" />
          <span>{{ context.mascot?.mascotName || 'AI Assistant' }}</span>
        </div>
        <div class="hero-meta">
          <div class="hero-chip">
            <strong>{{ context.onlineAppCount }}</strong>
            <span>在线应用</span>
          </div>
          <div class="hero-chip">
            <strong>{{ context.appCount }}</strong>
            <span>全部应用</span>
          </div>
          <div class="hero-chip">
            <strong>{{ context.aiTimeout }}s</strong>
            <span>超时设置</span>
          </div>
        </div>
      </div>

      <div class="page-grid">
        <aside class="side-stack">
          <section class="panel ask-panel">
            <div class="card-title">
              <h2>会话</h2>
              <button class="ghost-button small" type="button" @click="newSession">新会话</button>
            </div>
            <div class="session-tools">
              <input v-model.trim="sessionSearch" class="text-input small-input" type="text" placeholder="搜索会话标题或内容" />
              <select v-model="sessionSort" class="text-input small-input" @change="syncSessionState">
                <option value="recent">最近更新</option>
                <option value="oldest">最早更新</option>
                <option value="name">标题排序</option>
              </select>
            </div>
            <div class="session-list">
              <article
                v-for="session in visibleSessions"
                :key="session.id"
                class="session-card"
                :class="{ active: session.id === activeSessionId, pinned: session.pinned }"
              >
                <button class="session-main" type="button" @click="switchSession(session.id)">
                  <strong>{{ session.pinned ? '★ ' : '' }}{{ session.title }}</strong>
                  <span>{{ session.messages.length }} 条消息 · {{ formatSessionTime(session.updatedAt) }}</span>
                </button>
                <div class="session-actions">
                  <button type="button" title="置顶/取消置顶" @click="togglePinSession(session.id)">{{ session.pinned ? '取消置顶' : '置顶' }}</button>
                  <button type="button" title="重命名" @click="renameSession(session.id)">重命名</button>
                  <button type="button" title="删除" @click="deleteSession(session.id)">删除</button>
                </div>
              </article>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>能力卡片</h2>
            </div>
            <div class="ability-list">
              <article v-for="card in context.abilityCards" :key="`${card.title}-${card.value}`" class="ability-card">
                <strong>{{ card.title }}</strong>
                <span class="ability-value">{{ card.value }}</span>
                <p>{{ card.description }}</p>
              </article>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>快速问题</h2>
            </div>
            <div class="question-list">
              <button
                v-for="question in context.starterQuestions"
                :key="question"
                class="ghost-button"
                type="button"
                @click="useQuestion(question)"
              >
                {{ question }}
              </button>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>快捷入口</h2>
              <span class="muted">{{ mergedQuickLinks.length }} 个</span>
            </div>
            <div class="link-list compact-links">
              <button v-for="link in mergedQuickLinks" :key="link.title + link.url" class="link-card link-button" type="button" @click="openLink(link)">
                <strong>{{ link.title }}</strong>
                <span>{{ link.description }}</span>
              </button>
              <div v-if="!mergedQuickLinks.length" class="empty-card compact">暂无快捷入口</div>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>项目上下文</h2>
              <span class="muted">{{ context.appNames.length }} 个应用</span>
            </div>
            <div class="context-list">
              <div class="context-row">
                <span>AI 形象</span>
                <strong>{{ context.mascot?.mascotName || 'AI' }} · {{ context.mascot?.mascotRole || '助手' }}</strong>
              </div>
              <div class="context-row">
                <span>当前状态</span>
                <strong>{{ context.mascot?.mascotMood || '在线' }}</strong>
              </div>
              <div class="app-chip-list">
                <span v-for="appName in context.appNames" :key="appName" class="app-chip">{{ appName }}</span>
                <span v-if="!context.appNames.length" class="app-chip muted-chip">暂无应用</span>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>提问锚点</h2>
              <span class="muted">{{ visibleQuestionAnchors.length }}/{{ questionAnchors.length }} 个</span>
            </div>
            <div class="anchor-tools">
              <div class="anchor-filter" role="group" aria-label="锚点筛选">
                <button class="anchor-filter-button" :class="{ active: anchorFilterMode === 'all' }" type="button" @click="anchorFilterMode = 'all'">全部</button>
                <button class="anchor-filter-button" :class="{ active: anchorFilterMode === 'pending' }" type="button" @click="anchorFilterMode = 'pending'">仅看未回复</button>
              </div>
              <div class="anchor-search-row">
                <input v-model.trim="anchorSearch" class="text-input small-input" type="text" placeholder="搜索问题关键词" />
                <button v-if="anchorSearch" class="anchor-clear" type="button" title="清空搜索" @click="anchorSearch = ''">×</button>
              </div>
              <p class="anchor-tip">点击可快速定位到对应问答</p>
            </div>
            <div class="anchor-list">
              <button
                v-for="anchor in visibleQuestionAnchors"
                :key="anchor.id"
                class="anchor-item"
                :class="{ active: activeAnchorId === anchor.id, pending: !anchor.answered, answered: anchor.answered, expanded: expandedAnchorIds.has(anchor.id) }"
                type="button"
                @mouseenter="previewAnchorId = anchor.id"
                @mouseleave="previewAnchorId = ''"
                @click="scrollToAnchor(anchor.id)"
              >
                <span class="anchor-top">
                  <strong>{{ anchor.label }}</strong>
                  <span class="anchor-status" :class="anchor.answered ? 'answered' : 'pending'">
                    <i></i>{{ anchor.answered ? '已回复' : '待回复' }}
                  </span>
                </span>
                <span class="anchor-question">{{ anchor.question }}</span>
                <span class="anchor-meta">{{ anchor.responseTimeText || (anchor.answered ? '已生成回答' : '等待回复中') }}</span>
                <span class="anchor-actions" @click.stop>
                  <button class="anchor-link-button" type="button" @click="toggleAnchorText(anchor.id)">{{ expandedAnchorIds.has(anchor.id) ? '收起' : '展开' }}</button>
                  <button class="anchor-link-button" type="button" @click="copyAnchorLink(anchor)">复制链接</button>
                </span>
              </button>
              <div v-if="!visibleQuestionAnchors.length" class="empty-card compact">暂无匹配的提问锚点</div>
            </div>
          </section>

          <section class="panel">
            <div class="card-title">
              <h2>会话时间线</h2>
            </div>
            <div class="timeline-list">
              <button
                v-for="item in timelineItems"
                :key="item.id"
                class="timeline-item"
                :class="{ active: activeAnchorId === item.anchorId }"
                type="button"
                @click="scrollToAnchor(item.anchorId)"
              >
                <span>{{ item.label }}</span>
                <strong>问答</strong>
                <small>{{ item.question }}</small>
              </button>
              <div v-if="!timelineItems.length" class="empty-card compact">暂无会话节点</div>
            </div>
          </section>
        </aside>

        <div class="content-stack">
          <section class="panel ask-workspace-panel">
            <div v-if="questionAnchors.length" class="floating-anchors" aria-label="右侧问答锚点导航">
              <div class="floating-anchor-head">问答</div>
              <div class="floating-anchor-track" :style="{ height: `${floatingTrackHeight}px` }">
                <button
                  v-for="dot in floatingAnchorDots"
                  :key="dot.id"
                  class="floating-anchor-dot"
                  :class="{ active: activeAnchorId === dot.id, pending: !dot.answered, answered: dot.answered }"
                  type="button"
                  :style="{ top: `${dot.top}px` }"
                  :aria-label="`${dot.label} ${dot.question}`"
                  @mouseenter="previewAnchorId = dot.id"
                  @mouseleave="previewAnchorId = ''"
                  @click="scrollToAnchor(dot.id)"
                >
                  <span class="floating-anchor-label">{{ dot.label }}</span>
                  <span class="floating-anchor-tooltip">
                    <strong>{{ dot.label }} · {{ dot.answered ? '已回复' : '待回复' }}</strong>
                    <em>{{ dot.question }}</em>
                    <small>{{ dot.responseTimeText || (dot.answered ? '已生成回答' : '等待回复中') }}</small>
                  </span>
                </button>
              </div>
            </div>
            <div class="card-title">
              <h2>提问</h2>
            </div>
            <div v-if="activeMessages.length" class="message-history">
              <article
                v-for="section in messageSections"
                :id="section.startsQuestion ? section.anchorId : `ai-message-${section.id}`"
                :key="section.id"
                class="message-card"
                :class="[
                  section.message.role,
                  {
                    'anchor-section': section.startsQuestion,
                    'qa-group-start': section.startsQuestion,
                    'qa-group-end': section.endsAnswer,
                    'is-active': activeAnchorId === section.anchorId,
                    'is-preview': previewAnchorId === section.anchorId,
                    'is-target': targetAnchorId === section.anchorId,
                  },
                ]"
                :data-anchor-id="section.anchorId"
              >
                <button class="message-copy-button" type="button" :title="section.message.role === 'assistant' ? '复制回复内容' : '复制提问内容'" @click.stop="copyMessage(section.message.text, section.id)">
                  {{ copiedMessageId === section.id ? '已复制' : '复制' }}
                </button>
                <div class="message-role">{{ section.message.role === 'user' ? '你' : 'AI' }}</div>
                <div class="message-text">{{ section.message.text }}</div>
              </article>
            </div>
            <form class="ask-form" @submit.prevent="submitAsk">
              <textarea
                v-model="question"
                class="text-area"
                rows="6"
                placeholder="例如：帮我总结当前项目的测试覆盖盲区，优先按风险排序。"
              ></textarea>
              <input ref="imageInput" class="hidden-input" type="file" accept="image/*" @change="handleImageChange" />
              <div class="form-actions">
                <div class="ask-tools">
                  <button class="ghost-button" :class="{ active: Boolean(imageData) }" type="button" @click="selectImage">
                    {{ imageData ? '已附图片' : '上传图片' }}
                  </button>
                  <button v-if="imageData" class="ghost-button" type="button" @click="clearImage">移除图片</button>
                  <button class="ghost-button" :class="{ active: recording }" type="button" @click="toggleVoiceInput">语音输入</button>
                </div>
                <div class="ask-submit-actions">
                  <button v-if="asking" class="danger-button control-button" type="button" @click="stopAsk">
                    <span class="button-icon stop-icon"></span>
                    停止生成
                  </button>
                  <button class="primary-button control-button send-button" type="submit" :class="{ loading: asking }" :disabled="asking">
                    <span v-if="asking" class="button-spinner" aria-hidden="true"></span>
                    <span v-else class="button-icon send-icon" aria-hidden="true"></span>
                    {{ asking ? '生成中...' : '发送问题' }}
                  </button>
                  <button class="ghost-button control-button save-button" type="button" :disabled="asking" @click="saveSession">
                    <span class="button-icon save-icon" aria-hidden="true"></span>
                    保存会话状态
                  </button>
                </div>
              </div>
            </form>
          </section>

          <section class="panel" v-if="reply">
            <div class="card-title">
              <h2>回答</h2>
              <span class="muted">{{ reply.topic || 'general' }}</span>
            </div>
            <div class="answer-block">{{ reply.answer || '暂无回答' }}</div>

            <div v-if="reply.suggestions?.length" class="subsection">
              <h3>追问建议</h3>
              <div class="chip-list">
                <button
                  v-for="item in reply.suggestions"
                  :key="item"
                  class="ghost-button small"
                  type="button"
                  @click="useQuestion(item)"
                >
                  {{ item }}
                </button>
              </div>
            </div>

            <div v-if="reply.quickLinks?.length" class="subsection">
              <h3>推荐链接</h3>
              <div class="link-list">
                <button v-for="link in reply.quickLinks" :key="link.title + link.url" class="link-card link-button" type="button" @click="openLink(link)">
                  <strong>{{ link.title }}</strong>
                  <span>{{ link.description }}</span>
                </button>
              </div>
            </div>

            <div v-if="reply.actions?.length" class="subsection">
              <h3>建议动作</h3>
              <div class="link-list">
                <article v-for="action in reply.actions" :key="action.title + action.type" class="link-card">
                  <strong>{{ action.title }}</strong>
                  <span>{{ action.description }}</span>
                  <button class="inline-link action-inline-button" type="button" @click="executeAction(action)">执行</button>
                </article>
              </div>
            </div>

            <div v-if="reply.visualizationSuggestions?.length" class="subsection">
              <h3>可视化建议</h3>
              <div class="visual-list">
                <article v-for="item in reply.visualizationSuggestions" :key="String(item.title || item.type || JSON.stringify(item))" class="visual-card">
                  <strong>{{ String(item.title || item.type || '数据图表') }}</strong>
                  <code>{{ JSON.stringify(item) }}</code>
                </article>
              </div>
            </div>

            <div v-if="replyMetaEntries.length" class="subsection">
              <h3>生成信息</h3>
              <div class="meta-grid">
                <article v-for="item in replyMetaEntries" :key="item.label" class="meta-card">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.value }}</strong>
                </article>
              </div>
            </div>

            <div class="subsection feedback-box">
              <h3>回答反馈</h3>
              <div class="chip-list">
                <button class="ghost-button small" type="button" :disabled="feedbackSubmitting" @click="submitFeedback('helpful', 5)">有帮助</button>
                <button class="ghost-button small" type="button" :disabled="feedbackSubmitting" @click="submitFeedback('not_helpful', 1)">没帮助</button>
                <button class="ghost-button small" type="button" :disabled="feedbackSubmitting" @click="submitFeedback('incorrect', 1, true)">不正确</button>
                <button class="ghost-button small" type="button" :disabled="feedbackSubmitting" @click="submitFeedback('incomplete', 2, true)">不完整</button>
              </div>
              <p v-if="feedbackMessage" class="feedback-message">{{ feedbackMessage }}</p>
            </div>
          </section>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { submitAiFeedback } from '@/api/bootstrap'
import { backendApiUrl } from '@/api/http'
import MascotCanvas from '@/components/MascotCanvas.vue'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'
import { useAuthStore } from '@/stores/auth'
import type { AIAction, AIFeedbackPayload, AIQuickLink } from '@/api/types'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const authStore = useAuthStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const context = computed(() => projectStore.aiContextByProjectId[projectId.value])
const reply = computed(() => projectStore.aiLastReplyByProjectId[projectId.value])
type SessionMessage = { id: string; role: 'user' | 'assistant'; text: string; responseTime?: number }
type ChatSession = { id: string; title: string; messages: SessionMessage[]; updatedAt: number; pinned?: boolean }
type QuestionAnchor = { id: string; label: string; question: string; answered: boolean; responseTime: number; responseTimeText: string; shareUrl: string; messageId: string }
type MessageSection = { id: string; anchorId: string; message: SessionMessage; startsQuestion: boolean; endsAnswer: boolean }
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

const loading = ref(false)
const asking = ref(false)
const error = ref('')
const question = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const imageData = ref('')
const recording = ref(false)
let recognition: SpeechRecognitionLike | null = null
const sessions = ref<ChatSession[]>([])
const activeSessionId = ref('')
const sessionSearch = ref('')
const sessionSort = ref<'recent' | 'oldest' | 'name'>('recent')
const anchorFilterMode = ref<'all' | 'pending'>('all')
const anchorSearch = ref('')
const activeAnchorId = ref('')
const previewAnchorId = ref('')
const targetAnchorId = ref('')
const expandedAnchorIds = ref(new Set<string>())
const copiedMessageId = ref('')
const feedbackSubmitting = ref(false)
const feedbackMessage = ref('')
let askAbortController: AbortController | null = null
let targetAnchorTimer: number | undefined
let copiedMessageTimer: number | undefined

const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value) || null)
const activeMessages = computed(() => activeSession.value?.messages || [])
const mergedQuickLinks = computed(() => mergeQuickLinks([...(context.value?.quickLinks || []), ...(reply.value?.quickLinks || [])]))
const lastUserQuestion = computed(() => [...activeMessages.value].reverse().find((item) => item.role === 'user')?.text || reply.value?.question || '')
const lastAssistantAnswer = computed(() => reply.value?.answer || [...activeMessages.value].reverse().find((item) => item.role === 'assistant')?.text || '')
const replyMetaEntries = computed(() => {
  const currentReply = reply.value
  if (!currentReply) return []
  const entries: Array<{ label: string; value: string }> = []
  if (typeof currentReply.confidence === 'number') entries.push({ label: '置信度', value: `${Math.round(currentReply.confidence * 100)}%` })
  if (currentReply.usedTools?.length) entries.push({ label: '使用工具', value: currentReply.usedTools.join('、') })
  if (currentReply.needMoreData !== undefined) entries.push({ label: '需要更多数据', value: currentReply.needMoreData ? '是' : '否' })
  const responseTime = currentReply.metadata?.responseTime
  if (typeof responseTime === 'number') entries.push({ label: '响应耗时', value: `${responseTime}ms` })
  const routeName = currentReply.metadata?.route || currentReply.metadata?.topic
  if (routeName) entries.push({ label: '识别场景', value: String(routeName) })
  return entries
})
const visibleSessions = computed(() => {
  const needle = sessionSearch.value.toLowerCase()
  const filtered = sessions.value.filter((session) => {
    if (!needle) return true
    return [session.title, ...session.messages.map((message) => message.text)].join(' ').toLowerCase().includes(needle)
  })
  return [...filtered].sort((a, b) => {
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
    if (sessionSort.value === 'name') return a.title.localeCompare(b.title)
    return sessionSort.value === 'oldest' ? a.updatedAt - b.updatedAt : b.updatedAt - a.updatedAt
  })
})
const messageSections = computed<MessageSection[]>(() => {
  const sections: MessageSection[] = []
  let questionIndex = 0
  let currentAnchorId = ''
  activeMessages.value.forEach((message, index) => {
    const startsQuestion = message.role === 'user'
    if (startsQuestion) {
      questionIndex += 1
      currentAnchorId = questionAnchorId(questionIndex)
    }
    const anchorId = currentAnchorId || `ai-message-${message.id}`
    const nextMessage = activeMessages.value[index + 1]
    sections.push({
      id: message.id,
      anchorId,
      message,
      startsQuestion,
      endsAnswer: message.role === 'assistant' && (!nextMessage || nextMessage.role === 'user'),
    })
  })
  return sections
})
const questionAnchors = computed<QuestionAnchor[]>(() => {
  const anchors: QuestionAnchor[] = []
  let pendingAnchor: QuestionAnchor | null = null
  activeMessages.value.forEach((message) => {
    if (message.role === 'user') {
      pendingAnchor = createQuestionAnchor(message, anchors.length)
      anchors.push(pendingAnchor)
      return
    }
    if (message.role === 'assistant' && pendingAnchor) {
      pendingAnchor.answered = true
      pendingAnchor.responseTime = message.responseTime || pendingAnchor.responseTime
      pendingAnchor.responseTimeText = formatResponseTime(pendingAnchor.responseTime)
      pendingAnchor = null
    }
  })
  return anchors
})
const visibleQuestionAnchors = computed(() => {
  const keyword = anchorSearch.value.toLowerCase()
  return questionAnchors.value.filter((anchor) => {
    if (anchorFilterMode.value === 'pending' && anchor.answered) return false
    if (!keyword) return true
    return `${anchor.label} ${anchor.question}`.toLowerCase().includes(keyword)
  })
})
const timelineItems = computed(() => questionAnchors.value.map((anchor) => ({
  id: `timeline-${anchor.id}`,
  anchorId: anchor.id,
  label: anchor.label,
  question: anchor.question,
})))
const floatingTrackHeight = computed(() => {
  if (typeof window === 'undefined') return 260
  return Math.max(180, Math.min(window.innerHeight - 360, 360))
})
const floatingAnchorDots = computed(() => {
  const anchors = questionAnchors.value
  const maxTop = Math.max(0, floatingTrackHeight.value - 16)
  let lastTop = -18
  const dots = anchors.map((anchor, index) => {
    let top = Math.round(maxTop * (anchors.length <= 1 ? 0 : index / (anchors.length - 1)))
    if (top - lastTop < 18) top = Math.min(maxTop, lastTop + 18)
    lastTop = top
    return { ...anchor, top }
  })
  for (let index = dots.length - 2; index >= 0; index -= 1) {
    if (dots[index + 1].top - dots[index].top < 18) {
      dots[index].top = Math.max(0, dots[index + 1].top - 18)
    }
  }
  return dots
})

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadAiContext(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载 AI 工作台失败'
  } finally {
    loading.value = false
  }
}

function useQuestion(text: string) {
  question.value = text
}

function uid() {
  return `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

function createSession(title = '新会话'): ChatSession {
  return {
    id: uid(),
    title,
    messages: [],
    updatedAt: Date.now(),
  }
}

function normalizeSession(session: ChatSession): ChatSession {
  return { ...session, updatedAt: session.updatedAt || Date.now(), pinned: Boolean(session.pinned) }
}

function touchSession(session: ChatSession | null) {
  if (session) session.updatedAt = Date.now()
}

function formatSessionTime(value: number) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function questionAnchorId(index: number) {
  return `ai-question-anchor-${index}`
}

function formatResponseTime(ms: number) {
  if (!ms || ms <= 0) return ''
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(ms >= 10000 ? 0 : 1)}s`
}

function createQuestionAnchor(message: SessionMessage, index: number): QuestionAnchor {
  const anchorIndex = index + 1
  return {
    id: questionAnchorId(anchorIndex),
    label: `Q${anchorIndex}`,
    question: message.text || '未命名提问',
    answered: false,
    responseTime: message.responseTime || 0,
    responseTimeText: formatResponseTime(message.responseTime || 0),
    shareUrl: `${window.location.pathname}#${questionAnchorId(anchorIndex)}`,
    messageId: message.id,
  }
}

function mergeQuickLinks(links: AIQuickLink[]) {
  const seen = new Set<string>()
  return links.filter((link) => {
    if (!link?.url || seen.has(link.url)) return false
    seen.add(link.url)
    return true
  })
}

function hydrateSessions(rawState?: string) {
  if (!rawState) {
    const fresh = createSession()
    sessions.value = [fresh]
    activeSessionId.value = fresh.id
    return
  }
  try {
    const parsed = JSON.parse(rawState) as {
      sessions?: ChatSession[]
      history?: Array<{ id?: string; role?: 'user' | 'assistant'; message?: string; text?: string; responseTime?: number }>
      title?: string
      activeSessionId?: string
      sessionSort?: 'recent' | 'oldest' | 'name'
    }
    if (parsed.sessions?.length) {
      sessions.value = parsed.sessions.map(normalizeSession)
      activeSessionId.value = parsed.activeSessionId && parsed.sessions.some((item) => item.id === parsed.activeSessionId)
        ? parsed.activeSessionId
        : parsed.sessions[0].id
      sessionSort.value = parsed.sessionSort || 'recent'
      return
    }
    if (parsed.history?.length) {
      const legacyMessages = parsed.history
        .filter((item) => item.role === 'user' || item.role === 'assistant')
        .map((item) => ({
          id: item.id || uid(),
          role: item.role as 'user' | 'assistant',
          text: item.text || item.message || '',
          responseTime: item.responseTime,
        }))
      const fresh = createSession(parsed.title || '历史会话')
      fresh.messages = legacyMessages
      sessions.value = [fresh]
      activeSessionId.value = fresh.id
      return
    }
  } catch {
    // ignore parse failure and rebuild local session state
  }
  const fresh = createSession()
  sessions.value = [fresh]
  activeSessionId.value = fresh.id
}

function buildSessionState() {
  return JSON.stringify({
    sessions: sessions.value,
    activeSessionId: activeSessionId.value,
    sessionSort: sessionSort.value,
  })
}

async function syncSessionState() {
  try {
    await projectStore.persistAiSessionState(projectId.value, buildSessionState())
  } catch (err) {
    error.value = err instanceof Error ? err.message : '同步会话状态失败'
  }
}

function ensureActiveSession() {
  if (!activeSession.value) {
    const fresh = createSession()
    sessions.value = [fresh]
    activeSessionId.value = fresh.id
  }
}

function switchSession(sessionId: string) {
  activeSessionId.value = sessionId
  activeAnchorId.value = ''
  previewAnchorId.value = ''
  targetAnchorId.value = ''
  syncSessionState()
  nextTick(updateActiveAnchorFromScroll)
}

function newSession() {
  const fresh = createSession()
  sessions.value = [fresh, ...sessions.value]
  activeSessionId.value = fresh.id
  syncSessionState()
}

async function renameSession(sessionId: string) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const nextTitle = await dialog.prompt({
    title: '重命名会话',
    message: '请输入新的会话名称，便于在左侧会话列表中快速定位。',
    defaultValue: session.title,
    placeholder: '会话名称',
    confirmText: '保存名称',
  })
  if (!nextTitle?.trim()) return
  session.title = nextTitle.trim().slice(0, 40)
  touchSession(session)
  syncSessionState()
}

async function deleteSession(sessionId: string) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const confirmed = await dialog.confirm({
    title: '删除 AI 会话',
    message: `确认删除会话「${session.title}」？该会话中的问题、回答和图片上下文将从当前项目记忆中移除。`,
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) return
  sessions.value = sessions.value.filter((item) => item.id !== sessionId)
  if (!sessions.value.length) {
    const fresh = createSession()
    sessions.value = [fresh]
    activeSessionId.value = fresh.id
  } else if (activeSessionId.value === sessionId) {
    activeSessionId.value = visibleSessions.value[0]?.id || sessions.value[0].id
  }
  syncSessionState()
}

function togglePinSession(sessionId: string) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session) return
  session.pinned = !session.pinned
  touchSession(session)
  syncSessionState()
}

function scrollToAnchor(anchorId: string) {
  const target = document.getElementById(anchorId)
  if (!target) return
  activeAnchorId.value = anchorId
  targetAnchorId.value = anchorId
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${anchorId}`)
  if (targetAnchorTimer) window.clearTimeout(targetAnchorTimer)
  targetAnchorTimer = window.setTimeout(() => {
    if (targetAnchorId.value === anchorId) targetAnchorId.value = ''
  }, 1600)
}

function toggleAnchorText(anchorId: string) {
  const next = new Set(expandedAnchorIds.value)
  if (next.has(anchorId)) next.delete(anchorId)
  else next.add(anchorId)
  expandedAnchorIds.value = next
}

async function copyAnchorLink(anchor: QuestionAnchor) {
  const url = `${window.location.origin}${anchor.shareUrl}`
  try {
    await navigator.clipboard?.writeText(url)
  } catch {
    window.prompt('复制问答锚点链接', url)
  }
}

async function copyMessage(text: string, messageId: string) {
  if (!text) return
  try {
    await navigator.clipboard?.writeText(text)
    copiedMessageId.value = messageId
    if (copiedMessageTimer) window.clearTimeout(copiedMessageTimer)
    copiedMessageTimer = window.setTimeout(() => {
      if (copiedMessageId.value === messageId) copiedMessageId.value = ''
    }, 1400)
  } catch {
    window.prompt('复制消息内容', text)
  }
}

function updateActiveAnchorFromScroll() {
  const anchors = questionAnchors.value
  if (!anchors.length) {
    activeAnchorId.value = ''
    return
  }
  const activationLine = Math.max(120, Math.round(window.innerHeight * 0.24))
  let nextActive = anchors[0].id
  for (const anchor of anchors) {
    const element = document.getElementById(anchor.id)
    if (!element) continue
    const rect = element.getBoundingClientRect()
    if (rect.top <= activationLine) nextActive = anchor.id
    else break
  }
  activeAnchorId.value = nextActive
}

async function submitAsk() {
  if (!question.value.trim() && !imageData.value) {
    error.value = '请输入问题或上传图片'
    return
  }
  asking.value = true
  error.value = ''
  try {
    ensureActiveSession()
    const currentQuestion = question.value.trim()
    activeSession.value?.messages.push({
      id: uid(),
      role: 'user',
      text: currentQuestion || '[图片提问]',
    })
    touchSession(activeSession.value)
    if (activeSession.value && activeSession.value.title === '新会话') {
      activeSession.value.title = currentQuestion.slice(0, 20)
    }
    const assistantMessage: SessionMessage = { id: uid(), role: 'assistant', text: '正在连接 AI 流式响应...' }
    activeSession.value?.messages.push(assistantMessage)
    const startedAt = performance.now()
    await askAiWithFallback(currentQuestion, assistantMessage)
    assistantMessage.responseTime = Math.max(1, Math.round(performance.now() - startedAt))
    await executeAutoAction(reply.value?.actions)
    touchSession(activeSession.value)
    await syncSessionState()
    await nextTick()
    const latestAnchor = questionAnchors.value[questionAnchors.value.length - 1]
    if (latestAnchor) scrollToAnchor(latestAnchor.id)
    question.value = ''
    imageData.value = ''
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      const lastAssistant = [...activeMessages.value].reverse().find((item) => item.role === 'assistant')
      if (lastAssistant) lastAssistant.text = `${lastAssistant.text}\n[已停止生成]`
      error.value = '已停止生成'
      await syncSessionState()
    } else {
      error.value = friendlyAiError(err)
    }
  } finally {
    askAbortController = null
    asking.value = false
  }
}

function stopAsk() {
  askAbortController?.abort()
}

async function submitFeedback(feedbackType: AIFeedbackPayload['feedbackType'], rating: number, requireComment = false) {
  if (!reply.value && !lastAssistantAnswer.value) return
  let comment = ''
  if (requireComment) {
    comment = (await dialog.prompt({
      title: '补充反馈',
      message: '请简单说明哪里需要改进，便于 AI 后续学习。',
      placeholder: '例如：覆盖率入口不对、回答不完整...',
      confirmText: '提交反馈',
    }) || '').trim()
    if (!comment) return
  }
  feedbackSubmitting.value = true
  feedbackMessage.value = ''
  try {
    await submitAiFeedback({
      projectId: projectId.value,
      question: lastUserQuestion.value,
      answer: lastAssistantAnswer.value,
      rating,
      feedbackType,
      comment,
      usedTools: reply.value?.usedTools?.join(','),
      responseTime: typeof reply.value?.metadata?.responseTime === 'number' ? reply.value.metadata.responseTime : undefined,
    })
    feedbackMessage.value = '反馈已提交，感谢帮助 AI 改进。'
  } catch (err) {
    feedbackMessage.value = err instanceof Error ? err.message : '反馈提交失败'
  } finally {
    feedbackSubmitting.value = false
  }
}

function normalizeSpaUrl(url?: string) {
  if (!url) return ''
  if (url.startsWith('/api/')) return url
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

async function openLink(link: AIQuickLink) {
  const target = normalizeSpaUrl(link.url)
  if (!target) return
  if (/^https?:\/\//.test(target)) {
    window.open(target, '_blank', 'noopener,noreferrer')
    return
  }
  await router.push(target)
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
    return
  }
  if (action.type === 'monitorPageAction') {
    const event = new CustomEvent('oat:monitor-action', { detail: action.payload || {} })
    window.dispatchEvent(event)
    const target = `/p/${projectId.value}/monitor`
    if (route.path !== target) await router.push(target)
    return
  }
  const target = normalizeSpaUrl(action.url)
  if (!target) return
  if (/^https?:\/\//.test(target)) {
    window.open(target, '_blank', 'noopener,noreferrer')
    return
  }
  await router.push(target)
}

async function executeAutoAction(actions?: AIAction[]) {
  const action = actions?.find((item) => item.payload?.autoExecute === true && !item.requireConfirm && item.type !== 'logout')
  if (!action) return
  const lastAssistant = [...activeMessages.value].reverse().find((item) => item.role === 'assistant')
  if (lastAssistant) lastAssistant.text = `${lastAssistant.text}\n\n[已按建议执行：${action.title}]`
  await executeAction(action)
}


async function askAiWithFallback(currentQuestion: string, assistantMessage: SessionMessage) {
  try {
    await askAiStreaming(currentQuestion, assistantMessage)
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    assistantMessage.text = '流式响应不可用，正在切换普通响应...'
    const reply = await projectStore.askAi(projectId.value, {
      question: currentQuestion,
      pageContext: `route=/p/${projectId.value}/ai`,
      imageData: imageData.value || undefined,
      sessionState: buildSessionState(),
      activeSessionId: activeSessionId.value,
      sessionSortMode: sessionSort.value,
    })
    assistantMessage.text = reply.answer || reply.topic || 'AI 已返回结果，但没有可展示的文本。'
  }
}

function friendlyAiError(err: unknown) {
  if (err instanceof TypeError && err.message === 'Failed to fetch') {
    return '无法连接 AI 服务，请确认后端服务已启动，并且前端允许访问后端 API。'
  }
  if (err instanceof Error && err.message === 'Failed to fetch') {
    return '无法连接 AI 服务，请确认后端服务已启动，并且前端允许访问后端 API。'
  }
  if (err instanceof Error && (err.message.includes('INTERNAL_SERVER_ERROR') || err.message.includes('500') || err.message.includes('NullPointerException'))) {
    return 'AI 助手接口发生服务端异常，请稍后重试；后端日志中会记录具体原因。'
  }
  return err instanceof Error ? err.message : 'AI 提问失败'
}

async function askAiStreaming(currentQuestion: string, assistantMessage: SessionMessage) {
  askAbortController = new AbortController()
  const body = new URLSearchParams()
  body.set('question', currentQuestion)
  body.set('pageContext', `route=/p/${projectId.value}/ai`)
  body.set('sessionState', buildSessionState())
  body.set('activeSessionId', activeSessionId.value)
  body.set('sessionSortMode', sessionSort.value)
  if (imageData.value) body.set('imageData', imageData.value)
  const response = await fetch(backendApiUrl(`/api/projects/${projectId.value}/ai/ask/stream`), {
    method: 'POST',
    credentials: 'include',
    headers: { Accept: 'text/event-stream', 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body,
    signal: askAbortController.signal,
  })
  if (!response.ok || !response.body) throw new Error(`AI 流式请求失败 (${response.status})`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let answer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() || ''
    for (const chunk of chunks) {
      const event = parseSseChunk(chunk)
      if (!event) continue
      if (event.event === 'content') {
        answer += eventText(event.data.content || event.data.text)
        assistantMessage.text = answer || 'AI 正在生成...'
      } else if (event.event === 'tool_call') {
        assistantMessage.text = `正在调用工具：${event.data.primaryTool || event.data.intent || '分析中'}...`
      } else if (event.event === 'thinking') {
        assistantMessage.text = eventText(event.data.message) || 'AI 正在分析...'
      } else if (event.event === 'complete') {
        assistantMessage.text = answer || eventText(event.data.answer) || eventText(event.data.content) || assistantMessage.text
      } else if (event.event === 'error') {
        throw new Error(eventText(event.data.message) || 'AI 流式响应失败')
      }
    }
  }
}

function parseSseChunk(chunk: string) {
  const lines = chunk.split('\n')
  const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const dataText = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('\n')
  if (!dataText) return null
  try {
    return { event, data: JSON.parse(dataText) as Record<string, string | number | boolean | undefined> }
  } catch {
    return { event, data: { content: dataText } }
  }
}

function eventText(value: unknown) {
  if (value === undefined || value === null || value === false) return ''
  return typeof value === 'string' ? value : String(value)
}

async function saveSession() {
  try {
    await projectStore.persistAiSessionState(projectId.value, buildSessionState())
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存会话状态失败'
  }
}

async function clearMemory() {
  const confirmed = await dialog.confirm({
    title: '清空 AI 记忆',
    message: '确认清空当前项目 AI 工作台的会话记忆？清空后会重新创建一个空会话。',
    confirmText: '确认清空',
    tone: 'danger',
  })
  if (!confirmed) return
  try {
    await projectStore.resetAiSessionState(projectId.value)
    hydrateSessions('')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '清空 AI 记忆失败'
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

onMounted(async () => {
  await load()
  hydrateSessions(context.value?.sessionState)
  await nextTick()
  window.addEventListener('scroll', updateActiveAnchorFromScroll, { passive: true })
  window.addEventListener('resize', updateActiveAnchorFromScroll, { passive: true })
  if (window.location.hash.startsWith('#ai-question-anchor-')) {
    scrollToAnchor(window.location.hash.slice(1))
  } else {
    updateActiveAnchorFromScroll()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateActiveAnchorFromScroll)
  window.removeEventListener('resize', updateActiveAnchorFromScroll)
  if (targetAnchorTimer) window.clearTimeout(targetAnchorTimer)
  if (copiedMessageTimer) window.clearTimeout(copiedMessageTimer)
})
</script>

<style scoped>
.ai-page {
  position: relative;
  isolation: isolate;
}

.ai-page::before,
.ai-page::after {
  position: fixed;
  z-index: -1;
  content: '';
  border-radius: 999px;
  pointer-events: none;
  filter: blur(2px);
}

.ai-page::before {
  width: 360px;
  height: 360px;
  left: -120px;
  top: 64px;
  background: radial-gradient(circle, rgba(20, 184, 166, .14), transparent 70%);
}

.ai-page::after {
  width: 420px;
  height: 420px;
  right: -150px;
  bottom: 40px;
  background: radial-gradient(circle, rgba(182, 217, 0, .16), transparent 72%);
}

.page-header,
.header-actions,
.card-title,
.form-actions,
.hero-actions,
.ask-tools,
.ask-submit-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.page-header {
  margin-bottom: 20px;
}

.eyebrow,
.hero-kicker {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.muted,
.link-card span,
.ability-card p {
  color: #64748b;
}

.action-button,
.secondary-button,
.primary-button,
.danger-button,
.ghost-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 999px;
  padding: 10px 14px;
  font: inherit;
  font-weight: 800;
  cursor: pointer;
  transition: transform .16s ease, box-shadow .16s ease, background .16s ease, color .16s ease, border-color .16s ease, opacity .16s ease;
}

.action-button,
.secondary-button,
.primary-button,
.danger-button {
  border: none;
  color: #fff;
}

.action-button {
  background: #0f172a;
}

.secondary-button {
  background: #475569;
}

.primary-button {
  background: #0f766e;
}

.danger-button {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  box-shadow: 0 12px 24px rgba(220, 38, 38, .18);
}

.primary-button:not(:disabled):hover,
.action-button:not(:disabled):hover,
.secondary-button:not(:disabled):hover,
.danger-button:not(:disabled):hover,
.ghost-button:not(:disabled):hover {
  transform: translateY(-1px);
}

.primary-button:not(:disabled):active,
.action-button:not(:disabled):active,
.secondary-button:not(:disabled):active,
.danger-button:not(:disabled):active,
.ghost-button:not(:disabled):active {
  transform: translateY(0) scale(.98);
}

.primary-button:disabled,
.danger-button:disabled,
.ghost-button:disabled,
.action-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: .62;
  transform: none;
}

.ghost-button {
  border: 1px solid rgba(15, 118, 110, 0.2);
  background: rgba(15, 118, 110, 0.06);
  color: #0f766e;
}

.ghost-button.small {
  padding: 8px 12px;
}

.status-card,
.panel {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 45px rgba(15, 23, 42, .06);
}

.status-card.error {
  color: #b91c1c;
}

.hero-card {
  --hero-accent: #0f766e;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 240px) minmax(240px, .62fr);
  gap: 18px;
  position: relative;
  overflow: hidden;
  padding: 24px;
  border-radius: 24px;
  margin-bottom: 18px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, .13) 1px, transparent 1px),
    linear-gradient(rgba(255, 255, 255, .13) 1px, transparent 1px),
    radial-gradient(circle at top right, color-mix(in srgb, var(--hero-accent) 20%, white) 0%, transparent 36%),
    linear-gradient(135deg, color-mix(in srgb, var(--hero-accent) 82%, #0f172a), #0f172a 68%);
  background-size: 38px 38px, 38px 38px, auto, auto;
  border: 1px solid color-mix(in srgb, var(--hero-accent) 28%, transparent);
  color: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, .18);
}

.hero-card h2,
.hero-card p,
.hero-card .hero-kicker {
  color: #fff;
}

.hero-card p {
  opacity: .84;
}

.hero-meta {
  display: grid;
  gap: 12px;
}

.hero-chip {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(10px);
}

.hero-chip strong {
  display: block;
  font-size: 24px;
}

.page-grid {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.side-stack,
.content-stack,
.ability-list,
.question-list,
.link-list,
.context-list,
.visual-list,
.session-list,
.message-history {
  display: grid;
  gap: 12px;
}

.ability-card,
.link-card {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff, #f8fbfb);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.compact-links {
  gap: 8px;
}

.context-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid rgba(15, 23, 42, .06);
  padding-bottom: 8px;
}

.context-row span {
  color: #64748b;
}

.context-row strong {
  color: #172033;
  text-align: right;
}

.app-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.app-chip {
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(15, 118, 110, .06);
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.muted-chip {
  border-color: rgba(100, 116, 139, .18);
  background: rgba(148, 163, 184, .08);
  color: #64748b;
}

.ability-card,
.session-card,
.message-card,
.link-card {
  min-width: 0;
}

.ability-value {
  color: #0f766e;
  font-weight: 700;
}

.session-card,
.message-card {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
  text-align: left;
}

.session-card.active {
  background: linear-gradient(180deg, #f0fdfa, #ecfeff);
  border-color: rgba(15, 118, 110, 0.22);
}

.session-card.pinned {
  box-shadow: inset 3px 0 0 #0f766e;
}

.side-stack {
  position: sticky;
  top: 92px;
  max-height: calc(100vh - 116px);
  overflow: auto;
  padding-right: 4px;
  scrollbar-gutter: stable;
}

.side-stack > .panel,
.side-stack > .ask-panel {
  position: static;
}

.session-tools {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin: 12px 0;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, .12);
  border-radius: 14px;
  padding: 11px 13px;
  background: rgba(255, 255, 255, .92);
  color: #172033;
  font: inherit;
  outline: none;
  transition: border-color .16s ease, box-shadow .16s ease, background .16s ease;
}

.text-input:focus {
  border-color: rgba(15, 118, 110, .38);
  background: #fff;
  box-shadow: 0 0 0 4px rgba(15, 118, 110, .10);
}

.small-input {
  min-height: 38px;
  border-radius: 999px;
  padding: 9px 12px;
}

.session-main {
  display: grid;
  gap: 4px;
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  font: inherit;
  cursor: pointer;
}

.session-main strong {
  color: #172033;
  font-size: 14px;
}

.session-main span {
  color: #64748b;
  font-size: 12px;
}

.session-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.session-actions button,
.timeline-item,
.anchor-filter-button,
.anchor-link-button {
  border: 1px solid rgba(15, 118, 110, .16);
  border-radius: 999px;
  background: rgba(255, 255, 255, .72);
  color: #0f766e;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.session-actions button {
  padding: 6px 9px;
}

.session-actions button:hover,
.timeline-item:hover,
.anchor-filter-button:hover,
.anchor-link-button:hover {
  border-color: rgba(15, 118, 110, .34);
  background: rgba(15, 118, 110, .08);
}

.anchor-tools {
  display: grid;
  gap: 10px;
  margin: 12px 0;
}

.anchor-filter {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.anchor-filter-button {
  padding: 7px 10px;
}

.anchor-filter-button.active,
.anchor-item.active,
.timeline-item.active {
  border-color: rgba(15, 118, 110, .34);
  background: linear-gradient(180deg, #f0fdfa, #ecfeff);
  box-shadow: inset 3px 0 0 #0f766e, 0 10px 24px rgba(15, 118, 110, .08);
}

.anchor-search-row {
  position: relative;
}

.anchor-search-row .text-input {
  padding-right: 36px;
}

.anchor-clear {
  position: absolute;
  top: 50%;
  right: 8px;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, .08);
  color: #64748b;
  cursor: pointer;
  transform: translateY(-50%);
}

.anchor-tip {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
}

.anchor-list {
  display: grid;
  gap: 10px;
  max-height: 300px;
  overflow: auto;
  padding-right: 2px;
}

.anchor-item {
  display: grid;
  gap: 7px;
  width: 100%;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 18px;
  padding: 10px 11px;
  background: rgba(255, 255, 255, .82);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color .18s ease, background .18s ease, box-shadow .18s ease, transform .18s ease;
}

.anchor-item:hover {
  transform: translateY(-1px);
}

.anchor-item.pending {
  border-color: rgba(245, 158, 11, .26);
  background: rgba(245, 158, 11, .06);
}

.anchor-item.answered {
  border-color: rgba(15, 118, 110, .16);
}

.anchor-top,
.anchor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.anchor-top strong {
  color: #0f766e;
  font-size: 12px;
}

.anchor-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 10px;
  font-weight: 900;
}

.anchor-status i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
}

.anchor-status.answered {
  background: rgba(15, 118, 110, .12);
  color: #0f766e;
}

.anchor-status.answered i {
  background: #0f766e;
}

.anchor-status.pending {
  background: rgba(245, 158, 11, .16);
  color: #92400e;
}

.anchor-status.pending i {
  background: #f59e0b;
}

.anchor-question {
  display: -webkit-box;
  overflow: hidden;
  color: #334155;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.anchor-item.expanded .anchor-question {
  display: block;
  overflow: visible;
  -webkit-line-clamp: initial;
}

.anchor-meta {
  color: #94a3b8;
  font-size: 11px;
}

.anchor-actions {
  justify-content: flex-start;
  opacity: .76;
}

.anchor-item:hover .anchor-actions,
.anchor-item.active .anchor-actions {
  opacity: 1;
}

.anchor-link-button {
  padding: 7px 9px;
  border-style: dashed;
  font-size: 11px;
}

.timeline-list {
  display: grid;
  gap: 8px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 36px 38px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  text-align: left;
}

.timeline-item span {
  display: inline-grid;
  place-items: center;
  min-width: 30px;
  height: 22px;
  border-radius: 999px;
  background: rgba(15, 118, 110, .10);
}

.timeline-item small {
  overflow: hidden;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-card.compact {
  padding: 12px;
  border-radius: 14px;
  color: #94a3b8;
}

.message-card.user {
  background: rgba(15, 118, 110, 0.06);
}

.message-card.assistant {
  background: #f8fbfb;
  border-color: rgba(20, 184, 166, .16);
}

.message-history {
  max-height: min(44vh, 460px);
  overflow: auto;
  padding-right: 4px;
}

.ask-workspace-panel {
  position: relative;
  padding-right: 54px;
}

.floating-anchors {
  position: absolute;
  z-index: 4;
  top: 34px;
  right: 12px;
  width: 42px;
  padding: 10px 8px;
  border: 1px solid rgba(15, 118, 110, .12);
  border-radius: 999px;
  background: rgba(255, 255, 255, .82);
  box-shadow: 0 16px 38px rgba(15, 23, 42, .10);
  backdrop-filter: blur(14px);
  overflow: visible;
}

.floating-anchor-head {
  margin-bottom: 8px;
  color: #0f766e;
  font-size: 10px;
  font-weight: 900;
  text-align: center;
}

.floating-anchor-track {
  position: relative;
  width: 26px;
}

.floating-anchor-track::before {
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: 12px;
  width: 2px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(15, 118, 110, .14), rgba(20, 184, 166, .28));
  content: '';
}

.floating-anchor-dot {
  position: absolute;
  left: 2px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 22px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 4px 12px rgba(15, 23, 42, .10);
  color: #0f766e;
  font-size: 10px;
  font-weight: 900;
  cursor: pointer;
  transition: transform .18s ease, background .18s ease, box-shadow .18s ease, color .18s ease;
}

.floating-anchor-dot::before {
  position: absolute;
  left: -1px;
  top: 50%;
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #0f766e;
  box-shadow: 0 0 0 4px rgba(15, 118, 110, .12);
  content: '';
  transform: translateY(-50%);
  transition: box-shadow .18s ease, background .18s ease;
}

.floating-anchor-label {
  position: relative;
  z-index: 1;
}

.floating-anchor-dot.pending::before {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, .14);
}

.floating-anchor-dot.pending {
  color: #92400e;
}

.floating-anchor-dot:hover::before,
.floating-anchor-dot:focus-visible::before {
  box-shadow: 0 0 0 7px rgba(15, 118, 110, .16);
}

.floating-anchor-dot.active {
  outline: 2px solid rgba(15, 118, 110, .18);
  outline-offset: 2px;
}

.floating-anchor-dot:hover,
.floating-anchor-dot:focus-visible {
  background: #0f766e;
  box-shadow: 0 10px 20px rgba(15, 118, 110, .22);
  color: #fff;
  transform: translateX(-3px);
}

.floating-anchor-dot.pending:hover,
.floating-anchor-dot.pending:focus-visible {
  background: #f59e0b;
  box-shadow: 0 10px 20px rgba(245, 158, 11, .22);
  color: #fff;
}

.floating-anchor-dot.pending:hover::before,
.floating-anchor-dot.pending:focus-visible::before {
  box-shadow: 0 0 0 7px rgba(245, 158, 11, .18);
}

.floating-anchor-tooltip {
  position: absolute;
  right: 34px;
  top: 50%;
  display: grid;
  gap: 4px;
  width: 210px;
  border: 1px solid rgba(15, 118, 110, .16);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 18px 40px rgba(15, 23, 42, .14);
  opacity: 0;
  pointer-events: none;
  text-align: left;
  transform: translate(8px, -50%) scale(.98);
  transition: opacity .18s ease, transform .18s ease;
}

.floating-anchor-dot:hover .floating-anchor-tooltip,
.floating-anchor-dot:focus-visible .floating-anchor-tooltip {
  opacity: 1;
  transform: translate(0, -50%) scale(1);
}

.floating-anchor-tooltip strong {
  color: #0f766e;
  font-size: 11px;
}

.floating-anchor-tooltip em {
  display: -webkit-box;
  overflow: hidden;
  color: #334155;
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.floating-anchor-tooltip small {
  color: #94a3b8;
  font-size: 11px;
}

.message-card {
  position: relative;
  padding-left: 44px;
  transition: border-color .2s ease, box-shadow .2s ease, background .2s ease;
}

.message-copy-button {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  border: 1px solid rgba(15, 118, 110, .18);
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(255, 255, 255, .84);
  color: #0f766e;
  font: inherit;
  font-size: 11px;
  font-weight: 900;
  line-height: 1;
  cursor: pointer;
  opacity: .62;
  box-shadow: 0 8px 18px rgba(15, 23, 42, .08);
  transition: opacity .16s ease, transform .16s ease, background .16s ease, color .16s ease, border-color .16s ease;
}

.message-card:hover .message-copy-button,
.message-copy-button:focus-visible {
  opacity: 1;
}

.message-copy-button:hover,
.message-copy-button:focus-visible {
  border-color: rgba(15, 118, 110, .32);
  background: #0f766e;
  color: #fff;
  transform: translateY(-1px);
}

.message-card.assistant .message-copy-button {
  opacity: .78;
}

.message-card .message-text {
  padding-right: 58px;
}

.message-card.anchor-section {
  scroll-margin-top: 96px;
}

.message-card.anchor-section::after {
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: -8px;
  width: 3px;
  border-radius: 999px;
  background: transparent;
  content: '';
  opacity: 0;
  transition: opacity .18s ease, background .18s ease, box-shadow .18s ease;
}

.message-card.is-active::after,
.message-card.is-target::after {
  opacity: 1;
  background: linear-gradient(180deg, rgba(15, 118, 110, .95), rgba(20, 184, 166, .75));
  box-shadow: 0 0 0 4px rgba(15, 118, 110, .12);
}

.message-card.is-preview::after {
  opacity: 1;
  background: linear-gradient(180deg, rgba(245, 158, 11, .95), rgba(245, 158, 11, .55));
  box-shadow: 0 0 0 4px rgba(245, 158, 11, .12);
}

.message-card.is-target,
.message-card.is-preview,
.message-card.is-active {
  border-color: rgba(15, 118, 110, .26);
  box-shadow: 0 16px 30px rgba(15, 118, 110, .10);
}

.message-card.qa-group-start {
  margin-top: 4px;
}

.message-card.qa-group-end {
  margin-bottom: 10px;
}

.message-card::before {
  position: absolute;
  left: 14px;
  top: 14px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #0f766e;
  color: #fff;
  content: 'AI';
  font-size: 10px;
  font-weight: 900;
}

.message-card.user::before {
  background: #0f172a;
  content: '我';
}

.message-role {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.message-text {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #334155;
}

.ask-form {
  display: grid;
  gap: 14px;
}

.text-area {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 14px;
  padding: 12px 14px;
  min-height: 150px;
  background: linear-gradient(180deg, rgba(255, 255, 255, .98), rgba(248, 250, 252, .98));
  resize: vertical;
  font: inherit;
}

.text-area:focus {
  outline: none;
  border-color: rgba(15, 118, 110, .38);
  box-shadow: 0 0 0 4px rgba(15, 118, 110, .10);
}

.answer-block {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #334155;
}

.subsection + .subsection {
  margin-top: 18px;
}

.chip-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
}

.meta-card,
.visual-card {
  display: grid;
  gap: 5px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 14px;
  padding: 12px;
  background: rgba(248, 250, 252, .92);
}

.meta-card span {
  color: #64748b;
  font-size: 12px;
}

.meta-card strong,
.visual-card strong {
  color: #172033;
}

.visual-card code {
  max-height: 120px;
  overflow: auto;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
}

.feedback-box {
  border-top: 1px solid rgba(15, 23, 42, .08);
  padding-top: 16px;
}

.feedback-message {
  margin: 8px 0 0;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.inline-link {
  color: #0f766e;
  font-weight: 700;
}

.link-button,
.action-inline-button {
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.link-button:hover,
.action-inline-button:hover {
  color: #0b5f59;
  text-decoration: underline;
}

@media (max-width: 960px) {
  .hero-card,
  .page-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .header-actions,
  .form-actions {
    flex-direction: column;
    align-items: stretch;
  }
}

.hero-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-top: 18px;
}

.ghost-link {
  border-radius: 999px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, .78);
  color: #0f766e;
  font-weight: 800;
}

.hero-mascot {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 190px;
}

.hero-mascot span {
  position: absolute;
  bottom: 4px;
  border-radius: 999px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, .82);
  color: #0f766e;
  font-size: 12px;
  font-weight: 900;
}

.stage-ring {
  position: absolute;
  border: 1px solid color-mix(in srgb, var(--hero-accent) 32%, transparent);
  border-radius: 999px;
  animation: stage-pulse 3.2s ease-in-out infinite;
}

.stage-ring.one {
  width: 170px;
  height: 170px;
}

.stage-ring.two {
  width: 210px;
  height: 210px;
  animation-delay: -1.4s;
}

@keyframes stage-pulse {
  0%, 100% { transform: scale(.92); opacity: .48; }
  50% { transform: scale(1.04); opacity: .9; }
}

.form-actions {
  align-items: flex-start;
  flex-wrap: wrap;
}

.ask-submit-actions {
  justify-content: flex-end;
}

.ask-tools,
.ask-submit-actions {
  flex-wrap: wrap;
}

.control-button {
  min-height: 44px;
  padding: 11px 16px;
  white-space: nowrap;
}

.send-button {
  min-width: 118px;
  box-shadow: 0 16px 30px rgba(15, 118, 110, .18);
}

.send-button.loading {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
}

.save-button:disabled {
  background: rgba(15, 118, 110, .04);
  color: #7f9f9a;
}

.button-icon,
.button-spinner {
  position: relative;
  display: inline-block;
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
}

.send-icon::before {
  position: absolute;
  inset: 2px 1px 2px 3px;
  border-style: solid;
  border-width: 5px 0 5px 9px;
  border-color: transparent transparent transparent currentColor;
  content: '';
}

.save-icon::before {
  position: absolute;
  inset: 1px 2px 2px;
  border: 2px solid currentColor;
  border-radius: 3px;
  content: '';
}

.save-icon::after {
  position: absolute;
  left: 5px;
  right: 5px;
  bottom: 4px;
  height: 3px;
  border-radius: 999px;
  background: currentColor;
  content: '';
}

.stop-icon::before {
  position: absolute;
  inset: 3px;
  border-radius: 3px;
  background: currentColor;
  content: '';
}

.button-spinner {
  border: 2px solid rgba(255, 255, 255, .45);
  border-top-color: #fff;
  border-radius: 999px;
  animation: button-spin .8s linear infinite;
}

@keyframes button-spin {
  to { transform: rotate(360deg); }
}

.ghost-button.active {
  background: #0f766e;
  color: #fff;
}

.hidden-input {
  display: none;
}

.hero-mascot :deep(.mascot-canvas) {
  filter: drop-shadow(0 22px 36px rgba(0, 0, 0, .22));
}

.content-stack {
  position: relative;
  min-width: 0;
}

.session-list,
.timeline-list,
.ability-list,
.question-list {
  max-height: 330px;
  overflow: auto;
  padding-right: 2px;
}

.question-list .ghost-button {
  border-radius: 14px;
  text-align: left;
}

.primary-button,
.ghost-button,
.secondary-button,
.action-button {
  font-weight: 800;
}

.form-actions,
.ask-tools,
.ask-submit-actions {
  min-width: 0;
}

.ask-tools,
.ask-submit-actions {
  row-gap: 8px;
}

@media (max-width: 1200px) {
  .side-stack,
  .ask-panel {
    position: static;
  }

  .floating-anchors {
    display: none;
  }
}

@media (max-width: 980px) {
  .hero-card {
    grid-template-columns: 1fr;
  }
}

</style>
