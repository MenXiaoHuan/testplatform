import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSpaceStore } from '../../src/stores/space'
import { useScheduleEventStore } from '../../src/stores/schedule-event'

vi.mock('../../src/api/schedule-event', () => ({
  listScheduleEvents: vi.fn(),
  retryScheduleEvent: vi.fn(),
}))

import { listScheduleEvents, retryScheduleEvent } from '../../src/api/schedule-event'

describe('schedule event store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    const spaceStore = useSpaceStore()
    spaceStore.items = [{ id: 7, name: 'Alpha', description: 'alpha space' }]
    spaceStore.currentSpaceId = 7
  })

  it('should fetch issue events with current filters', async () => {
    vi.mocked(listScheduleEvents).mockResolvedValue({
      items: [
        {
          id: 7,
          sceneId: 11,
          sceneName: '测试场景',
          scheduleType: 'CRON',
          plannedFireAt: '2026-07-02T12:00:00',
          status: 'FAILED',
          retryCount: 1,
          triggerReason: 'cron:0 */5 * * * *',
          errorMessage: 'system busy',
          taskId: null,
        },
      ],
      total: 1,
      page: 1,
      size: 20,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    })

    const store = useScheduleEventStore()
    store.setScheduleTypeFilter('CRON')
    store.setSceneIdFilter(11)
    await store.fetchAll()

    expect(listScheduleEvents).toHaveBeenCalledWith({
      spaceId: 7,
      scheduleType: 'CRON',
      sceneId: 11,
      sceneName: '',
      traceId: '',
      page: 1,
      limit: 20,
    })
    expect(store.items).toHaveLength(1)
    expect(store.items[0].status).toBe('FAILED')
  })

  it('should retry issue event and refresh the first page', async () => {
    vi.mocked(retryScheduleEvent).mockResolvedValue({
      id: 101,
      sceneId: 11,
      repoId: 21,
      status: 'QUEUED',
      triggerType: 'SCHEDULED',
      triggerReason: 'cron:0 */5 * * * *',
      triggerUser: 'scheduler',
      branch: 'main',
      currentStage: 'QUEUED',
      cancelRequested: false,
      resolvedBranch: 'main',
      resolvedBrowser: 'chromium',
      resolvedTestRoot: 'tests',
      resolvedRunCommand: 'npm run test:e2e',
    })
    vi.mocked(listScheduleEvents).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 20,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    })

    const store = useScheduleEventStore()
    const task = await store.retry(7, {
      operatorName: 'anonymous',
      comment: 'manual retry from web',
    })

    expect(retryScheduleEvent).toHaveBeenCalledWith(7, 7, {
      operatorName: 'anonymous',
      comment: 'manual retry from web',
    })
    expect(listScheduleEvents).toHaveBeenCalled()
    expect(task.id).toBe(101)
  })
})
