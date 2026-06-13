<script setup lang="ts">
import type { PluginInfo } from '../types/traffic'

defineProps<{
  plugins: PluginInfo[]
  pluginsPath: string
}>()

const emit = defineEmits<{
  reload: []
}>()
</script>

<template>
  <section class="plugin-panel">
    <div class="panel-head">
      <div>
        <h2>插件扩展</h2>
        <p>{{ pluginsPath || '加载中...' }}</p>
      </div>
      <button class="small-btn primary" type="button" @click="emit('reload')">重新加载</button>
    </div>
    <div v-if="plugins.length === 0" class="empty-line">插件目录中暂无插件</div>
    <div v-for="plugin in plugins" :key="plugin.id" class="plugin-row">
      <div>
        <strong>{{ plugin.name }}</strong>
        <span>{{ plugin.version }}</span>
      </div>
      <span class="state" :class="{ off: !plugin.enabled, error: plugin.error }">
        {{ plugin.error ? '异常' : plugin.enabled ? '启用' : '停用' }}
      </span>
      <p v-if="plugin.description">{{ plugin.description }}</p>
      <p v-if="plugin.error" class="error-text">{{ plugin.error }}</p>
    </div>
  </section>
</template>

<style scoped>
.plugin-panel {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 14px 30px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

h2 {
  margin: 0;
  font-size: 15px;
  color: #262626;
}

p {
  margin: 4px 0 0;
  color: #8c8c8c;
  font-size: 12px;
  word-break: break-all;
}

.small-btn {
  height: 32px;
  border: 1px solid #667eea;
  border-radius: 4px;
  padding: 0 10px;
  background: #667eea;
  color: #fff;
  cursor: pointer;
}

.plugin-row {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px;
  margin-top: 8px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 12px;
}

.plugin-row strong {
  color: #262626;
  margin-right: 8px;
}

.plugin-row span {
  color: #8c8c8c;
  font-size: 12px;
}

.state {
  color: #52c41a;
}

.state.off {
  color: #8c8c8c;
}

.state.error,
.error-text {
  color: #ff4d4f;
}

.plugin-row p {
  grid-column: 1 / -1;
}

.empty-line {
  color: #8c8c8c;
  font-size: 13px;
}
</style>
