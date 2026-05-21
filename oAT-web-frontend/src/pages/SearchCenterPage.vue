<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Search Center</div>
        <h1>搜索中心</h1>
        <p class="subtext">统一替代旧搜索页，覆盖快照关键词检索与表关系检索。</p>
      </div>
    </div>

    <div class="tab-row">
      <button type="button" :class="['tab-button', tab === 'keyword' && 'active']" @click="tab = 'keyword'">关键词搜索</button>
      <button type="button" :class="['tab-button', tab === 'table' && 'active']" @click="tab = 'table'">表结构搜索</button>
    </div>

    <form v-if="tab === 'keyword'" class="editor-card" @submit.prevent="submitKeywordSearch">
      <label class="field">
        <span>关键词</span>
        <input v-model.trim="keyword" class="text-input" type="text" placeholder="输入标题、SQL、远程调用等关键词" />
      </label>
      <div class="editor-actions">
        <button class="primary-button" type="submit" :disabled="loadingKeyword">
          {{ loadingKeyword ? '搜索中...' : '开始搜索' }}
        </button>
      </div>
    </form>

    <form v-else class="editor-card" @submit.prevent="submitTableSearch">
      <div class="grid-two">
        <label class="field">
          <span>数据库</span>
          <input v-model.trim="database" class="text-input" type="text" placeholder="例如 order_center" />
        </label>
        <label class="field">
          <span>表名</span>
          <input v-model.trim="table" class="text-input" type="text" placeholder="例如 t_order" />
        </label>
      </div>
      <div class="editor-actions">
        <button class="primary-button" type="submit" :disabled="loadingTable">
          {{ loadingTable ? '检索中...' : '开始检索' }}
        </button>
      </div>
    </form>

    <div v-if="error" class="status-card error">{{ error }}</div>

    <section v-if="tab === 'keyword'" class="panel">
      <div class="panel-head">
        <h2>关键词结果</h2>
        <span>{{ keywordResults?.total || 0 }}</span>
      </div>
      <div v-if="!keywordResults?.results?.length" class="empty-card">暂无结果</div>
      <div v-else class="result-list">
        <article v-for="item in keywordResults.results" :key="item.id" class="result-card">
          <div class="result-top">
            <RouterLink class="result-link" :to="item.targetPath">{{ item.title }}</RouterLink>
            <span class="tag">{{ item.appId || '-' }}</span>
          </div>
          <p v-if="item.subTitle" class="subtext">{{ item.subTitle }}</p>
          <p class="result-desc" v-html="item.description || '-'"></p>
          <div class="meta-line">
            <span>{{ item.directoryPath || '未归档目录' }}</span>
            <span>{{ item.updateTimeText || '-' }}</span>
          </div>
        </article>
      </div>
    </section>

    <RelationBoard
      v-else
      eyebrow="Table Graph"
      title="表关系结果"
      subtext="基于系统快照反查数据库表的使用链路。"
      :loading="loadingTable"
      :error="''"
      :nodes="tableNodes"
      :edges="tableEdges"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import { searchKeyword, searchTableGraph } from '@/api/bootstrap'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId || ''))
const tab = ref<'keyword' | 'table'>(route.query.tab === 'table' ? 'table' : 'keyword')
const keyword = ref('')
const database = ref('')
const table = ref('')
const error = ref('')
const loadingKeyword = ref(false)
const loadingTable = ref(false)
const keywordResults = ref<Awaited<ReturnType<typeof searchKeyword>> | null>(null)
const tableGraph = ref<Awaited<ReturnType<typeof searchTableGraph>> | null>(null)

const tableNodes = computed(() =>
  (tableGraph.value?.nodes || []).map((node) => ({
    id: node.id,
    label: node.label || node.id,
    type: node.type,
    description: node.backgroundImage ? `缩略图 ${node.backgroundImage}` : undefined,
  })),
)

const tableEdges = computed(() => {
  const labelMap = new Map(tableNodes.value.map((node) => [node.id, node.label]))
  return (tableGraph.value?.edges || []).map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.label,
    action: edge.action,
    sourceLabel: labelMap.get(edge.source),
    targetLabel: labelMap.get(edge.target),
  }))
})

async function submitKeywordSearch() {
  if (!keyword.value) {
    error.value = '请输入关键词'
    return
  }
  loadingKeyword.value = true
  error.value = ''
  try {
    keywordResults.value = await searchKeyword(projectId.value, keyword.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '搜索失败'
  } finally {
    loadingKeyword.value = false
  }
}

async function submitTableSearch() {
  if (!database.value || !table.value) {
    error.value = '数据库和表名都不能为空'
    return
  }
  loadingTable.value = true
  error.value = ''
  try {
    tableGraph.value = await searchTableGraph(projectId.value, database.value, table.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '表关系检索失败'
  } finally {
    loadingTable.value = false
  }
}
</script>

<style scoped>
.page-header,
.tab-row,
.editor-actions,
.panel-head,
.result-top,
.meta-line {
  display: flex;
  gap: 12px;
}

.page-header,
.panel-head,
.result-top,
.meta-line {
  justify-content: space-between;
  align-items: center;
}

.page-header {
  margin-bottom: 20px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.subtext,
.field span,
.meta-line {
  color: #64748b;
}

.tab-row {
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.tab-button,
.primary-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
}

.tab-button {
  background: rgba(15, 23, 42, 0.08);
}

.tab-button.active,
.primary-button {
  background: #0f172a;
  color: #fff;
}

.editor-card,
.status-card,
.panel,
.empty-card,
.result-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
  margin-bottom: 16px;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
}

.text-input {
  width: 100%;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
}

.panel {
  margin-top: 18px;
}

.result-list {
  display: grid;
  gap: 12px;
}

.result-link {
  color: #0f766e;
  font-weight: 700;
}

.result-desc :deep(em) {
  color: #b91c1c;
  font-style: normal;
  font-weight: 700;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
