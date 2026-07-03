export interface PublicKeyResponse {
  algorithm: string
  publicKeyPem: string
}

export interface LoginPayload {
  username: string
  encryptedPassword: string
}

export interface RegisterPayload {
  username: string
  nickname: string
  encryptedPassword: string
}

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  nickname: string
  password: string
  confirmPassword: string
}

export interface UpdateProfilePayload {
  nickname: string
}

export interface AuthUser {
  id: number
  username: string
  nickname: string
  avatarUrl: string | null
  lastSpaceId: number | null
}
