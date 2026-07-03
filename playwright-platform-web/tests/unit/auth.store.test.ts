import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/api/auth', () => ({
  fetchCurrentUser: vi.fn(),
  fetchPublicKey: vi.fn(),
  loginWithPassword: vi.fn(),
  logoutCurrentUser: vi.fn(),
  registerWithPassword: vi.fn(),
  updateCurrentUserProfile: vi.fn(),
  uploadCurrentUserAvatar: vi.fn(),
}))

vi.mock('../../src/utils/auth-crypto', () => ({
  encryptPassword: vi.fn(),
}))

import { fetchCurrentUser, fetchPublicKey, loginWithPassword, logoutCurrentUser, registerWithPassword, uploadCurrentUserAvatar } from '../../src/api/auth'
import { encryptPassword } from '../../src/utils/auth-crypto'

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('should restore current user session from /auth/me', async () => {
    vi.mocked(fetchCurrentUser).mockResolvedValue({
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 7,
    })

    const store = useAuthStore()
    const user = await store.restoreSession()

    expect(fetchCurrentUser).toHaveBeenCalled()
    expect(user?.nickname).toBe('平台管理员')
    expect(store.isAuthenticated).toBe(true)
  })

  it('should encrypt password before login submission', async () => {
    vi.mocked(fetchPublicKey).mockResolvedValue({
      algorithm: 'RSA',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----demo-----END PUBLIC KEY-----',
    })
    vi.mocked(encryptPassword).mockResolvedValue('ciphertext')
    vi.mocked(loginWithPassword).mockResolvedValue({
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 7,
    })

    const store = useAuthStore()
    await store.login({
      username: 'admin',
      password: 'secret',
    })

    expect(fetchPublicKey).toHaveBeenCalled()
    expect(encryptPassword).toHaveBeenCalledWith('secret', '-----BEGIN PUBLIC KEY-----demo-----END PUBLIC KEY-----')
    expect(loginWithPassword).toHaveBeenCalledWith({
      username: 'admin',
      encryptedPassword: 'ciphertext',
    })
    expect(store.user?.lastSpaceId).toBe(7)
  })

  it('should refresh public key and retry login when encrypted credentials become stale', async () => {
    vi.mocked(fetchPublicKey)
      .mockResolvedValueOnce({
        algorithm: 'RSA',
        publicKeyPem: 'old-key',
      })
      .mockResolvedValueOnce({
        algorithm: 'RSA',
        publicKeyPem: 'new-key',
      })
    vi.mocked(encryptPassword)
      .mockResolvedValueOnce('old-ciphertext')
      .mockResolvedValueOnce('new-ciphertext')
    vi.mocked(loginWithPassword)
      .mockRejectedValueOnce({
        response: {
          status: 400,
          data: {
            code: 'BAD_REQUEST',
            msg: 'invalid encrypted credentials',
          },
        },
      })
      .mockResolvedValueOnce({
        id: 1,
        username: 'admin',
        nickname: '平台管理员',
        avatarUrl: null,
        lastSpaceId: 7,
      })

    const store = useAuthStore()
    await store.login({
      username: 'admin',
      password: 'secret',
    })

    expect(fetchPublicKey).toHaveBeenCalledTimes(2)
    expect(encryptPassword).toHaveBeenNthCalledWith(1, 'secret', 'old-key')
    expect(encryptPassword).toHaveBeenNthCalledWith(2, 'secret', 'new-key')
    expect(loginWithPassword).toHaveBeenNthCalledWith(1, {
      username: 'admin',
      encryptedPassword: 'old-ciphertext',
    })
    expect(loginWithPassword).toHaveBeenNthCalledWith(2, {
      username: 'admin',
      encryptedPassword: 'new-ciphertext',
    })
  })

  it('should clear local session state when logging out', async () => {
    vi.mocked(logoutCurrentUser).mockResolvedValue(undefined)

    const store = useAuthStore()
    store.user = {
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: null,
      lastSpaceId: 7,
    }

    await store.logout()

    expect(logoutCurrentUser).toHaveBeenCalled()
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('should encrypt password before register submission', async () => {
    vi.mocked(fetchPublicKey).mockResolvedValue({
      algorithm: 'RSA',
      publicKeyPem: '-----BEGIN PUBLIC KEY-----demo-----END PUBLIC KEY-----',
    })
    vi.mocked(encryptPassword).mockResolvedValue('ciphertext')
    vi.mocked(registerWithPassword).mockResolvedValue({
      id: 2,
      username: 'zhangsan',
      nickname: '张三',
      avatarUrl: null,
      lastSpaceId: 12,
    })

    const store = useAuthStore()
    await store.register({
      username: 'zhangsan',
      nickname: '张三',
      password: 'secret123',
      confirmPassword: 'secret123',
    })

    expect(registerWithPassword).toHaveBeenCalledWith({
      username: 'zhangsan',
      nickname: '张三',
      encryptedPassword: 'ciphertext',
    })
    expect(store.user?.lastSpaceId).toBe(12)
  })

  it('should refresh public key and retry register when encrypted credentials become stale', async () => {
    vi.mocked(fetchPublicKey)
      .mockResolvedValueOnce({
        algorithm: 'RSA',
        publicKeyPem: 'old-key',
      })
      .mockResolvedValueOnce({
        algorithm: 'RSA',
        publicKeyPem: 'new-key',
      })
    vi.mocked(encryptPassword)
      .mockResolvedValueOnce('old-ciphertext')
      .mockResolvedValueOnce('new-ciphertext')
    vi.mocked(registerWithPassword)
      .mockRejectedValueOnce({
        response: {
          status: 400,
          data: {
            code: 'BAD_REQUEST',
            msg: 'invalid encrypted credentials',
          },
        },
      })
      .mockResolvedValueOnce({
        id: 2,
        username: 'zhangsan',
        nickname: '张三',
        avatarUrl: null,
        lastSpaceId: 12,
      })

    const store = useAuthStore()
    await store.register({
      username: 'zhangsan',
      nickname: '张三',
      password: 'secret123',
      confirmPassword: 'secret123',
    })

    expect(fetchPublicKey).toHaveBeenCalledTimes(2)
    expect(encryptPassword).toHaveBeenNthCalledWith(1, 'secret123', 'old-key')
    expect(encryptPassword).toHaveBeenNthCalledWith(2, 'secret123', 'new-key')
    expect(registerWithPassword).toHaveBeenNthCalledWith(1, {
      username: 'zhangsan',
      nickname: '张三',
      encryptedPassword: 'old-ciphertext',
    })
    expect(registerWithPassword).toHaveBeenNthCalledWith(2, {
      username: 'zhangsan',
      nickname: '张三',
      encryptedPassword: 'new-ciphertext',
    })
  })

  it('should upload avatar and refresh current user state', async () => {
    vi.mocked(uploadCurrentUserAvatar).mockResolvedValue({
      id: 1,
      username: 'admin',
      nickname: '平台管理员',
      avatarUrl: 'http://localhost/avatar.png',
      lastSpaceId: 7,
    })

    const store = useAuthStore()
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const user = await store.uploadAvatar(file)

    expect(uploadCurrentUserAvatar).toHaveBeenCalledWith(file)
    expect(user.avatarUrl).toBe('http://localhost/avatar.png')
    expect(store.user?.avatarUrl).toBe('http://localhost/avatar.png')
  })
})
