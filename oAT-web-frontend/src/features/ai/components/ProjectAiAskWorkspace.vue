<template>
  <section class="panel ask-workspace-panel" :class="{ 'empty-ask': !activeMessages.length }">
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
          @mouseenter="$emit('update:previewAnchorId', dot.id)"
          @mouseleave="$emit('update:previewAnchorId', '')"
          @click="$emit('scroll-to-anchor', dot.id)"
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
    <div v-if="activeMessages.length" class="card-title">
      <h2>提问</h2>
    </div>
    <div v-else class="empty-ask-title" aria-hidden="true">使用 AI 助手开始测试分析</div>
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
        <button class="message-copy-button" type="button" :title="section.message.role === 'assistant' ? '复制回复内容' : '复制提问内容'" @click.stop="$emit('copy-message', section.message.text, section.id)">
          {{ copiedMessageId === section.id ? '已复制' : '复制' }}
        </button>
        <div class="message-role">{{ section.message.role === 'user' ? '你' : 'AI' }}</div>
        <div v-if="section.message.role === 'assistant'" class="message-text markdown-message" v-html="formatAssistantMessage(section.message.text)"></div>
        <div v-else class="message-text">{{ section.message.text }}</div>

        <div v-if="section.message.role === 'assistant' && section.endsAnswer" class="message-actions">
          <button
            class="message-action-btn"
            type="button"
            :disabled="feedbackSubmitting"
            :class="{ active: getMessageFeedback(section.id) === 'helpful' }"
            :title="getMessageFeedback(section.id) === 'helpful' ? '已标记有帮助' : '有帮助'"
            @click.stop="$emit('submit-message-feedback', section.id, section.message.text, 'helpful', 5)"
          >
            <span class="action-icon">👍</span>
          </button>
          <button
            class="message-action-btn"
            type="button"
            :disabled="feedbackSubmitting"
            :class="{ active: getMessageFeedback(section.id) === 'not_helpful' }"
            :title="getMessageFeedback(section.id) === 'not_helpful' ? '已标记没帮助' : '没帮助'"
            @click.stop="$emit('submit-message-feedback', section.id, section.message.text, 'not_helpful', 1)"
          >
            <span class="action-icon">👎</span>
          </button>
          <button
            class="message-action-btn"
            type="button"
            :disabled="feedbackSubmitting"
            title="标记为不正确"
            @click.stop="$emit('submit-message-feedback', section.id, section.message.text, 'incorrect', 1, true)"
          >
            <span class="action-icon">⚠️</span>
          </button>
        </div>
      </article>
    </div>
    <input
      ref="imageInput"
      class="hidden-input"
      type="file"
      accept="image/*,.txt,.md,.json,.yaml,.yml,.csv,.log,.xml,.html,.css,.js,.ts,.java,.py,.sql,.pdf,.doc,.docx,.xls,.xlsx"
      @change="$emit('image-change', $event)"
    />
    <form v-if="activeMessages.length" ref="askFormRef" class="ask-form" :class="{ 'is-dragging': draggingFiles, 'has-attachment': attachments.length, 'tool-picker-open': toolPickerOpen }" @dragenter.prevent="draggingFiles = true" @dragover.prevent="draggingFiles = true" @dragleave.prevent="handleDragLeave" @drop.prevent="handleDrop" @keydown.esc.stop.prevent="closeToolPicker" @submit.prevent="$emit('submit-ask')">
      <div v-if="attachments.length" class="compose-attachments">
        <div v-for="item in attachments" :key="item.id" class="compose-attachment">
          <div v-if="item.isImage" class="compose-image-preview">
            <img :src="item.imageData" :alt="item.name" />
            <button class="compose-image-remove" type="button" title="移除图片" aria-label="移除图片" @click="$emit('remove-attachment', item.id)">
              <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
              </svg>
            </button>
          </div>
          <div v-else class="compose-file-card">
            <span class="compose-file-icon" aria-hidden="true">{{ extLabel(item) }}</span>
            <span class="compose-file-copy">
              <strong>{{ item.name }}</strong>
              <small>{{ metaLabel(item) }}</small>
            </span>
            <button class="compose-file-remove" type="button" title="移除附件" aria-label="移除附件" @click="$emit('remove-attachment', item.id)">
              <svg viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </div>
      </div>
      <textarea
        ref="askInputRef"
        v-model="questionModel"
        class="text-area"
        rows="6"
        placeholder="例如：帮我总结当前项目的测试覆盖盲区，优先按风险排序。"
        @keydown.enter.exact="$emit('ask-enter', $event)"
        @paste="handlePaste"
      ></textarea>
      <div class="form-actions">
        <div class="ask-tools">
          <button class="ghost-button control-button save-button" type="button" :disabled="asking" @click="$emit('save-session')">
            <span class="button-icon save-icon" aria-hidden="true"></span>
            保存会话状态
          </button>
        </div>
        <div class="ask-submit-actions">
          <div class="ai-tool-picker">
            <button class="icon-button ai-tool-picker-button" type="button" aria-label="选择 AI 工具" :aria-expanded="toolPickerOpen" aria-controls="workspace-ai-tool-panel" title="选择 AI 工具" @click.stop="toggleToolPicker">
              <svg class="button-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path d="M5 7.5h14M8 4.5v6M16 4.5v6M7 16.5h10M12 13.5v6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </button>
            <div v-if="toolPickerOpen" id="workspace-ai-tool-panel" class="ai-tool-panel" @pointerdown.stop>
              <div class="ai-tool-panel-head">
                <strong>选择 AI 工具</strong>
                <span>选择后写入输入框</span>
              </div>
              <div v-for="group in aiToolGroups" :key="group" class="ai-tool-group">
                <div class="ai-tool-group-title">{{ group }}</div>
                <button v-for="tool in toolsByGroup(group)" :key="tool.id" class="ai-tool-option" type="button" @click="selectAiTool(tool.prompt)">
                  <span class="ai-tool-glyph" :class="`icon-${tool.icon}`" aria-hidden="true">{{ tool.name.slice(0, 1) }}</span>
                  <span>
                    <strong>{{ tool.name }}</strong>
                    <small>{{ tool.description }}</small>
                  </span>
                </button>
              </div>
            </div>
          </div>
          <button class="icon-button attachment-icon-button" type="button" aria-label="添加文件或图片" aria-describedby="workspace-attachment-help" @click="$emit('select-image')">
            <svg class="button-icon attachment-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M20.5 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.6 1.6 0 0 1-2.3-2.3l7.8-7.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span id="workspace-attachment-help" class="attachment-tooltip" role="tooltip">快速模式下，仅识别图片与文件中的文字<br>最多 50 个，每个 100 MB</span>
          </button>
          <button v-if="asking" class="danger-button control-button" type="button" @click="$emit('stop-ask')">
            <span class="button-icon stop-icon"></span>
            停止生成
          </button>
          <button v-else class="primary-button control-button send-button" type="submit" aria-label="发送问题" title="发送问题">
            <svg class="button-icon send-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M12 20V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span class="send-label">发送问题</span>
          </button>
        </div>
      </div>
    </form>
    <form v-else ref="askFormRef" class="ask-form deepseek-compose" :class="{ 'has-attachment': attachments.length, 'is-dragging': draggingFiles, 'tool-picker-open': toolPickerOpen }" @dragenter.prevent="draggingFiles = true" @dragover.prevent="draggingFiles = true" @dragleave.prevent="handleDragLeave" @drop.prevent="handleDrop" @keydown.esc.stop.prevent="closeToolPicker" @submit.prevent="$emit('submit-ask')">
      <div v-if="attachments.length" class="compose-attachments">
        <div v-for="item in attachments" :key="item.id" class="compose-attachment">
          <div v-if="item.isImage" class="compose-image-preview">
            <img :src="item.imageData" :alt="item.name" />
            <button class="compose-image-remove" type="button" title="移除图片" aria-label="移除图片" @click="$emit('remove-attachment', item.id)">
              <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
              </svg>
            </button>
          </div>
          <div v-else class="compose-file-card">
            <span class="compose-file-icon" aria-hidden="true">{{ extLabel(item) }}</span>
            <span class="compose-file-copy">
              <strong>{{ item.name }}</strong>
              <small>{{ metaLabel(item) }}</small>
            </span>
            <button class="compose-file-remove" type="button" title="移除附件" aria-label="移除附件" @click="$emit('remove-attachment', item.id)">
              <svg viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
              </svg>
            </button>
          </div>
        </div>
      </div>
      <textarea
        ref="askInputRef"
        v-model="questionModel"
        class="text-area deepseek-compose-input"
        rows="2"
        placeholder="给 AI 助手发送消息"
        @keydown.enter.exact="$emit('ask-enter', $event)"
        @paste="handlePaste"
      ></textarea>
      <div class="form-actions deepseek-compose-actions">
        <div class="ask-tools deepseek-compose-left"></div>
        <div class="ask-submit-actions deepseek-compose-controls">
          <div class="ai-tool-picker">
            <button class="icon-button ai-tool-picker-button" type="button" aria-label="选择 AI 工具" :aria-expanded="toolPickerOpen" aria-controls="workspace-ai-tool-panel-empty" title="选择 AI 工具" @click.stop="toggleToolPicker">
              <svg class="button-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path d="M5 7.5h14M8 4.5v6M16 4.5v6M7 16.5h10M12 13.5v6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </button>
            <div v-if="toolPickerOpen" id="workspace-ai-tool-panel-empty" class="ai-tool-panel" @pointerdown.stop>
              <div class="ai-tool-panel-head">
                <strong>选择 AI 工具</strong>
                <span>选择后写入输入框</span>
              </div>
              <div v-for="group in aiToolGroups" :key="group" class="ai-tool-group">
                <div class="ai-tool-group-title">{{ group }}</div>
                <button v-for="tool in toolsByGroup(group)" :key="tool.id" class="ai-tool-option" type="button" @click="selectAiTool(tool.prompt)">
                  <span class="ai-tool-glyph" :class="`icon-${tool.icon}`" aria-hidden="true">{{ tool.name.slice(0, 1) }}</span>
                  <span>
                    <strong>{{ tool.name }}</strong>
                    <small>{{ tool.description }}</small>
                  </span>
                </button>
              </div>
            </div>
          </div>
          <button class="icon-button attachment-icon-button" type="button" aria-label="添加文件或图片" aria-describedby="workspace-attachment-help-empty" @click="$emit('select-image')">
            <svg class="button-icon attachment-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M20.5 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.6 1.6 0 0 1-2.3-2.3l7.8-7.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span id="workspace-attachment-help-empty" class="attachment-tooltip" role="tooltip">快速模式下，仅识别图片与文件中的文字<br>最多 50 个，每个 100 MB</span>
          </button>
          <button v-if="asking" class="danger-button control-button" type="button" @click="$emit('stop-ask')">
            <span class="button-icon stop-icon"></span>
            停止生成
          </button>
          <button v-else class="primary-button control-button send-button deepseek-send-button" type="submit" aria-label="发送问题" title="发送问题">
            <svg class="button-icon send-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M12 20V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span class="send-label"></span>
          </button>
        </div>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import type { AIFeedbackPayload } from '@/api/types'
import { AI_TOOL_PROMPT_GROUPS, AI_TOOL_PROMPTS } from '@/features/ai/toolPrompts'
import type { AiAttachment, AiMessageSection, AiQuestionAnchor, AiSessionMessage } from '@/features/ai/types'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  question: string
  asking: boolean
  attachments: AiAttachment[]
  recording: boolean
  activeMessages: AiSessionMessage[]
  messageSections: AiMessageSection[]
  questionAnchors: AiQuestionAnchor[]
  floatingAnchorDots: Array<AiQuestionAnchor & { top: number }>
  floatingTrackHeight: number
  activeAnchorId: string
  previewAnchorId: string
  targetAnchorId: string
  copiedMessageId: string
  feedbackSubmitting: boolean
  messageFeedbacks: Record<string, string>
}>()

const emit = defineEmits<{
  'update:question': [value: string]
  'update:previewAnchorId': [value: string]
  'scroll-to-anchor': [anchorId: string]
  'copy-message': [text: string, messageId: string]
  'submit-message-feedback': [messageId: string, answerText: string, feedbackType: AIFeedbackPayload['feedbackType'], rating: number, requireComment?: boolean]
  'submit-ask': []
  'ask-enter': [event: KeyboardEvent]
  'image-change': [event: Event]
  'files-drop': [files: File[]]
  'select-image': []
  'clear-image': []
  'remove-attachment': [id: string]
  'toggle-voice-input': []
  'stop-ask': []
  'save-session': []
}>()

const askFormRef = ref<HTMLFormElement | null>(null)
const askInputRef = ref<HTMLTextAreaElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const draggingFiles = ref(false)
const toolPickerOpen = ref(false)
const aiToolGroups = AI_TOOL_PROMPT_GROUPS

const questionModel = computed({
  get: () => props.question,
  set: (value: string) => emit('update:question', value),
})

function toolsByGroup(group: string) {
  return AI_TOOL_PROMPTS.filter((tool) => tool.group === group)
}

function toggleToolPicker() {
  toolPickerOpen.value = !toolPickerOpen.value
}

function closeToolPicker() {
  toolPickerOpen.value = false
}

function selectAiTool(prompt: string) {
  const current = props.question.trim()
  const next = current ? `${current}\n${prompt}` : prompt
  emit('update:question', next)
  toolPickerOpen.value = false
  focusAskInput()
}

function extLabel(item: AiAttachment) {
  const ext = item.name.includes('.') ? item.name.split('.').pop() || '' : ''
  if (ext) return ext.slice(0, 4).toUpperCase()
  return item.isImage ? 'IMG' : 'FILE'
}

function metaLabel(item: AiAttachment) {
  const parts: string[] = [extLabel(item)]
  if (item.size) parts.push(formatFileSize(item.size))
  return parts.join(' ')
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function handleDragLeave(event: DragEvent) {
  const current = event.currentTarget as HTMLElement | null
  const related = event.relatedTarget as Node | null
  if (!current?.contains(related)) draggingFiles.value = false
}

function handleDrop(event: DragEvent) {
  draggingFiles.value = false
  const files = Array.from(event.dataTransfer?.files || [])
  if (files.length) emit('files-drop', files)
}

function handlePaste(event: ClipboardEvent) {
  const items = Array.from(event.clipboardData?.items || [])
  const files = items
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter((file): file is File => Boolean(file))
  if (files.length) {
    event.preventDefault()
    emit('files-drop', files)
  }
}

function formatAssistantMessage(text: string) {
  return renderMarkdown(text)
}

function getMessageFeedback(messageId: string): string {
  return props.messageFeedbacks[messageId] || ''
}

function focusAskInput() {
  askInputRef.value?.focus({ preventScroll: true })
}

function scrollAskFormIntoView() {
  askFormRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function selectImageFile() {
  imageInput.value?.click()
}

defineExpose({
  focusAskInput,
  scrollAskFormIntoView,
  selectImageFile,
})
</script>

<style scoped>
.ai-tool-picker {
  position: relative;
}

.ai-tool-picker-button[aria-expanded="true"] {
  background: rgba(15, 118, 110, .12);
  color: #0f766e;
}

.ai-tool-panel {
  position: absolute;
  right: 0;
  bottom: calc(100% + 12px);
  z-index: 30;
  display: grid;
  gap: 12px;
  width: min(440px, calc(100vw - 40px));
  max-height: min(540px, calc(100vh - 220px));
  overflow-y: auto;
  padding: 14px;
  border: 1px solid rgba(203, 213, 225, .9);
  border-radius: 14px;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 24px 64px rgba(15, 23, 42, .2);
}

.ai-tool-panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.ai-tool-panel-head strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.ai-tool-panel-head span {
  color: #64748b;
  font-size: 11px;
}

.ai-tool-group {
  display: grid;
  gap: 7px;
}

.ai-tool-group-title {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.ai-tool-option {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid rgba(226, 232, 240, .95);
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
  text-align: left;
  cursor: pointer;
  transition: border-color .16s ease, background .16s ease, transform .16s ease;
}

.ai-tool-option:hover,
.ai-tool-option:focus-visible {
  border-color: rgba(15, 118, 110, .38);
  background: #f8fafc;
  outline: none;
  transform: translateY(-1px);
}

.ai-tool-glyph {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: rgba(15, 118, 110, .12);
  color: #0f766e;
  font-size: 13px;
  font-weight: 800;
}

.ai-tool-option strong,
.ai-tool-option small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-tool-option strong {
  font-size: 13px;
  font-weight: 700;
}

.ai-tool-option small {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
}

.attachment-icon-button { position: relative; }
.attachment-tooltip {
  position: absolute;
  right: 0;
  bottom: calc(100% + 10px);
  z-index: 5;
  width: 240px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #29292d;
  color: #fff;
  font-size: 12px;
  line-height: 1.55;
  text-align: left;
  pointer-events: none;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity .15s ease, transform .15s ease;
}
.attachment-icon-button:hover .attachment-tooltip,
.attachment-icon-button:focus-visible .attachment-tooltip { opacity: 1; transform: translateY(0); }
</style>
