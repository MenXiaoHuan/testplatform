import http from './http'
import type { ArtifactRecord, TaskRecord, TaskReport } from '../types/task'

export const listTasks = async () => {
  const { data } = await http.get<TaskRecord[]>('/tasks')
  return data
}

export const getTask = async (id: number) => {
  const { data } = await http.get<TaskRecord>(`/tasks/${id}`)
  return data
}

export const runScene = async (sceneId: number) => {
  const { data } = await http.post<TaskRecord>(`/scenes/${sceneId}/run`)
  return data
}

export const getTaskReport = async (taskId: number) => {
  const { data } = await http.get<TaskReport>(`/tasks/${taskId}/report`)
  return data
}

export const listArtifacts = async (taskId: number) => {
  const { data } = await http.get<ArtifactRecord[]>(`/tasks/${taskId}/artifacts`)
  return data
}
