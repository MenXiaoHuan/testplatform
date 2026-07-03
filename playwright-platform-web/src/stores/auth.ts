import { defineStore } from 'pinia'
import { fetchCurrentUser, fetchPublicKey, loginWithPassword, logoutCurrentUser, registerWithPassword, updateCurrentUserProfile, uploadCurrentUserAvatar } from '../api/auth'
import type { AuthUser, LoginForm, PublicKeyResponse, RegisterForm } from '../types/auth'
import { encryptPassword } from '../utils/auth-crypto'

function isInvalidEncryptedCredentials(error: unknown) {
  if (typeof error !== 'object' || error === null) {
    return false
  }
  const response = Reflect.get(error, 'response')
  if (typeof response !== 'object' || response === null) {
    return false
  }
  const data = Reflect.get(response, 'data')
  if (typeof data !== 'object' || data === null) {
    return false
  }
  const msg = Reflect.get(data, 'msg')
  return typeof msg === 'string' && msg.trim().toLowerCase() === 'invalid encrypted credentials'
}

function isUnauthorized(error: unknown) {
  if (typeof error !== 'object' || error === null) {
    return false
  }
  const response = Reflect.get(error, 'response')
  if (typeof response !== 'object' || response === null) {
    return false
  }
  return Reflect.get(response, 'status') === 401
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as AuthUser | null,
    publicKey: null as PublicKeyResponse | null,
    initialized: false,
    restoring: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.user),
  },
  actions: {
    async ensurePublicKey(force = false) {
      if (!force && this.publicKey !== null) {
        return this.publicKey
      }
      this.publicKey = await fetchPublicKey()
      return this.publicKey
    },
    async restoreSession() {
      if (this.restoring) {
        return this.user
      }

      this.restoring = true
      try {
        this.user = (await fetchCurrentUser()) ?? null
        return this.user
      } catch (error) {
        if (isUnauthorized(error)) {
          this.user = null
          return null
        }
        throw error
      } finally {
        this.initialized = true
        this.restoring = false
      }
    },
    async login(form: LoginForm) {
      try {
        const publicKey = await this.ensurePublicKey()
        const encryptedPassword = await encryptPassword(form.password, publicKey.publicKeyPem)
        this.user = await loginWithPassword({
          username: form.username,
          encryptedPassword,
        })
      } catch (error) {
        if (!isInvalidEncryptedCredentials(error)) {
          throw error
        }
        const refreshedPublicKey = await this.ensurePublicKey(true)
        const encryptedPassword = await encryptPassword(form.password, refreshedPublicKey.publicKeyPem)
        this.user = await loginWithPassword({
          username: form.username,
          encryptedPassword,
        })
      }
      this.initialized = true
      return this.user
    },
    async register(form: RegisterForm) {
      try {
        const publicKey = await this.ensurePublicKey()
        const encryptedPassword = await encryptPassword(form.password, publicKey.publicKeyPem)
        this.user = await registerWithPassword({
          username: form.username,
          nickname: form.nickname,
          encryptedPassword,
        })
      } catch (error) {
        if (!isInvalidEncryptedCredentials(error)) {
          throw error
        }
        const refreshedPublicKey = await this.ensurePublicKey(true)
        const encryptedPassword = await encryptPassword(form.password, refreshedPublicKey.publicKeyPem)
        this.user = await registerWithPassword({
          username: form.username,
          nickname: form.nickname,
          encryptedPassword,
        })
      }
      this.initialized = true
      return this.user
    },
    async logout() {
      try {
        await logoutCurrentUser()
      } finally {
        this.clearSession()
      }
    },
    async updateNickname(nickname: string) {
      this.user = await updateCurrentUserProfile({ nickname })
      return this.user
    },
    async uploadAvatar(file: File) {
      this.user = await uploadCurrentUserAvatar(file)
      return this.user
    },
    clearSession() {
      this.user = null
      this.publicKey = null
      this.initialized = true
      this.restoring = false
    },
  },
})
