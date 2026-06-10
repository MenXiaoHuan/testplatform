import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSceneStore } from './scene'

const { fetchAllMock, deleteSceneMock } = vi.hoisted(() => ({
  fetchAllMock: vi.fn(async () => []),
  deleteSceneMock: vi.fn(async () => undefined),
}))

vi.mock('../api/scene', () => ({
  listScenes: fetchAllMock,
  createScene: vi.fn(async () => undefined),
  updateScene: vi.fn(async () => undefined),
  deleteScene: deleteSceneMock,
}))

describe('scene store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchAllMock.mockClear()
    deleteSceneMock.mockClear()
  })

  it('should delete a scene and refresh the list', async () => {
    const store = useSceneStore()

    await store.remove(5)

    expect(deleteSceneMock).toHaveBeenCalledWith(5)
    expect(fetchAllMock).toHaveBeenCalled()
  })
})
