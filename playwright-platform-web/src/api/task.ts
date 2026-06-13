import { get, post } from './http'
import type { PageResult } from '../types/common'
import type { ArtifactRecord, TaskRecord, TaskReport } from '../types/task'
import type { CaseResultRecord, TaskReportSummary } from '../types/report'

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

export const getTaskReport = async (taskId: number) => {
  return get<TaskReport>(`/tasks/${taskId}/report`)
}

export const getTaskReportSummary = async (taskId: number) => {
  return get<TaskReportSummary>(`/tasks/${taskId}/report-summary`)
}

export const listArtifacts = async (taskId: number) => {
  return get<ArtifactRecord[]>(`/tasks/${taskId}/artifacts`)
}

export const listTaskCases = async (taskId: number) => {
  return get<CaseResultRecord[]>(`/tasks/${taskId}/cases`)
}
