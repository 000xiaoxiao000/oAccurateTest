<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <form class="snapshot-modal" @submit.prevent="$emit('submit')">
      <div class="modal-head">
        <div>
          <div class="eyebrow">{{ mode === 'my' ? 'My Snapshot' : 'System Snapshot' }}</div>
          <h2>{{ mode === 'my' ? '保存我的快照' : '保存系统快照' }}</h2>
          <p>{{ context?.subTitle || selectedTraceId }}</p>
        </div>
        <button class="icon-button" type="button" @click="$emit('close')">×</button>
      </div>

      <div v-if="error" class="status-card error">{{ error }}</div>
      <div v-if="contextLoading" class="status-card">正在加载保存上下文...</div>
      <template v-else>
        <div class="snapshot-form-grid">
          <div class="form-main">
            <label class="field required">
              <span>名称</span>
              <input
                class="text-input"
                type="text"
                maxlength="50"
                :value="form.title"
                :placeholder="mode === 'my' ? '我的快照名称' : '系统快照名称'"
                @input="updateField('title', ($event.target as HTMLInputElement).value.trim())"
              />
            </label>
            <label v-if="mode === 'system'" class="field">
              <span>图片</span>
              <input class="text-input" type="file" accept="image/*" @change="$emit('upload-image', $event)" />
            </label>
            <div v-if="mode === 'system' && form.topicImage" class="image-preview-line">
              <img :src="form.topicImage" alt="系统快照图片" />
              <span>{{ form.topicImage }}</span>
            </div>
            <label class="field">
              <span>描述</span>
              <textarea
                class="text-input textarea"
                maxlength="512"
                rows="6"
                placeholder="描述本次调用场景、关键输入或异常现象"
                :value="form.describe"
                @input="updateField('describe', ($event.target as HTMLTextAreaElement).value.trim())"
              ></textarea>
            </label>
          </div>

          <div class="form-side">
            <label class="field">
              <span>所属应用</span>
              <input class="text-input" type="text" :value="context?.appName || form.appId" readonly />
            </label>
            <label v-if="mode === 'system'" class="field">
              <span>目录</span>
              <select class="text-input" :value="form.directory" @change="updateField('directory', ($event.target as HTMLSelectElement).value)">
                <option value="root">/root</option>
                <option v-for="dir in context?.directories || []" :key="dir.id" :value="dir.id">
                  {{ dir.path || dir.name }}
                </option>
              </select>
            </label>
            <label v-if="mode === 'system'" class="field">
              <span>版本有效周期（天）</span>
              <input class="text-input" type="number" min="1" max="3650" :value="form.versionCycle" @input="updateField('versionCycle', Number(($event.target as HTMLInputElement).value))" />
            </label>
            <div class="field">
              <span>添加标签</span>
              <div class="choice-grid">
                <label v-for="label in context?.labels || []" :key="label.name" class="choice-pill">
                  <input type="checkbox" :checked="form.labels.includes(label.name)" @change="toggleChoice('labels', label.name)" />
                  <span>{{ label.name }}</span>
                </label>
              </div>
            </div>
            <div v-if="mode === 'system'" class="field required">
              <span>负责人</span>
              <div class="choice-grid">
                <label v-for="member in context?.members || []" :key="member.memberId" class="choice-pill">
                  <input type="checkbox" :checked="form.principals.includes(member.memberId)" @change="toggleChoice('principals', member.memberId)" />
                  <span>{{ member.memberName || member.memberEmail || member.memberId }}</span>
                </label>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-actions">
          <button class="ghost-button" type="button" @click="$emit('close')">算啦</button>
          <button class="action-button" type="submit" :disabled="saving || imageUploading">
            {{ saving ? '保存中...' : imageUploading ? '图片上传中...' : mode === 'my' ? '保存到我的快照' : '保存到系统快照' }}
          </button>
        </div>
      </template>
    </form>
  </div>
</template>

<script setup lang="ts">
import type { MonitorSnapshotContextPayload } from '@/api/types'

export type MonitorSnapshotDialogMode = 'my' | 'system'
export type MonitorSnapshotFormState = {
  traceId: string
  appId: string
  directory: string
  title: string
  topicImage: string
  describe: string
  versionCycle: number
  labels: string[]
  principals: string[]
}

const props = defineProps<{
  mode: MonitorSnapshotDialogMode
  selectedTraceId: string
  context: MonitorSnapshotContextPayload | null
  form: MonitorSnapshotFormState
  error: string
  contextLoading: boolean
  saving: boolean
  imageUploading: boolean
}>()

const emit = defineEmits<{
  'update:form': [form: MonitorSnapshotFormState]
  close: []
  submit: []
  'upload-image': [event: Event]
}>()

function updateField<K extends keyof MonitorSnapshotFormState>(key: K, value: MonitorSnapshotFormState[K]) {
  emit('update:form', { ...props.form, [key]: value })
}

function toggleChoice(key: 'labels' | 'principals', value: string) {
  const current = props.form[key]
  const next = current.includes(value)
    ? current.filter((item) => item !== value)
    : [...current, value]
  updateField(key, next)
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .5);
  backdrop-filter: blur(4px);
  animation: fade-in 200ms ease;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.snapshot-modal {
  width: min(1040px, 100%);
  max-height: min(860px, 92vh);
  overflow-y: auto;
  border-radius: 24px;
  padding: 24px;
  background: #ffffff;
  box-shadow: 0 24px 64px rgba(15, 23, 42, .24);
  animation: slide-up 300ms ease;
}

@keyframes slide-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-head,
.modal-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.modal-head {
  margin-bottom: 20px;
}

.modal-head h2 {
  margin: 4px 0;
  font-size: 22px;
  font-weight: 600;
  color: #0f172a;
}

.modal-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  word-break: break-all;
  line-height: 1.5;
}

.eyebrow {
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .12em;
  text-transform: uppercase;
  margin-bottom: 4px;
}

.icon-button {
  border: none;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(241, 245, 249, .8);
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  color: #64748b;
  transition: all 150ms ease;
}

.icon-button:hover {
  background: rgba(226, 232, 240, 1);
  color: #0f172a;
  transform: rotate(90deg);
}

.snapshot-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, .6fr);
  gap: 20px;
}

.form-main,
.form-side,
.field {
  display: grid;
  gap: 12px;
}

.field {
  margin-bottom: 12px;
  font-weight: 500;
}

.field > span {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.field.required > span::after {
  content: ' *';
  color: #dc2626;
  font-weight: 700;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(203, 213, 225, .8);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fbfdfe;
  color: #0f172a;
  font-size: 14px;
  transition: all 150ms ease;
}

.text-input:focus {
  outline: none;
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(14, 116, 144, .1);
}

.textarea {
  resize: vertical;
  line-height: 1.6;
}

.choice-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.choice-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(241, 245, 249, .8);
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms ease;
}

.choice-pill:hover {
  background: rgba(226, 232, 240, 1);
}

.choice-pill input[type='checkbox'] {
  accent-color: #0f766e;
  width: 14px;
  height: 14px;
}

.image-preview-line {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(248, 250, 252, .8);
  color: #64748b;
  font-size: 12px;
  word-break: break-all;
}

.image-preview-line img {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  object-fit: cover;
  box-shadow: 0 1px 3px rgba(15, 23, 42, .08);
}

.modal-actions {
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid rgba(15, 23, 42, .08);
}

.action-button,
.ghost-button {
  border: none;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 150ms ease;
  white-space: nowrap;
}

.action-button {
  background: #0f766e;
  color: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, .08);
}

.ghost-button {
  background: rgba(14, 116, 144, .08);
  color: #0f766e;
}

.action-button:disabled,
.ghost-button:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.status-card {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  padding: 16px;
  background: rgba(241, 245, 249, .6);
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.status-card.error {
  background: rgba(254, 226, 226, .8);
  color: #b91c1c;
}

@media (max-width: 980px) {
  .snapshot-form-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
