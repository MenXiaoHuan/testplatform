<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const loginForm = reactive({
  username: '',
  password: '',
})
const registerMode = ref(false)
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const submitLabel = computed(() => (registerMode.value ? '注册并进入个人空间' : '登录并进入首页'))

async function submit() {
  loading.value = true
  try {
    if (registerMode.value) {
      if (!registerForm.username.trim() || !registerForm.password || !registerForm.confirmPassword) {
        showAppToast('请完整填写注册信息', 'warning')
        return
      }
      if (registerForm.password !== registerForm.confirmPassword) {
        showAppToast('两次输入的密码不一致', 'warning')
        return
      }
      if (registerForm.password.length < 8 || !/[A-Za-z]/.test(registerForm.password) || !/\d/.test(registerForm.password)) {
        showAppToast('密码至少 8 位，且需包含字母和数字', 'warning')
        return
      }
      const user = await authStore.register({
        username: registerForm.username.trim(),
        password: registerForm.password,
        confirmPassword: registerForm.confirmPassword,
      })
      if (user?.needsSetup) {
        await router.push('/setup-nickname')
      } else {
        await router.push(user?.lastSpaceId ? `/spaces/${user.lastSpaceId}/repos` : '/home')
      }
      return
    }

    if (!loginForm.username.trim() || !loginForm.password) {
      showAppToast('请输入用户名和密码', 'warning')
      return
    }

    const user = await authStore.login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })
    if (user?.needsSetup) {
      await router.push('/setup-nickname')
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.push(redirect || '/home')
  } catch (error) {
    showAppToast(
      toErrorMessage(error, registerMode.value ? '注册失败，请稍后重试' : '用户名或密码错误'),
      'error',
    )
  } finally {
    loading.value = false
  }
}

function toggleMode() {
  registerMode.value = !registerMode.value
}
</script>

<template>
  <section class="login-page">
    <div class="login-wrap">
      <section class="login-left">
        <div class="login-left__content">
          <p class="login-left__eyebrow">Platform Overview</p>
          <div class="login-left__logo">测试平台</div>
          <h1>自动化测试协作平台</h1>
          <p class="login-left__copy">
            一站式自动化测试协作解决方案，统一承接空间协作、场景执行、任务调度和结果归档，
            让团队在同一个工作台内完成从配置到执行再到追踪的完整链路。
          </p>

          <div class="login-left__features">
            <div class="feature-item">✓ 空间与权限协作</div>
            <div class="feature-item">✓ 场景执行与调度</div>
            <div class="feature-item">✓ 结果留痕与产物归档</div>
          </div>
        </div>

        <p class="login-left__footer">© 2026 测试平台 | 自动化测试工作台</p>
      </section>

      <section class="login-right">
        <div class="login-card">
            <div class="login-card__header">
              <p class="login-card__eyebrow">Account Sign In</p>
              <h2>{{ registerMode ? '注册账号' : '欢迎回来' }}</h2>
              <p>{{ registerMode ? '创建账号后请设置昵称，系统会自动为你创建个人测试空间。' : '输入账号信息，立即进入你的测试协作空间。' }}</p>
            </div>

            <el-form class="login-form" label-position="top" @submit.prevent="submit">
            <template v-if="registerMode">
              <el-form-item label="用户名">
                <el-input v-model="registerForm.username" placeholder="请输入用户名" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="registerForm.password" type="password" show-password placeholder="请输入密码" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="用户名">
                <el-input v-model="loginForm.username" placeholder="请输入用户名" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
              </el-form-item>
            </template>

            <el-button class="login-form__submit" type="primary" :loading="loading" @click="submit">
              {{ submitLabel }}
            </el-button>
            <div class="login-form__footer">
              <span>{{ registerMode ? '已有账号？' : '还没有账号？' }}</span>
              <button class="login-form__switch" type="button" @click="toggleMode">
                {{ registerMode ? '去登录' : '立即注册' }}
              </button>
            </div>
          </el-form>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f4f7fb;
}

.login-wrap {
  display: flex;
  min-height: 100vh;
}

.login-left {
  width: 45%;
  min-height: 100vh;
  padding: 72px 60px 48px;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.18), transparent 28%),
    linear-gradient(140deg, #0f9f92 0%, #14b8a6 45%, #2dd4bf 100%);
}

.login-left__content {
  display: grid;
  gap: 24px;
}

.login-left__eyebrow,
.login-card__eyebrow {
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.login-left__logo {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.login-left h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1.35;
}

.login-left__copy {
  margin: 0;
  max-width: 520px;
  font-size: 16px;
  line-height: 1.85;
  color: rgba(255, 255, 255, 0.92);
}

.login-left__features {
  display: grid;
  gap: 18px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.96);
}

.login-left__footer {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

.login-right {
  width: 55%;
  min-height: 100vh;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.login-card {
  width: min(380px, 100%);
  display: grid;
  gap: 28px;
}

.login-card__header {
  display: grid;
  gap: 10px;
}

.login-card__header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.2;
}

.login-card__header p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
  font-size: 15px;
}

.login-form {
  display: grid;
  gap: 14px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-form :deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.login-form :deep(.el-form-item__content) {
  width: 100%;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e5e6eb inset;
  transition:
    box-shadow 160ms ease,
    border-color 160ms ease;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0f9f92 inset;
}

.login-form__submit {
  width: 100%;
  min-height: 48px;
  margin-top: 8px;
  border-radius: 10px;
  box-shadow: none;
}

.login-form__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 8px;
  color: #86909c;
  font-size: 14px;
}

.login-form__switch {
  border: 0;
  background: transparent;
  color: #165dff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

@media (max-width: 1180px) {
  .login-left {
    padding: 56px 40px 36px;
  }

  .login-left h1 {
    font-size: 34px;
  }
}

@media (max-width: 900px) {
  .login-wrap {
    flex-direction: column;
  }

  .login-left,
  .login-right {
    width: 100%;
  }

  .login-left {
    min-height: 40vh;
    padding: 32px 24px;
  }

  .login-right {
    min-height: 60vh;
    padding: 28px 20px 40px;
  }

  .login-left h1 {
    font-size: 28px;
  }
}
</style>
