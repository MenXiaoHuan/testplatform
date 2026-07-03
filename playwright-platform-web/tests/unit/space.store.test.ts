import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth'
import { useSpaceStore } from '../../src/stores/space'

vi.mock('../../src/api/space', () => ({
  createSpace: vi.fn(),
  listMySpaces: vi.fn(),
  listSpacePlaza: vi.fn(),
  listSpaceAccessRequests: vi.fn(),
  submitSpaceAccessRequest: vi.fn(),
}))

import { createSpace, listMySpaces, listSpaceAccessRequests, listSpacePlaza, submitSpaceAccessRequest } from '../../src/api/space'

describe('space store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    window.localStorage.clear()
  })

  it('should prefer the user lastSpaceId when loading spaces', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 8,
    }
    vi.mocked(listMySpaces).mockResolvedValue([
      { id: 7, name: 'Alpha', description: 'alpha space' },
      { id: 8, name: 'Beta', description: 'beta space' },
    ])

    const store = useSpaceStore()
    await store.fetchSpaces()

    expect(store.items).toHaveLength(2)
    expect(store.currentSpaceId).toBe(8)
    expect(store.currentSpace?.name).toBe('Beta')
  })

  it('should create a space and switch current space to the new record', async () => {
    vi.mocked(createSpace).mockResolvedValue({
      id: 12,
      name: 'Gamma',
      description: 'new space',
    })

    const store = useSpaceStore()
    const created = await store.createNewSpace({
      name: 'Gamma',
      description: 'new space',
    })

    expect(createSpace).toHaveBeenCalledWith({
      name: 'Gamma',
      description: 'new space',
    })
    expect(created.id).toBe(12)
    expect(store.currentSpaceId).toBe(12)
    expect(store.items[0].name).toBe('Gamma')
    expect(window.localStorage.getItem('platform:last-space-id')).toBe('12')
  })

  it('should not auto-select the first space when there is no preferred space', async () => {
    vi.mocked(listMySpaces).mockResolvedValue([
      { id: 7, name: 'Alpha', description: 'alpha space' },
      { id: 8, name: 'Beta', description: 'beta space' },
    ])

    const store = useSpaceStore()
    await store.fetchSpaces()

    expect(store.items).toHaveLength(2)
    expect(store.currentSpaceId).toBeNull()
    expect(store.currentSpace).toBeNull()
  })

  it('should submit access request without fetching admin approval list', async () => {
    const store = useSpaceStore()

    await store.submitAccessRequest({
      requestedRole: 'OPERATOR',
      reason: '需要处理任务',
    }, 7)

    expect(submitSpaceAccessRequest).toHaveBeenCalledWith(7, {
      requestedRole: 'OPERATOR',
      reason: '需要处理任务',
    })
    expect(listSpaceAccessRequests).not.toHaveBeenCalled()
  })

  it('should sort plaza items by owner first, then accessible spaces, then others', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: null,
    }
    vi.mocked(listSpacePlaza).mockResolvedValue([
      {
        id: 3,
        name: 'other',
        description: '',
        ownerUserId: 9,
        ownerUsername: 'owner-9',
        ownerNickname: 'Owner 9',
        ownerAvatarUrl: null,
        accessible: false,
        manageable: false,
        currentRole: null,
        pendingRequestedRole: null,
      },
      {
        id: 2,
        name: 'accessible',
        description: '',
        ownerUserId: 8,
        ownerUsername: 'owner-8',
        ownerNickname: 'Owner 8',
        ownerAvatarUrl: null,
        accessible: true,
        manageable: false,
        currentRole: 'VIEWER',
        pendingRequestedRole: null,
      },
      {
        id: 1,
        name: 'owner',
        description: '',
        ownerUserId: 1,
        ownerUsername: 'admin',
        ownerNickname: '平台管理员',
        ownerAvatarUrl: null,
        accessible: true,
        manageable: true,
        currentRole: 'ADMIN',
        pendingRequestedRole: null,
      },
    ])

    const store = useSpaceStore()
    await store.fetchPlaza()

    expect(store.plazaItems.map((item) => item.id)).toEqual([1, 2, 3])
  })

  it('should sync the current user avatar to owned plaza items immediately', () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: 'http://localhost/new-avatar.png',
      lastSpaceId: null,
    }

    const store = useSpaceStore()
    store.plazaItems = [
      {
        id: 1,
        name: 'owner-space',
        description: '',
        ownerUserId: 1,
        ownerUsername: 'admin',
        ownerNickname: '平台管理员',
        ownerAvatarUrl: null,
        accessible: true,
        manageable: true,
        currentRole: 'ADMIN',
        pendingRequestedRole: null,
      },
      {
        id: 2,
        name: 'other-space',
        description: '',
        ownerUserId: 9,
        ownerUsername: 'other',
        ownerNickname: 'Other',
        ownerAvatarUrl: 'http://localhost/other-avatar.png',
        accessible: true,
        manageable: false,
        currentRole: 'VIEWER',
        pendingRequestedRole: null,
      },
    ]

    store.syncCurrentUserIdentity()

    expect(store.plazaItems[0].ownerAvatarUrl).toBe('http://localhost/new-avatar.png')
    expect(store.plazaItems[1].ownerAvatarUrl).toBe('http://localhost/other-avatar.png')
  })
})
