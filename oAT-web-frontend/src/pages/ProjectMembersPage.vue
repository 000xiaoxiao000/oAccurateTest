<template>
  <section>
    <div class="page-header">
      <div>
        <div class="eyebrow">Members</div>
        <h1>项目成员</h1>
      </div>
      <button class="action-button" type="button" @click="load">刷新</button>
    </div>

    <div v-if="loading" class="status-card">正在加载成员数据...</div>
    <div v-else-if="error" class="status-card error">{{ error }}</div>
    <template v-else-if="payload">
      <div class="toolbar">
        <select v-model="selectedUserId" class="select">
          <option value="">选择要添加的用户</option>
          <option v-for="user in payload.availableUsers" :key="user.id" :value="user.id">
            {{ user.name }}{{ user.email ? ` (${user.email})` : '' }}
          </option>
        </select>
        <button class="primary-button" type="button" :disabled="!selectedUserId" @click="addMember">
          添加成员
        </button>
      </div>

      <div class="table-card">
        <table class="table">
          <thead>
            <tr>
              <th>成员</th>
              <th>邮箱</th>
              <th>角色</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in payload.members" :key="member.id">
              <td>{{ member.memberName || member.memberId }}</td>
              <td>{{ member.memberEmail || '-' }}</td>
              <td>
                <template v-if="member.role === 'owner'">
                  <span class="owner-chip">owner</span>
                </template>
                <template v-else>
                  <select
                    class="table-select"
                    :value="member.role || 'visitor'"
                    @change="changeRole(member.id, ($event.target as HTMLSelectElement).value)"
                  >
                    <option value="admin">admin</option>
                    <option value="normal">normal</option>
                    <option value="visitor">visitor</option>
                  </select>
                </template>
              </td>
              <td>
                <button
                  v-if="member.role !== 'owner'"
                  class="danger-button"
                  type="button"
                  @click="removeMemberAction(member.id)"
                >
                  移除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { useProjectStore } from '@/stores/project'

const route = useRoute()
const projectStore = useProjectStore()
const projectId = computed(() => String(route.params.projectId || ''))
const payload = computed(() => projectStore.membersByProjectId[projectId.value])
const loading = ref(false)
const error = ref('')
const selectedUserId = ref('')

async function load() {
  if (!projectId.value) {
    error.value = '缺少 projectId'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.loadProjectMembers(projectId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载成员失败'
  } finally {
    loading.value = false
  }
}

async function addMember() {
  if (!selectedUserId.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    await projectStore.addMembers(projectId.value, [selectedUserId.value])
    selectedUserId.value = ''
  } catch (err) {
    error.value = err instanceof Error ? err.message : '添加成员失败'
  } finally {
    loading.value = false
  }
}

async function removeMemberAction(memberId: string) {
  loading.value = true
  error.value = ''
  try {
    await projectStore.deleteMember(projectId.value, memberId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '移除成员失败'
  } finally {
    loading.value = false
  }
}

async function changeRole(memberId: string, role: string) {
  loading.value = true
  error.value = ''
  try {
    await projectStore.changeMemberRole(projectId.value, memberId, role)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '修改角色失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-header,
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.page-header {
  margin-bottom: 20px;
}

.toolbar {
  margin-bottom: 16px;
}

.eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.status-card,
.table-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.status-card.error {
  color: #b91c1c;
}

.select,
.table-select {
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.select {
  min-width: 320px;
}

.action-button,
.primary-button,
.danger-button {
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  color: #fff;
  cursor: pointer;
}

.action-button {
  background: #0f172a;
}

.primary-button {
  background: #0f766e;
}

.danger-button {
  background: #b91c1c;
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 14px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  text-align: left;
}

.owner-chip {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(79, 70, 229, 0.1);
  color: #4338ca;
  font-size: 12px;
}

@media (max-width: 860px) {
  .page-header,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .select {
    min-width: 0;
    width: 100%;
  }
}
</style>
