import { defineStore } from 'pinia'
import { listScheduleEvents, retryScheduleEvent } from '../api/schedule-event'
import { requireCurrentSpaceId } from './space'
import type {
  ScheduleEventIssueRecord,
  ScheduleEventRetryRequest,
  ScheduleEventRetryResult,
  ScheduleEventType,
} from '../types/schedule-event'

export const useScheduleEventStore = defineStore('schedule-event', {
  state: () => ({
    items: [] as ScheduleEventIssueRecord[],
    loading: false,
    retryingIds: [] as number[],
    page: 1,
    size: 20,
    total: 0,
    totalPages: 0,
    scheduleType: 'AGENT' as ScheduleEventType,
    sceneId: null as number | null,
    sceneName: '' as string,
    traceId: '' as string,
  }),
  actions: {
    async fetchAll(page?: number, size?: number) {
      const spaceId = requireCurrentSpaceId()
      const currentPage = page ?? this.page
      const currentSize = size ?? this.size
      this.loading = true
      try {
        const response = await listScheduleEvents({
          spaceId,
          scheduleType: this.scheduleType,
          sceneId: this.sceneId,
          sceneName: this.sceneName,
          traceId: this.traceId,
          page: currentPage,
          limit: currentSize,
        })
        this.items = response.items
        this.page = response.page
        this.size = response.size
        this.total = response.total
        this.totalPages = response.totalPages
      } finally {
        this.loading = false
      }
    },
    setScheduleTypeFilter(scheduleType: ScheduleEventType) {
      this.scheduleType = scheduleType
      this.page = 1
    },
    setSceneIdFilter(sceneId: number | null) {
      this.sceneId = typeof sceneId === 'number' && Number.isFinite(sceneId) ? sceneId : null
      this.page = 1
    },
    setSceneNameFilter(sceneName: string) {
      this.sceneName = sceneName.trim()
      this.page = 1
    },
    setTraceIdFilter(traceId: string) {
      this.traceId = traceId.trim()
      this.page = 1
    },
    setPage(page: number) {
      this.page = page
    },
    async retry(eventId: number, payload?: ScheduleEventRetryRequest): Promise<ScheduleEventRetryResult> {
      this.retryingIds = [...this.retryingIds, eventId]
      try {
        const task = await retryScheduleEvent(requireCurrentSpaceId(), eventId, payload)
        await this.fetchAll(1, this.size)
        return task
      } finally {
        this.retryingIds = this.retryingIds.filter((id) => id !== eventId)
      }
    },
  },
})
