<template>
  <div v-show="!collapsed" class="starters">
    <button v-for="item in starters" :key="item" type="button" @click="$emit('preset', item)">{{ item }}</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  starters: string[]
  collapsed: boolean
}>()

defineEmits<{
  preset: [text: string]
}>()
</script>

<style scoped>
.starters {
  display: grid;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 12px 10px;
}

.starters button {
  width: 100%;
  border: 1px solid rgba(20, 184, 166, .18);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
  color: #334155;
  font: inherit;
  font-size: 12px;
  line-height: 1.35;
  text-align: left;
  cursor: pointer;
}

.starters button:hover { border-color: rgba(15, 118, 110, .35); background: rgba(240, 253, 250, .7); }
</style>
