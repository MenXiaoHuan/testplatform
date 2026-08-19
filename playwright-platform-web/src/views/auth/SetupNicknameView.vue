<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'

const authStore = useAuthStore()
const router = useRouter()
const nickname = ref('')
const loading = ref(false)
const MAX_LENGTH = 10

async function submit() {
  const trimmed = nickname.value.trim()
  if (!trimmed) {
    showAppToast('请输入昵称', 'warning')
    return
  }
  if (trimmed.length > MAX_LENGTH) {
    showAppToast(`昵称不能超过${MAX_LENGTH}个字符`, 'warning')
    return
  }

  loading.value = true
  try {
    const user = await authStore.setupProfile(trimmed)
    if (user?.lastSpaceId) {
      await router.push(`/spaces/${user.lastSpaceId}/repos`)
    } else {
      await router.push('/home')
    }
  } catch (error) {
    showAppToast(toErrorMessage(error, '设置昵称失败，请稍后重试'), 'error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="setup-page">
    <div class="setup-card">
      <div class="setup-card__icon">👋</div>
      <h1>欢迎加入！</h1>
      <p class="setup-desc">请先设置你的昵称，我们会为你创建一个默认的测试空间</p>

      <el-form class="setup-form" @submit.prevent="submit">
        <el-form-item :rules="[{ required: true, message: '请输入昵称' }]">
          <el-input
            v-model="nickname"
            :maxlength="MAX_LENGTH"
            placeholder="输入你的昵称（最多10个字符）"
            show-word-limit
            clearable
            size="large"
          />
        </el-form-item>

        <el-button
          class="setup-form__submit"
          type="primary"
          :loading="loading"
          size="large"
          @click="submit"
        >
          完成设置，进入平台
        </el-button>
      </el-form>
    </div>
  </section>
</template>

<style scoped>
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #0f9f92 0%, #14b8a6 45%, #2dd4bf 100%);
  padding: 24px;
}

.setup-card {
  width: min(440px, 100%);
  background: #ffffff;
  border-radius: 16px;
  padding: 48px 40px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.12);
  display: grid;
  gap: 20px;
  text-align: center;
}

.setup-card__icon {
  font-size: 48px;
  line-height: 1;
}

.setup-card h1 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  font-weight: 700;
}

.setup-desc {
  margin: 0;
  color: #64748b;
  font-size: 15px;
  line-height: 1.7;
}

.setup-form {
  display: grid;
  gap: 16px;
  margin-top: 8px;
}

.setup-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.setup-form :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e5e6eb inset;
}

.setup-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0f9f92 inset;
}

.setup-form__submit {
  width: 100%;
  min-height: 48px;
  border-radius: 10px;
  box-shadow: none;
  font-size: 16px;
}
</style>
