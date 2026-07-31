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
    <form v-if="activeMessages.length" ref="askFormRef" class="ask-form" :class="{ 'is-dragging': draggingFiles }" @dragenter.prevent="draggingFiles = true" @dragover.prevent="draggingFiles = true" @dragleave.prevent="handleDragLeave" @drop.prevent="handleDrop" @submit.prevent="$emit('submit-ask')">
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
          <button class="ghost-button attachment-button" :class="{ active: Boolean(attachmentName || imageData) }" type="button" title="添加文件或图片" @click="$emit('select-image')">
            <svg class="button-icon attachment-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M20.5 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.6 1.6 0 0 1-2.3-2.3l7.8-7.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            {{ attachmentName || imageData ? '已添加附件' : '添加文件' }}
          </button>
          <span v-if="attachmentName || imageData" class="attachment-pill">{{ attachmentName || '图片已添加' }}</span>
          <button v-if="attachmentName || imageData" class="ghost-button" type="button" @click="$emit('clear-image')">移除附件</button>
        </div>
        <div class="ask-submit-actions">
          <button v-if="asking" class="danger-button control-button" type="button" @click="$emit('stop-ask')">
            <span class="button-icon stop-icon"></span>
            停止生成
          </button>
          <button v-else class="primary-button control-button send-button" type="submit" title="发送问题">
            <svg class="button-icon send-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M12 20V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            <span class="send-label">{{ activeMessages.length ? '发送问题' : '' }}</span>
          </button>
          <button class="ghost-button control-button save-button" type="button" :disabled="asking" @click="$emit('save-session')">
            <span class="button-icon save-icon" aria-hidden="true"></span>
            保存会话状态
          </button>
        </div>
      </div>
    </form>
    <form v-else ref="askFormRef" class="ask-form deepseek-compose" :class="{ 'has-attachment': attachmentName || imageData, 'is-dragging': draggingFiles }" @dragenter.prevent="draggingFiles = true" @dragover.prevent="draggingFiles = true" @dragleave.prevent="handleDragLeave" @drop.prevent="handleDrop" @submit.prevent="$emit('submit-ask')">
      <div v-if="attachmentName || imageData" class="compose-attachments">
        <div v-if="imageData" class="compose-image-preview">
          <img :src="imageData" alt="已添加图片" />
          <button class="compose-image-remove" type="button" title="移除图片" aria-label="移除图片" @click="$emit('clear-image')">
            <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <div v-if="!imageData" class="compose-file-card">
          <span class="compose-file-icon" aria-hidden="true">{{ attachmentExtLabel }}</span>
          <span class="compose-file-copy">
            <strong>{{ attachmentName || '图片附件' }}</strong>
            <small>{{ attachmentMeta }}</small>
          </span>
          <button class="compose-file-remove" type="button" title="移除附件" aria-label="移除附件" @click="$emit('clear-image')">
            <svg viewBox="0 0 24 24" width="13" height="13" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
            </svg>
          </button>
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
        <div class="ask-tools deepseek-compose-left">
          <button class="icon-button attachment-icon-button" type="button" aria-label="添加文件或图片" title="添加文件或图片" @click="$emit('select-image')">
            <svg class="button-icon attachment-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M20.5 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.6 1.6 0 0 1-2.3-2.3l7.8-7.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </div>
        <div class="ask-submit-actions deepseek-compose-controls">
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
import type { AiMessageSection, AiQuestionAnchor, AiSessionMessage } from '@/features/ai/types'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  question: string
  asking: boolean
  imageData: string
  attachmentName: string
  attachmentSize?: number
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
  'toggle-voice-input': []
  'stop-ask': []
  'save-session': []
}>()

const askFormRef = ref<HTMLFormElement | null>(null)
const askInputRef = ref<HTMLTextAreaElement | null>(null)
const imageInput = ref<HTMLInputElement | null>(null)
const draggingFiles = ref(false)

const questionModel = computed({
  get: () => props.question,
  set: (value: string) => emit('update:question', value),
})

const attachmentExtLabel = computed(() => {
  const name = props.attachmentName || ''
  const ext = name.includes('.') ? name.split('.').pop() || '' : ''
  if (ext) return ext.slice(0, 4).toUpperCase()
  return props.imageData ? 'IMG' : 'FILE'
})

const attachmentMeta = computed(() => {
  const parts: string[] = [attachmentExtLabel.value]
  if (props.attachmentSize) parts.push(formatFileSize(props.attachmentSize))
  return parts.join(' ')
})

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
