<template>
  <div v-for="chip in chips" :key="chip.id" class="context-chip" :title="`${chip.label}：${chip.value}`">
    <span class="context-chip-label">{{ chip.label }}</span>
    <span class="context-chip-value">{{ chip.value }}</span>
    <button type="button" class="context-chip-remove" title="清除" aria-label="清除" @click="$emit('remove', chip.id)">
      <svg viewBox="0 0 24 24" width="12" height="12" aria-hidden="true">
        <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { AiFloatingContextChip, AiFloatingContextChipId } from '@/features/ai/types'

defineProps<{
  chips: AiFloatingContextChip[]
}>()

defineEmits<{
  remove: [id: AiFloatingContextChipId]
}>()
</script>

<style scoped>
.context-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  min-width: 0;
  border: 1px solid rgba(15, 118, 110, .22);
  border-radius: 999px;
  padding: 4px 5px 4px 10px;
  background: rgba(240, 253, 250, .92);
  color: #0f766e;
  font-size: 11.5px;
  line-height: 1.4;
}

.context-chip-label {
  flex: 0 0 auto;
  font-weight: 600;
  color: rgba(15, 118, 110, .72);
}

.context-chip-value {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-chip-remove {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(15, 118, 110, .1);
  color: rgba(15, 118, 110, .7);
  cursor: pointer;
  transition: background .16s ease, color .16s ease;
}

.context-chip-remove:hover {
  background: rgba(220, 38, 68, .14);
  color: rgba(220, 38, 68, .95);
}
</style>
