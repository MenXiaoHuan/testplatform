import { del, get, put } from './http'
import type { AdminUser, AdminUserPage, AdminUserQuery, RegistrationSetting, UpdateAdminUserPayload } from '../types/admin'

export const fetchAdminUsers = async (query: AdminUserQuery) => {
  return get<AdminUserPage>('/admin/users', {
    params: {
      page: query.page,
      size: query.size,
      keyword: query.keyword?.trim() || undefined,
    },
  })
}

export const fetchAdminUser = async (id: number) => {
  return get<AdminUser>(`/admin/users/${id}`)
}

export const updateAdminUser = async (id: number, payload: UpdateAdminUserPayload) => {
  return put<AdminUser>(`/admin/users/${id}`, payload)
}

export const resetAdminUserPassword = async (id: number, encryptedPassword: string) => {
  await put<void>(`/admin/users/${id}/password`, { encryptedPassword })
}

export const deleteAdminUser = async (id: number) => {
  await del(`/admin/users/${id}`)
}

export const fetchRegistrationSetting = async () => {
  return get<RegistrationSetting>('/admin/registration-setting')
}

export const updateRegistrationSetting = async (enabled: boolean) => {
  return put<RegistrationSetting>('/admin/registration-setting', { enabled })
}
