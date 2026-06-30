<template>
  <div class="overview-grid">
    <div class="overview-card">
      <span>在线探针</span>
      <strong>{{ probeCount }}</strong>
      <small>当前项目已连接实例</small>
    </div>
    <div class="overview-card">
      <span>项目应用</span>
      <strong>{{ appCount }}</strong>
      <small>已纳入监控范围</small>
    </div>
    <div class="overview-card">
      <span>实时请求</span>
      <strong>{{ traceCount }}</strong>
      <small>当前过滤窗口内</small>
    </div>
    <div class="overview-card">
      <span>最后接收</span>
      <strong class="time-value">{{ lastReceiveText }}</strong>
      <small>监控数据到达时间</small>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  probeCount: number
  appCount: number
  traceCount: number
  lastReceiveText: string
}>()
</script>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.overview-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 4px 12px;
  padding: 12px 16px;
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 16px;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, .12), transparent 50%),
    #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
  transition: all 200ms ease;
}

.overview-card:hover {
  box-shadow: 0 10px 28px rgba(15, 23, 42, .12);
  transform: translateY(-2px);
}

.overview-card span {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
}

.overview-card strong {
  grid-row: 1 / span 2;
  grid-column: 2;
  display: block;
  margin-top: 0;
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
  color: #0f766e;
}

.overview-card small {
  display: block;
  margin-top: 0;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 500;
}

.overview-card .time-value {
  font-size: 20px;
}

@media (max-width: 980px) {
  .overview-grid {
    grid-template-columns: 1fr !important;
  }

  .overview-card {
    padding: 8px 12px;
  }
}
</style>
