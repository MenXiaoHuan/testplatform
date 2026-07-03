<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import defaultAvatarUrl from '../../assets/default-avatar.svg'
import { useAuthStore } from '../../stores/auth'
import { useSpaceStore } from '../../stores/space'
import type { SpacePlazaItem, SpaceRole } from '../../types/space'
import { isPendingSpaceAccessRequestError, toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'

const router = useRouter()
const authStore = useAuthStore()
const spaceStore = useSpaceStore()
const createDialogVisible = ref(false)
const saving = ref(false)
const requestDialogVisible = ref(false)
const requesting = ref(false)
const editingSpaceId = ref<number | null>(null)
const requestTarget = ref<SpacePlazaItem | null>(null)
const form = reactive({
  name: '',
  description: '',
})
const requestForm = reactive({
  requestedRole: 'VIEWER' as SpaceRole,
  reason: '',
})

const spaces = computed(() => spaceStore.plazaItems)
const greetingName = computed(() => authStore.user?.nickname ?? authStore.user?.username ?? '用户')

const roleOptions: Array<{ label: string, value: SpaceRole, description: string }> = [
  { label: '访问者', value: 'VIEWER', description: '只读查看空间内容' },
  { label: '运维', value: 'OPERATOR', description: '可执行仓库与场景操作' },
  { label: '管理员', value: 'ADMIN', description: '可审批申请并管理空间' },
]

async function loadPlaza() {
  try {
    await spaceStore.fetchPlaza()
  } catch (error) {
    showAppToast(toErrorMessage(error, '空间列表加载失败'), 'error')
  }
}

function openCreateDialog() {
  editingSpaceId.value = null
  form.name = ''
  form.description = ''
  createDialogVisible.value = true
}

function openEditDialog(spaceId: number) {
  const target = spaceStore.items.find((item) => item.id === spaceId)
  if (!target) {
    return
  }
  editingSpaceId.value = spaceId
  form.name = target.name
  form.description = target.description ?? ''
  createDialogVisible.value = true
}

async function saveSpace() {
  if (!form.name.trim()) {
    showAppToast('请输入空间名称', 'warning')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
    }
    if (editingSpaceId.value === null) {
      const created = await spaceStore.createNewSpace(payload)
      showAppToast('空间已创建', 'success')
      createDialogVisible.value = false
      await router.push(`/spaces/${created.id}/repos`)
      return
    }

    await spaceStore.updateExistingSpace(editingSpaceId.value, payload)
    showAppToast('空间已更新', 'success')
    createDialogVisible.value = false
  } catch (error) {
    showAppToast(toErrorMessage(error, editingSpaceId.value === null ? '空间创建失败' : '空间更新失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function enterSpace(spaceId: number) {
  const target = spaceStore.plazaItems.find((item) => item.id === spaceId)
  if (!target?.accessible) {
    showAppToast('你当前没有该空间的访问权限', 'error')
    return
  }
  spaceStore.setCurrentSpace(spaceId)
  await router.push(`/spaces/${spaceId}/repos`)
}

function defaultRequestedRole(space: SpacePlazaItem): SpaceRole {
  if (space.currentRole === 'VIEWER') {
    return 'OPERATOR'
  }
  if (space.currentRole === 'OPERATOR') {
    return 'ADMIN'
  }
  return 'VIEWER'
}

function openRequestDialog(space: SpacePlazaItem) {
  if (space.currentRole === 'ADMIN') {
    showAppToast('你已经是该空间管理员', 'warning')
    return
  }
  if (space.pendingRequestedRole) {
    showAppToast(`你已提交 ${space.pendingRequestedRole} 权限申请，请等待审批`, 'warning')
    return
  }
  requestTarget.value = space
  requestForm.requestedRole = defaultRequestedRole(space)
  requestForm.reason = ''
  requestDialogVisible.value = true
}

function isRoleDisabled(space: SpacePlazaItem, role: SpaceRole) {
  return space.currentRole === role
}

function roleHint(space: SpacePlazaItem, role: SpaceRole) {
  if (space.currentRole === role) {
    return `你当前已经拥有${role === 'VIEWER' ? '访问者' : role === 'OPERATOR' ? '运维' : '管理员'}权限`
  }
  return ''
}

function resolveOwnerDisplayName(space: SpacePlazaItem) {
  return space.ownerNickname?.trim() || space.ownerUsername || '未命名用户'
}

function resolveOwnerAvatar(space: SpacePlazaItem) {
  return space.ownerAvatarUrl || defaultAvatarUrl
}

function ownerBio() {
  return '空间 Owner，负责空间管理、成员审批和关键操作维护。'
}

async function submitAccessRequest() {
  if (!requestTarget.value) {
    return
  }
  if (!requestForm.reason.trim()) {
    showAppToast('请填写申请原因', 'warning')
    return
  }
  if (isRoleDisabled(requestTarget.value, requestForm.requestedRole)) {
    showAppToast(roleHint(requestTarget.value, requestForm.requestedRole), 'warning')
    return
  }

  requesting.value = true
  try {
    await spaceStore.submitAccessRequest({
      requestedRole: requestForm.requestedRole,
      reason: requestForm.reason.trim(),
    }, requestTarget.value.id)
    requestDialogVisible.value = false
    showAppToast('申请已提交，等待管理员审批', 'success')
    await loadPlaza()
  } catch (error) {
    if (isPendingSpaceAccessRequestError(error)) {
      requestDialogVisible.value = false
      await loadPlaza()
      showAppToast('你已有一条待审批申请，已为你刷新当前状态', 'warning')
      return
    }
    showAppToast(toErrorMessage(error, '提交申请失败'), 'error')
  } finally {
    requesting.value = false
  }
}

async function deleteCurrentSpace(spaceId: number) {
  const confirmed = await confirmDangerAction({
    title: '删除空间',
    message: '确认删除该空间吗？删除后将无法在首页继续看到它。',
    confirmButtonText: '删除',
  })
  if (!confirmed) {
    return
  }

  try {
    await spaceStore.removeSpace(spaceId)
    showAppToast('空间已删除', 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '空间删除失败'), 'error')
  }
}

onMounted(() => {
  if (!spaceStore.plazaLoaded) {
    void loadPlaza()
  }
})
</script>

<template>
  <ListPageShell :pagination="null">
    <template #header-left>
      <div class="home-page__title-group">
        <h1>你好，{{ greetingName }}</h1>
      </div>
    </template>
    <template #header-right>
      <el-button type="primary" @click="openCreateDialog">创建空间</el-button>
    </template>

    <el-table v-if="spaces.length > 0" class="list-table" :data="spaces" empty-text="当前还没有空间">
      <el-table-column prop="name" label="空间名称" min-width="220" />
      <el-table-column label="Owner" min-width="220">
        <template #default="{ row }">
          <el-popover placement="right" :width="280" trigger="hover">
            <template #reference>
              <div class="home-owner">
                <img
                  class="home-owner__avatar"
                  :src="resolveOwnerAvatar(row)"
                  :alt="`${resolveOwnerDisplayName(row)} 头像`"
                >
                <div class="home-owner__meta">
                  <strong>{{ resolveOwnerDisplayName(row) }}</strong>
                  <span>{{ row.ownerUsername }}</span>
                </div>
              </div>
            </template>
            <div class="home-owner-card">
              <img
                class="home-owner-card__avatar"
                :src="resolveOwnerAvatar(row)"
                :alt="`${resolveOwnerDisplayName(row)} 头像`"
              >
              <div class="home-owner-card__meta">
                <strong>{{ resolveOwnerDisplayName(row) }}</strong>
                <span>{{ row.ownerUsername }}</span>
                <p>{{ ownerBio() }}</p>
              </div>
            </div>
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="空间说明" min-width="320">
        <template #default="{ row }">
          <span>{{ row.description || '暂无空间说明' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <div class="home-page__actions">
            <el-tooltip v-if="!row.accessible" content="你当前没有该空间的访问权限" placement="top">
              <span class="home-page__tooltip-trigger">
                <el-button link type="primary" disabled>进入</el-button>
              </span>
            </el-tooltip>
            <el-button v-else link type="primary" @click="enterSpace(row.id)">进入</el-button>

            <el-tooltip
              v-if="row.pendingRequestedRole"
              :content="`你已提交 ${row.pendingRequestedRole} 权限申请，请等待审批`"
              placement="top"
            >
              <span class="home-page__tooltip-trigger">
                <el-button link type="primary" disabled>审批中</el-button>
              </span>
            </el-tooltip>
            <el-button v-else-if="row.currentRole !== 'ADMIN'" link type="primary" @click="openRequestDialog(row)">申请权限</el-button>

            <el-button v-if="row.manageable" link type="primary" @click="openEditDialog(row.id)">编辑</el-button>
            <el-button v-if="row.manageable" link type="danger" @click="deleteCurrentSpace(row.id)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else description="当前还没有空间">
      <el-button type="primary" @click="openCreateDialog">创建第一个空间</el-button>
    </el-empty>

    <el-dialog v-model="createDialogVisible" title="创建空间" width="520px">
      <el-form label-position="top">
        <el-form-item label="空间名称">
          <el-input v-model="form.name" placeholder="请输入空间名称" />
        </el-form-item>
        <el-form-item label="空间说明">
          <el-input v-model="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveSpace">
            {{ editingSpaceId === null ? '创建' : '保存' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="requestDialogVisible" title="申请权限" width="520px">
      <el-form label-position="top">
        <el-form-item label="申请空间">
          <el-input :model-value="requestTarget?.name ?? '-'" disabled />
        </el-form-item>
        <el-form-item label="申请权限">
          <div class="request-role-list">
            <el-tooltip
              v-for="option in roleOptions"
              :key="option.value"
              :disabled="!requestTarget || !isRoleDisabled(requestTarget, option.value)"
              :content="requestTarget ? roleHint(requestTarget, option.value) : ''"
              placement="top"
            >
              <label
                class="request-role-option"
                :class="{
                  'request-role-option--active': requestForm.requestedRole === option.value,
                  'request-role-option--disabled': requestTarget && isRoleDisabled(requestTarget, option.value),
                }"
              >
                <input
                  v-model="requestForm.requestedRole"
                  type="radio"
                  :value="option.value"
                  :disabled="Boolean(requestTarget && isRoleDisabled(requestTarget, option.value))"
                >
                <span>{{ option.label }}</span>
                <small>{{ option.description }}</small>
              </label>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="申请原因">
          <el-input
            v-model="requestForm.reason"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 5 }"
            placeholder="例如：需要接手该空间的仓库维护、场景巡检或调度故障处理"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="requestDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="requesting" @click="submitAccessRequest">提交申请</el-button>
        </div>
      </template>
    </el-dialog>
  </ListPageShell>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 24px;
}

.home-page__title-group h1 {
  margin: 8px 0 12px;
}

.home-page__title-group p {
  margin: 0;
  color: #64748b;
}

.home-page__eyebrow {
  color: #2563eb;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.home-page__actions {
  display: flex;
  gap: 8px;
}

.home-page__tooltip-trigger {
  display: inline-flex;
}

.home-owner,
.home-owner-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.home-owner__avatar,
.home-owner-card__avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: cover;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.home-owner__meta,
.home-owner-card__meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.home-owner__meta strong,
.home-owner-card__meta strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.home-owner__meta span,
.home-owner-card__meta span {
  color: #64748b;
  font-size: 12px;
}

.home-owner-card {
  align-items: flex-start;
}

.home-owner-card__meta p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.request-role-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.request-role-option {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 2px 8px;
  align-items: center;
  min-height: 62px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #0f172a;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}

.request-role-option input {
  margin: 0;
  grid-row: 1 / span 2;
}

.request-role-option--active {
  border-color: #14b8a6;
  background: #ecfeff;
  color: #0f766e;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.request-role-option--disabled {
  color: #94a3b8;
  background: #f8fafc;
  cursor: not-allowed;
}

.request-role-option span {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.request-role-option small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.request-role-option--active small {
  color: #0f766e;
}
</style>
