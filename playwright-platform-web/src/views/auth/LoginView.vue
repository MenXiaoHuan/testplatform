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
  nickname: '',
  password: '',
  confirmPassword: '',
})

const cardTitle = computed(() => (registerMode.value ? '注册账号' : '账号登录'))
const submitLabel = computed(() => (registerMode.value ? '注册并进入个人空间' : '登录并进入首页'))

async function submit() {
  loading.value = true
  try {
    if (registerMode.value) {
      if (!registerForm.username.trim() || !registerForm.nickname.trim() || !registerForm.password || !registerForm.confirmPassword) {
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
        nickname: registerForm.nickname.trim(),
        password: registerForm.password,
        confirmPassword: registerForm.confirmPassword,
      })
      await router.push(user?.lastSpaceId ? `/spaces/${user.lastSpaceId}/repos` : '/home')
      return
    }

    if (!loginForm.username.trim() || !loginForm.password) {
      showAppToast('请输入用户名和密码', 'warning')
      return
    }

    await authStore.login({
      username: loginForm.username.trim(),
      password: loginForm.password,
    })
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
    <div class="login-page__orb login-page__orb--primary" />
    <div class="login-page__orb login-page__orb--secondary" />

    <div class="login-layout">
      <section class="login-showcase">
        <p class="login-showcase__eyebrow">Platform Overview</p>
        <h1>测试平台</h1>
        <p class="login-showcase__copy">
          一个面向自动化测试协作的统一工作台。登录后进入空间首页，
          再按空间切换仓库、场景、任务与调度事件，保持团队协作、执行链路和状态追踪在同一处完成。
        </p>

        <div class="login-showcase__section">
          <span class="login-showcase__section-line" />
          <span>产品亮点</span>
        </div>

        <div class="login-showcase__highlights">
          <article class="highlight-card">
            <span class="highlight-card__badge">01</span>
            <h2>空间化协作</h2>
            <p>仓库、场景、任务和调度事件统一归属空间，协作边界更清晰。</p>
          </article>
          <article class="highlight-card">
            <span class="highlight-card__badge">02</span>
            <h2>任务与调度联动</h2>
            <p>从场景执行到调度异常重试，都能在同一条工作流里追踪状态。</p>
          </article>
          <article class="highlight-card">
            <span class="highlight-card__badge">03</span>
            <h2>安全登录与留痕</h2>
            <p>支持口令加密传输、设备隔离登录态和服务端会话续期。</p>
          </article>
        </div>

      </section>

      <section class="login-card">
        <div class="login-card__header">
          <p class="login-card__eyebrow">Account Sign In</p>
          <h2>{{ cardTitle }}</h2>
          <p>{{ registerMode ? '创建账号后会自动拥有个人空间，并直接进入你的空间工作台。' : '输入账号信息，进入你的空间工作台与执行看板。' }}</p>
        </div>

        <el-form class="login-form" label-position="top" @submit.prevent="submit">
          <template v-if="registerMode">
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="registerForm.nickname" placeholder="请输入昵称" />
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
          <button class="login-form__switch" type="button" @click="toggleMode">
            {{ registerMode ? '已有账号，去登录' : '没有账号？注册一个' }}
          </button>
        </el-form>
      </section>
    </div>
  </section>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: grid;
  align-items: center;
  padding: 40px;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.14), transparent 28%),
    linear-gradient(180deg, #f8fbfc 0%, #eef4f6 100%);
}

.login-page__orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(4px);
  opacity: 0.75;
  pointer-events: none;
  animation: float 8s ease-in-out infinite;
}

.login-page__orb--primary {
  top: 72px;
  left: 72px;
  width: 220px;
  height: 220px;
  background: radial-gradient(circle, rgba(20, 184, 166, 0.18) 0%, transparent 70%);
}

.login-page__orb--secondary {
  right: 72px;
  bottom: 72px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(45, 212, 191, 0.18) 0%, transparent 72%);
  animation-delay: -2s;
}

.login-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(380px, 460px);
  gap: 28px;
  align-items: stretch;
  width: min(1240px, 100%);
  margin: 0 auto;
}

.login-showcase {
  display: grid;
  align-content: center;
  gap: 24px;
  padding: 28px 8px 28px 4px;
}

.login-showcase__eyebrow,
.login-card__eyebrow {
  margin: 0;
  color: #0f9f92;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.login-showcase h1 {
  margin: 0;
  font-size: 52px;
  line-height: 1.05;
  color: #0f172a;
}

.login-showcase__copy {
  max-width: 720px;
  margin: 0;
  color: #475569;
  font-size: 16px;
  line-height: 1.8;
}

.login-showcase__section {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-showcase__section-line {
  width: 36px;
  height: 1px;
  background: rgba(20, 184, 166, 0.42);
}

.login-showcase__highlights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.highlight-card {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    border-color 180ms ease;
}

.highlight-card:hover {
  transform: translateY(-4px);
  border-color: rgba(20, 184, 166, 0.26);
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.12);
}

.highlight-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #14b8a6 0%, #2dd4bf 100%);
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
}

.highlight-card h2 {
  margin: 0;
  font-size: 18px;
  color: #111827;
}

.highlight-card p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
  font-size: 14px;
}

.login-card {
  position: relative;
  display: grid;
  gap: 20px;
  align-content: start;
  padding: 32px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.96);
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(12px);
}

.login-card__header {
  display: grid;
  gap: 10px;
}

.login-card__header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
}

.login-card__header p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.login-form {
  display: grid;
  gap: 12px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #334155;
}

.login-form :deep(.el-form-item__content) {
  width: 100%;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px;
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.95) inset;
  transition:
    box-shadow 160ms ease,
    transform 160ms ease;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px rgba(20, 184, 166, 0.2),
    0 0 0 1px rgba(20, 184, 166, 0.95) inset;
  transform: translateY(-1px);
}

.login-form__submit {
  width: 100%;
  min-height: 46px;
  margin-top: 4px;
  border-radius: 14px;
  box-shadow: 0 14px 28px rgba(20, 184, 166, 0.22);
}

.login-form__switch {
  border: 0;
  background: transparent;
  color: #0f9f92;
  font-size: 14px;
  font-weight: 600;
  justify-self: start;
  cursor: pointer;
  padding: 0;
}

@keyframes float {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0, -14px, 0);
  }
}

@media (max-width: 1180px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .login-showcase__highlights {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .login-page {
    padding: 24px;
  }

  .login-showcase {
    padding: 0;
  }

  .login-showcase h1 {
    font-size: 40px;
  }
}
</style>
