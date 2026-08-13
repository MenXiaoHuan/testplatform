import { defineStore } from 'pinia'
import { sendChatMessage, streamChatMessage, clearChatSession, type ChatRequestPayload, type ChatResponseData } from '../api/ai'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'error'
  content: string
  timestamp: number
  usedTools?: string[]
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
    abortStream: null as (() => void) | null,
  }),
  getters: {
    hasMessages: (state) => state.messages.length > 0,
    isLoading: (state) => state.loading,
  },
  actions: {
    open(spaceId: number) {
      this.currentSpaceId = spaceId
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

      const payload: ChatRequestPayload = {
        sessionId: this.sessionId,
        message: message.trim(),
        spaceId: this.currentSpaceId,
        taskId: extra?.taskId ?? null,
        sceneId: extra?.sceneId ?? null,
        saveHistory: true,
      }

      try {
        this.abortStream = streamChatMessage(payload, {
          onMeta: (data) => {
            const msg = this.messages.find(m => m.id === assistantId)
            if (msg) {
              msg.usedTools = data.usedTools
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
      if (!this.sessionId) {
        this.sessionId = crypto.randomUUID()
      }
      if (this.messages.length === 0 && taskId) {
        this.messages.push({
          id: crypto.randomUUID(),
          role: 'user',
          content: `请帮我分析任务 #${taskId} 的错误`,
          timestamp: Date.now(),
        })
      }
    },
    reset() {
      this.stopStreaming()
      this.messages = []
      this.sessionId = ''
      this.currentSpaceId = null
      this.loading = false
      this.visible = false
    },
  },
})
