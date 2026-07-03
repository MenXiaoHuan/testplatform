import { del, get, post, put } from './http'
import type { PageResult } from '../types/common'
import type { RepositoryForm, RepositoryRecord } from '../types/repository'

export const listRepositories = async (spaceId: number, page = 1, size = 10) => {
  return get<PageResult<RepositoryRecord>>(`/spaces/${spaceId}/repos`, {
    params: { page, size },
  })
}

export const getRepository = async (spaceId: number, id: number) => {
  return get<RepositoryRecord>(`/spaces/${spaceId}/repos/${id}`)
}

export const createRepository = async (spaceId: number, payload: RepositoryForm) => {
  return post<RepositoryRecord>(`/spaces/${spaceId}/repos`, payload)
}

export const updateRepository = async (spaceId: number, id: number, payload: RepositoryForm) => {
  return put<RepositoryRecord>(`/spaces/${spaceId}/repos/${id}`, payload)
}

export const deleteRepository = async (spaceId: number, id: number) => {
  await del(`/spaces/${spaceId}/repos/${id}`)
}
