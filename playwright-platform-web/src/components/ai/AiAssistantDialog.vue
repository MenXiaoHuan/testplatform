<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { useAiStore, type ChatMessage } from '../../stores/ai'
import { useSpaceStore } from '../../stores/space'
import { useAuthStore } from '../../stores/auth'
import { showAppToast } from '../../utils/ui-feedback'

marked.setOptions({
  gfm: true,
  breaks: false,
})

const TABLE_SEP_RE = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?\s*$/

function isTableSeparator(line: string): boolean {
  return TABLE_SEP_RE.test(line)
}

function protectTablesAndEscape(text: string): string {
  const protectedBlocks: string[] = []
  let working = text.replace(/```[\s\S]*?```/g, (m) => {
    const ph = `\u0000CB${protectedBlocks.length}\u0000`
    protectedBlocks.push(m)
    return ph
  })
  working = working.replace(/`[^`\n]+`/g, (m) => {
    const ph = `\u0000CI${protectedBlocks.length}\u0000`
    protectedBlocks.push(m)
    return ph
  })

  const lines = working.split('\n')
  const protectedRanges: Array<{ start: number; end: number }> = []

  for (let i = 0; i < lines.length - 1; i++) {
    const hasPipe = lines[i].includes('|')
    const nextIsSep = isTableSeparator(lines[i + 1])
    if (hasPipe && nextIsSep) {
      let end = i + 2
      while (end < lines.length && lines[end].includes('|')) end++
      protectedRanges.push({ start: i, end })
      i = end - 1
    }
  }

  const inProtected = (idx: number) =>
    protectedRanges.some(r => idx >= r.start && idx < r.end)

  for (let i = 0; i < lines.length; i++) {
    if (inProtected(i)) continue
    if (lines[i].includes('|')) {
      lines[i] = lines[i].replace(/\|/g, '\\|')
    }
  }

  return lines.join('\n').replace(/\u0000(CB|CI)(\d+)\u0000/g, (_, __, idx) => protectedBlocks[Number(idx)])
}

function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['target'],
  })
}

function renderInline(text?: string | null): string {
  if (!text) return ''
  try {
    return sanitize(marked.parseInline(text) as string)
  } catch {
    return text
  }
}

function renderHighlightedCode(code: string, language?: string | null): string {
  try {
    if (language && hljs.getLanguage(language)) {
      return hljs.highlight(code, { language }).value
    }
    return hljs.highlightAuto(code).value
  } catch {
    return code
  }
}

const aiStore = useAiStore()
const spaceStore = useSpaceStore()
const authStore = useAuthStore()

const inputText = ref('')
const messagesContainerRef = ref<HTMLDivElement | null>(null)
const copiedCodeBlock = ref<string | null>(null)
let copiedCodeResetTimer: ReturnType<typeof setTimeout> | null = null
const sending = computed(() => aiStore.isLoading)
const messages = computed(() => aiStore.messages)
const currentSpaceName = computed<string>(() => spaceStore.currentSpace?.name ?? '当前空间')
const currentUserNickname = computed<string>(() => authStore.user?.nickname ?? authStore.user?.username ?? '用户')
const currentUserAvatar = computed<string | undefined>(() => authStore.user?.avatarUrl ?? undefined)
const avatarLoadError = ref(false)
const showUserAvatar = computed(() => currentUserAvatar.value && !avatarLoadError.value)
const lastMessage = computed(() => messages.value[messages.value.length - 1])
const isStreaming = computed(() => {
  const last = lastMessage.value
  return last?.streaming === true && last.content.length > 0
})

const isThinking = computed(() => {
  const last = lastMessage.value
  return aiStore.loading && last?.streaming === true && last.content.length === 0
})

const transitioning = ref(false)
let transitionTimer: ReturnType<typeof setTimeout> | null = null

const throttledHtml = ref('')
let renderRafId: number | null = null
let pendingContent = ''

function scheduleRender(content: string) {
  pendingContent = content
  if (renderRafId !== null) return
  renderRafId = requestAnimationFrame(() => {
    renderRafId = null
    throttledHtml.value = renderContent(pendingContent)
  })
}

watch(
  () => lastMessage.value?.content ?? '',
  (val) => {
    if (lastMessage.value?.streaming) {
      scheduleRender(val ?? '')
    } else {
      throttledHtml.value = renderContent(val ?? '')
    }
  },
  { immediate: true },
)

watch(
  () => lastMessage.value?.streaming,
  (streaming, wasStreaming) => {
    if (wasStreaming === true && streaming === false) {
      const hasSections = lastMessage.value?.sections && lastMessage.value.sections.length > 0
      if (hasSections) {
        transitioning.value = true
        if (transitionTimer) clearTimeout(transitionTimer)
        transitionTimer = setTimeout(() => {
          transitioning.value = false
        }, 350)
      }
    }
  },
)

const quickActions = computed(() => {
  return [
    { label: '平台介绍', prompt: '请介绍一下这个平台的整体架构、技术栈和主要功能' },
    { label: '列出已有仓库', prompt: '请列出当前空间下所有代码仓库及其状态' },
    { label: '列出已有场景', prompt: '请列出当前空间下所有测试场景及其执行模式' },
  ]
})

let scrollRafId: number | null = null
let scrollBehavior: ScrollBehavior = 'auto'

async function scrollToBottom() {
  await nextTick()
  if (messagesContainerRef.value) {
    const target = messagesContainerRef.value.scrollHeight
    if (scrollRafId !== null) return
    scrollRafId = requestAnimationFrame(() => {
      scrollRafId = null
      messagesContainerRef.value?.scrollTo({
        top: target,
        behavior: scrollBehavior,
      })
      scrollBehavior = 'auto'
    })
  }
}

watch(
  () => messages.value.length,
  () => {
    scrollBehavior = 'auto'
    scrollToBottom()
  },
)

watch(
  () => lastMessage.value?.content?.length,
  () => {
    if (isStreaming.value) {
      scrollBehavior = 'smooth'
    } else {
      scrollBehavior = 'auto'
    }
    scrollToBottom()
  },
)

onMounted(() => {
  scrollToBottom()
})

onBeforeUnmount(() => {
  if (transitionTimer) clearTimeout(transitionTimer)
  if (renderRafId !== null) cancelAnimationFrame(renderRafId)
  if (scrollRafId !== null) cancelAnimationFrame(scrollRafId)
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

function renderContent(content: string): string {
  if (!content) return ''
  try {
    const safe = protectTablesAndEscape(content)
    const html = marked.parse(safe) as string
    return sanitize(html)
  } catch {
    return content.replace(/\n/g, '<br>')
  }
}

function copyCode(code: string, event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  const key = code
  navigator.clipboard.writeText(code).then(() => {
    copiedCodeBlock.value = key
    if (copiedCodeResetTimer) clearTimeout(copiedCodeResetTimer)
    copiedCodeResetTimer = setTimeout(() => {
      copiedCodeBlock.value = null
    }, 2000)
    showAppToast('代码已复制到剪贴板', 'success')
  }).catch(() => {
    showAppToast('复制失败，请手动选择复制', 'error')
  })
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
              {{ currentUserNickname }} · {{ currentSpaceName }}
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
          <h4>你好，有什么我能帮你的吗？</h4>
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
                v-if="msg.content || msg.streaming || (msg.sections && msg.sections.length > 0)"
                class="ai-message__body"
                :class="{
                  'ai-message__body--streaming': msg.streaming,
                  'ai-message__body--transitioning': transitioning,
                }"
              >
                <div
                  v-show="msg.streaming || transitioning"
                  class="ai-message__layer ai-message__layer--streaming"
                  :class="{ 'ai-message__layer--fading': !msg.streaming && transitioning }"
                >
                  <div v-if="msg.content.length === 0 && msg.streaming" class="ai-thinking-block">
                    <span class="ai-thinking-block__text">思考中</span>
                    <span class="ai-thinking-block__dots">
                      <span></span><span></span><span></span>
                    </span>
                  </div>
                  <template v-else>
                    <div v-html="throttledHtml"></div>
                    <span class="ai-cursor" :class="{ 'ai-cursor--fade': !msg.streaming }">|</span>
                  </template>
                </div>
                <div
                  v-show="(!msg.streaming || transitioning) && msg.sections && msg.sections.length > 0"
                  class="ai-message__layer ai-message__layer--sections"
                  :class="{ 'ai-message__layer--entering': transitioning && msg.streaming }"
                >
                  <template v-for="(block, i) in msg.sections" :key="i">
                    <h1 v-if="block.type === 'heading' && block.level === 1" class="ai-sec-heading ai-sec-h1">{{ block.text }}</h1>
                    <h2 v-else-if="block.type === 'heading' && block.level === 2" class="ai-sec-heading ai-sec-h2">{{ block.text }}</h2>
                    <h3 v-else-if="block.type === 'heading' && block.level === 3" class="ai-sec-heading ai-sec-h3">{{ block.text }}</h3>
                    <p v-else-if="block.type === 'paragraph'" class="ai-sec-p" v-html="renderInline(block.text)"></p>
                    <ul v-else-if="block.type === 'list' && !block.ordered" class="ai-sec-ul">
                      <li v-for="(item, j) in block.items" :key="j" v-html="renderInline(item)"></li>
                    </ul>
                    <ol v-else-if="block.type === 'list' && block.ordered" class="ai-sec-ol">
                      <li v-for="(item, j) in block.items" :key="j" v-html="renderInline(item)"></li>
                    </ol>
                    <div v-else-if="block.type === 'code'" class="ai-sec-code-wrap">
                      <div class="ai-sec-code-header">
                        <span class="ai-sec-code-lang">{{ block.language || 'text' }}</span>
                        <button class="ai-sec-copy-btn" type="button" @click="copyCode(block.code || '', $event)">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                          </svg>
                          {{ (copiedCodeBlock === (block.code || '') + (block.language || '')) ? '已复制' : '复制' }}
                        </button>
                      </div>
                      <pre class="ai-sec-pre"><code :class="block.language ? `language-${block.language}` : ''" v-html="renderHighlightedCode(block.code || '', block.language)"></code></pre>
                    </div>
                    <blockquote v-else-if="block.type === 'quote'" class="ai-sec-blockquote" v-html="renderInline(block.text)"></blockquote>
                    <table v-else-if="block.type === 'table'" class="ai-sec-table">
                      <thead v-if="block.headers && block.headers.length > 0">
                        <tr>
                          <th v-for="(h, j) in block.headers" :key="j" v-html="renderInline(h)"></th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(row, j) in block.rows" :key="j">
                          <td v-for="(cell, k) in row" :key="k" v-html="renderInline(cell)"></td>
                        </tr>
                      </tbody>
                    </table>
                  </template>
                </div>
                <div
                  v-show="!msg.streaming && !(msg.sections && msg.sections.length > 0)"
                  class="ai-message__layer"
                >
                  <div v-html="renderContent(msg.content)"></div>
                </div>
              </div>
              <div v-else-if="msg.terminated" class="ai-message__terminated">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="15" y1="9" x2="9" y2="15"/>
                  <line x1="9" y1="9" x2="15" y2="15"/>
                </svg>
                <span>已中止生成</span>
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

              <div v-if="(!msg.streaming && (isToolUsageMessage(msg) || msg.traceId || msg.processingTime))" class="ai-message__tools ai-tools-reveal">
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
          <div class="ai-dialog__input-row">
            <textarea
              v-model="inputText"
              class="ai-dialog__input"
              placeholder="输入你的问题，例如：分析任务 #123 的错误根因"
              :disabled="sending"
              @keydown="handleKeydown"
            />
          </div>
          <div class="ai-dialog__input-footer">
            <span class="ai-dialog__hint">AI 回答基于当前空间数据生成，仅供参考</span>
            <el-button
              v-if="isStreaming || isThinking"
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
              @click="handleSend"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="22" y1="2" x2="11" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </el-button>
          </div>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ai-dialog-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
  padding: 24px;
}

.ai-dialog {
  width: 560px;
  max-width: calc(100vw - 48px);
  height: min(760px, calc(100vh - 48px));
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

/* 使用 CSS Grid 保证用户消息和 AI 消息高度计算完全一致 */
.ai-message {
  display: grid;
  grid-template-columns: 32px 1fr;
  grid-template-areas: "avatar content";
  gap: 8px;
  max-width: 100%;
  min-width: 0;
  overflow: visible;
  align-items: start;
  margin-bottom: 2px;
}

.ai-message--user {
  grid-template-columns: 1fr 32px;
  grid-template-areas: "content avatar";
}

.ai-message__avatar-user,
.ai-message__avatar-ai,
.ai-message__avatar-error {
  grid-area: avatar;
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
  grid-area: content;
  width: 100%;
  min-width: 0;
  overflow: visible;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 100%;
  align-items: flex-start !important;
  padding-top: 1px;
}

.ai-message--user .ai-message__content {
  align-items: flex-end !important;
}

.ai-message--user .ai-message__body {
  text-align: left;
}

.ai-message--user .ai-message__body :deep(p),
.ai-message--user .ai-message__body :deep(li) {
  text-align: left;
}

.ai-message__header {
  display: flex;
  align-items: center;
  gap: 6px;
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
  padding: 8px 12px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
  overflow-wrap: anywhere;
  max-width: 340px;
  box-sizing: border-box;
  position: relative;
}

.ai-message__body > .ai-message__layer > div {
  display: contents;
}

.ai-message__body--streaming > .ai-message__layer--sections {
  display: none;
}

.ai-message__body:not(.ai-message__body--streaming):not(.ai-message__body--transitioning) > .ai-message__layer--streaming {
  display: none;
}

.ai-message__body--transitioning > .ai-message__layer--sections {
  position: absolute;
  inset: 0;
  padding: 8px 12px;
  overflow: hidden;
}

.ai-message__body--transitioning > .ai-message__layer--streaming {
  opacity: 1;
}

.ai-message__body :deep(h1),
.ai-message__body :deep(h2),
.ai-message__body :deep(h3),
.ai-message__body :deep(h4),
.ai-message__body :deep(h5),
.ai-message__body :deep(h6) {
  margin: 12px 0 6px;
  font-weight: 700;
  line-height: 1.35;
  color: #0f172a;
}

.ai-message__body :deep(h1) { font-size: 16px; }
.ai-message__body :deep(h2) { font-size: 15px; }
.ai-message__body :deep(h3) { font-size: 14px; }
.ai-message__body :deep(h4) { font-size: 14px; }
.ai-message__body :deep(h5) { font-size: 13px; }
.ai-message__body :deep(h6) { font-size: 12px; }

.ai-message__body :deep(p) {
  margin: 4px 0;
  line-height: 1.6;
}

.ai-message__body :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-message__body :deep(ul),
.ai-message__body :deep(ol) {
  margin: 6px 0 8px;
  padding-left: 0;
  list-style-position: outside;
  line-height: 1.7;
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
  margin: 3px 0;
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
  margin: 8px 0;
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
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid #14b8a6;
  background: rgba(20, 184, 166, 0.06);
  border-radius: 0 8px 8px 0;
  color: #475569;
  line-height: 1.6;
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
  margin: 8px 0;
  font-size: 12.5px;
  display: block;
  overflow-x: auto;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 0 0 1px #e2e8f0;
}

.ai-message__body :deep(th),
.ai-message__body :deep(td) {
  padding: 7px 10px;
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
  border: 1px solid rgba(15, 159, 146, 0.6);
  border-radius: 14px;
}

.ai-message--assistant .ai-message__body {
  background: #f8fafc;
  color: #0f172a;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.ai-message--error .ai-message__body {
  background: #fef2f2;
  color: #991b1b;
  border: 1px solid #fecaca;
}

.ai-message__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  overflow: visible;
  flex-shrink: 0;
}

.ai-tools-reveal > * {
  opacity: 0;
  animation: ai-tool-pop 300ms ease-out forwards;
}
.ai-tools-reveal > *:nth-child(1) { animation-delay: 200ms; }
.ai-tools-reveal > *:nth-child(2) { animation-delay: 260ms; }
.ai-tools-reveal > *:nth-child(3) { animation-delay: 320ms; }
.ai-tools-reveal > *:nth-child(4) { animation-delay: 380ms; }
.ai-tools-reveal > *:nth-child(5) { animation-delay: 440ms; }
.ai-tools-reveal > *:nth-child(6) { animation-delay: 500ms; }
.ai-tools-reveal > *:nth-child(7) { animation-delay: 560ms; }
.ai-tools-reveal > *:nth-child(8) { animation-delay: 620ms; }

@keyframes ai-tool-pop {
  from { opacity: 0; transform: translateY(3px) scale(0.95); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

.ai-message--user .ai-message__tools {
  justify-content: flex-end;
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
  padding: 8px 12px;
  display: flex;
  align-items: center;
  min-height: 24px;
}

.ai-message__body--streaming {
  position: relative;
}

.ai-message__body--transitioning .ai-message__layer--streaming {
  animation: ai-stream-fade-out 350ms ease-out forwards;
}

.ai-message__body--transitioning .ai-message__layer--sections {
  animation: ai-section-fade-in 350ms ease-out forwards;
}

@keyframes ai-stream-fade-out {
  from { opacity: 1; }
  to   { opacity: 0; }
}

@keyframes ai-section-fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.ai-message__body--empty {
  padding: 8px 12px;
  display: flex;
  align-items: center;
  min-height: 24px;
}

.ai-message__terminated {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  margin-top: 4px;
  background: #fee2e2;
  color: #b91c1c;
  font-size: 12px;
  border-radius: 10px;
  width: fit-content;
  max-width: 100%;
}

.ai-message__terminated svg {
  flex-shrink: 0;
}

.ai-message__layer {
  width: 100%;
  min-width: 0;
}

.ai-cursor {
  display: inline-block;
  margin-left: 2px;
  color: #14b8a6;
  font-weight: 700;
  animation: blink 0.8s step-end infinite;
  transition: opacity 200ms ease;
}

.ai-cursor--fade {
  opacity: 0;
  animation: none;
}

.ai-thinking-block {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: linear-gradient(135deg, #f0fdfa 0%, #ecfdf5 100%);
  border-radius: 10px;
  border: 1px solid #ccfbf1;
  margin: 4px 0;
}

.ai-thinking-block__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #14b8a6;
  color: #ffffff;
  animation: ai-thinking-pulse 2s ease-in-out infinite;
}

.ai-thinking-block__text {
  font-size: 13px;
  font-weight: 500;
  color: #0f766e;
}

.ai-thinking-block__dots {
  display: inline-flex;
  gap: 2px;
}

.ai-thinking-block__dots span {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #14b8a6;
  animation: ai-thinking-dots 1.4s infinite;
}

.ai-thinking-block__dots span:nth-child(2) { animation-delay: 0.2s; }
.ai-thinking-block__dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes ai-thinking-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(0.92); opacity: 0.85; }
}

@keyframes ai-thinking-dots {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
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

.ai-dialog__input-area {
  padding: 12px 16px 16px;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
}

.ai-dialog__input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  padding: 8px 8px 6px;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.ai-dialog__input-wrapper:focus-within {
  border-color: #14b8a6;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.ai-dialog__input-row {
  display: flex;
}

.ai-dialog__input {
  flex: 1 1 auto;
  resize: none;
  padding: 6px 6px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.5;
  min-height: 32px;
  max-height: 120px;
  font-family: inherit;
  outline: none;
  transition: background-color 160ms ease;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.ai-dialog__input::-webkit-scrollbar {
  display: none;
}

.ai-dialog__input::placeholder {
  color: #94a3b8;
}

.ai-dialog__input:disabled {
  cursor: not-allowed;
}

.ai-dialog__input-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 4px;
  position: relative;
  min-height: 32px;
}

.ai-dialog__hint {
  font-size: 10px;
  color: #94a3b8;
  text-align: center;
}

.ai-dialog__thinking-indicator {
  position: absolute;
  right: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #14b8a6;
  font-weight: 500;
}

.ai-spinner {
  animation: ai-spin 0.8s linear infinite;
}

@keyframes ai-spin {
  to { transform: rotate(360deg); }
}

.ai-dialog__send {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: 8px;
  flex-shrink: 0;
}

.ai-dialog__stop {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
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

.ai-message__body .ai-sec-heading {
  margin: 12px 0 6px;
  font-weight: 700;
  line-height: 1.35;
  color: #0f172a;
}
.ai-message__body .ai-sec-h1 { font-size: 16px; }
.ai-message__body .ai-sec-h2 { font-size: 15px; }
.ai-message__body .ai-sec-h3 { font-size: 14px; }

.ai-message__body .ai-sec-p {
  margin: 4px 0;
  line-height: 1.6;
  color: #0f172a;
}

.ai-message__body .ai-sec-p:last-child {
  margin-bottom: 0;
}

.ai-message__body .ai-sec-ul,
.ai-message__body .ai-sec-ol {
  margin: 6px 0 8px;
  padding-left: 1.4em;
  line-height: 1.7;
}
.ai-message__body .ai-sec-ul { list-style: disc; }
.ai-message__body .ai-sec-ol { list-style: decimal; }

.ai-message__body .ai-sec-ul > li,
.ai-message__body .ai-sec-ol > li {
  margin: 3px 0;
  padding-left: 0.3em;
}

.ai-message__body .ai-sec-ul > li::marker {
  color: #14b8a6;
  font-weight: 600;
}

.ai-message__body .ai-sec-code-wrap {
  margin: 8px 0;
  border-radius: 10px;
  overflow: hidden;
  background: #1e293b;
}

.ai-message__body .ai-sec-code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.15);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.ai-message__body .ai-sec-code-lang {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
}

.ai-message__body .ai-sec-copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  font-size: 11px;
  line-height: 1;
  background: rgba(255, 255, 255, 0.08);
  color: #cbd5e1;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s ease, color 0.15s ease;
}
.ai-message__body .ai-sec-copy-btn:hover {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}

.ai-message__body .ai-sec-pre {
  margin: 0;
  padding: 12px 14px;
  background: #1e293b;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  max-width: 100%;
  box-sizing: border-box;
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  white-space: pre;
}

.ai-message__body .ai-sec-blockquote {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid #14b8a6;
  background: rgba(20, 184, 166, 0.06);
  border-radius: 0 8px 8px 0;
  color: #475569;
  line-height: 1.6;
  font-size: 12.5px;
}

.ai-message__body .ai-sec-table {
  width: 100%;
  max-width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12.5px;
  display: block;
  overflow-x: auto;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 0 0 1px #e2e8f0;
}
.ai-message__body .ai-sec-table th,
.ai-message__body .ai-sec-table td {
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  text-align: left;
  line-height: 1.5;
}
.ai-message__body .ai-sec-table th {
  background: #f1f5f9;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
}
.ai-message__body .ai-sec-table tbody tr:nth-child(even) td {
  background: #f8fafc;
}
</style>
