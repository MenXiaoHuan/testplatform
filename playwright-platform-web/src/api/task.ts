import { get, post } from './http'
import type { PageResult } from '../types/common'
import type { ArtifactRecord, TaskDiagnosticsRecord, TaskRecord, TaskStageLogRecord } from '../types/task'
import type { CaseResultRecord } from '../types/report'

export const listTasks = async (page = 1, size = 10) => {
  return get<PageResult<TaskRecord>>('/tasks', {
    params: { page, size },
  })
}

export const fetchSceneTasks = async (sceneId: number, page = 1, size = 10) => {
  return get<PageResult<TaskRecord>>(`/scenes/${sceneId}/tasks`, {
    params: { page, size },
  })
}

export const getTask = async (id: number) => {
  return get<TaskRecord>(`/tasks/${id}`)
}

export const runScene = async (sceneId: number) => {
  return post<TaskRecord>(`/scenes/${sceneId}/run`)
}

export const cancelTask = async (taskId: number) => {
  return post<void>(`/tasks/${taskId}/cancel`)
}

export const listArtifacts = async (taskId: number) => {
  return get<ArtifactRecord[]>(`/tasks/${taskId}/artifacts`)
}

export const listTaskCases = async (taskId: number) => {
  return get<CaseResultRecord[]>(`/tasks/${taskId}/cases`)
}

export const listTaskLogs = async (taskId: number) => {
  return get<TaskStageLogRecord[]>(`/tasks/${taskId}/logs`)
}

export const getTaskDiagnostics = async (taskId: number) => {
  return get<TaskDiagnosticsRecord>(`/tasks/${taskId}/diagnostics`)
}
