<template>
  <div v-show="!collapsed" class="message-list">
    <article v-for="item in messages" :key="item.id" class="message" :class="item.role">
      <div class="message-avatar">{{ item.role === 'user' ? '我' : 'AI' }}</div>
      <div class="message-body">
        <div class="message-name">{{ item.role === 'user' ? '你' : 'AI 助手' }}</div>
        <div class="message-card">
          <button
            class="copy-button"
            type="button"
            :class="{ copied: copiedMessageId === item.id }"
            :aria-label="item.role === 'assistant' ? '复制回复内容' : '复制提问内容'"
            @pointerdown.stop
            @click="$emit('copy', item.text, item.id)"
          >
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
              @click.stop="$emit('feedback', item.id, item.text, 'helpful', 5)"
            >
              <span class="feedback-icon">👍</span>
            </button>
            <button
              class="feedback-btn"
              type="button"
              :disabled="feedbackSubmitting"
              :class="{ active: getMessageFeedback(item.id) === 'not_helpful' }"
              :title="getMessageFeedback(item.id) === 'not_helpful' ? '已标记没帮助' : '没帮助'"
              @click.stop="$emit('feedback', item.id, item.text, 'not_helpful', 1)"
            >
              <span class="feedback-icon">👎</span>
            </button>
            <button
              class="feedback-btn"
              type="button"
              :disabled="feedbackSubmitting"
              title="标记为不正确"
              @click.stop="$emit('feedback', item.id, item.text, 'incorrect', 1, true)"
            >
              <span class="feedback-icon">⚠️</span>
            </button>
          </div>

          <div v-if="item.suggestions?.length" class="message-actions">
            <button v-for="suggestion in item.suggestions" :key="suggestion" class="message-action" type="button" @click="$emit('preset', suggestion)">{{ suggestion }}</button>
          </div>
          <div v-if="item.actions?.length" class="message-actions">
            <button v-for="action in item.actions" :key="action.title + action.type" class="message-action exec-action" type="button" @click="$emit('execute', action)">{{ action.title || '执行操作' }}</button>
          </div>
        </div>
      </div>
    </article>
    <article v-if="!messages.length" class="message assistant">
      <div class="message-avatar">AI</div>
      <div class="message-body">
        <div class="message-name">AI 助手</div>
        <div class="message-card">
          <div class="message-text markdown-message" v-html="renderMarkdown(emptyMessage)"></div>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import type { AIAction } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import type { AiFeedbackType, AiFloatingMessage } from '@/features/ai/types'

defineProps<{
  messages: AiFloatingMessage[]
  collapsed: boolean
  copiedMessageId: string
  feedbackSubmitting: boolean
  emptyMessage: string
  getMessageFeedback: (messageId: string) => string
}>()

defineEmits<{
  copy: [text: string, messageId: string]
  feedback: [messageId: string, answerText: string, feedbackType: AiFeedbackType, rating: number, requireComment?: boolean]
  preset: [text: string]
  execute: [action: AIAction]
}>()
</script>

<style scoped>
.message-list {
  display: grid;
  align-content: start;
  gap: 7px;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 12px 10px;
  scrollbar-gutter: stable;
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.message.user { flex-direction: row-reverse; }
.message-avatar { flex: 0 0 28px; display: grid; place-items: center; width: 28px; height: 28px; border-radius: 10px; background: #dff7f5; color: #0f172a; font-size: 11px; font-weight: 800; }
.message.user .message-avatar { background: #e5e7eb; }
.message-body { min-width: 0; flex: 1; }
.message-name { margin-bottom: 3px; color: #6b7280; font-size: 11px; }
.message.user .message-name { text-align: right; }
.message-card { position: relative; border: 1px solid rgba(226, 232, 240, .82); border-radius: 12px; padding: 8px 10px; background: rgba(248, 250, 252, .9); color: #1f2937; font-size: 12.5px; line-height: 1.55; overflow-wrap: anywhere; }
.message.user .message-card { background: rgba(238, 242, 255, .9); }
.message-text { white-space: pre-wrap; user-select: text; }
.markdown-message { white-space: normal; }
.copy-button { position: absolute; top: 7px; right: 8px; min-width: 42px; height: 22px; border: 1px solid rgba(20, 184, 166, .2); border-radius: 999px; background: #fff; color: #0f766e; font-size: 11px; cursor: pointer; opacity: 0; }
.message-card:hover .copy-button, .copy-button.copied { opacity: 1; }
.feedback-actions, .message-actions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 7px; }
.feedback-btn { display: grid; place-items: center; width: 26px; height: 26px; border: 0; border-radius: 6px; background: transparent; cursor: pointer; }
.feedback-btn:hover, .feedback-btn.active { background: rgba(15, 118, 110, .1); }
.message-action { border: 1px solid rgba(15, 118, 110, .18); border-radius: 999px; padding: 5px 8px; background: #fff; color: #0f766e; font-size: 11px; cursor: pointer; }
</style>
