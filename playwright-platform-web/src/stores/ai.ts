import { defineStore } from 'pinia'
import { sendChatMessage, streamChatMessage, clearChatSession, type ChatRequestPayload, type ChatResponseData } from '../api/ai'

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

      if (context?.taskId) {
        const taskId = context.taskId
        const prompt = context.sceneId
          ? `请帮我分析任务 #${taskId} 的错误根因，该任务属于场景 #${context.sceneId}`
          : `请帮我分析任务 #${taskId} 的错误根因`
        void this.sendMessage(prompt, { taskId, sceneId: context.sceneId })
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

      try {
        this.abortStream = streamChatMessage(payload, {
          onMeta: (data) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.traceId = data.traceId
              msg.usedTools = data.usedTools
              msg.responseType = data.responseType
              msg.confidence = data.confidence
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
            this.abortStream = null
            this.loading = false
          },
          onError: (error) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.role = 'error'
              msg.content = error
              msg.streaming = false
            }
            this.abortStream = null
            this.loading = false
          },
        })
      } catch (error: unknown) {
        const msg = this.messages.find(m => m.id === assistantId)
        if (msg) {
          msg.role = 'error'
          msg.content = error instanceof Error ? error.message : '请求失败，请重试'
          msg.streaming = false
        }
        this.loading = false
      }
    },
    stopStreaming() {
      if (this.abortStream) {
        this.abortStream()
        this.abortStream = null
        const lastMsg = this.messages[this.messages.length - 1]
        if (lastMsg && lastMsg.streaming) {
          lastMsg.streaming = false
        }
        this.loading = false
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
