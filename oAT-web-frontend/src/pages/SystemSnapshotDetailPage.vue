<template>
  <section class="snapshot-detail-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Snapshot Detail</div>
        <h1>{{ payload?.snapshot.title || '系统快照详情' }}</h1>
        <p class="subtext">{{ payload?.snapshot.describe || payload?.snapshot.subTitle || '暂无说明' }}</p>
      </div>
      <div class="header-actions">
        <RouterLink class="ghost-button" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`">
          查看链路图
        </RouterLink>
        <RouterLink
          class="ghost-button"
          :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/report`"
        >
          覆盖率报告
        </RouterLink>
        <button class="danger-button" type="button" @click="removeSnapshot">删除快照</button>
        <RouterLink class="secondary-link" :to="`/p/${projectId}/apps/${appId}/snapshots`">返回列表</RouterLink>
      </div>
    </div>

    <div v-if="loading" class="status-card">正在加载系统快照详情...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="hero-card" :class="{ 'has-topic-image': payload.snapshot.topicImage }">
        <img v-if="payload.snapshot.topicImage" class="topic-image" :src="payload.snapshot.topicImage" alt="topic" />
        <div class="hero-main">
          <div class="hero-topline">
            <div class="tag-row snapshot-meta-strip">
              <span v-if="payload.snapshot.versionNumber || payload.snapshot.version" class="meta-pill" :title="payload.snapshot.versionNumber || payload.snapshot.version">版本 {{ payload.snapshot.versionNumber || payload.snapshot.version }}</span>
              <span v-if="payload.snapshot.repoBranch" class="meta-pill branch" :title="payload.snapshot.repoBranch">分支 {{ payload.snapshot.repoBranch }}</span>
              <span v-if="payload.snapshot.repoCommitId" class="meta-pill commit" :title="payload.snapshot.repoCommitId">Commit {{ abbreviateCommit(payload.snapshot.repoCommitId) }}</span>
              <span
                v-for="label in payload.labels"
                :key="label.name"
                class="tag"
                :style="{ '--tag-color': label.color || '#0f766e' }"
                :title="`标签：${label.name}`"
              >
                {{ label.name }}
              </span>
              <span v-if="!payload.labels.length" class="tag muted" title="尚未为该快照设置任何标签">暂无标签</span>
            </div>
            <span
              class="status-pill"
              :class="reportStatusTone(payload.snapshot.reportStatus)"
              :title="reportStatusHint(payload.snapshot.reportStatus)"
            >
              覆盖率报告 {{ reportStatusText(payload.snapshot.reportStatus) }}
            </span>
          </div>
          <div class="meta-grid">
            <div class="meta-item" title="系统快照数据最后更新时间">
              <span>更新时间</span>
              <strong>{{ payload.snapshot.versionLastUpdateText || '-' }}</strong>
            </div>
            <div
              class="meta-item"
              :title="principalMembers.length ? `负责人：${principalMembers.map(m => m.memberName || m.memberId).join('、')}` : '暂无负责人，可在下方负责人字段中选择'"
            >
              <span>负责人</span>
              <strong>{{ payload.selectedPrincipalIds.length || 0 }}</strong>
            </div>
            <div class="meta-item" :title="`已关联 ${payload.usecases.length} 个用例`">
              <span>关联用例</span>
              <strong>{{ payload.usecases.length }}</strong>
            </div>
          </div>
        </div>
      </div>

      <div class="detail-grid">
        <section class="panel">
          <div class="card-title panel-heading">
            <div>
              <h2>基本信息</h2>
              <p>聚焦标题、版本、负责人和标签，其他信息收敛在下方卡片中。</p>
            </div>
            <div class="header-actions compact-actions">
              <button class="ghost-button primary-action" type="button" @click="saveBasic">保存基本信息</button>
              <button class="ghost-button" type="button" @click="openUsecasePicker">更新关联用例</button>
            </div>
          </div>
          <div class="form-grid">
            <label class="field">
              <span>标题</span>
              <input v-model="form.title" class="text-input" type="text" />
            </label>
            <label class="field field-wide">
              <span>描述</span>
              <textarea v-model="form.describe" class="text-area" rows="4"></textarea>
            </label>
            <label class="field">
              <span>标签</span>
              <select v-model="form.labels" class="select" multiple>
                <option v-for="label in payload.labels" :key="label.name" :value="label.name">
                  {{ label.name }}
                </option>
              </select>
            </label>
            <label class="field">
              <span>负责人</span>
              <select v-model="form.principals" class="select" multiple>
                <option v-for="member in payload.members" :key="member.memberId" :value="member.memberId">
                  {{ member.memberName || member.memberId }}
                </option>
              </select>
            </label>
          </div>
          <div class="info-list">
            <div class="info-item" :title="payload.snapshot.subTitle || '暂无副标题'">
              <span>副标题</span>
              <strong>{{ payload.snapshot.subTitle || '-' }}</strong>
            </div>
            <div class="info-item" title="系统快照数据最后更新时间">
              <span>更新时间</span>
              <strong>{{ payload.snapshot.versionLastUpdateText || '-' }}</strong>
            </div>
            <div class="info-item" :title="reportStatusHint(payload.snapshot.reportStatus)">
              <span>报告状态</span>
              <strong>{{ reportStatusText(payload.snapshot.reportStatus) }}</strong>
            </div>
          </div>
          <div class="action-row">
            <RouterLink class="inline-link" :to="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`">
              打开完整链路图
            </RouterLink>
          </div>
          <div class="status-callout" :class="reportStatusTone(payload.snapshot.reportStatus)">
            <strong>覆盖率状态：{{ reportStatusText(payload.snapshot.reportStatus) }}</strong>
            <span>{{ reportStatusHint(payload.snapshot.reportStatus) }}</span>
          </div>

          <div class="subsection usecase-section">
            <div class="subsection-title">
              <div>
                <h3>关联用例</h3>
                <p>长列表在卡片内滚动，保持编辑区和链路入口在同一屏可见。</p>
              </div>
              <button class="ghost-button small-button" type="button" @click="openUsecasePicker">调整用例</button>
            </div>
            <UsecasePicker
              v-model:open="usecasePickerOpen"
              title="更新关联用例"
              description="从项目用例中选择当前系统快照需要关联的条目。"
              :usecases="payload.allUsecases"
              :selected-ids="payload.usecases.map((usecase) => usecase.id)"
              :busy="loading"
              @submit="bindUsecases"
            />
            <div v-if="!payload.usecases.length" class="empty-card compact">暂无关联用例</div>
            <div v-else class="usecase-list">
              <RouterLink
                v-for="usecase in payload.usecases"
                :key="usecase.id"
                class="usecase-card"
                :to="`/p/${projectId}/usecases/${usecase.id}`"
              >
                <strong>{{ usecase.title }}</strong>
                <span>{{ usecase.updateTimeText || '-' }}</span>
              </RouterLink>
            </div>
          </div>

          <div class="subsection graph-preview-section graph-embed-section">
            <GraphView
              :eyebrow="'Snapshot Graph'"
              :fallback-title="'系统快照链路图'"
              :back-route="`/p/${projectId}/apps/${appId}/snapshots/${snapshotId}/graph`"
              :back-label="'查看完整图谱'"
              :loading="graphPreviewLoading"
              :loading-text="'正在加载链路图...'"
              :graph="graphPreview"
              :arrow-marker-id="'graph-preview-system'"
              compact
            />
            <div v-if="!graphPreviewLoading && !graphPreview" class="empty-card compact">暂无链路图数据</div>
          </div>
        </section>

        <aside class="side-stack" aria-label="系统快照侧栏信息">
          <section class="panel side-panel dynamic-panel">
            <div class="card-title panel-heading">
              <div>
                <h2>动态记录</h2>
                <p>评论和变更记录固定在侧栏内浏览。</p>
              </div>
            </div>
            <form class="comment-form" @submit.prevent="addComment">
              <textarea
                v-model="commentText"
                class="text-area"
                rows="3"
                placeholder="输入评论内容"
              ></textarea>
              <button class="ghost-button" type="submit">添加评论</button>
            </form>
            <div v-if="!payload.dynamics.length" class="empty-card compact">暂无动态记录</div>
            <div v-else class="dynamic-list">
              <article v-for="item in payload.dynamics" :key="`${item.type}-${item.date}-${item.describe}`" class="dynamic-card">
                <div class="dynamic-top">
                  <strong>{{ item.title || '未知用户' }}</strong>
                  <span>{{ item.time || '-' }}</span>
                </div>
                <div class="dynamic-type">{{ item.type || '-' }}</div>
                <p>{{ item.describe || '-' }}</p>
                <button
                  v-if="item.type === 'comment' && item.describe && item.date"
                  class="danger-link"
                  type="button"
                  @click="removeComment(item.describe, item.date)"
                >
                  删除评论
                </button>
              </article>
            </div>
          </section>

          <section class="panel side-panel">
            <div class="card-title panel-heading">
              <div>
                <h2>负责人</h2>
                <p>当前快照维护人。</p>
              </div>
            </div>
            <div class="member-list">
              <article
                v-for="member in principalMembers"
                :key="member.memberId"
                class="member-card"
              >
                <strong>{{ member.memberName || member.memberId }}</strong>
                <span>{{ member.role || '-' }}</span>
              </article>
              <div v-if="!principalMembers.length" class="empty-card compact">暂无负责人</div>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import UsecasePicker from '@/components/usecase/UsecasePicker.vue'
import GraphView from '@/components/snapshot/GraphView.vue'
import { useDialog } from '@/composables/useDialog'
import { useProjectStore } from '@/stores/project'
import { reportStatusText, reportStatusTone } from '@/utils/snapshot'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const dialog = useDialog()
const projectId = computed(() => String(route.params.projectId || ''))
const appId = computed(() => String(route.params.appId || ''))
const snapshotId = computed(() => String(route.params.snapshotId || ''))
const storeKey = computed(() => `${projectId.value}:${snapshotId.value}`)
const payload = computed(() => projectStore.systemSnapshotDetailByKey[storeKey.value])
const graphPreview = computed(() => projectStore.systemSnapshotGraphByKey[storeKey.value])
const principalMembers = computed(() =>
  (payload.value?.members || []).filter((member) => payload.value?.selectedPrincipalIds.includes(member.memberId)),
)
const loading = ref(false)
const error = ref('')
const commentText = ref('')
const usecasePickerOpen = ref(false)
const graphPreviewLoading = ref(false)

const form = ref({
  title: '',
  describe: '',
  labels: [] as string[],
  principals: [] as string[],
})

function reportStatusHint(status?: number) {
  switch (status) {
    case 1:
      return '报告任务正在处理，刷新当前页面或进入报告页可查看最新结果。'
    case 2:
      return '覆盖率报告已完成，可直接进入报告页查看类级和源码级详情。'
    case 3:
      return '最近一次报告生成失败，可在报告页重新触发计算。'
    default:
      return '当前还没有生成覆盖率报告，可进入报告页发起首次计算。'
  }
}

function abbreviateCommit(commitId?: string) {
  if (!commitId) return ''
  return commitId.length > 12 ? commitId.slice(0, 12) : commitId
}

async function load() {
  if (!projectId.value || !appId.value || !snapshotId.value) {
    error.value = '缺少必要参数'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadSystemSnapshotDetail(projectId.value, appId.value, snapshotId.value)
    if (payload.value) {
      form.value = {
        title: payload.value.snapshot.title || '',
        describe: payload.value.snapshot.describe || '',
        labels: [...payload.value.selectedLabelNames],
        principals: [...payload.value.selectedPrincipalIds],
      }
    }
    graphPreviewLoading.value = true
    try {
      await projectStore.loadSystemSnapshotGraph(projectId.value, appId.value, snapshotId.value)
    } catch (graphErr) {
      console.error('Failed to load graph:', graphErr)
    } finally {
      graphPreviewLoading.value = false
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载系统快照详情失败'
  } finally {
    loading.value = false
  }
}

async function saveBasic() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateSystemSnapshotBasicInfo(projectId.value, appId.value, snapshotId.value, {
      title: form.value.title.trim(),
      describe: form.value.describe.trim(),
      labels: form.value.labels,
      principals: form.value.principals,
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存系统快照基本信息失败'
  } finally {
    loading.value = false
  }
}

function openUsecasePicker() {
  usecasePickerOpen.value = true
}

async function bindUsecases(usecaseIds: string[]) {
  loading.value = true
  error.value = ''
  try {
    await projectStore.updateSystemSnapshotUsecases(projectId.value, appId.value, snapshotId.value, usecaseIds)
    usecasePickerOpen.value = false
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '更新系统快照关联用例失败'
  } finally {
    loading.value = false
  }
}

async function addComment() {
  if (!commentText.value.trim()) {
    error.value = '请输入评论内容'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.appendSystemSnapshotComment(projectId.value, appId.value, snapshotId.value, commentText.value.trim())
    commentText.value = ''
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '添加评论失败'
  } finally {
    loading.value = false
  }
}

async function removeComment(content: string, dateTime: string) {
  const confirmed = await dialog.confirm({
    title: '删除评论',
    message: '确认删除这条评论？删除后系统快照动态中将不再显示该记录。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeSystemSnapshotComment(projectId.value, appId.value, snapshotId.value, {
      content,
      dateTime: String(dateTime).slice(0, 19).replace('T', ' '),
    })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除评论失败'
  } finally {
    loading.value = false
  }
}

async function removeSnapshot() {
  const confirmed = await dialog.confirm({
    title: '删除系统快照',
    message: '确认删除该系统快照？覆盖率报告、链路图、评论和关联用例将不可恢复。',
    confirmText: '确认删除',
    tone: 'danger',
  })
  if (!confirmed) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.removeSystemSnapshot(projectId.value, appId.value, snapshotId.value)
    await router.push(`/p/${projectId.value}/apps/${appId.value}/snapshots`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除系统快照失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped src="@/features/snapshot/styles/system-snapshot-detail-page.css"></style>
