export interface PublicKeyResponse {
  algorithm: string
  publicKeyPem: string
  registrationEnabled?: boolean
}

export interface LoginPayload {
  username: string
  encryptedPassword: string
}

export interface RegisterPayload {
  username: string
  encryptedPassword: string
}

export interface SetupProfilePayload {
  nickname: string
}

export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
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
  /** 后端总是返回；测试 fixture 可省略，默认按已初始化处理。 */
  needsSetup?: boolean
}
