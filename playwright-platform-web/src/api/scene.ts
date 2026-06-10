import http from './http'
import type { SceneForm, SceneRecord } from '../types/scene'

export const listScenes = async () => {
  const { data } = await http.get<SceneRecord[]>('/scenes')
  return data
}

export const getScene = async (id: number) => {
  const { data } = await http.get<SceneRecord>(`/scenes/${id}`)
  return data
}

export const createScene = async (payload: SceneForm) => {
  const { data } = await http.post<SceneRecord>('/scenes', payload)
  return data
}

export const updateScene = async (id: number, payload: SceneForm) => {
  const { data } = await http.put<SceneRecord>(`/scenes/${id}`, payload)
  return data
}

export const deleteScene = async (id: number) => {
  await http.delete(`/scenes/${id}`)
}
