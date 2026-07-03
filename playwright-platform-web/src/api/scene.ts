import { del, get, post, put } from './http'
import type { PageResult } from '../types/common'
import type { SceneDetail, SceneForm, SceneRecord } from '../types/scene'

export const listScenes = async (spaceId: number, page = 1, size = 10) => {
  return get<PageResult<SceneRecord>>(`/spaces/${spaceId}/scenes`, {
    params: { page, size },
  })
}

export const getScene = async (spaceId: number, id: number) => {
  return get<SceneDetail>(`/spaces/${spaceId}/scenes/${id}`)
}

export const createScene = async (spaceId: number, payload: SceneForm) => {
  return post<SceneRecord>(`/spaces/${spaceId}/scenes`, payload)
}

export const updateScene = async (spaceId: number, id: number, payload: SceneForm) => {
  return put<SceneRecord>(`/spaces/${spaceId}/scenes/${id}`, payload)
}

export const deleteScene = async (spaceId: number, id: number) => {
  await del(`/spaces/${spaceId}/scenes/${id}`)
}
