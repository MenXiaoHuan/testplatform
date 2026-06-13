import { del, get, post, put } from './http'
import type { PageResult } from '../types/common'
import type { RepositoryForm, RepositoryRecord } from '../types/repository'

export const listRepositories = async (page = 1, size = 10) => {
  return get<PageResult<RepositoryRecord>>('/repos', {
    params: { page, size },
  })
}

export const getRepository = async (id: number) => {
  return get<RepositoryRecord>(`/repos/${id}`)
}

export const createRepository = async (payload: RepositoryForm) => {
  return post<RepositoryRecord>('/repos', payload)
}

export const updateRepository = async (id: number, payload: RepositoryForm) => {
  return put<RepositoryRecord>(`/repos/${id}`, payload)
}

export const deleteRepository = async (id: number) => {
  await del(`/repos/${id}`)
}
