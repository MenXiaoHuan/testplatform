import type { PageResult } from './common'
import type { TaskRecord } from './task'

export type ScheduleEventType = 'CRON' | 'AGENT' | 'MANUAL'

export interface ScheduleEventIssueRecord {
  id: number
  sceneId: number | null
  sceneName?: string | null
  plannedFireAt: string
  status: string
  scheduleType?: ScheduleEventType | string
  traceId?: string | null
  sessionId?: string | null
  userMessage?: string | null
  retryCount: number
  nextRetryAt?: string | null
  lastErrorAt?: string | null
  triggerReason?: string | null
  errorMessage?: string | null
  taskId?: number | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface ScheduleEventRetryRequest {
  operatorName?: string
  operatorId?: string
  comment?: string
}

export interface ScheduleEventListQuery {
  scheduleType?: ScheduleEventType | ''
  sceneId?: number | null
  sceneName?: string
  traceId?: string
  page?: number
  limit?: number
}

export type ScheduleEventPageResult = PageResult<ScheduleEventIssueRecord>

export type ScheduleEventRetryResult = TaskRecord
