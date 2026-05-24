<template>
  <section class="search-center">
    <div class="page-header">
      <div>
        <div class="eyebrow">Search Center</div>
        <h1>搜索中心</h1>
        <p class="subtext">输入关键词搜索系统快照、SQL、远程调用，或按“数据库名 表名”查看表结构关联快照。</p>
      </div>
    </div>

    <form class="search-card" @submit.prevent="submitSearch">
      <div class="mode-row" role="tablist" aria-label="搜索类型">
        <button type="button" :class="['tab-button', tab === 'keyword' && 'active']" @click="switchTab('keyword')">用例</button>
        <button type="button" :class="['tab-button', tab === 'table' && 'active']" @click="switchTab('table')">表结构图</button>
      </div>

      <div class="action-input">
        <input
          v-model.trim="searchText"
          class="text-input"
          type="text"
          :maxlength="tab === 'table' ? 30 : 120"
          :placeholder="tab === 'table' ? '格式：数据库名 表名' : '搜索系统快照、SQL 或远程调用...'"
          @keydown.enter.prevent="submitSearch"
        />
        <select v-model="tab" class="mode-select" @change="syncTextFromMode">
          <option value="keyword">用例</option>
          <option value="table">表结构图</option>
        </select>
        <button class="primary-button" type="submit" :disabled="activeLoading">
          {{ activeLoading ? '搜索中...' : '搜索' }}
        </button>
      </div>
      <p class="search-hint">{{ tab === 'table' ? '表结构搜索与老前端一致，输入示例：order_center t_order。' : '关键词会匹配快照标题、描述、SQL 内容和远程调用内容。' }}</p>
    </form>

    <div v-if="error" class="status-card error">{{ error }}</div>

    <section v-if="tab === 'keyword'" class="panel result-panel">
      <div class="panel-head">
        <h2>关键词结果</h2>
        <span v-if="keywordResults">为您找到：{{ keywordResults.total }} 条结果</span>
        <span v-else>等待输入</span>
      </div>
      <div v-if="!keywordSearched" class="empty-state">
        <strong>输入关键词搜索系统快照、SQL 或远程调用</strong>
        <span>搜索结果会展示缩略图、标题高亮片段、命中内容和快照入口。</span>
      </div>
      <div v-else-if="!keywordResults?.results?.length" class="empty-state">
        <strong>没有找到匹配结果</strong>
        <span>可以换一个关键词，或确认快照索引是否已经更新。</span>
      </div>
      <div v-else class="result-list">
        <RouterLink
          v-for="item in keywordResults.results"
          :key="item.id"
          class="result-card"
          :to="item.targetPath"
          target="_blank"
        >
          <img class="result-image" :src="resultImage(item)" alt="" loading="lazy" />
          <div class="result-content">
            <div class="result-top">
              <strong class="result-link" v-html="item.title || '-'" />
              <span class="tag">{{ item.appId || 'snapshot' }}</span>
            </div>
            <p v-if="item.subTitle" class="subtext">{{ item.subTitle }}</p>
            <p class="result-desc" v-html="item.description || '暂无命中片段'"></p>
            <div class="meta-line">
              <span>{{ item.directoryPath || '未归档目录' }}</span>
              <span>{{ item.updateTimeText || '-' }}</span>
            </div>
          </div>
        </RouterLink>
      </div>
    </section>

    <template v-else>
      <div v-if="!tableSearched && !loadingTable" class="table-empty panel">
        <strong>输入“数据库名 表名”查看表结构关联快照</strong>
        <span>搜索后会展示表节点、快照节点、增删改查关系、节点详情和关系清单。</span>
      </div>
      <RelationBoard
        v-else
        eyebrow="Table Graph"
        title="表结构关系图"
        subtext="基于系统快照反查数据库表的使用链路，边关系会标记增、删、改、查。"
        :loading="loadingTable"
        :error="''"
        :nodes="tableNodes"
        :edges="tableEdges"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import RelationBoard from '@/components/map/RelationBoard.vue'
import { searchKeyword, searchTableGraph } from '@/api/bootstrap'
import type { SearchKeywordResult } from '@/api/types'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId || ''))
const tab = ref<'keyword' | 'table'>(route.query.tab === 'table' ? 'table' : 'keyword')
const keyword = ref(String(route.query.keyword || route.query.q || ''))
const tableKeyword = ref(String(route.query.tableKeyword || ''))
const database = ref(String(route.query.database || ''))
const table = ref(String(route.query.table || ''))
const searchText = ref(tab.value === 'table' ? initialTableText() : keyword.value)
const error = ref('')
const loadingKeyword = ref(false)
const loadingTable = ref(false)
const keywordSearched = ref(false)
const tableSearched = ref(false)
const keywordResults = ref<Awaited<ReturnType<typeof searchKeyword>> | null>(null)
const tableGraph = ref<Awaited<ReturnType<typeof searchTableGraph>> | null>(null)

const activeLoading = computed(() => tab.value === 'keyword' ? loadingKeyword.value : loadingTable.value)
const tableNodes = computed(() =>
  (tableGraph.value?.nodes || []).map((node) => ({
    id: node.id,
    label: node.label || node.id,
    type: node.type,
    description: node.type === 'table'
      ? '数据库表节点'
      : node.backgroundImage
        ? '命中的系统快照'
        : undefined,
    meta: node.backgroundImage ? [`缩略图: ${node.backgroundImage}`] : undefined,
  })),
)

const tableEdges = computed(() => {
  const labelMap = new Map(tableNodes.value.map((node) => [node.id, node.label]))
  return (tableGraph.value?.edges || []).map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.label || actionLabel(edge.action),
    action: edge.action,
    sourceLabel: labelMap.get(edge.source),
    targetLabel: labelMap.get(edge.target),
  }))
})

watch(tab, () => syncTextFromMode())

onMounted(() => {
  if (tab.value === 'keyword' && keyword.value) {
    submitKeywordSearch()
  } else if (tab.value === 'table' && (tableKeyword.value || (database.value && table.value))) {
    submitTableSearch()
  }
})

function initialTableText() {
  if (tableKeyword.value) return tableKeyword.value
  return [database.value, table.value].filter(Boolean).join(' ')
}

function syncTextFromMode() {
  searchText.value = tab.value === 'table' ? initialTableText() : keyword.value
  error.value = ''
}

function switchTab(next: 'keyword' | 'table') {
  tab.value = next
  syncTextFromMode()
}

function submitSearch() {
  if (tab.value === 'keyword') {
    keyword.value = searchText.value
    submitKeywordSearch()
    return
  }
  tableKeyword.value = searchText.value
  submitTableSearch()
}

async function submitKeywordSearch() {
  const value = keyword.value.trim()
  if (!value) {
    error.value = '请输入搜索关键字'
    return
  }
  loadingKeyword.value = true
  keywordSearched.value = true
  error.value = ''
  try {
    keywordResults.value = await searchKeyword(projectId.value, value)
    updateQuery({ keyword: value, tab: undefined, q: undefined, database: undefined, table: undefined, tableKeyword: undefined })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '搜索失败，请稍后重试'
  } finally {
    loadingKeyword.value = false
  }
}

async function submitTableSearch() {
  const parsed = parseTableKeyword(tableKeyword.value || searchText.value)
  if (!parsed) return
  loadingTable.value = true
  tableSearched.value = true
  error.value = ''
  database.value = parsed.database
  table.value = parsed.table
  tableKeyword.value = `${parsed.database} ${parsed.table}`
  searchText.value = tableKeyword.value
  try {
    tableGraph.value = await searchTableGraph(projectId.value, parsed.database, parsed.table)
    updateQuery({ tab: 'table', database: parsed.database, table: parsed.table, tableKeyword: undefined, keyword: undefined, q: undefined })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '表结构搜索失败，请稍后重试'
  } finally {
    loadingTable.value = false
  }
}

function parseTableKeyword(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    error.value = '请输入数据库名和表名'
    return null
  }
  if (trimmed.length > 30) {
    error.value = '搜索关键字不能超过30个字符'
    return null
  }
  const parts = trimmed.split(/\s+/)
  if (parts.length !== 2 || !parts[0] || !parts[1]) {
    error.value = '输入格式为:数据库名 表名'
    return null
  }
  return { database: parts[0], table: parts[1] }
}

function updateQuery(next: Record<string, string | undefined>) {
  const query = { ...route.query }
  Object.entries(next).forEach(([key, value]) => {
    if (value === undefined || value === '') delete query[key]
    else query[key] = value
  })
  router.replace({ query }).catch(() => undefined)
}

function resultImage(item: SearchKeywordResult) {
  return item.imagePath || (item.headImage ? `/r/${item.headImage}` : '/images/image.png')
}

function actionLabel(action?: string) {
  if (!action) return ''
  return action.split(',').map((item) => {
    const value = item.trim().toLowerCase()
    if (value === 'insert') return '增'
    if (value === 'delete') return '删'
    if (value === 'update') return '改'
    if (value === 'select') return '查'
    return item
  }).filter(Boolean).join(',')
}
</script>

<style scoped>
.search-center {
  min-width: 0;
}

.page-header,
.mode-row,
.action-input,
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
.meta-line,
.search-hint,
.empty-state span,
.table-empty span {
  color: #64748b;
}

.search-card,
.status-card,
.panel,
.empty-state,
.result-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.search-card {
  display: grid;
  gap: 14px;
}

.mode-row {
  flex-wrap: wrap;
}

.tab-button,
.primary-button,
.mode-select {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
}

.tab-button,
.mode-select {
  background: rgba(15, 23, 42, 0.08);
  color: #172033;
  font-weight: 700;
}

.tab-button.active,
.primary-button {
  background: #0f172a;
  color: #fff;
}

.action-input {
  align-items: stretch;
}

.text-input {
  width: 100%;
  min-width: 0;
  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 12px 14px;
  font-size: 15px;
}

.mode-select {
  flex: 0 0 128px;
}

.primary-button {
  flex: 0 0 auto;
  min-width: 92px;
}

.search-hint {
  margin: 0;
  font-size: 13px;
}

.status-card.error {
  color: #b91c1c;
  margin-top: 16px;
}

.result-panel,
.table-empty {
  margin-top: 18px;
}

.panel-head {
  margin-bottom: 16px;
}

.empty-state,
.table-empty {
  display: grid;
  gap: 6px;
  min-height: 116px;
  align-content: center;
}

.empty-state strong,
.table-empty strong {
  color: #172033;
}

.result-list {
  display: grid;
  gap: 12px;
}

.result-card {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 14px;
  color: inherit;
  transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease;
}

.result-card:hover {
  border-color: rgba(15, 118, 110, .28);
  box-shadow: 0 16px 34px rgba(15, 118, 110, .10);
  transform: translateY(-1px);
}

.result-image {
  width: 72px;
  height: 72px;
  border-radius: 12px;
  object-fit: cover;
  background: #eef4f5;
  border: 1px solid rgba(15, 23, 42, .08);
}

.result-content {
  min-width: 0;
}

.result-link {
  color: #0f766e;
  font-size: 16px;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.result-desc {
  margin: 8px 0;
  color: #475569;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.result-desc :deep(em),
.result-link :deep(em) {
  color: #b91c1c;
  font-style: normal;
  font-weight: 900;
}

.tag {
  flex: 0 0 auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .action-input,
  .result-card {
    grid-template-columns: 1fr;
  }

  .action-input {
    display: grid;
  }

  .mode-select,
  .primary-button {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
