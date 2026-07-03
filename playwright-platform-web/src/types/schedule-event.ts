import type { PageResult } from './common'
import type { TaskRecord } from './task'

export interface ScheduleEventIssueRecord {
  id: number
  sceneId: number
  plannedFireAt: string
  status: string
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
  statusCsv?: string
  sceneId?: number | null
  page?: number
  limit?: number
}

export type ScheduleEventPageResult = PageResult<ScheduleEventIssueRecord>

export type ScheduleEventRetryResult = TaskRecord
