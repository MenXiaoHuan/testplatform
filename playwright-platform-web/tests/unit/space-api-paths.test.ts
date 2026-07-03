import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSpaceStore } from '../../src/stores/space'
import { useRepositoryStore } from '../../src/stores/repository'
import { useSceneStore } from '../../src/stores/scene'
import { useTaskStore } from '../../src/stores/task'
import { useScheduleEventStore } from '../../src/stores/schedule-event'

vi.mock('../../src/api/repository', () => ({
  createRepository: vi.fn(),
  deleteRepository: vi.fn(),
  listRepositories: vi.fn(),
  updateRepository: vi.fn(),
}))

vi.mock('../../src/api/scene', () => ({
  createScene: vi.fn(),
  deleteScene: vi.fn(),
  getScene: vi.fn(),
  listScenes: vi.fn(),
  updateScene: vi.fn(),
}))

vi.mock('../../src/api/task', () => ({
  cancelTask: vi.fn(),
  fetchSceneTasks: vi.fn(),
  getTask: vi.fn(),
  listArtifacts: vi.fn(),
  listTaskCases: vi.fn(),
  listTaskLogs: vi.fn(),
  listTasks: vi.fn(),
  runScene: vi.fn(),
}))

vi.mock('../../src/api/schedule-event', () => ({
  listScheduleEvents: vi.fn(),
  retryScheduleEvent: vi.fn(),
}))
vi.mock('../../src/api/space', () => ({
  createSpace: vi.fn(),
  updateSpace: vi.fn(),
  deleteSpace: vi.fn(),
  listMySpaces: vi.fn(),
  listSpaceAccessRequests: vi.fn(),
  submitSpaceAccessRequest: vi.fn(),
  approveSpaceAccessRequest: vi.fn(),
  rejectSpaceAccessRequest: vi.fn(),
}))

import { listRepositories } from '../../src/api/repository'
import { listScenes } from '../../src/api/scene'
import { listTasks } from '../../src/api/task'
import { listScheduleEvents } from '../../src/api/schedule-event'
import { createSpace, updateSpace, deleteSpace } from '../../src/api/space'

describe('space-aware stores', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    const spaceStore = useSpaceStore()
    spaceStore.items = [{ id: 7, name: 'Alpha', description: 'alpha space' }]
    spaceStore.currentSpaceId = 7
  })

  it('should call repository, scene, task and schedule event APIs with current spaceId', async () => {
    vi.mocked(listRepositories).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 10,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    })
    vi.mocked(listScenes).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 10,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
    })
    vi.mocked(listTasks).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 10,
      totalPages: 0,
      hasNext: false,
      hasPrevious: false,
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

    await Promise.all([
      useRepositoryStore().fetchAll(),
      useSceneStore().fetchAll(),
      useTaskStore().fetchAll(),
      useScheduleEventStore().fetchAll(),
    ])

    expect(listRepositories).toHaveBeenCalledWith(7, 1, 10)
    expect(listScenes).toHaveBeenCalledWith(7, 1, 10)
    expect(listTasks).toHaveBeenCalledWith(7, 1, 10)
    expect(listScheduleEvents).toHaveBeenCalledWith({
      spaceId: 7,
      statusCsv: 'FAILED,ABANDONED',
      sceneId: null,
      page: 1,
      limit: 20,
    })
  })

  it('should call space CRUD APIs through the space store', async () => {
    vi.mocked(createSpace).mockResolvedValue({ id: 9, name: 'Gamma', description: 'g' })
    vi.mocked(updateSpace).mockResolvedValue({ id: 7, name: 'Alpha-2', description: 'updated' })
    vi.mocked(deleteSpace).mockResolvedValue(undefined)

    const spaceStore = useSpaceStore()
    await spaceStore.createNewSpace({ name: 'Gamma', description: 'g' })
    await spaceStore.updateExistingSpace(7, { name: 'Alpha-2', description: 'updated' })
    await spaceStore.removeSpace(7)

    expect(createSpace).toHaveBeenCalledWith({ name: 'Gamma', description: 'g' })
    expect(updateSpace).toHaveBeenCalledWith(7, { name: 'Alpha-2', description: 'updated' })
    expect(deleteSpace).toHaveBeenCalledWith(7)
  })
})
