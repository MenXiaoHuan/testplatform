import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRepositoryStore } from './repository'

const { fetchAllMock, deleteRepositoryMock } = vi.hoisted(() => ({
  fetchAllMock: vi.fn(async () => []),
  deleteRepositoryMock: vi.fn(async () => undefined),
}))

vi.mock('../api/repository', () => ({
  listRepositories: fetchAllMock,
  createRepository: vi.fn(async () => undefined),
  updateRepository: vi.fn(async () => undefined),
  deleteRepository: deleteRepositoryMock,
}))

describe('repository store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchAllMock.mockClear()
    deleteRepositoryMock.mockClear()
  })

  it('should delete a repository and refresh the list', async () => {
    const store = useRepositoryStore()

    await store.remove(3)

    expect(deleteRepositoryMock).toHaveBeenCalledWith(3)
    expect(fetchAllMock).toHaveBeenCalled()
  })
})
