<template>
  <section class="not-found">
    <div class="not-found-card">
      <div class="eyebrow">404</div>
      <h1>页面不存在</h1>
      <p>当前新前端骨架只接入了项目列表和项目上下文页，其余业务页会逐步迁移。</p>
    </div>
  </section>
</template>

<style scoped>
.not-found {
  min-height: calc(100vh - 160px);
  display: grid;
  place-items: center;
}

.not-found-card {
  width: min(520px, 100%);
  padding: 30px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}
</style>
