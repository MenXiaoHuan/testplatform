import { del, get, post, put } from './http'
import type { PageResult } from '../types/common'
import type { SceneDetail, SceneForm, SceneRecord } from '../types/scene'

export const listScenes = async (page = 1, size = 10) => {
  return get<PageResult<SceneRecord>>('/scenes', {
    params: { page, size },
  })
}

export const getScene = async (id: number) => {
  return get<SceneDetail>(`/scenes/${id}`)
}

export const createScene = async (payload: SceneForm) => {
  return post<SceneRecord>('/scenes', payload)
}

export const updateScene = async (id: number, payload: SceneForm) => {
  return put<SceneRecord>(`/scenes/${id}`, payload)
}

export const deleteScene = async (id: number) => {
  await del(`/scenes/${id}`)
}
