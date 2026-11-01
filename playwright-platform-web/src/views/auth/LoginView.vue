<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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

const submitLabel = computed(() => (registerMode.value ? '创建账号' : '登录'))
const registrationEnabled = computed(() => authStore.publicKey?.registrationEnabled === true)

onMounted(() => {
  // 后端默认关闭注册；先拿公钥，同时决定是否展示注册入口。
  void authStore.ensurePublicKey().catch(() => {})
})

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
  if (!registrationEnabled.value) {
    showAppToast('注册已关闭，请联系管理员', 'warning')
    return
  }
  registerMode.value = !registerMode.value
}
</script>

<template>
  <section class="login-page">
    <div class="login-wrap">
      <aside class="login-left">
        <div class="login-left__inner">
          <div class="brand">
            <span class="brand__mark">易</span>
            <div class="brand__meta">
              <span class="brand__name">易测</span>
              <span class="brand__tag">AI QUALITY WORKSPACE</span>
            </div>
          </div>

          <div class="hero">
            <p class="hero__eyebrow">自动化测试 · 大模型评测</p>
            <h1>让每一次调用<br />都可评测、可追踪</h1>
            <p class="hero__copy">
              统一管理 UI 自动化场景、Prompt 版本、评测集与 Trace：从浏览器执行到离线 / 在线评分，团队在一个工作台完成质量闭环。
            </p>
          </div>

          <ul class="features">
            <li class="feature">
              <span class="feature__dot" />
              <div class="feature__text">
                <strong>接入与追踪</strong>
                <span>Agent 接入，在线 Trace 全链路回溯</span>
              </div>
            </li>
            <li class="feature">
              <span class="feature__dot" />
              <div class="feature__text">
                <strong>Prompt 管理</strong>
                <span>版本对比、灰度发布与 Pull API</span>
              </div>
            </li>
            <li class="feature">
              <span class="feature__dot" />
              <div class="feature__text">
                <strong>评测闭环</strong>
                <span>内置评测集、批量离线与线上策略</span>
              </div>
            </li>
            <li class="feature">
              <span class="feature__dot" />
              <div class="feature__text">
                <strong>UI 自动化执行</strong>
                <span>场景调度、多浏览器执行与结果归档</span>
              </div>
            </li>
          </ul>
        </div>

        <p class="login-left__footer">© 2026 易测 · AI 质量与评测工作台</p>
      </aside>

      <main class="login-right">
        <div class="login-card">
          <div class="login-card__header">
            <p class="login-card__eyebrow">测试与评测工作台</p>
            <h2>{{ registerMode ? '创建账号' : '欢迎回来' }}</h2>
            <p>
              {{ registerMode ? '创建账号后将自动为你生成个人工作空间。' : '输入账号信息，继续你的测试与评测工作。' }}
            </p>
          </div>

          <el-form class="login-form" label-position="top" @submit.prevent="submit">
            <template v-if="registerMode">
              <el-form-item label="用户名">
                <el-input v-model="registerForm.username" placeholder="请输入用户名" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="registerForm.password" type="password" show-password placeholder="请输入密码" autocomplete="new-password" />
              </el-form-item>
              <el-form-item label="确认密码">
                <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" autocomplete="new-password" />
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="用户名">
                <el-input v-model="loginForm.username" placeholder="请输入用户名" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" autocomplete="current-password" />
              </el-form-item>
            </template>

            <el-button class="login-form__submit" type="primary" :loading="loading" @click="submit">
              {{ submitLabel }}
            </el-button>
            <div class="login-form__footer">
              <template v-if="registrationEnabled">
                <span>{{ registerMode ? '已有账号？' : '还没有账号？' }}</span>
                <button class="login-form__switch" type="button" @click="toggleMode">
                  {{ registerMode ? '去登录' : '立即注册' }}
                </button>
              </template>
              <span v-else>注册已关闭，请联系管理员</span>
            </div>
          </el-form>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f1f6f5;
}

.login-wrap {
  display: flex;
  min-height: 100vh;
}

.login-left {
  position: relative;
  width: 46%;
  min-height: 100vh;
  padding: 48px 56px 32px;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  background:
    radial-gradient(circle at 88% 8%, rgba(255, 255, 255, 0.22), transparent 42%),
    radial-gradient(circle at 0% 100%, rgba(3, 64, 60, 0.55), transparent 46%),
    linear-gradient(150deg, #0b7f76 0%, #0f9f92 52%, #17b7a8 100%);
}

.login-left::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.07) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.65), transparent 72%);
  pointer-events: none;
}

.login-left__inner {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 40px;
  margin: auto 0;
  max-width: 520px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0;
}

.brand__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand__name {
  font-size: 17px;
  font-weight: 600;
}

.brand__tag {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.66);
}

.hero {
  display: grid;
  gap: 14px;
}

.hero__eyebrow {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.72);
}

.hero h1 {
  margin: 0;
  font-size: 40px;
  line-height: 1.26;
  letter-spacing: -0.01em;
}

.hero__copy {
  margin: 0;
  max-width: 460px;
  font-size: 15px;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.88);
}

.features {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.feature {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(6px);
}

.feature__dot {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  margin-top: 7px;
  border-radius: 50%;
  background: #d7fff7;
  box-shadow: 0 0 0 4px rgba(215, 255, 247, 0.16);
}

.feature__text {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.feature__text strong {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
}

.feature__text span {
  font-size: 12px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.78);
}

.login-left__footer {
  position: relative;
  z-index: 1;
  margin: 32px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.login-right {
  flex: 1;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background:
    radial-gradient(circle at 100% 0%, rgba(15, 159, 146, 0.08), transparent 38%),
    #f4f8f7;
}

.login-card {
  width: min(420px, 100%);
  padding: 40px 36px 32px;
  display: grid;
  gap: 28px;
  background: #ffffff;
  border: 1px solid #e4eceb;
  border-radius: 18px;
  box-shadow: 0 22px 55px rgba(15, 23, 42, 0.08);
}

.login-card__header {
  display: grid;
  gap: 10px;
}

.login-card__eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.16em;
  color: #0f9f92;
}

.login-card__header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.25;
  font-weight: 700;
}

.login-card__header p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
  font-size: 14px;
}

.login-form {
  display: grid;
  gap: 16px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  padding-bottom: 6px;
}

.login-form :deep(.el-form-item__content) {
  width: 100%;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 10px;
  background: #f8fafc;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  transition:
    box-shadow 160ms ease,
    background-color 160ms ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #0f9f92 inset;
}

.login-form :deep(.el-input__inner) {
  font-size: 14px;
}

.login-form__submit {
  width: 100%;
  min-height: 46px;
  margin-top: 6px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  box-shadow: 0 8px 20px rgba(15, 159, 146, 0.22);
}

.login-form__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 4px;
  color: #86909c;
  font-size: 13px;
}

.login-form__switch {
  border: 0;
  background: transparent;
  color: #0f9f92;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}

.login-form__switch:hover {
  text-decoration: underline;
}

@media (max-width: 1180px) {
  .login-left {
    padding: 44px 40px 28px;
  }

  .hero h1 {
    font-size: 34px;
  }
}

@media (max-width: 960px) {
  .login-wrap {
    flex-direction: column;
  }

  .login-left,
  .login-right {
    width: 100%;
    min-height: auto;
  }

  .login-left {
    padding: 28px 24px;
  }

  .login-left__inner {
    gap: 22px;
    margin: 0;
  }

  .hero__copy,
  .features {
    display: none;
  }

  .hero h1 {
    font-size: 27px;
  }

  .login-left__footer {
    margin-top: 20px;
  }

  .login-right {
    flex: 1;
    padding: 24px 16px 40px;
    align-items: flex-start;
  }

  .login-card {
    padding: 28px 22px 24px;
    border-radius: 16px;
    box-shadow: 0 12px 32px rgba(15, 23, 42, 0.07);
  }
}
</style>
