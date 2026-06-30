<template>
  <section class="panel">
    <div class="panel-head">
      <div class="panel-title-block">
        <h2>版本入口</h2>
        <p>选择版本查看覆盖率；仅当前版本允许生成版本全量或增量报告。</p>
      </div>
      <div class="panel-actions">
        <span class="count-badge">{{ filteredCount }} / {{ visibleCount }}</span>
        <button class="ghost-button small-button" type="button" :disabled="refreshing || !appId" @click="$emit('refresh')">
          {{ refreshing ? '刷新中...' : '刷新版本' }}
        </button>
      </div>
    </div>
    <div class="card-grid version-card-grid">
      <article v-for="version in versions" :key="version.id" class="card">
        <div class="card-main">
          <div class="card-title-block">
            <div class="card-top">
              <strong>{{ version.versionNumber }}</strong>
              <span v-if="version.current" class="tag">当前版本</span>
            </div>
            <p class="subtext">{{ version.describe || '暂无描述' }}</p>
          </div>
          <div class="meta-stack">
            <span class="branch-name">{{ version.repoBranch || '-' }}</span>
            <span class="commit-id" :title="commitTooltip(version.repoCommitId)">{{ version.repoCommitId || '-' }}</span>
          </div>
          <div class="coverage-data-stack">
            <span
              v-for="status in coverageDataStatus(version)"
              :key="status.key"
              :class="['data-status-chip', status.tone]"
            >
              {{ status.label }}
            </span>
          </div>
          <span
            class="time-text time-tooltip"
            :class="{ 'has-tooltip': timeTooltip(version) }"
            :aria-label="timeTooltip(version) || undefined"
            :tabindex="timeTooltip(version) ? 0 : undefined"
            @mouseenter="$emit('show-time-tooltip', $event, timeTooltip(version))"
            @mouseleave="$emit('hide-time-tooltip')"
            @focus="$emit('show-time-tooltip', $event, timeTooltip(version))"
            @blur="$emit('hide-time-tooltip')"
          >{{ displayTime(version) }}</span>
        </div>
        <div class="action-row">
          <RouterLink
            class="table-link"
            :to="{
              name: 'coverage-overview',
              params: { projectId, appId },
              query: { versionNumber: version.versionNumber, commitId: version.repoCommitId || undefined },
            }"
          >
            查看覆盖率概览
          </RouterLink>
          <button
            class="ghost-button small-button generate-button"
            type="button"
            :disabled="!canGenerateVersionType(version, 'full') || Boolean(generatingVersionKey)"
            :title="generateDisabledTitle(version, 'full')"
            @click="$emit('generate-full', version)"
          >
            {{ isGeneratingVersion(version, 'full') ? '全量生成中...' : '生成版本全量' }}
          </button>
          <button
            class="ghost-button small-button generate-button frontend-generate-button"
            type="button"
            :disabled="!canGenerateVersionType(version, 'frontend') || Boolean(generatingVersionKey)"
            :title="generateDisabledTitle(version, 'frontend')"
            @click="$emit('generate-frontend', version)"
          >
            {{ isGeneratingVersion(version, 'frontend') ? '前端生成中...' : '生成前端报告' }}
          </button>
          <button
            v-for="coverageSource in universalGenerateSources"
            :key="coverageSource.type"
            class="ghost-button small-button generate-button universal-generate-button"
            type="button"
            :disabled="!canGenerateVersionType(version, coverageSource.type) || Boolean(generatingVersionKey)"
            :title="generateDisabledTitle(version, coverageSource.type)"
            @click="$emit('generate-universal', version, coverageSource.type)"
          >
            {{ isGeneratingVersion(version, coverageSource.type) ? `${coverageSource.shortLabel}生成中...` : `生成${coverageSource.shortLabel}报告` }}
          </button>
          <button
            class="ghost-button small-button generate-button"
            type="button"
            :disabled="!canGenerateVersionType(version, 'incremental') || Boolean(generatingVersionKey)"
            :title="generateDisabledTitle(version, 'incremental')"
            @click="$emit('open-incremental', version)"
          >
            {{ isGeneratingVersion(version, 'incremental') ? '增量生成中...' : '生成版本增量' }}
          </button>
        </div>
      </article>
    </div>
    <div v-if="!filteredCount" class="empty-card compact">暂无匹配版本</div>
    <AppPagination
      v-if="filteredCount > 0"
      :page="page"
      :page-size="pageSize"
      :total="filteredCount"
      item-name="版本"
      :page-sizes="[6, 12, 24, 48]"
      @update:page="$emit('update:page', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
    />
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppPagination from '@/components/AppPagination.vue'
import type { VersionItemSummary } from '@/api/types'

export type CoverageHubUniversalGenerateType = 'GO' | 'PYTHON' | 'CPP'
export type CoverageHubVersionGenerateType = 'full' | 'incremental' | 'frontend' | CoverageHubUniversalGenerateType
export type CoverageHubUniversalGenerateSource = {
  type: CoverageHubUniversalGenerateType
  shortLabel: string
}
export type CoverageDataStatus = {
  key: string
  label: string
  tone: string
}

defineProps<{
  projectId: string
  appId: string
  versions: VersionItemSummary[]
  filteredCount: number
  visibleCount: number
  refreshing: boolean
  generatingVersionKey: string
  page: number
  pageSize: number
  universalGenerateSources: readonly CoverageHubUniversalGenerateSource[]
  coverageDataStatus: (version: VersionItemSummary) => CoverageDataStatus[]
  timeTooltip: (item: { createTimeText?: string }) => string
  displayTime: (item: { createTimeRelativeText?: string; createTimeText?: string }) => string
  commitTooltip: (value?: string) => string
  canGenerateVersionType: (version: VersionItemSummary, type: CoverageHubVersionGenerateType) => boolean
  isGeneratingVersion: (version: VersionItemSummary, type: CoverageHubVersionGenerateType) => boolean
  generateDisabledTitle: (version: VersionItemSummary, type: CoverageHubVersionGenerateType) => string
}>()

defineEmits<{
  (event: 'refresh'): void
  (event: 'update:page', page: number): void
  (event: 'update:pageSize', pageSize: number): void
  (event: 'show-time-tooltip', payload: MouseEvent | FocusEvent, text: string): void
  (event: 'hide-time-tooltip'): void
  (event: 'generate-full', version: VersionItemSummary): void
  (event: 'generate-frontend', version: VersionItemSummary): void
  (event: 'generate-universal', version: VersionItemSummary, sourceType: CoverageHubUniversalGenerateType): void
  (event: 'open-incremental', version: VersionItemSummary): void
}>()
</script>

<style scoped>
.panel,
.card,
.empty-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.panel {
  margin-top: 12px;
}

.panel-head,
.panel-actions,
.action-row {
  display: flex;
  gap: 12px;
}

.panel-head {
  justify-content: space-between;
  align-items: center;
}

.panel-title-block {
  display: grid;
  gap: 4px;
}

.panel-title-block h2,
.panel-title-block p {
  margin: 0;
}

.panel-title-block p,
.subtext {
  color: #64748b;
}

.panel-title-block p {
  font-size: 13px;
}

.panel-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.count-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
  font-weight: 800;
  white-space: nowrap;
}

.ghost-button {
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.06);
  cursor: pointer;
}

.ghost-button.small-button {
  min-height: 36px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 800;
}

.ghost-button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr;
  align-items: stretch;
  gap: 12px;
  max-height: min(520px, calc(100vh - 300px));
  overflow: auto;
  padding-right: 4px;
}

.card {
  display: grid;
  gap: 14px;
  transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease;
}

.card:hover {
  border-color: rgba(var(--oat-primary-rgb), 0.2);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.card-main {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(220px, 1fr) minmax(180px, 0.8fr) minmax(72px, auto);
  align-items: center;
  gap: 18px;
}

.card-title-block,
.meta-stack {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.card-title-block .subtext {
  margin: 0;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-stack {
  gap: 6px;
}

.branch-name {
  color: #64748b;
  font-weight: 700;
}

.commit-id {
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coverage-data-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.data-status-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.data-status-chip.ok {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}

.data-status-chip.info {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.data-status-chip.neutral {
  background: rgba(100, 116, 139, 0.12);
  color: #475569;
}

.data-status-chip.empty {
  background: rgba(148, 163, 184, 0.14);
  color: #64748b;
}

.time-text {
  justify-self: end;
  color: #64748b;
  white-space: nowrap;
}

.time-tooltip {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: max-content;
  min-height: 28px;
  padding: 4px 8px;
  border-radius: 999px;
  outline: none;
  cursor: default;
  transition: color .16s ease, background .16s ease, box-shadow .16s ease;
}

.time-tooltip.has-tooltip:hover,
.time-tooltip.has-tooltip:focus-visible {
  color: #0f766e;
  background: rgba(15, 118, 110, 0.08);
  box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.14);
}

.action-row {
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.table-link {
  color: #0f766e;
  font-weight: 700;
}

.generate-button {
  color: #0f766e;
}

.empty-card.compact {
  margin-top: 12px;
  padding: 14px;
  text-align: center;
  color: #64748b;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .card-main {
    grid-template-columns: 1fr;
  }

  .time-text {
    justify-self: start;
  }
}
</style>
