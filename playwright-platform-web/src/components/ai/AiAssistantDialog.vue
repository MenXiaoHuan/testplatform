<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import { useAiStore, type ChatMessage } from '../../stores/ai'
import { useSpaceStore } from '../../stores/space'
import { useAuthStore } from '../../stores/auth'
import { showAppToast } from '../../utils/ui-feedback'

marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false,
})

const aiStore = useAiStore()
const spaceStore = useSpaceStore()
const authStore = useAuthStore()

const inputText = ref('')
const messagesContainerRef = ref<HTMLDivElement | null>(null)
const sending = computed(() => aiStore.isLoading)
const messages = computed(() => aiStore.messages)
const currentSpaceName = computed(() => spaceStore.currentSpace?.name ?? '当前空间')
const currentUserNickname = computed(() => authStore.user?.nickname ?? authStore.user?.username ?? '用户')
const currentUserAvatar = computed(() => authStore.user?.avatarUrl ?? null)
const avatarLoadError = ref(false)
const showUserAvatar = computed(() => currentUserAvatar.value && !avatarLoadError.value)
const lastMessage = computed(() => messages.value[messages.value.length - 1])
const isStreaming = computed(() => {
  const last = lastMessage.value
  return last?.streaming === true
})

const quickActions = computed(() => {
  const actions: Array<{ label: string; prompt: string }> = []

  if (aiStore.hasTaskContext && aiStore.currentTaskId) {
    const taskId = aiStore.currentTaskId
    actions.push({
      label: `分析当前任务 #${taskId}`,
      prompt: aiStore.currentSceneId
        ? `请帮我分析任务 #${taskId} 的错误根因，该任务属于场景 #${aiStore.currentSceneId}`
        : `请帮我分析任务 #${taskId} 的错误根因`,
    })
    actions.push({
      label: '查看最近失败用例',
      prompt: `请列出任务 #${taskId} 中失败的用例，并分析每个用例的失败原因`,
    })
    actions.push({
      label: '检查执行日志',
      prompt: `请分析任务 #${taskId} 的执行阶段日志，提取关键错误信息`,
    })
  } else {
    actions.push({ label: '列出最近任务', prompt: '请列出最近的测试任务' })
    actions.push({ label: '分析失败任务', prompt: '请分析当前空间最近失败的任务，并给出根因分析' })
    actions.push({ label: '查看场景列表', prompt: '请列出当前空间的所有测试场景' })
  }

  return actions
})

async function scrollToBottom() {
  await nextTick()
  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
  }
}

watch(
  () => messages.value.length,
  () => {
    scrollToBottom()
  },
)

watch(
  () => lastMessage.value?.content?.length,
  () => {
    scrollToBottom()
  },
)

onMounted(() => {
  scrollToBottom()
})

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value || aiStore.currentSpaceId === null) {
    return
  }

  inputText.value = ''
  await aiStore.sendMessage(text)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void handleSend()
  }
}

function handleQuickAction(prompt: string) {
  if (sending.value) {
    return
  }
  void aiStore.sendMessage(prompt)
}

async function handleClear() {
  await aiStore.clearConversation()
}

function handleStopStreaming() {
  aiStore.stopStreaming()
}

function formatTime(timestamp: number) {
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

function renderContent(content: string) {
  try {
    const codeBlocks: string[] = []
    let protectedContent = content.replace(/```[\s\S]*?```|`[^`\n]+`/g, (match) => {
      const placeholder = `\u0000CB${codeBlocks.length}\u0000`
      codeBlocks.push(match)
      return placeholder
    })

    let processed = protectedContent
      .replace(/([a-zA-Z_][a-zA-Z0-9_.]*(?:\.ts|\.js|\.java|\.sql|\.json|\.yml|\.yaml|\.xml))/g, '`$1`')
      .replace(/([A-Z][a-zA-Z]+(?:Exception|Error|Timeout))/g, '`$1`')
      .replace(/\b(locator\.click|locator\.fill|locator|assert|expect|page|browser|context)\b/g, '`$1`')
      .replace(/\b(getTask|getSceneDetail|getRepository|getTestCase|analyzeLogs|getSystemConfig)\b/g, '`$1`')
      .replace(/\b(envCount)=\d+\b/g, '`$1`')
      .replace(/\b(status:\s*(?:\d{3}|"[A-Z_]+")\b/g, '`$1`')
      .replace(/\b(PASS|FAIL|ERROR|SKIP|TIMEOUT)\b/g, '`$1`')

    processed = processed.replace(/\u0000CB(\d+)\u0000/g, (_, idx) => codeBlocks[Number(idx)])

    const raw = marked.parse(processed) as string
    return raw
  } catch {
    return content.replace(/\n/g, '<br>')
  }
}

function isToolUsageMessage(msg: ChatMessage) {
  return msg.role === 'assistant' && msg.usedTools && msg.usedTools.length > 0
}

function isFaultAnalysis(msg: ChatMessage) {
  return msg.responseType === 'FAULT_ANALYSIS' && msg.faultDetail
}

function getResponseTypeLabel(type?: string) {
  switch (type) {
    case 'FAULT_ANALYSIS': return '故障分析'
    case 'BUSINESS_QA': return '业务问答'
    case 'INFORMATION_QUERY': return '信息查询'
    default: return ''
  }
}

function getResponseTypeTagType(type?: string) {
  switch (type) {
    case 'FAULT_ANALYSIS': return 'danger'
    case 'BUSINESS_QA': return ''
    case 'INFORMATION_QUERY': return 'info'
    default: return 'info'
  }
}

function copyTraceId(traceId?: string) {
  if (!traceId) return
  navigator.clipboard.writeText(traceId).then(() => {
    showAppToast('traceId 已复制')
  }).catch(() => {
    showAppToast('复制失败', 'warning')
  })
}
</script>

<template>
  <div v-if="aiStore.visible" class="ai-dialog-overlay" @click.self="aiStore.close()">
    <div class="ai-dialog" role="dialog" aria-label="智能助手">
      <header class="ai-dialog__header">
        <div class="ai-dialog__header-left">
          <div class="ai-dialog__icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2a8 8 0 0 0-8 8v6a4 4 0 0 0 4 4h8a4 4 0 0 0 4-4v-6a8 8 0 0 0-8-8z"/>
              <circle cx="9" cy="11" r="1" fill="currentColor"/>
              <circle cx="15" cy="11" r="1" fill="currentColor"/>
              <path d="M9 15c1 1 2 1.5 3 1.5s2-.5 3-1.5"/>
            </svg>
          </div>
          <div class="ai-dialog__title-group">
            <h3 class="ai-dialog__title">智能测试助手</h3>
            <span class="ai-dialog__subtitle">
              {{ currentUserNickname }} · {{ currentSpaceName }} · AI 驱动
              <template v-if="aiStore.hasTaskContext">
                · 分析任务 #{{ aiStore.currentTaskId }}
              </template>
            </span>
          </div>
        </div>
        <div class="ai-dialog__header-actions">
          <el-button text size="small" @click="handleClear" :disabled="sending">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="margin-right: 2px;">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/>
              <path d="M10 11v6M14 11v6"/>
              <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
            </svg>
            清空
          </el-button>
          <el-button text size="small" @click="aiStore.close()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </el-button>
        </div>
      </header>

      <div ref="messagesContainerRef" class="ai-dialog__messages">
        <div v-if="messages.length === 0" class="ai-dialog__welcome">
          <div class="ai-dialog__welcome-icon">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2a8 8 0 0 0-8 8v6a4 4 0 0 0 4 4h8a4 4 0 0 0 4-4v-6a8 8 0 0 0-8-8z"/>
              <circle cx="9" cy="11" r="1" fill="currentColor"/>
              <circle cx="15" cy="11" r="1" fill="currentColor"/>
              <path d="M9 15c1 1 2 1.5 3 1.5s2-.5 3-1.5"/>
            </svg>
          </div>
          <h4>你好，我是你的测试助手</h4>
          <p>我可以帮你查询任务、分析错误、查看场景信息。试试下面的快捷操作：</p>
          <div class="ai-dialog__quick-actions">
            <el-button
              v-for="action in quickActions"
              :key="action.label"
              size="small"
              round
              :disabled="sending"
              @click="handleQuickAction(action.prompt)"
            >
              {{ action.label }}
            </el-button>
          </div>
        </div>

        <template v-else>
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="ai-message"
            :class="[
              `ai-message--${msg.role}`,
              { 'ai-message--streaming': msg.streaming },
            ]"
          >
            <div class="ai-message__avatar">
              <div v-if="msg.role === 'user'" class="ai-message__avatar-user">
                <img
                  v-if="showUserAvatar"
                  :src="currentUserAvatar"
                  :alt="currentUserNickname"
                  class="ai-avatar-img"
                  @error="avatarLoadError = true"
                />
                <template v-else>
                  {{ currentUserNickname.charAt(0).toUpperCase() }}
                </template>
              </div>
              <div v-else-if="msg.role === 'error'" class="ai-message__avatar-error">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="8" x2="12" y2="12"/>
                  <line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
              </div>
              <div v-else class="ai-message__avatar-ai">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 2a8 8 0 0 0-8 8v6a4 4 0 0 0 4 4h8a4 4 0 0 0 4-4v-6a8 8 0 0 0-8-8z"/>
                  <circle cx="9" cy="11" r="1" fill="currentColor"/>
                  <circle cx="15" cy="11" r="1" fill="currentColor"/>
                </svg>
              </div>
            </div>
            <div class="ai-message__content">
              <div class="ai-message__header">
                <span class="ai-message__role">
                  {{ msg.role === 'user' ? '你' : msg.role === 'error' ? '错误' : '助手' }}
                </span>
                <el-tag
                  v-if="msg.responseType"
                  :type="getResponseTypeTagType(msg.responseType)"
                  size="small"
                  effect="plain"
                  class="ai-message__type-tag"
                >
                  {{ getResponseTypeLabel(msg.responseType) }}
                </el-tag>
                <span class="ai-message__time">{{ formatTime(msg.timestamp) }}</span>
              </div>
              <div
                v-if="msg.content || msg.streaming"
                class="ai-message__body"
                :class="{ 'ai-message__body--streaming': msg.streaming }"
              >
                <div v-html="renderContent(msg.content)"></div>
                <span v-if="msg.streaming" class="ai-cursor">|</span>
              </div>
              <div v-else class="ai-message__body ai-message__body--empty">
                <div class="ai-typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>

              <div v-if="isFaultAnalysis(msg) && !msg.streaming" class="ai-fault-card">
                <div class="ai-fault-card__header">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/>
                    <line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                  <span>故障诊断详情</span>
                </div>
                <div class="ai-fault-card__grid">
                  <div class="ai-fault-card__item" v-if="msg.faultDetail?.fault_type">
                    <span class="ai-fault-card__label">故障类型</span>
                    <span class="ai-fault-card__value ai-fault-card__value--type">{{ msg.faultDetail.fault_type as string }}</span>
                  </div>
                  <div class="ai-fault-card__item" v-if="msg.faultDetail?.root_cause">
                    <span class="ai-fault-card__label">根因分析</span>
                    <span class="ai-fault-card__value">{{ msg.faultDetail.root_cause as string }}</span>
                  </div>
                  <div class="ai-fault-card__item" v-if="msg.faultDetail?.immediate_solution">
                    <span class="ai-fault-card__label">临时方案</span>
                    <span class="ai-fault-card__value">{{ msg.faultDetail.immediate_solution as string }}</span>
                  </div>
                  <div class="ai-fault-card__item" v-if="msg.faultDetail?.long_term_optimize">
                    <span class="ai-fault-card__label">长期优化</span>
                    <span class="ai-fault-card__value">{{ msg.faultDetail.long_term_optimize as string }}</span>
                  </div>
                  <div class="ai-fault-card__item" v-if="msg.faultDetail?.test_risk">
                    <span class="ai-fault-card__label">测试风险</span>
                    <span class="ai-fault-card__value">{{ msg.faultDetail.test_risk as string }}</span>
                  </div>
                  <div class="ai-fault-card__item" v-if="msg.faultDetail?.reproduce_steps">
                    <span class="ai-fault-card__label">复现步骤</span>
                    <span class="ai-fault-card__value">{{ msg.faultDetail.reproduce_steps as string }}</span>
                  </div>
                </div>
              </div>

              <div v-if="(!msg.streaming && (isToolUsageMessage(msg) || msg.traceId || msg.processingTime))" class="ai-message__tools">
                <template v-if="isToolUsageMessage(msg)">
                  <el-tag
                    v-for="tool in msg.usedTools"
                    :key="tool"
                    size="small"
                    type="info"
                    effect="plain"
                    class="ai-message__tool-tag"
                  >
                    {{ tool }}
                  </el-tag>
                </template>
                <el-tag v-if="msg.confidence" size="small" :type="msg.confidence === 'HIGH' ? 'success' : msg.confidence === 'MEDIUM' ? 'warning' : 'danger'" effect="plain">
                  置信度: {{ msg.confidence }}
                </el-tag>
                <el-tag v-if="msg.processingTime" size="small" type="success" effect="plain">
                  {{ msg.processingTime }}
                </el-tag>
                <el-tooltip
                  v-if="msg.traceId"
                  placement="top"
                  :show-after="200"
                  effect="dark"
                  :show-arrow="true"
                >
                  <template #content>
                    <div class="ai-trace-tooltip">
                      <div class="ai-trace-tooltip__label">traceId</div>
                      <div class="ai-trace-tooltip__value">{{ msg.traceId }}</div>
                      <div class="ai-trace-tooltip__hint">点击标签复制完整 ID</div>
                    </div>
                  </template>
                  <el-tag
                    size="small"
                    type="info"
                    effect="plain"
                    class="ai-message__trace-id"
                    @click="copyTraceId(msg.traceId)"
                  >
                    <span class="ai-message__trace-label">traceId:</span>
                    <span class="ai-message__trace-value">{{ msg.traceId.slice(0, 8) }}...</span>
                  </el-tag>
                </el-tooltip>
              </div>
            </div>
          </div>
        </template>
      </div>

      <footer class="ai-dialog__input-area">
        <div class="ai-dialog__input-wrapper">
          <textarea
            v-model="inputText"
            class="ai-dialog__input"
            placeholder="输入你的问题，例如：分析任务 #123 的错误根因"
            :disabled="sending"
            @keydown="handleKeydown"
          />
          <el-button
            v-if="isStreaming"
            type="danger"
            class="ai-dialog__stop"
            size="small"
            @click="handleStopStreaming"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
              <rect x="4" y="4" width="16" height="16" rx="2" ry="2"/>
            </svg>
          </el-button>
          <el-button
            v-else
            type="primary"
            class="ai-dialog__send"
            :disabled="!inputText.trim() || sending"
            :loading="sending"
            @click="handleSend"
          >
            <svg v-if="!sending" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </el-button>
        </div>
        <p class="ai-dialog__hint">AI 回答基于当前空间数据生成，仅供参考</p>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ai-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
  padding: 24px;
}

.ai-dialog {
  width: 440px;
  max-width: calc(100vw - 48px);
  height: min(640px, calc(100vh - 48px));
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
  overflow: hidden;
  box-sizing: border-box;
}

.ai-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.08), rgba(20, 184, 166, 0.02));
}

.ai-dialog__header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-dialog__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #14b8a6, #0f9f92);
  color: #ffffff;
}

.ai-dialog__title-group {
  display: grid;
  gap: 2px;
}

.ai-dialog__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.ai-dialog__subtitle {
  font-size: 12px;
  color: #64748b;
}

.ai-dialog__header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-dialog__messages {
  flex: 1 1 auto;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 20px;
  display: grid;
  gap: 16px;
  align-content: start;
  min-width: 0;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.ai-dialog__messages::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.ai-dialog__welcome {
  display: grid;
  gap: 12px;
  place-items: center;
  text-align: center;
  padding: 32px 16px;
}

.ai-dialog__welcome-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.12), rgba(20, 184, 166, 0.04));
  color: #14b8a6;
}

.ai-dialog__welcome h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.ai-dialog__welcome p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.ai-dialog__quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 8px;
}

.ai-message {
  display: flex;
  gap: 10px;
  max-width: 100%;
  min-width: 0;
  overflow: visible;
}

.ai-message--user {
  flex-direction: row-reverse;
}

.ai-message--error {
  opacity: 0.95;
}

.ai-message__avatar {
  flex-shrink: 0;
}

.ai-message__avatar-user,
.ai-message__avatar-ai,
.ai-message__avatar-error {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
}

.ai-message__avatar-user {
  background: linear-gradient(135deg, #14b8a6, #0f9f92);
  color: #ffffff;
  font-weight: 600;
  font-size: 14px;
}

.ai-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
  display: block;
}

.ai-message__avatar-ai {
  background: linear-gradient(135deg, #14b8a6, #0f9f92);
  color: #ffffff;
}

.ai-message__avatar-error {
  background: #fef2f2;
  color: #ef4444;
}

.ai-message__content {
  min-width: 0;
  flex: 1 1 auto;
  overflow: visible;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-message--user .ai-message__content {
  align-items: flex-end;
  display: grid;
}

.ai-message__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ai-message--user .ai-message__header {
  justify-content: flex-end;
}

.ai-message__role {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.ai-message__time {
  font-size: 11px;
  color: #94a3b8;
}

.ai-message__body {
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
  overflow-wrap: anywhere;
  max-width: 100%;
  box-sizing: border-box;
}

.ai-message__body :deep(h1),
.ai-message__body :deep(h2),
.ai-message__body :deep(h3),
.ai-message__body :deep(h4),
.ai-message__body :deep(h5),
.ai-message__body :deep(h6) {
  margin: 14px 0 8px;
  font-weight: 700;
  line-height: 1.35;
  color: #0f172a;
}

.ai-message__body :deep(h1) { font-size: 18px; }
.ai-message__body :deep(h2) { font-size: 16px; }
.ai-message__body :deep(h3) { font-size: 15px; }
.ai-message__body :deep(h4) { font-size: 14px; }
.ai-message__body :deep(h5) { font-size: 13px; }
.ai-message__body :deep(h6) { font-size: 12px; }

.ai-message__body :deep(p) {
  margin: 0 0 10px;
  line-height: 1.75;
}

.ai-message__body :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-message__body :deep(ul),
.ai-message__body :deep(ol) {
  margin: 8px 0 10px;
  padding-left: 0;
  list-style-position: outside;
  line-height: 1.75;
}

.ai-message__body :deep(ul) {
  list-style: disc;
  padding-left: 1.4em;
}

.ai-message__body :deep(ol) {
  list-style: decimal;
  padding-left: 1.4em;
}

.ai-message__body :deep(li) {
  margin: 4px 0;
  padding-left: 0.3em;
  line-height: 1.7;
}

.ai-message__body :deep(li > ul),
.ai-message__body :deep(li > ol) {
  margin: 4px 0 4px;
  padding-left: 1.2em;
}

.ai-message__body :deep(li::marker) {
  color: #14b8a6;
  font-weight: 600;
}

.ai-message__body :deep(strong) {
  font-weight: 700;
  word-break: break-word;
}

.ai-message__body :deep(em) {
  font-style: italic;
}

.ai-message__body :deep(code) {
  padding: 2px 7px;
  border-radius: 6px;
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
  font-size: 12.5px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  word-break: break-all;
}

.ai-message__body :deep(pre) {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 12px;
  background: #1e293b;
  color: #e2e8f0;
  font-size: 12.5px;
  line-height: 1.65;
  overflow-x: auto;
  max-width: 100%;
  box-sizing: border-box;
}

.ai-message__body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: inherit;
  line-height: inherit;
  white-space: pre;
}

.ai-message__body :deep(blockquote) {
  margin: 10px 0;
  padding: 10px 14px;
  border-left: 3px solid #14b8a6;
  background: rgba(20, 184, 166, 0.06);
  border-radius: 0 10px 10px 0;
  color: #475569;
  line-height: 1.7;
}

.ai-message__body :deep(a) {
  color: #0f766e;
  text-decoration: underline;
  word-break: break-all;
}

.ai-message__body :deep(a:hover) {
  color: #0d9488;
}

.ai-message__body :deep(hr) {
  margin: 14px 0;
  border: none;
  border-top: 1px solid #e2e8f0;
}

.ai-message__body :deep(table) {
  width: 100%;
  max-width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 12.5px;
  display: block;
  overflow-x: auto;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 0 0 1px #e2e8f0;
}

.ai-message__body :deep(th),
.ai-message__body :deep(td) {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  text-align: left;
  line-height: 1.5;
}

.ai-message__body :deep(th) {
  background: #f1f5f9;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
}

.ai-message__body :deep(tr:nth-child(even) td) {
  background: #f8fafc;
}

.ai-message--user .ai-message__body :deep(a) {
  color: #ffffff;
}

.ai-message--user .ai-message__body {
  background: linear-gradient(135deg, #14b8a6, #0f9f92);
  color: #ffffff;
  border-bottom-right-radius: 4px;
}

.ai-message--assistant .ai-message__body {
  background: #f8fafc;
  color: #0f172a;
  border: 1px solid #e2e8f0;
  border-bottom-left-radius: 4px;
}

.ai-message--error .ai-message__body {
  background: #fef2f2;
  color: #991b1b;
  border: 1px solid #fecaca;
  border-bottom-left-radius: 4px;
}

.ai-message__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  width: 100%;
  overflow: visible;
  flex-shrink: 0;
}

.ai-message__type-tag {
  margin-left: 4px;
}

.ai-message__tool-tag {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 11px;
  flex-shrink: 0;
}

.ai-message__trace-id {
  cursor: pointer;
  user-select: none;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  transition: all 0.2s ease;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}

.ai-message__trace-id:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(20, 184, 166, 0.35);
  background: rgba(20, 184, 166, 0.15) !important;
  border-color: #14b8a6 !important;
}

.ai-message__trace-label {
  opacity: 0.7;
  font-size: 11px;
}

.ai-message__trace-value {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 11px;
}

:deep(.ai-trace-tooltip) {
  max-width: 280px;
  padding: 4px 0;
}

:deep(.ai-trace-tooltip__label) {
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

:deep(.ai-trace-tooltip__value) {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 12px;
  color: #fff;
  word-break: break-all;
  line-height: 1.5;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  margin-bottom: 4px;
}

:deep(.ai-trace-tooltip__hint) {
  font-size: 10.5px;
  color: rgba(255, 255, 255, 0.55);
}

.ai-fault-card {
  margin-top: 10px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(254, 226, 226, 0.4), rgba(254, 242, 242, 0.6));
  overflow: hidden;
}

.ai-fault-card__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(239, 68, 68, 0.08);
  color: #b91c1c;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid #fecaca;
}

.ai-fault-card__grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}

.ai-fault-card__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 14px;
  border-bottom: 1px solid rgba(254, 202, 202, 0.5);
}

.ai-fault-card__item:last-child {
  border-bottom: none;
}

.ai-fault-card__label {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.ai-fault-card__value {
  font-size: 13px;
  color: #1f2937;
  line-height: 1.6;
  word-break: break-word;
}

.ai-fault-card__value--type {
  color: #b91c1c;
  font-weight: 600;
}

.ai-message--loading .ai-message__body {
  padding: 16px;
  display: flex;
  align-items: center;
  min-height: 32px;
}

.ai-message__body--streaming {
  position: relative;
}

.ai-message__body--empty {
  padding: 16px;
  display: flex;
  align-items: center;
  min-height: 32px;
}

.ai-cursor {
  display: inline-block;
  margin-left: 2px;
  color: #14b8a6;
  font-weight: 700;
  animation: blink 0.8s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.ai-typing-indicator {
  display: flex;
  gap: 4px;
}

.ai-typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  animation: typing 1.4s infinite;
}

.ai-typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.ai-typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.ai-code-block {
  margin: 8px 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: #1e293b;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
}

.ai-code-block code {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}

.ai-inline-code {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(20, 184, 166, 0.1);
  color: #0f766e;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}

.ai-dialog__input-area {
  padding: 12px 16px 16px;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
}

.ai-dialog__input-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.ai-dialog__input {
  flex: 1 1 auto;
  resize: none;
  padding: 10px 14px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.5;
  min-height: 40px;
  max-height: 120px;
  font-family: inherit;
  outline: none;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.ai-dialog__input::placeholder {
  color: #94a3b8;
}

.ai-dialog__input:focus {
  border-color: #14b8a6;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.ai-dialog__send {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 12px;
  flex-shrink: 0;
}

.ai-dialog__stop {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-dialog__hint {
  margin: 8px 0 0;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}

@media (max-width: 520px) {
  .ai-dialog-overlay {
    padding: 0;
    align-items: stretch;
  }
  .ai-dialog {
    width: 100%;
    max-width: 100%;
    height: 100%;
    border-radius: 0;
    border: none;
  }
}
</style>
