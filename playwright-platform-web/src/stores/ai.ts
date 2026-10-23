import { defineStore } from 'pinia'
import { streamChatMessage, clearChatSession, terminateChatSession, type ChatRequestPayload } from '../api/ai'

export interface ContentBlock {
  type: 'heading' | 'paragraph' | 'list' | 'code' | 'quote' | 'table'
  level?: number | null
  text?: string | null
  items?: string[] | null
  ordered?: boolean | null
  language?: string | null
  code?: string | null
  headers?: string[] | null
  rows?: string[][] | null
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'error'
  content: string
  timestamp: number
  traceId?: string
  usedTools?: string[]
  responseType?: string
  faultDetail?: Record<string, unknown> | null
  confidence?: string | null
  processingTime?: string
  streaming?: boolean
  terminated?: boolean
  sections?: ContentBlock[]
}

let watchdogTimer: ReturnType<typeof setTimeout> | null = null

function clearWatchdog() {
  if (watchdogTimer !== null) {
    clearTimeout(watchdogTimer)
    watchdogTimer = null
  }
}

export const useAiStore = defineStore('ai', {
  state: () => ({
    visible: false,
    messages: [] as ChatMessage[],
    loading: false,
    sessionId: '' as string,
    currentSpaceId: null as number | null,
    currentTaskId: null as number | null,
    currentSceneId: null as number | null,
    abortStream: null as (() => void) | null,
  }),
  getters: {
    hasMessages: (state) => state.messages.length > 0,
    isLoading: (state) => state.loading,
    hasTaskContext: (state) => state.currentTaskId !== null,
  },
  actions: {
    open(spaceId: number, context?: { taskId?: number | null; sceneId?: number | null }) {
      this.currentSpaceId = spaceId
      this.currentTaskId = context?.taskId ?? null
      this.currentSceneId = context?.sceneId ?? null
      this.visible = true
      if (!this.sessionId) {
        this.sessionId = crypto.randomUUID()
      }
    },
    close() {
      this.visible = false
    },
    async sendMessage(message: string, extra?: { taskId?: number; sceneId?: number }) {
      if (this.loading || !message.trim() || this.currentSpaceId === null) {
        return
      }

      this.loading = true
      const userMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: message.trim(),
        timestamp: Date.now(),
      }
      this.messages.push(userMessage)

      const assistantId = crypto.randomUUID()
      const assistantMessage: ChatMessage = {
        id: assistantId,
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
        streaming: true,
      }
      this.messages.push(assistantMessage)

      const effectiveTaskId = extra?.taskId ?? this.currentTaskId ?? undefined
      const effectiveSceneId = extra?.sceneId ?? this.currentSceneId ?? undefined

      const payload: ChatRequestPayload = {
        sessionId: this.sessionId,
        message: message.trim(),
        spaceId: this.currentSpaceId,
        taskId: effectiveTaskId ?? null,
        sceneId: effectiveSceneId ?? null,
        saveHistory: true,
      }

      clearWatchdog()
      watchdogTimer = setTimeout(() => {
        if (!this.loading) {
          return
        }
        this.stopStreaming()
        const msg = this.messages.find(m => m.id === assistantId)
        if (msg) {
          msg.role = 'error'
          msg.content = 'AI 响应超时，请重试'
          msg.streaming = false
        }
      }, 300_000)

      try {
        this.abortStream = streamChatMessage(payload, {
          onMeta: (data) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.traceId = data.traceId
              msg.usedTools = data.usedTools
              msg.responseType = data.responseType
              msg.confidence = data.confidence
              if (data.sections && data.sections.length > 0) {
                msg.sections = data.sections
              }
            }
          },
          onChunk: (chunk) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.content += chunk
            }
          },
          onComplete: (data) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.streaming = false
              msg.processingTime = data.processingTime
            }
            this.sessionId = data.sessionId
            this.finishStream()
          },
          onError: (error) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.role = 'error'
              msg.content = error
              msg.streaming = false
            }
            this.finishStream()
          },
          onDone: () => {
            // 服务端/Nginx 提前断开时可能没有 complete/error 事件，这里兜底释放输入框。
            this.finishStream()
          },
        })
      } catch (error: unknown) {
        const msg = this.messages.find(m => m.id === assistantId)
        if (msg) {
          msg.role = 'error'
          msg.content = error instanceof Error ? error.message : '请求失败，请重试'
          msg.streaming = false
        }
        this.finishStream()
      }
    },
    finishStream() {
      clearWatchdog()
      this.abortStream = null
      this.loading = false
      const lastMsg = this.messages[this.messages.length - 1]
      if (lastMsg?.streaming) {
        lastMsg.streaming = false
      }
    },
    stopStreaming() {
      clearWatchdog()
      if (this.abortStream) {
        this.abortStream()
        this.abortStream = null
        const lastMsg = this.messages[this.messages.length - 1]
        if (lastMsg && lastMsg.streaming) {
          lastMsg.streaming = false
          lastMsg.terminated = true
        }
        this.loading = false
        if (this.sessionId) {
          terminateChatSession(this.sessionId).catch(() => {})
        }
      }
    },
    async clearConversation() {
      this.stopStreaming()
      if (this.sessionId) {
        try {
          await clearChatSession(this.sessionId)
        } catch {
          // ignore
        }
      }
      this.messages = []
      this.sessionId = crypto.randomUUID()
    },
    setContext(spaceId: number, taskId?: number | null, sceneId?: number | null) {
      this.currentSpaceId = spaceId
      this.currentTaskId = taskId ?? null
      this.currentSceneId = sceneId ?? null
      if (!this.sessionId) {
        this.sessionId = crypto.randomUUID()
      }
    },
    setTaskContext(taskId: number | null, sceneId?: number | null) {
      this.currentTaskId = taskId
      this.currentSceneId = sceneId ?? null
    },
    reset() {
      this.stopStreaming()
      this.messages = []
      this.sessionId = ''
      this.currentSpaceId = null
      this.currentTaskId = null
      this.currentSceneId = null
      this.loading = false
      this.visible = false
    },
  },
})
