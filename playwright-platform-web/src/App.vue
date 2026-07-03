<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import defaultAvatarUrl from './assets/default-avatar.svg'
import SidebarUserPanel from './components/layout/SidebarUserPanel.vue'
import SpaceSwitcher from './components/layout/SpaceSwitcher.vue'
import { useAuthStore } from './stores/auth'
import { useSpaceStore } from './stores/space'
import { getActiveSpaceMenuIndex, getSpaceMenuItems } from './utils/space-permissions'
import { toErrorMessage } from './utils/error'
import { showAppToast } from './utils/ui-feedback'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const spaceStore = useSpaceStore()
const nicknameDialogVisible = ref(false)
const savingNickname = ref(false)
const avatarDialogVisible = ref(false)
const uploadingAvatar = ref(false)
const avatarPreviewUrl = ref('')
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarImageRef = ref<HTMLImageElement | null>(null)
const avatarCropper = shallowRef<Cropper | null>(null)
const profileForm = reactive({
  nickname: '',
})
const displayUserName = computed(() => authStore.user?.nickname?.trim() || authStore.user?.username?.trim() || '未命名用户')
const displayUserAccount = computed(() => authStore.user?.username?.trim() || '当前账号')
const currentAvatarUrl = computed(() => authStore.user?.avatarUrl || '')

const showShell = computed(() => route.path !== '/login' && authStore.isAuthenticated)
const hasActiveSpace = computed(() => typeof spaceStore.currentSpaceId === 'number')

const menuItems = computed(() => {
  const spaceId = spaceStore.currentSpaceId
  if (typeof spaceId !== 'number') {
    return [{ index: '/home', label: '空间广场' }]
  }
  return getSpaceMenuItems(spaceId, spaceStore.currentSpaceRole)
})

const activeMenu = computed(() => {
  if (route.path === '/home') {
    return '/home'
  }
  return getActiveSpaceMenuIndex(route.path, menuItems.value)
})

function handleSelect(index: string) {
  void router.push(index)
}

async function handleUserCommand(command: 'edit-avatar' | 'edit-nickname' | 'logout') {
  if (command === 'edit-avatar') {
    avatarDialogVisible.value = true
    return
  }
  if (command === 'edit-nickname') {
    profileForm.nickname = authStore.user?.nickname === authStore.user?.username ? '' : (authStore.user?.nickname ?? '')
    nicknameDialogVisible.value = true
    return
  }

  await authStore.logout()
  spaceStore.clearState()
  await router.push('/login')
}

async function submitNickname() {
  savingNickname.value = true
  try {
    await authStore.updateNickname(profileForm.nickname.trim())
    nicknameDialogVisible.value = false
    showAppToast('昵称已更新', 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '昵称更新失败'), 'error')
  } finally {
    savingNickname.value = false
  }
}

function destroyAvatarCropper() {
  avatarCropper.value?.destroy()
  avatarCropper.value = null
}

function resetAvatarEditor(closeDialog = false) {
  destroyAvatarCropper()
  if (avatarPreviewUrl.value) {
    URL.revokeObjectURL(avatarPreviewUrl.value)
    avatarPreviewUrl.value = ''
  }
  if (avatarInputRef.value) {
    avatarInputRef.value.value = ''
  }
  if (closeDialog) {
    avatarDialogVisible.value = false
  }
}

function triggerAvatarSelection() {
  avatarInputRef.value?.click()
}

async function handleAvatarFileChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (!file) {
    return
  }
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    showAppToast('仅支持 PNG、JPG、WEBP 图片', 'warning')
    input.value = ''
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    showAppToast('头像文件不能超过 2MB', 'warning')
    input.value = ''
    return
  }
  resetAvatarEditor()
  avatarPreviewUrl.value = URL.createObjectURL(file)
  await nextTick()
  if (!avatarImageRef.value) {
    return
  }
  avatarCropper.value = new Cropper(avatarImageRef.value, {
    aspectRatio: 1,
    viewMode: 1,
    dragMode: 'move',
    autoCropArea: 1,
    background: false,
    guides: false,
    center: false,
    highlight: false,
  })
}

async function submitAvatar() {
  if (!avatarCropper.value) {
    showAppToast('请先选择头像图片', 'warning')
    return
  }
  uploadingAvatar.value = true
  try {
    const blob = await new Promise<Blob>((resolve, reject) => {
      avatarCropper.value?.getCroppedCanvas({
        width: 256,
        height: 256,
        imageSmoothingQuality: 'high',
      }).toBlob((value) => {
        if (value) {
          resolve(value)
          return
        }
        reject(new Error('头像裁剪失败'))
      }, 'image/png')
    })
    const file = new File([blob], 'avatar.png', { type: 'image/png' })
    await authStore.uploadAvatar(file)
    spaceStore.syncCurrentUserIdentity()
    showAppToast('头像已更新', 'success')
    resetAvatarEditor(true)
  } catch (error) {
    showAppToast(toErrorMessage(error, '头像上传失败'), 'error')
  } finally {
    uploadingAvatar.value = false
  }
}

onBeforeUnmount(() => {
  resetAvatarEditor()
})
</script>

<template>
  <el-config-provider>
    <router-view v-if="!showShell" />
    <el-container v-else class="shell">
      <el-aside width="264px" class="shell-aside">
        <SpaceSwitcher v-if="hasActiveSpace" class="shell-space-switcher" />

        <el-menu :default-active="activeMenu" class="nav-menu" @select="handleSelect">
          <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">{{ item.label }}</el-menu-item>
        </el-menu>

        <div v-if="!hasActiveSpace" class="shell-onboarding">
          <p class="shell-onboarding__title">使用平台前，请先进入空间</p>
          <p class="shell-onboarding__copy">所有仓库、自动化任务、场景能力均归属独立空间，点击列表「进入」即可解锁全部功能</p>
        </div>

        <SidebarUserPanel
          v-if="authStore.user"
          class="shell-user-panel"
          :user="authStore.user"
          @command="handleUserCommand"
        />
      </el-aside>

      <el-container>
        <el-main class="shell-main">
          <router-view v-slot="{ Component }">
            <component :is="Component" :key="route.fullPath" />
          </router-view>
        </el-main>
      </el-container>

      <el-dialog v-model="nicknameDialogVisible" title="修改昵称" width="420px">
        <el-form label-position="top">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickname" maxlength="30" placeholder="未填写时默认显示为未命名用户" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="nicknameDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingNickname" @click="submitNickname">保存</el-button>
          </div>
        </template>
      </el-dialog>

      <el-dialog
        v-model="avatarDialogVisible"
        title="上传头像"
        width="760px"
        @closed="resetAvatarEditor()"
      >
        <div class="avatar-dialog">
          <section class="avatar-dialog__panel avatar-dialog__panel--sidebar">
            <div class="avatar-dialog__identity">
              <img
                class="avatar-dialog__identity-avatar"
                :src="currentAvatarUrl || defaultAvatarUrl"
                alt="当前头像"
              >
              <div class="avatar-dialog__identity-meta">
                <strong>{{ displayUserName }}</strong>
                <span>{{ displayUserAccount }}</span>
              </div>
            </div>
            <p class="avatar-dialog__hint">支持 PNG、JPG、WEBP，文件大小不超过 2MB。建议上传清晰的人像或品牌图标，裁剪后会自动压成方形头像。</p>
            <div class="avatar-dialog__tips">
              <span>1. 先选择图片</span>
              <span>2. 调整裁剪区域</span>
              <span>3. 上传后自动刷新头像</span>
            </div>
            <div class="avatar-dialog__actions">
              <el-button class="avatar-dialog__select-button" type="primary" @click="triggerAvatarSelection">选择图片</el-button>
            </div>
          </section>

          <section class="avatar-dialog__panel avatar-dialog__panel--workspace">
            <div v-if="avatarPreviewUrl" class="avatar-dialog__cropper">
              <img ref="avatarImageRef" :src="avatarPreviewUrl" alt="头像裁剪预览">
            </div>
            <el-empty v-else description="请选择一张头像图片后再裁剪">
              <template #image>
                <div class="avatar-dialog__empty-illustration" />
              </template>
            </el-empty>
          </section>

          <input
            ref="avatarInputRef"
            class="avatar-dialog__input"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="handleAvatarFileChange"
          >
        </div>
        <template #footer>
          <div class="dialog-footer">
            <el-button @click="resetAvatarEditor(true)">取消</el-button>
            <el-button type="primary" :loading="uploadingAvatar" @click="submitAvatar">上传头像</el-button>
          </div>
        </template>
      </el-dialog>
    </el-container>
  </el-config-provider>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  background: #f8fafc;
}

.shell-aside {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 16px;
  background: #f8fafc;
  color: #111827;
  border-right: 1px solid #e2e8f0;
}

.shell-space-switcher {
  margin-top: 2px;
}

.nav-menu {
  flex: 1 1 auto;
  border-right: 0;
  background: transparent;
}

.nav-menu :deep(.el-menu-item) {
  color: #475569;
  border-radius: 12px;
}

.nav-menu :deep(.el-menu-item:hover) {
  background: #ffffff;
  color: #0f766e;
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #0f766e;
  background: #ecfeff;
  font-weight: 600;
}

.shell-user-panel {
  margin-top: auto;
}

.shell-onboarding {
  margin-top: auto;
  padding: 14px 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.shell-onboarding__title {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.shell-onboarding__copy {
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.shell-main {
  min-width: 0;
  overflow-x: hidden;
  padding: 28px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.avatar-dialog {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 18px;
  align-items: stretch;
}

.avatar-dialog__panel {
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
}

.avatar-dialog__panel--sidebar {
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 18px;
  background:
    linear-gradient(180deg, rgba(20, 184, 166, 0.05), rgba(20, 184, 166, 0.01)),
    #ffffff;
}

.avatar-dialog__panel--workspace {
  padding: 18px;
  min-height: 380px;
  display: grid;
  align-items: center;
}

.avatar-dialog__identity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-dialog__identity-avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  object-fit: cover;
  background: #f8fafc;
  border: 1px solid #dbe4ee;
}

.avatar-dialog__identity-meta {
  display: grid;
  gap: 2px;
}

.avatar-dialog__identity-meta strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}

.avatar-dialog__identity-meta span {
  color: #64748b;
  font-size: 12px;
}

.avatar-dialog__hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.avatar-dialog__tips {
  display: grid;
  gap: 8px;
}

.avatar-dialog__tips span {
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.95);
  border: 1px solid #e2e8f0;
}

.avatar-dialog__input {
  display: none;
}

.avatar-dialog__actions {
  display: flex;
  justify-content: stretch;
}

.avatar-dialog__select-button {
  color: #ffffff;
}

.avatar-dialog__select-button :deep(span) {
  color: #ffffff;
}

.avatar-dialog__cropper {
  width: 100%;
  min-height: 340px;
  border-radius: 16px;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.avatar-dialog__cropper img {
  display: block;
  max-width: 100%;
}

.avatar-dialog__empty-illustration {
  width: 108px;
  height: 108px;
  margin: 0 auto;
  border-radius: 32px;
  background:
    radial-gradient(circle at 30% 30%, rgba(20, 184, 166, 0.28), transparent 45%),
    linear-gradient(135deg, rgba(59, 130, 246, 0.18), rgba(20, 184, 166, 0.18));
}
</style>
