import http from './http'
import type { RepositoryForm, RepositoryRecord } from '../types/repository'

export const listRepositories = async () => {
  const { data } = await http.get<RepositoryRecord[]>('/repos')
  return data
}

export const getRepository = async (id: number) => {
  const { data } = await http.get<RepositoryRecord>(`/repos/${id}`)
  return data
}

export const createRepository = async (payload: RepositoryForm) => {
  const { data } = await http.post<RepositoryRecord>('/repos', payload)
  return data
}

export const updateRepository = async (id: number, payload: RepositoryForm) => {
  const { data } = await http.put<RepositoryRecord>(`/repos/${id}`, payload)
  return data
}

export const deleteRepository = async (id: number) => {
  await http.delete(`/repos/${id}`)
}
