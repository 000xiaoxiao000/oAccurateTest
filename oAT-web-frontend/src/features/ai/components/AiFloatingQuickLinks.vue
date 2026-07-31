<template>
  <div v-show="!collapsed" class="quick-links">
    <button v-for="link in links" :key="link.title + link.url" type="button" @click="$emit('open', link)">
      <span class="quick-link-icon">{{ quickLinkIcon(link.title) }}</span>
      <span class="quick-link-copy">
        <strong>{{ link.title }}</strong>
        <small>{{ link.description }}</small>
      </span>
    </button>
    <div v-if="!links.length" class="quick-links-empty">暂无快捷入口，请先在 AI 工作台或当前页面产生上下文。</div>
  </div>
</template>

<script setup lang="ts">
import type { AIQuickLink } from '@/api/types'

defineProps<{
  links: AIQuickLink[]
  collapsed: boolean
}>()

defineEmits<{
  open: [link: AIQuickLink]
}>()

function quickLinkIcon(title?: string) {
  const text = title || ''
  if (text.includes('覆盖率')) return '覆'
  if (text.includes('快照')) return '照'
  if (text.includes('监控') || text.includes('链路')) return '链'
  if (text.includes('AI')) return 'AI'
  if (text.includes('应用')) return '用'
  return '↗'
}
</script>

<style scoped>
.quick-links {
  display: grid;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 12px 10px;
}

.quick-links button {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(20, 184, 166, .18);
  border-radius: 12px;
  padding: 8px 10px;
  background: rgba(240, 253, 250, .55);
  color: #0f766e;
  text-align: left;
  cursor: pointer;
}

.quick-links button:hover { background: rgba(204, 251, 241, .75); border-color: rgba(15, 118, 110, .34); }
.quick-link-icon { flex: 0 0 22px; display: grid; place-items: center; width: 22px; height: 22px; border-radius: 8px; background: rgba(20, 184, 166, .12); font-size: 10px; font-weight: 800; }
.quick-link-copy { display: grid; gap: 2px; min-width: 0; }
.quick-links strong { color: #111827; font-size: 12px; line-height: 1.25; }
.quick-links small { overflow: hidden; color: #64748b; font-size: 11px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.quick-links-empty { padding: 10px; border: 1px dashed rgba(20, 184, 166, .26); border-radius: 10px; color: #64748b; font-size: 11px; line-height: 1.45; }
</style>
