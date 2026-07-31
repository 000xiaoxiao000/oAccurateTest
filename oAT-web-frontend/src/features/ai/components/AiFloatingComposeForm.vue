<template>
  <form class="compose layout-item" :class="{ 'is-dragging': draggingFiles }" :style="layoutStyle" @pointerdown="$emit('layout-drag', $event)" @dragenter.prevent="draggingFiles = true" @dragover.prevent="draggingFiles = true" @dragleave.prevent="handleDragLeave" @drop.prevent="handleDrop" @submit.prevent="$emit('send')">
    <span
      v-for="direction in resizeDirections"
      :key="direction"
      :class="['layout-resizer', direction]"
      title="拖拽调整区域大小"
      @pointerdown.stop.prevent="$emit('layout-resize', $event, direction)"
    ></span>
    <textarea :value="question" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看" @input="$emit('update:question', ($event.target as HTMLTextAreaElement).value)" @keydown.enter.exact="$emit('enter', $event)" @paste="handlePaste"></textarea>
    <input ref="imageInput" type="file" accept="image/*,.txt,.md,.json,.yaml,.yml,.csv,.log,.xml,.html,.css,.js,.ts,.java,.py,.sql,.pdf,.doc,.docx,.xls,.xlsx" class="hidden-input" @change="$emit('image-change', $event)" />
    <div class="compose-actions">
      <div class="toolbar toolbar-left">
        <button type="button" :class="['tool-button', (imageData || attachmentName) && 'active']" aria-label="添加文件或图片" title="添加文件或图片" @click="selectImage">
          <svg class="tool-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path d="M20.5 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.6 1.6 0 0 1-2.3-2.3l7.8-7.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <span class="state">{{ stateText }}</span>
      </div>
      <div class="toolbar toolbar-right">
        <button class="send-button" type="submit" :disabled="asking" aria-label="发送问题" title="发送问题">
          <svg class="send-svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path d="M12 20V5M6 11l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { StyleValue } from 'vue'

import type { AiFloatingLayoutResizeDirection } from '@/features/ai/types'

defineProps<{
  question: string
  imageData: string
  attachmentName: string
  recording: boolean
  asking: boolean
  stateText: string
  layoutStyle: StyleValue
  resizeDirections: AiFloatingLayoutResizeDirection[]
}>()

const emit = defineEmits<{
  'update:question': [value: string]
  send: []
  enter: [event: KeyboardEvent]
  'image-change': [event: Event]
  'files-drop': [files: File[]]
  'clear-image': []
  'toggle-voice': []
  'layout-drag': [event: PointerEvent]
  'layout-resize': [event: PointerEvent, direction: AiFloatingLayoutResizeDirection]
}>()

const imageInput = ref<HTMLInputElement | null>(null)
const draggingFiles = ref(false)

function selectImage() {
  imageInput.value?.click()
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
</script>

<style scoped>
.compose {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  box-sizing: border-box;
  padding: 10px 12px 12px;
  border: 1px solid rgba(203, 213, 225, .9);
  border-radius: 18px;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 12px 28px rgba(15, 23, 42, .08);
  cursor: move;
}

.compose textarea {
  width: 100%;
  min-height: 0;
  max-height: none;
  box-sizing: border-box;
  border: 0;
  padding: 8px 8px 6px;
  resize: none;
  outline: none;
  background: transparent;
  color: #0f172a;
  font: inherit;
  font-size: 13px;
  line-height: 1.5;
}

.compose textarea::placeholder { color: #94a3b8; }
.compose textarea:focus { box-shadow: none; }
.compose.is-dragging { border-color: #0f766e; border-style: dashed; background: #f0fdfa; box-shadow: 0 0 0 4px rgba(15, 118, 110, .12), 0 12px 28px rgba(15, 23, 42, .08); }
.compose.is-dragging textarea { background: #f0fdfa; }

.compose-actions,
.toolbar {
  display: flex;
  align-items: center;
}

.compose-actions {
  justify-content: space-between;
  gap: 10px;
  min-height: 34px;
}

.toolbar-left { flex: 1 1 auto; min-width: 0; }
.toolbar-right { flex: 0 0 auto; }

.state {
  min-width: 0;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar { gap: 8px; }
.hidden-input { display: none; }

.tool-button,
.send-button {
  display: inline-grid;
  place-items: center;
  width: 34px;
  min-width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
  transition: background .16s ease, transform .16s ease;
}

.tool-button {
  background: rgba(15, 23, 42, .05);
  color: #475569;
}

.tool-button:hover { background: rgba(15, 23, 42, .1); }
.tool-button.active { background: rgba(15, 118, 110, .12); color: #0f766e; }

.tool-icon,
.send-svg { display: block; }

.send-button {
  background: #0f766e;
  color: #fff;
}

.send-button:hover:not(:disabled) { background: #0b5f59; transform: translateY(-1px); }
.send-button:disabled { opacity: .55; cursor: wait; }

.remove-image { position: absolute; font-size: 0; }
</style>
