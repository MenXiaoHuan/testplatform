<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import defaultAvatarUrl from '../../assets/default-avatar.svg'
import type { AuthUser } from '../../types/auth'

const props = defineProps<{
  user: AuthUser
}>()

const emit = defineEmits<{
  command: [value: 'edit-avatar' | 'edit-nickname' | 'logout']
}>()

const useDefaultAvatar = ref(false)
const displayNickname = computed(() => props.user.nickname?.trim() || props.user.username?.trim() || '未命名用户')

const resolvedAvatarUrl = computed(() => {
  if (useDefaultAvatar.value || !props.user.avatarUrl) {
    return defaultAvatarUrl
  }
  return props.user.avatarUrl
})

watch(
  () => props.user.avatarUrl,
  (current, previous) => {
    if (current !== previous) {
      useDefaultAvatar.value = false
    }
  },
)

function handleAvatarError() {
  useDefaultAvatar.value = true
}

function emitCommand(command: 'edit-avatar' | 'edit-nickname' | 'logout') {
  emit('command', command)
}
</script>

<template>
  <section class="sidebar-user-panel">
    <button class="sidebar-user-panel__avatar-button" type="button" @click="emitCommand('edit-avatar')">
      <img
        class="sidebar-user-panel__avatar"
        :src="resolvedAvatarUrl"
        :alt="`${displayNickname} 头像`"
        @error="handleAvatarError"
      >
    </button>
    <div class="sidebar-user-panel__meta">
      <strong class="sidebar-user-panel__nickname">{{ displayNickname }}</strong>
    </div>
    <el-dropdown trigger="click" placement="top-end">
      <button class="sidebar-user-panel__action" type="button">菜单</button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item @click="emitCommand('edit-nickname')">修改昵称</el-dropdown-item>
          <el-dropdown-item @click="emitCommand('logout')">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </section>
</template>

<style scoped>
.sidebar-user-panel {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.sidebar-user-panel__avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  object-fit: cover;
  background: #f8fafc;
}

.sidebar-user-panel__avatar-button {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.sidebar-user-panel__meta {
  min-width: 0;
  display: flex;
  align-items: center;
}

.sidebar-user-panel__nickname {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-user-panel__nickname {
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.sidebar-user-panel__action {
  border: 0;
  background: transparent;
  color: #475569;
  cursor: pointer;
  padding: 4px 0;
}
</style>
