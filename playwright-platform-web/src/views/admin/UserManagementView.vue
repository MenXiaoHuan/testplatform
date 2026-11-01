<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import ListPageShell from '../../components/list/ListPageShell.vue'
import defaultAvatarUrl from '../../assets/default-avatar.svg'
import { ElMessageBox } from 'element-plus'
import {
  deleteAdminUser,
  fetchAdminUser,
  fetchAdminUsers,
  fetchRegistrationSetting,
  resetAdminUserPassword,
  updateAdminUser,
  updateRegistrationSetting,
} from '../../api/admin'
import type { AdminUser } from '../../types/admin'
import { useAuthStore } from '../../stores/auth'
import { encryptPassword } from '../../utils/auth-crypto'
import { formatBeijingDateTime } from '../../utils/datetime'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'

const authStore = useAuthStore()

const items = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const keywordInput = ref('')
const loading = ref(false)

const registrationEnabled = ref(false)
const registrationLoading = ref(false)
const registrationSaving = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailUser = ref<AdminUser | null>(null)

const editVisible = ref(false)
const editSaving = ref(false)
const editingId = ref<number | null>(null)
const editingUsername = ref('')
const editingSuperAdmin = ref(false)
const editForm = reactive({ nickname: '', enabled: true })

const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordTarget = ref<AdminUser | null>(null)
const passwordForm = reactive({ password: '', confirm: '' })

const pagination = computed(() => ({ page: page.value, size: size.value, total: total.value }))
const currentUserId = computed(() => authStore.user?.id ?? null)

function avatarOf(user: AdminUser) {
  return user.avatarUrl || defaultAvatarUrl
}

function displayName(user: AdminUser) {
  return user.nickname?.trim() || user.username
}

function isSelf(user: AdminUser) {
  return currentUserId.value !== null && user.id === currentUserId.value
}

async function loadUsers() {
  loading.value = true
  try {
    const result = await fetchAdminUsers({ page: page.value, size: size.value, keyword: keyword.value })
    items.value = result.items ?? []
    total.value = result.total ?? 0
  } catch (error) {
    showAppToast(toErrorMessage(error, '用户列表加载失败'), 'error')
  } finally {
    loading.value = false
  }
}

async function loadRegistrationSetting() {
  registrationLoading.value = true
  try {
    registrationEnabled.value = (await fetchRegistrationSetting()).enabled
  } catch (error) {
    showAppToast(toErrorMessage(error, '注册开关加载失败'), 'error')
  } finally {
    registrationLoading.value = false
  }
}

function handleSearch() {
  page.value = 1
  keyword.value = keywordInput.value.trim()
  void loadUsers()
}

function handlePageChange(value: number) {
  page.value = value
  void loadUsers()
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
  void loadUsers()
}

async function openDetail(user: AdminUser) {
  detailVisible.value = true
  detailLoading.value = true
  detailUser.value = user
  try {
    detailUser.value = await fetchAdminUser(user.id)
  } catch (error) {
    showAppToast(toErrorMessage(error, '用户详情加载失败'), 'error')
  } finally {
    detailLoading.value = false
  }
}

function openEdit(user: AdminUser) {
  editingId.value = user.id
  editingUsername.value = user.username
  editingSuperAdmin.value = user.superAdmin
  editForm.nickname = user.nickname?.trim() || user.username
  editForm.enabled = user.enabled
  editVisible.value = true
}

async function submitEdit() {
  if (editingId.value === null) return
  const nickname = editForm.nickname.trim()
  if (!nickname) {
    showAppToast('请输入昵称', 'warning')
    return
  }
  if (nickname.length > 10) {
    showAppToast('昵称不能超过10个字符', 'warning')
    return
  }
  editSaving.value = true
  try {
    await updateAdminUser(editingId.value, { nickname, enabled: editForm.enabled })
    showAppToast('用户信息已更新', 'success')
    editVisible.value = false
    if (editingId.value === currentUserId.value) {
      await authStore.restoreSession()
    }
    await loadUsers()
  } catch (error) {
    showAppToast(toErrorMessage(error, '用户信息更新失败'), 'error')
  } finally {
    editSaving.value = false
  }
}

function openPasswordDialog(user: AdminUser) {
  passwordTarget.value = user
  passwordForm.password = ''
  passwordForm.confirm = ''
  passwordVisible.value = true
}

async function submitPassword() {
  const target = passwordTarget.value
  if (!target) return
  if (passwordForm.password.length < 8 || !/[A-Za-z]/.test(passwordForm.password) || !/\d/.test(passwordForm.password)) {
    showAppToast('密码至少 8 位，且需包含字母和数字', 'warning')
    return
  }
  if (passwordForm.password !== passwordForm.confirm) {
    showAppToast('两次输入的密码不一致', 'warning')
    return
  }
  passwordSaving.value = true
  try {
    const publicKey = await authStore.ensurePublicKey()
    const encryptedPassword = await encryptPassword(passwordForm.password, publicKey.publicKeyPem)
    await resetAdminUserPassword(target.id, encryptedPassword)
    passwordVisible.value = false
    showAppToast(`已重置 ${displayName(target)} 的密码`, 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '密码重置失败'), 'error')
  } finally {
    passwordSaving.value = false
  }
}

function handleRowCommand(command: string, user: AdminUser) {
  if (command === 'password') {
    openPasswordDialog(user)
    return
  }
  if (command === 'delete') {
    void handleDelete(user)
  }
}

async function handleDelete(user: AdminUser) {
  const confirmed = await confirmDangerAction({
    title: '删除用户',
    message: `确认删除用户「${displayName(user)}」吗？该用户的空间成员关系与登录会话将一并清理，且不可恢复。`,
    confirmButtonText: '删除',
  })
  if (!confirmed) return
  try {
    await deleteAdminUser(user.id)
    showAppToast('用户已删除', 'success')
    if (items.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadUsers()
  } catch (error) {
    showAppToast(toErrorMessage(error, '用户删除失败'), 'error')
  }
}

async function handleRegistrationChange(value: string | number | boolean) {
  if (registrationSaving.value) return
  const next = Boolean(value)
  const confirmed = await confirmRegistrationChange(next)
  if (!confirmed) return
  registrationSaving.value = true
  try {
    const setting = await updateRegistrationSetting(next)
    registrationEnabled.value = setting.enabled
    showAppToast(next ? '自助注册已开启' : '自助注册已关闭', 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '注册开关保存失败'), 'error')
  } finally {
    registrationSaving.value = false
  }
}

async function confirmRegistrationChange(next: boolean) {
  const currentLabel = registrationEnabled.value ? '已开启' : '已关闭'
  const nextLabel = next ? '已开启' : '已关闭'
  const html = `
    <p class="reg-confirm__desc">${next
      ? '开启后，登录页会展示注册入口，任何访客都可以自助创建账号。'
      : '关闭后，登录页将隐藏注册入口，已注册账号仍可正常登录。'}</p>
    <div class="reg-confirm__flow">
      <div class="reg-confirm__state">
        <span>当前状态</span>
        <strong>${currentLabel}</strong>
      </div>
      <div class="reg-confirm__arrow">→</div>
      <div class="reg-confirm__state">
        <span>切换后</span>
        <strong class="${next ? 'reg-confirm__state--on' : 'reg-confirm__state--off'}">${nextLabel}</strong>
      </div>
    </div>`
  try {
    await ElMessageBox.confirm(html, next ? '开启自助注册' : '关闭自助注册', {
      type: next ? 'success' : 'warning',
      dangerouslyUseHTMLString: true,
      customClass: 'app-confirm-dialog registration-confirm-dialog',
      confirmButtonText: next ? '确认开启' : '确认关闭',
      cancelButtonText: '取消',
      confirmButtonClass: next
        ? 'app-confirm-dialog__confirm registration-confirm-dialog__confirm--on'
        : 'app-confirm-dialog__confirm',
      cancelButtonClass: 'app-confirm-dialog__cancel',
      showClose: false,
      center: true,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      distinguishCancelAndClose: true,
    })
    return true
  } catch {
    return false
  }
}

onMounted(() => {
  void loadUsers()
  void loadRegistrationSetting()
})
</script>

<template>
<ListPageShell
  :pagination="pagination"
  @page-change="handlePageChange"
  @size-change="handleSizeChange"
>
  <template #header-left>
    <div class="admin-users__toolbar">
      <span class="admin-users__title">用户管理</span>
      <el-input
        v-model="keywordInput"
        class="admin-users__search"
        placeholder="搜索用户名或昵称"
        clearable
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
    </div>
  </template>
  <template #header-right>
    <div class="admin-users__switch">
      <span class="admin-users__switch-label">允许自助注册</span>
      <el-switch
        :model-value="registrationEnabled"
        :loading="registrationLoading || registrationSaving"
        @change="handleRegistrationChange"
      />
    </div>
  </template>

  <el-table class="list-table" :data="items" :loading="loading" empty-text="暂无用户">
    <el-table-column prop="id" label="ID" width="80" />
    <el-table-column label="用户" min-width="220">
      <template #default="{ row }">
        <div class="admin-users__identity">
          <img class="admin-users__avatar" :src="avatarOf(row)" :alt="`${displayName(row)} 头像`">
          <div class="admin-users__identity-meta">
            <strong>{{ displayName(row) }}</strong>
            <span>{{ row.username }}</span>
          </div>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="角色" width="150">
      <template #default="{ row }">
        <el-tag class="admin-users__role-tag" :type="row.superAdmin ? 'danger' : 'info'" effect="light" round>
          {{ row.superAdmin ? '超级管理员' : '普通用户' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-tag :type="row.enabled ? 'success' : 'info'" effect="light" round>
          {{ row.enabled ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="注册时间" width="200">
      <template #default="{ row }">{{ formatBeijingDateTime(row.createdAt) }}</template>
    </el-table-column>
    <el-table-column label="更新时间" width="200">
      <template #default="{ row }">{{ formatBeijingDateTime(row.updatedAt) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="200" fixed="right">
      <template #default="{ row }">
        <div class="table-actions">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-dropdown trigger="click" @command="handleRowCommand($event, row)">
            <el-button link type="primary">更多 ▾</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">重置密码</el-dropdown-item>
                <el-dropdown-item command="delete" :disabled="row.superAdmin || isSelf(row)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </el-table-column>
  </el-table>
</ListPageShell>

<el-drawer v-model="detailVisible" title="用户详情" size="420px">
  <div v-loading="detailLoading" class="admin-users__detail">
    <template v-if="detailUser">
      <div class="admin-users__detail-identity">
        <img class="admin-users__detail-avatar" :src="avatarOf(detailUser)" :alt="`${displayName(detailUser)} 头像`">
        <div class="admin-users__identity-meta">
          <strong>{{ displayName(detailUser) }}</strong>
          <span>{{ detailUser.username }}</span>
        </div>
      </div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户ID">{{ detailUser.id }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ detailUser.superAdmin ? '超级管理员' : '普通用户' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailUser.enabled ? '启用' : '禁用' }}</el-descriptions-item>
        <el-descriptions-item label="当前空间">{{ detailUser.lastSpaceId ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ formatBeijingDateTime(detailUser.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatBeijingDateTime(detailUser.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
    </template>
    <el-empty v-else description="未获取到用户信息" />
  </div>
</el-drawer>

<el-dialog v-model="editVisible" title="编辑用户" width="460px">
  <el-form label-position="top">
    <el-form-item label="用户名">
      <el-input v-model="editingUsername" disabled />
    </el-form-item>
    <el-form-item label="昵称">
      <el-input v-model="editForm.nickname" maxlength="10" show-word-limit placeholder="请输入昵称" />
    </el-form-item>
    <el-form-item label="账号状态">
      <el-switch
        v-model="editForm.enabled"
        active-text="启用"
        inactive-text="禁用"
        :disabled="editingSuperAdmin || editingId === currentUserId"
      />
      <p v-if="editingSuperAdmin || editingId === currentUserId" class="admin-users__hint">
        超级管理员或当前登录账号不允许禁用
      </p>
    </el-form-item>
  </el-form>
  <template #footer>
    <div class="dialog-footer">
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
    </div>
  </template>
</el-dialog>

<el-dialog v-model="passwordVisible" title="重置密码" width="460px">
  <el-alert
    v-if="passwordTarget"
    type="warning"
    :closable="false"
    show-icon
    :title="`正在重置「${displayName(passwordTarget)}」的登录密码，保存后该用户需使用新密码登录。`"
  />
  <el-form class="admin-users__password-form" label-position="top">
    <el-form-item label="新密码">
      <el-input v-model="passwordForm.password" type="password" show-password placeholder="至少 8 位，且包含字母和数字" />
    </el-form-item>
    <el-form-item label="确认新密码">
      <el-input v-model="passwordForm.confirm" type="password" show-password placeholder="再次输入新密码" />
    </el-form-item>
  </el-form>
  <template #footer>
    <div class="dialog-footer">
      <el-button @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSaving" @click="submitPassword">保存</el-button>
    </div>
  </template>
</el-dialog>
</template>

<style scoped>
.admin-users__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.admin-users__title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.admin-users__search {
  width: 240px;
}

.admin-users__switch {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-users__switch-label {
  color: #475569;
  font-size: 13px;
}

.admin-users__identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.admin-users__avatar,
.admin-users__detail-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: cover;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.admin-users__detail-avatar {
  width: 56px;
  height: 56px;
  border-radius: 16px;
}

.admin-users__identity-meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.admin-users__identity-meta strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.admin-users__identity-meta span {
  color: #64748b;
  font-size: 12px;
}

.admin-users__role-tag {
  flex-shrink: 0;
  max-width: none;
  padding: 0 12px;
  white-space: nowrap;
}

.table-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.admin-users__detail {
  display: grid;
  gap: 18px;
}

.admin-users__detail-identity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-users__hint {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.admin-users__password-form {
  margin-top: 16px;
}
</style>
