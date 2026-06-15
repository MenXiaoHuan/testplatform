import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTaskStore } from '../../src/stores/task'

vi.mock('../../src/api/task', () => ({
  getTask: vi.fn(),
  listTaskCases: vi.fn(),
  listTaskLogs: vi.fn(),
  cancelTask: vi.fn(),
  listArtifacts: vi.fn(),
  listTasks: vi.fn(),
  fetchSceneTasks: vi.fn(),
  runScene: vi.fn(),
}))

import {
  cancelTask,
  getTask,
  listArtifacts,
  listTaskCases,
  listTaskLogs,
} from '../../src/api/task'

describe('task store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('should fetch task detail page with stage logs', async () => {
    vi.mocked(getTask).mockResolvedValue({
      id: 101,
      sceneId: 11,
      repoId: 21,
      status: 'FAILED',
      triggerType: 'MANUAL',
      triggerReason: 'manual-run',
      branch: 'main',
      currentStage: 'FINISHED',
      resultCode: 'TEST_FAILED',
      cancelRequested: false,
    })
    vi.mocked(listArtifacts).mockResolvedValue([])
    vi.mocked(listTaskCases).mockResolvedValue([])
    vi.mocked(listTaskLogs).mockResolvedValue([
      {
        id: 1,
        stage: 'TESTING',
        streamType: 'COMBINED',
        previewText: 'error line',
        lineCount: 12,
        downloadUrl: 'http://localhost/logs/testing.log',
      },
    ])

    const store = useTaskStore()
    await store.fetchTaskDetailPage(101)

    expect(store.current?.currentStage).toBe('FINISHED')
    expect(store.current?.resultCode).toBe('TEST_FAILED')
    expect(store.stageLogs).toHaveLength(1)
    expect(store.stageLogs[0].downloadUrl).toBe('http://localhost/logs/testing.log')
  })

  it('should cancel current task and refresh current detail', async () => {
    vi.mocked(cancelTask).mockResolvedValue(undefined)
    vi.mocked(getTask).mockResolvedValue({
      id: 101,
      sceneId: 11,
      repoId: 21,
      status: 'CANCELED',
      triggerType: 'MANUAL',
      triggerReason: 'manual-run',
      branch: 'main',
      currentStage: 'FINISHED',
      resultCode: 'CANCELED',
      cancelRequested: true,
      cancelRequestedBy: 'system-user',
    })

    const store = useTaskStore()
    await store.cancelCurrentTask(101)

    expect(cancelTask).toHaveBeenCalledWith(101)
    expect(getTask).toHaveBeenCalledWith(101)
    expect(store.current?.status).toBe('CANCELED')
    expect(store.current?.cancelRequested).toBe(true)
    expect(store.current?.cancelRequestedBy).toBe('system-user')
  })
})
