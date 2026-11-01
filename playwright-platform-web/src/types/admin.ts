export interface AdminUser {
  id: number
  username: string
  nickname: string | null
  avatarUrl: string | null
  enabled: boolean
  superAdmin: boolean
  lastSpaceId: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AdminUserPage {
  page: number
  size: number
  total: number
  items: AdminUser[]
}

export interface AdminUserQuery {
  page: number
  size: number
  keyword?: string
}

export interface UpdateAdminUserPayload {
  nickname: string
  enabled: boolean
}

export interface RegistrationSetting {
  enabled: boolean
}
