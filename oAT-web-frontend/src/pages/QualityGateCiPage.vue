<template>
  <section class="quality-gate-ci-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Quality Gate CI</div>
        <h1>质量门禁 CI 集成</h1>
        <p class="subtext">校验覆盖率质量门禁，并生成可复制到流水线的 GitHub Actions、Jenkins 和 curl 调用。</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/coverage`">覆盖率中心</RouterLink>
        <button class="submit-button" type="button" :disabled="checking" @click="checkGate">
          {{ checking ? '检查中...' : '检查门禁' }}
        </button>
      </div>
    </div>

    <div class="gate-layout">
      <section class="panel config-panel">
        <div class="panel-head">
          <div>
            <h2>门禁参数</h2>
            <p>按覆盖率报告 ID，或按应用版本元数据查询最新匹配报告。</p>
          </div>
        </div>

        <div class="mode-tabs" role="tablist" aria-label="质量门禁查询模式">
          <button type="button" :class="{ active: mode === 'app' }" @click="mode = 'app'">按应用</button>
          <button type="button" :class="{ active: mode === 'report' }" @click="mode = 'report'">按报告 ID</button>
        </div>

        <div class="form-grid">
          <label v-if="mode === 'app'" class="field">
            <span>应用</span>
            <select v-model="form.appId" class="text-input">
              <option value="">请选择应用</option>
              <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.name || app.srcName || app.id }}</option>
            </select>
          </label>
          <label v-else class="field">
            <span>报告 ID</span>
            <input v-model.trim="form.reportId" class="text-input" type="text" placeholder="coverage report id" />
          </label>
          <label class="field">
            <span>最低行覆盖率</span>
            <input v-model.number="form.minLineCoverageRate" class="text-input" type="number" min="0" max="100" step="0.1" />
          </label>
          <template v-if="mode === 'app'">
            <label class="field">
              <span>版本</span>
              <input v-model.trim="form.versionNumber" class="text-input" type="text" placeholder="VERSION_NUMBER" />
            </label>
            <label class="field">
              <span>Commit</span>
              <input v-model.trim="form.commitId" class="text-input" type="text" placeholder="COMMIT_ID" />
            </label>
            <label class="field">
              <span>Build</span>
              <input v-model.trim="form.buildId" class="text-input" type="text" placeholder="BUILD_ID" />
            </label>
            <label class="field">
              <span>测试阶段</span>
              <input v-model.trim="form.testStage" class="text-input" type="text" placeholder="regression" />
            </label>
            <label class="field">
              <span>报告类型</span>
              <select v-model.number="form.reportType" class="text-input">
                <option :value="undefined">自动</option>
                <option :value="1">全量</option>
                <option :value="2">增量</option>
              </select>
            </label>
          </template>
        </div>

        <div v-if="error" class="status-card error">{{ error }}</div>
        <div v-if="gateResult" :class="['gate-result', gateResult.passed ? 'passed' : 'failed']">
          <span>{{ gateResult.passed ? 'PASS' : 'FAIL' }}</span>
          <strong>{{ formatRate(gateResult.actualLineCoverageRate) }}</strong>
          <small>阈值 {{ formatRate(gateResult.minLineCoverageRate) }} · 未覆盖行 {{ gateResult.uncoveredLines ?? 0 }} · 风险单元 {{ gateResult.riskyUnits ?? 0 }}</small>
        </div>
        <div v-if="gateResult?.reasons?.length" class="reason-list">
          <div v-for="reason in gateResult.reasons" :key="reason">{{ reason }}</div>
        </div>
      </section>

      <section class="panel ci-panel">
        <div class="panel-head">
          <div>
            <h2>CI 配置</h2>
            <p>示例使用 Cookie 鉴权；如果你的部署使用 Token，可替换 Header。</p>
          </div>
        </div>

        <div class="snippet-tabs">
          <button v-for="item in snippetTabs" :key="item.key" type="button" :class="{ active: snippetTab === item.key }" @click="snippetTab = item.key">
            {{ item.label }}
          </button>
        </div>

        <div class="snippet-actions">
          <span>{{ endpointPath }}</span>
          <button class="mini-button" type="button" @click="copySnippet">复制</button>
        </div>
        <pre class="code-block"><code>{{ activeSnippet }}</code></pre>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { fetchAppQualityGate, fetchQualityGate } from '@/features/coverage/api/core'
import type { AppSummary } from '@/api/types'
import type { QualityGateResult } from '@/entities/coverage/model'
import { useProjectStore } from '@/stores/project'

type Mode = 'app' | 'report'
type SnippetTab = 'curl' | 'github' | 'jenkins'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const context = computed(() => projectStore.contextByProjectId[projectId.value])
const apps = computed<AppSummary[]>(() => context.value?.apps || [])
const mode = ref<Mode>(String(route.query.reportId || '') ? 'report' : 'app')
const snippetTab = ref<SnippetTab>('github')
const checking = ref(false)
const error = ref('')
const gateResult = ref<QualityGateResult | null>(null)
const form = reactive({
  appId: String(route.query.appId || ''),
  reportId: String(route.query.reportId || ''),
  versionNumber: String(route.query.versionNumber || ''),
  commitId: String(route.query.commitId || ''),
  buildId: String(route.query.buildId || ''),
  testStage: String(route.query.testStage || ''),
  reportType: undefined as number | undefined,
  minLineCoverageRate: Number(route.query.minLineCoverageRate || 80),
})

const snippetTabs: Array<{ key: SnippetTab; label: string }> = [
  { key: 'github', label: 'GitHub Actions' },
  { key: 'jenkins', label: 'Jenkins' },
  { key: 'curl', label: 'curl' },
]

const selectedApp = computed(() => apps.value.find((app) => app.id === form.appId))
const baseUrl = computed(() => typeof window === 'undefined' ? '${OAT_BASE_URL}' : window.location.origin)
const endpointPath = computed(() => mode.value === 'report'
  ? `/api/v2/coverage/reports/${encodeURIComponent(form.reportId || '${REPORT_ID}')}/quality-gate`
  : `/api/v2/coverage/apps/${encodeURIComponent(form.appId || '${APP_ID}')}/quality-gate`)
const endpointUrl = computed(() => `${baseUrl.value}${endpointPath.value}?${ciQuery.value}`)
const ciQuery = computed(() => {
  const query = new URLSearchParams({ projectId: projectId.value || '${PROJECT_ID}', minLineCoverageRate: String(form.minLineCoverageRate || 80) })
  if (mode.value === 'app') {
    if (form.versionNumber) query.set('versionNumber', form.versionNumber)
    else query.set('versionNumber', '${VERSION_NUMBER}')
    if (form.commitId) query.set('commitId', form.commitId)
    else query.set('commitId', '${COMMIT_ID}')
    if (form.buildId) query.set('buildId', form.buildId)
    if (form.testStage) query.set('testStage', form.testStage)
    if (form.reportType !== undefined) query.set('reportType', String(form.reportType))
  }
  return query.toString()
})

const curlSnippet = computed(() => [
  'response=$(curl -fsS \\',
  '  -H "Cookie: ${OAT_COOKIE}" \\',
  `  "${endpointUrl.value}")`,
  'echo "$response"',
  'passed=$(echo "$response" | jq -r \'.data.passed\')',
  'test "$passed" = "true"',
].join('\n'))

const githubSnippet = computed(() => `jobs:
  oat-quality-gate:
    runs-on: ubuntu-latest
    steps:
      - name: Check oAT quality gate
        env:
          OAT_COOKIE: \${{ secrets.OAT_COOKIE }}
          PROJECT_ID: ${projectId.value || '${{ vars.OAT_PROJECT_ID }}'}
          APP_ID: ${form.appId || '${{ vars.OAT_APP_ID }}'}
          VERSION_NUMBER: \${{ github.ref_name }}
          COMMIT_ID: \${{ github.sha }}
        run: |
${indent(curlSnippet.value, 10)}`)

const jenkinsSnippet = computed(() => `stage('oAT Quality Gate') {
  steps {
    withCredentials([string(credentialsId: 'oat-cookie', variable: 'OAT_COOKIE')]) {
      sh '''
${indent(curlSnippet.value, 8)}
      '''
    }
  }
}`)

const activeSnippet = computed(() => {
  if (snippetTab.value === 'curl') return curlSnippet.value
  if (snippetTab.value === 'jenkins') return jenkinsSnippet.value
  return githubSnippet.value
})

watch(apps, (items) => {
  if (!form.appId && items.length) {
    form.appId = items[0].id
  }
}, { immediate: true })

onMounted(async () => {
  if (projectId.value) {
    await projectStore.loadProjectContext(projectId.value).catch(() => undefined)
  }
})

async function checkGate() {
  error.value = ''
  gateResult.value = null
  if (mode.value === 'report' && !form.reportId) {
    error.value = '请填写报告 ID'
    return
  }
  if (mode.value === 'app' && !form.appId) {
    error.value = '请选择应用'
    return
  }
  checking.value = true
  try {
    gateResult.value = mode.value === 'report'
      ? await fetchQualityGate(projectId.value, form.reportId, form.minLineCoverageRate)
      : await fetchAppQualityGate(projectId.value, form.appId, {
        versionNumber: emptyToUndefined(form.versionNumber),
        commitId: emptyToUndefined(form.commitId),
        buildId: emptyToUndefined(form.buildId),
        testStage: emptyToUndefined(form.testStage),
        reportType: form.reportType,
        minLineCoverageRate: form.minLineCoverageRate,
      })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '质量门禁检查失败'
  } finally {
    checking.value = false
  }
}

async function copySnippet() {
  await navigator.clipboard?.writeText(activeSnippet.value)
}

function formatRate(value?: number) {
  return value === undefined || value === null ? '-' : `${Number(value).toFixed(1)}%`
}

function emptyToUndefined(value: string) {
  return value.trim() || undefined
}

function indent(value: string, spaces: number) {
  const prefix = ' '.repeat(spaces)
  return value.split('\n').map((line) => `${prefix}${line}`).join('\n')
}
</script>

<style scoped>
.quality-gate-ci-page {
  display: grid;
  gap: 16px;
}

.gate-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.82fr) minmax(420px, 1.18fr);
  gap: 16px;
  align-items: start;
}

.panel {
  border: 1px solid rgba(15, 23, 42, .08);
  border-radius: 18px;
  background: #fff;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .08);
}

.panel-head,
.header-actions,
.snippet-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.panel-head p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.mode-tabs,
.snippet-tabs {
  display: flex;
  gap: 6px;
  margin: 14px 0;
  padding: 4px;
  border: 1px solid rgba(203, 213, 225, .7);
  border-radius: 12px;
  background: #f8fafc;
}

.mode-tabs button,
.snippet-tabs button {
  flex: 1;
  border: 0;
  border-radius: 9px;
  padding: 9px 12px;
  background: transparent;
  color: #475569;
  font-weight: 700;
  cursor: pointer;
}

.mode-tabs button.active,
.snippet-tabs button.active {
  background: #0f766e;
  color: #fff;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.text-input {
  width: 100%;
  border: 1px solid rgba(203, 213, 225, .8);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fbfdfe;
  color: #0f172a;
}

.submit-button,
.ghost-button,
.mini-button {
  border: 0;
  border-radius: 999px;
  padding: 10px 16px;
  background: #0f766e;
  color: #fff;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}

.ghost-button,
.mini-button {
  background: rgba(15, 118, 110, .09);
  color: #0f766e;
}

.status-card,
.gate-result,
.reason-list {
  margin-top: 14px;
}

.status-card {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  color: #64748b;
}

.status-card.error {
  background: #fee2e2;
  color: #991b1b;
}

.gate-result {
  display: grid;
  gap: 4px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, .08);
}

.gate-result span {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .08em;
}

.gate-result strong {
  font-size: 32px;
}

.gate-result.passed {
  background: rgba(20, 184, 166, .1);
  color: #0f766e;
}

.gate-result.failed {
  background: rgba(239, 68, 68, .1);
  color: #b91c1c;
}

.reason-list {
  display: grid;
  gap: 6px;
}

.reason-list div {
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}

.snippet-actions {
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
}

.snippet-actions span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.code-block {
  min-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 16px;
  border-radius: 14px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 980px) {
  .gate-layout,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
