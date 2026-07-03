import { get, post } from './http'
import type { PageResult } from '../types/common'
import type { ArtifactRecord, TaskDiagnosticsRecord, TaskRecord, TaskStageLogRecord, TaskTraceShareRecord } from '../types/task'
import type { CaseResultRecord } from '../types/report'

export const listTasks = async (spaceId: number, page = 1, size = 10) => {
  return get<PageResult<TaskRecord>>(`/spaces/${spaceId}/tasks`, {
    params: { page, size },
  })
}

export const fetchSceneTasks = async (spaceId: number, sceneId: number, page = 1, size = 10) => {
  return get<PageResult<TaskRecord>>(`/spaces/${spaceId}/scenes/${sceneId}/tasks`, {
    params: { page, size },
  })
}

export const getTask = async (spaceId: number, id: number) => {
  return get<TaskRecord>(`/spaces/${spaceId}/tasks/${id}`)
}

export const runScene = async (spaceId: number, sceneId: number) => {
  return post<TaskRecord>(`/spaces/${spaceId}/scenes/${sceneId}/run`)
}

export const cancelTask = async (spaceId: number, taskId: number) => {
  return post<void>(`/spaces/${spaceId}/tasks/${taskId}/cancel`)
}

export const listArtifacts = async (spaceId: number, taskId: number) => {
  return get<ArtifactRecord[]>(`/spaces/${spaceId}/tasks/${taskId}/artifacts`)
}

export const listTaskCases = async (spaceId: number, taskId: number) => {
  return get<CaseResultRecord[]>(`/spaces/${spaceId}/tasks/${taskId}/cases`)
}

export const listTaskLogs = async (spaceId: number, taskId: number) => {
  return get<TaskStageLogRecord[]>(`/spaces/${spaceId}/tasks/${taskId}/logs`)
}

export const getTaskDiagnostics = async (spaceId: number, taskId: number) => {
  return get<TaskDiagnosticsRecord>(`/spaces/${spaceId}/tasks/${taskId}/diagnostics`)
}

export const createTraceShareUrl = async (spaceId: number, taskId: number, artifactId: number) => {
  return post<TaskTraceShareRecord>(`/spaces/${spaceId}/tasks/${taskId}/artifacts/${artifactId}/trace-share`)
}
