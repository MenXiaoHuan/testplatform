import { get, post, put } from './http'
import type { AuthUser, LoginPayload, PublicKeyResponse, RegisterPayload, UpdateProfilePayload } from '../types/auth'

interface AuthUserResponse {
  id: number
  username: string
  nickname: string
  avatarObjectKey: string | null
  lastSpaceId: number | null
}

function normalizeUser(payload: AuthUserResponse): AuthUser {
  return {
    id: payload.id,
    username: payload.username,
    nickname: payload.nickname?.trim() || payload.username?.trim() || '未命名用户',
    avatarUrl: payload.avatarObjectKey ?? null,
    lastSpaceId: payload.lastSpaceId ?? null,
  }
}

export const fetchPublicKey = async () => {
  return get<PublicKeyResponse>('/auth/public-key')
}

export const loginWithPassword = async (payload: LoginPayload) => {
  const user = await post<AuthUserResponse>('/auth/login', payload)
  return normalizeUser(user)
}

export const registerWithPassword = async (payload: RegisterPayload) => {
  const user = await post<AuthUserResponse>('/auth/register', payload)
  return normalizeUser(user)
}

export const fetchCurrentUser = async () => {
  const user = await get<AuthUserResponse>('/auth/me')
  return normalizeUser(user)
}

export const logoutCurrentUser = async () => {
  await post<void>('/auth/logout')
}

export const updateCurrentUserProfile = async (payload: UpdateProfilePayload) => {
  const user = await put<AuthUserResponse>('/auth/profile', payload)
  return normalizeUser(user)
}

export const uploadCurrentUserAvatar = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const user = await post<AuthUserResponse>('/auth/avatar', formData)
  return normalizeUser(user)
}
