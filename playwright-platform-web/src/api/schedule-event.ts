import { get, post } from './http'
import type {
  ScheduleEventListQuery,
  ScheduleEventPageResult,
  ScheduleEventRetryRequest,
  ScheduleEventRetryResult,
} from '../types/schedule-event'

export const listScheduleEvents = async ({
  spaceId,
  scheduleType,
  sceneId,
  sceneName,
  traceId,
  page = 1,
  limit = 20,
}: ScheduleEventListQuery & { spaceId: number }) => {
  return get<ScheduleEventPageResult>(`/spaces/${spaceId}/schedule-events`, {
    params: {
      scheduleType: scheduleType && scheduleType.trim() ? scheduleType : undefined,
      sceneId: typeof sceneId === 'number' ? sceneId : undefined,
      sceneName: sceneName && sceneName.trim() ? sceneName : undefined,
      traceId: traceId && traceId.trim() ? traceId : undefined,
      page,
      limit,
    },
  })
}

export const retryScheduleEvent = async (spaceId: number, eventId: number, payload?: ScheduleEventRetryRequest) => {
  return post<ScheduleEventRetryResult>(`/spaces/${spaceId}/schedule-events/${eventId}/retry`, payload)
}
