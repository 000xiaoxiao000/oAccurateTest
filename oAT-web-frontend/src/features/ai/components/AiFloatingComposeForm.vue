<template>
  <form class="compose layout-item" :class="{ 'is-dragging': draggingFiles, 'tool-picker-open': toolPickerOpen }" :style="layoutStyle" @pointerdown="$emit('layout-drag', $event)" @dragenter.prevent="draggingFiles = true" @dragover.prevent="draggingFiles = true" @dragleave.prevent="handleDragLeave" @drop.prevent="handleDrop" @keydown.esc.stop.prevent="closeToolPicker" @submit.prevent="$emit('send')">
    <span
      v-for="direction in resizeDirections"
      :key="direction"
      :class="['layout-resizer', direction]"
      title="拖拽调整区域大小"
      @pointerdown.stop.prevent="$emit('layout-resize', $event, direction)"
    ></span>
    <div v-if="attachments.length" class="compose-attachments">
      <div v-for="item in attachments" :key="item.id" class="compose-attachment">
        <div v-if="item.isImage" class="compose-image-preview">
          <img :src="item.imageData" :alt="item.name" />
          <button class="attachment-remove" type="button" title="移除图片" aria-label="移除图片" @click.stop="$emit('remove-attachment', item.id)">
            <svg viewBox="0 0 24 24" width="10" height="10" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <div v-else class="compose-file-card">
          <span class="compose-file-icon" aria-hidden="true">{{ extLabel(item) }}</span>
          <span class="compose-file-copy">
            <strong>{{ item.name }}</strong>
            <small>{{ metaLabel(item) }}</small>
          </span>
          <button class="attachment-remove" type="button" title="移除附件" aria-label="移除附件" @click.stop="$emit('remove-attachment', item.id)">
            <svg viewBox="0 0 24 24" width="10" height="10" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>
    </div>
    <textarea :value="question" rows="3" placeholder="随时提问，例如：这个页面的数据该从哪里看" @input="$emit('update:question', ($event.target as HTMLTextAreaElement).value)" @keydown.enter.exact="$emit('enter', $event)" @paste="handlePaste"></textarea>
    <input ref="imageInput" type="file" multiple accept="image/*,.txt,.md,.json,.yaml,.yml,.csv,.log,.xml,.html,.css,.js,.ts,.java,.py,.sql,.pdf,.doc,.docx,.xls,.xlsx" class="hidden-input" @change="$emit('image-change', $event)" />
    <div class="compose-actions">
      <span class="state">{{ stateText }}</span>
      <div class="toolbar toolbar-right">
        <div class="ai-tool-picker">
          <button type="button" :class="['tool-button', toolPickerOpen && 'active']" aria-label="选择 AI 工具" :aria-expanded="toolPickerOpen" aria-controls="floating-ai-tool-panel" title="选择 AI 工具" @click.stop="toggleToolPicker">
            <svg class="tool-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path d="M5 7.5h14M8 4.5v6M16 4.5v6M7 16.5h10M12 13.5v6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
          <div v-if="toolPickerOpen" id="floating-ai-tool-panel" class="ai-tool-panel" @pointerdown.stop>
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
        <button type="button" :class="['tool-button', attachments.length && 'active']" aria-label="添加文件或图片" aria-describedby="floating-attachment-help" @click="selectImage">
          <svg class="tool-icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
            <path d="M20.5 11.5l-8.1 8.1a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.6 1.6 0 0 1-2.3-2.3l7.8-7.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span id="floating-attachment-help" class="attachment-tooltip" role="tooltip">快速模式下，仅识别图片与文件中的文字<br>最多 50 个，每个 100 MB</span>
        </button>
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

import { AI_TOOL_PROMPT_GROUPS, AI_TOOL_PROMPTS } from '@/features/ai/toolPrompts'
import type { AiAttachment, AiFloatingLayoutResizeDirection } from '@/features/ai/types'

const props = defineProps<{
  question: string
  attachments: AiAttachment[]
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
  'remove-attachment': [id: string]
  'toggle-voice': []
  'layout-drag': [event: PointerEvent]
  'layout-resize': [event: PointerEvent, direction: AiFloatingLayoutResizeDirection]
}>()

const imageInput = ref<HTMLInputElement | null>(null)
const draggingFiles = ref(false)
const toolPickerOpen = ref(false)

const aiToolGroups = AI_TOOL_PROMPT_GROUPS

function toolsByGroup(group: string) {
  return AI_TOOL_PROMPTS.filter((tool) => tool.group === group)
}

function selectImage() {
  toolPickerOpen.value = false
  imageInput.value?.click()
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
</script>

<style scoped>
.compose {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  box-sizing: border-box;
  padding: 10px 12px 12px;
  border: 1px solid rgba(203, 213, 225, .9);
  border-radius: 18px;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 12px 28px rgba(15, 23, 42, .08);
  cursor: move;
}

.compose-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 132px;
  overflow-y: auto;
  padding: 2px 2px 8px;
}

.compose-attachment { position: relative; }

.compose-image-preview {
  position: relative;
  width: 52px;
  height: 52px;
  border: 1px solid rgba(203, 213, 225, .9);
  border-radius: 10px;
  overflow: visible;
  background: #f8fafc;
}

.compose-image-preview img {
  width: 100%;
  height: 100%;
  border-radius: 9px;
  object-fit: cover;
}

.compose-file-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 220px;
  min-width: 0;
  border: 1px solid rgba(203, 213, 225, .9);
  border-radius: 10px;
  padding: 6px 10px;
  background: #fff;
}

.compose-file-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 7px;
  background: rgba(15, 118, 110, .14);
  color: #0f766e;
  font-size: 9px;
  font-weight: 800;
}

.compose-file-copy {
  display: grid;
  min-width: 0;
  gap: 1px;
}

.compose-file-copy strong,
.compose-file-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compose-file-copy strong { color: #1f2937; font-size: 12px; font-weight: 600; }
.compose-file-copy small { color: #94a3b8; font-size: 10.5px; }

.attachment-remove {
  position: absolute;
  top: -6px;
  left: -6px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border: 2px solid #fff;
  border-radius: 999px;
  padding: 0;
  background: #4b5563;
  color: #fff;
  cursor: pointer;
  transition: background .16s ease;
}

.attachment-remove:hover { background: #1f2937; }

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

.toolbar-right { flex: 0 0 auto; }

.state {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toolbar { gap: 8px; }
.hidden-input { display: none; }

.ai-tool-picker {
  position: relative;
}

.ai-tool-panel {
  position: absolute;
  right: 0;
  bottom: calc(100% + 12px);
  z-index: 20;
  display: grid;
  gap: 12px;
  width: min(420px, calc(100vw - 40px));
  max-height: min(520px, calc(100vh - 180px));
  overflow-y: auto;
  padding: 14px;
  border: 1px solid rgba(203, 213, 225, .88);
  border-radius: 14px;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 22px 60px rgba(15, 23, 42, .2);
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

.tool-button { position: relative; }
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
.tool-button:hover .attachment-tooltip,
.tool-button:focus-visible .attachment-tooltip { opacity: 1; transform: translateY(0); }
</style>
