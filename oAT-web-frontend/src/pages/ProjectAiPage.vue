<template>
  <section>
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
          <MascotCanvas :size="170" :color="context.mascot?.mascotPrimary || '#0f766e'" :seed="projectId" :mood="asking ? 'thinking' : error ? 'error' : 'happy'" />
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
          <section class="panel">
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
              <h2>会话时间线</h2>
            </div>
            <div class="timeline-list">
              <button
                v-for="(item, index) in activeMessages"
                :key="item.id"
                class="timeline-item"
                type="button"
                @click="scrollToMessage(item.id)"
              >
                <span>{{ index + 1 }}</span>
                <strong>{{ item.role === 'user' ? '你' : 'AI' }}</strong>
                <small>{{ item.text.slice(0, 42) || '-' }}</small>
              </button>
              <div v-if="!activeMessages.length" class="empty-card compact">暂无会话节点</div>
            </div>
          </section>
        </aside>

        <div class="content-stack">
          <section class="panel">
            <div class="card-title">
              <h2>提问</h2>
            </div>
            <div v-if="activeMessages.length" class="message-history">
              <article
                v-for="item in activeMessages"
                :id="`ai-message-${item.id}`"
                :key="item.id"
                class="message-card"
                :class="item.role"
              >
                <div class="message-role">{{ item.role === 'user' ? '你' : 'AI' }}</div>
                <div class="message-text">{{ item.text }}</div>
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
                  <button v-if="asking" class="danger-button" type="button" @click="stopAsk">停止生成</button>
                  <button class="primary-button" type="submit" :disabled="asking">{{ asking ? '生成中...' : '发送问题' }}</button>
                  <button class="ghost-button" type="button" @click="saveSession">保存会话状态</button>
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
                <a v-for="link in reply.quickLinks" :key="link.title + link.url" class="link-card" :href="link.url">
                  <strong>{{ link.title }}</strong>
                  <span>{{ link.description }}</span>
                </a>
              </div>
            </div>

            <div v-if="reply.actions?.length" class="subsection">
              <h3>建议动作</h3>
              <div class="link-list">
                <article v-for="action in reply.actions" :key="action.title + action.type" class="link-card">
                  <strong>{{ action.title }}</strong>
                  <span>{{ action.description }}</span>
                  <a v-if="action.url" class="inline-link" :href="action.url">打开</a>
                </article>
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const context = computed(() => projectStore.aiContextByProjectId[projectId.value])
const reply = computed(() => projectStore.aiLastReplyByProjectId[projectId.value])
type SessionMessage = { id: string; role: 'user' | 'assistant'; text: string }
type ChatSession = { id: string; title: string; messages: SessionMessage[]; updatedAt: number; pinned?: boolean }
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
let askAbortController: AbortController | null = null

const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value) || null)
const activeMessages = computed(() => activeSession.value?.messages || [])
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
  syncSessionState()
}

function newSession() {
  const fresh = createSession()
  sessions.value = [fresh, ...sessions.value]
  activeSessionId.value = fresh.id
  syncSessionState()
}

function renameSession(sessionId: string) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session) return
  const nextTitle = window.prompt('请输入会话名称', session.title)
  if (!nextTitle?.trim()) return
  session.title = nextTitle.trim().slice(0, 40)
  touchSession(session)
  syncSessionState()
}

function deleteSession(sessionId: string) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session) return
  if (!window.confirm(`确认删除会话「${session.title}」？`)) return
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

function scrollToMessage(messageId: string) {
  document.getElementById(`ai-message-${messageId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
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
    await askAiStreaming(currentQuestion, assistantMessage)
    touchSession(activeSession.value)
    await syncSessionState()
    question.value = ''
    imageData.value = ''
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      const lastAssistant = [...activeMessages.value].reverse().find((item) => item.role === 'assistant')
      if (lastAssistant) lastAssistant.text = `${lastAssistant.text}\n[已停止生成]`
      error.value = '已停止生成'
      await syncSessionState()
    } else {
      error.value = err instanceof Error ? err.message : 'AI 提问失败'
    }
  } finally {
    askAbortController = null
    asking.value = false
  }
}

function stopAsk() {
  askAbortController?.abort()
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
  const response = await fetch(`/p/${projectId.value}/AIInteractive/ask/stream`, {
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
})
</script>

<style scoped>
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
.ghost-button {
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
}

.action-button,
.secondary-button,
.primary-button {
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
}

.status-card.error {
  color: #b91c1c;
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 320px;
  gap: 18px;
  padding: 22px;
  border-radius: 24px;
  margin-bottom: 18px;
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--hero-accent) 20%, white) 0%, transparent 36%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(239, 247, 248, 0.94));
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.hero-meta {
  display: grid;
  gap: 12px;
}

.hero-chip {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.hero-chip strong {
  display: block;
  font-size: 24px;
}

.page-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
}

.side-stack,
.content-stack,
.ability-list,
.question-list,
.link-list,
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
  background: #f8fbfb;
  border: 1px solid rgba(15, 23, 42, 0.06);
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

.message-card.user {
  background: rgba(15, 118, 110, 0.06);
}

.message-card.assistant {
  background: #f8fbfb;
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
  resize: vertical;
  font: inherit;
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

.inline-link {
  color: #0f766e;
  font-weight: 700;
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


.hero-card {
  grid-template-columns: minmax(0, 1fr) minmax(180px, 240px) minmax(260px, .65fr);
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

.ask-tools,
.ask-submit-actions {
  flex-wrap: wrap;
}

.ghost-button.active {
  background: #0f766e;
  color: #fff;
}

.hidden-input {
  display: none;
}

@media (max-width: 980px) {
  .hero-card {
    grid-template-columns: 1fr;
  }
}

</style>
