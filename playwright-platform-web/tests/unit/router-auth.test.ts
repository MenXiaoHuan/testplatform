import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '../../src/router'
import { useAuthStore } from '../../src/stores/auth'
import { useSpaceStore } from '../../src/stores/space'

vi.mock('../../src/api/auth', () => ({
  fetchCurrentUser: vi.fn(),
  fetchPublicKey: vi.fn(),
  loginWithPassword: vi.fn(),
  logoutCurrentUser: vi.fn(),
}))
const { showAppToastMock } = vi.hoisted(() => ({
  showAppToastMock: vi.fn(),
}))
vi.mock('../../src/utils/ui-feedback', () => ({
  showAppToast: showAppToastMock,
}))

vi.mock('../../src/api/space', () => ({
  createSpace: vi.fn(),
  updateSpace: vi.fn(),
  deleteSpace: vi.fn(),
  listMySpaces: vi.fn(),
  listSpacePlaza: vi.fn(),
  listSpaceAccessRequests: vi.fn(),
  submitSpaceAccessRequest: vi.fn(),
  approveSpaceAccessRequest: vi.fn(),
  rejectSpaceAccessRequest: vi.fn(),
}))

import { fetchCurrentUser } from '../../src/api/auth'
import { listSpacePlaza } from '../../src/api/space'

describe('router auth guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(listSpacePlaza).mockResolvedValue([])
    await router.push('/login')
    await router.isReady()
  })

  it('should redirect unauthenticated users to /login', async () => {
    vi.mocked(fetchCurrentUser).mockRejectedValue({
      response: {
        status: 401,
      },
    })

    await router.push('/spaces/7/scenes')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('should redirect authenticated users away from /login to /home', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 7,
    }
    authStore.initialized = true

    await router.push('/home')
    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/home')
  })

  it('should redirect / to the current space repository page when a space is already selected', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 7,
    }
    authStore.initialized = true

    const spaceStore = useSpaceStore()
    spaceStore.items = [{ id: 7, name: 'Alpha', description: 'alpha' }]
    spaceStore.currentSpaceId = 7
    spaceStore.loaded = true

    await router.push('/')

    expect(router.currentRoute.value.path).toBe('/spaces/7/repos')
  })

  it('should redirect to no-access and show error when user enters an unauthorized space', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: null,
    }
    authStore.initialized = true

    const spaceStore = useSpaceStore()
    spaceStore.items = [{ id: 7, name: 'Alpha', description: 'alpha' }]
    spaceStore.loaded = true

    await router.push('/spaces/99/scenes')

    expect(router.currentRoute.value.path).toBe('/spaces/99/no-access')
    expect(showAppToastMock).toHaveBeenCalledWith('你当前没有该空间的访问权限', 'error')
  })

  it('should redirect viewer away from schedule events route', async () => {
    const authStore = useAuthStore()
    authStore.user = {
      id: 1,
      username: 'viewer',
      nickname: '访客',
      avatarUrl: null,
      lastSpaceId: 7,
    }
    authStore.initialized = true

    const spaceStore = useSpaceStore()
    spaceStore.items = [{ id: 7, name: 'Alpha', description: 'alpha' }]
    spaceStore.plazaItems = [{
      id: 7,
      name: 'Alpha',
      description: 'alpha',
      ownerUserId: 9,
      ownerUsername: 'owner',
      ownerNickname: 'Owner',
      ownerAvatarUrl: null,
      accessible: true,
      manageable: false,
      currentRole: 'VIEWER',
      pendingRequestedRole: null,
    }]
    spaceStore.loaded = true
    spaceStore.plazaLoaded = true

    await router.push('/spaces/7/schedule-events')

    expect(router.currentRoute.value.path).toBe('/spaces/7/repos')
    expect(showAppToastMock).toHaveBeenCalledWith('你当前没有该模块访问权限', 'warning')
  })
})
