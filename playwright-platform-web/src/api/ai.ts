import { del, get, post } from './http'

export interface ChatRequestPayload {
  sessionId?: string
  message: string
  taskId?: number | null
  sceneId?: number | null
  spaceId: number
  saveHistory?: boolean
}

export interface ChatResponseData {
  traceId: string
  response: string
  usedTools: string[]
  confidence: string | null
  responseType: string
  faultDetail: Record<string, unknown> | null
  taskId: number | null
  sceneId: number | null
  processingTime: string
  sessionId: string
  compressed: boolean
}

export type StreamEventHandler = (data: unknown) => void

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

export interface StreamHandlers {
  onMeta?: (data: { traceId: string; usedTools: string[]; confidence: string | null; responseType: string; sections?: ContentBlock[] }) => void
  onChunk: (chunk: string) => void
  onComplete?: (data: { traceId: string; processingTime: string; sessionId: string }) => void
  onError?: (error: string) => void
}

export const sendChatMessage = async (payload: ChatRequestPayload) => {
  return post<ChatResponseData>('/ai/chat', payload)
}

export const streamChatMessage = (payload: ChatRequestPayload, handlers: StreamHandlers): (() => void) => {
  const controller = new AbortController()
  const { signal } = controller

  const fetchUrl = '/api/ai/chat/stream'
  
  fetch(fetchUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify(payload),
    signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      
      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('No response body')
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.slice(6).trim()
          } else if (trimmed.startsWith('data:')) {
            const dataStr = trimmed.slice(5).trim()
            if (!dataStr) continue
            
            try {
              const parsed = JSON.parse(dataStr)
              switch (currentEvent) {
                case 'meta':
                  handlers.onMeta?.(parsed as { traceId: string; usedTools: string[]; confidence: string | null; responseType: string })
                  break
                case 'chunk':
                  handlers.onChunk(typeof parsed === 'string' ? parsed : dataStr)
                  break
                case 'complete':
                  handlers.onComplete?.(parsed as { traceId: string; processingTime: string; sessionId: string })
                  break
                case 'error':
                  handlers.onError?.((parsed as { error: string }).error)
                  break
              }
            } catch {
              if (currentEvent === 'chunk') {
                handlers.onChunk(dataStr)
              }
            }
            currentEvent = ''
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        handlers.onError?.(err.message ?? 'Stream request failed')
      }
    })

  return () => controller.abort()
}

export const clearChatSession = async (sessionId: string) => {
  return del(`/ai/session/${sessionId}`)
}

export const terminateChatSession = async (sessionId: string) => {
  return post(`/ai/session/${sessionId}/terminate`)
}

/** 单条 trace 日志条目，对应后端 AgentTraceLogService.TraceLogEntry。 */
export interface TraceLogEntry {
  id: string
  traceId: string
  timestamp: string
  level: string
  stage: string
  message: string
  metadata: Record<string, unknown>
}

/** trace 摘要，对应后端 AgentTraceLogService.TraceSummary。 */
export interface TraceSummary {
  traceId: string
  entryCount: number
  lastUpdatedAt: string
}

/** 拉取某条 trace 的完整时间线（所有日志条目按时间升序）。 */
export const getTrace = async (traceId: string) => {
  return get<TraceLogEntry[]>(`/ai/trace/${encodeURIComponent(traceId)}`)
}

/** 拉取最近的 trace 摘要列表（按时间倒序）。 */
export const listRecentTraces = async (limit = 20) => {
  return get<TraceSummary[]>('/ai/trace', { params: { limit } })
}
