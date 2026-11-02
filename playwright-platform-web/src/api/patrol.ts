import { del, get, post, put } from './http'
import type { PageResult } from '../types/common'
import type {
  AiModelConfig,
  AiModelConfigPayload,
  AiModelOption,
  PatrolApprovalPayload,
  PatrolCase,
  PatrolReport,
  PatrolScene,
  PatrolScenePayload,
  PatrolModelLog,
  PatrolTask,
  PatrolTaskDetail,
  PatrolTaskSummary,
} from '../types/patrol'

// ───────── 模型配置 ─────────

export const listModelConfigs = (spaceId: number) =>
  get<AiModelConfig[]>(`/spaces/${spaceId}/ai-models`)

export const listModelOptions = (spaceId: number) =>
  get<AiModelOption[]>(`/spaces/${spaceId}/ai-models/options`)

export const createModelConfig = (spaceId: number, payload: AiModelConfigPayload) =>
  post<AiModelConfig>(`/spaces/${spaceId}/ai-models`, payload)

export const updateModelConfig = (spaceId: number, id: number, payload: AiModelConfigPayload) =>
  put<AiModelConfig>(`/spaces/${spaceId}/ai-models/${id}`, payload)

export const deleteModelConfig = (spaceId: number, id: number) =>
  del(`/spaces/${spaceId}/ai-models/${id}`)

export const testModelConfig = (spaceId: number, id: number) =>
  post<{ success: boolean; message: string; elapsedMs: number }>(
    `/spaces/${spaceId}/ai-models/${id}/test`,
  )

// ───────── 巡页场景 ─────────

export const listPatrolScenes = (spaceId: number, page = 1, size = 10) =>
  get<PageResult<PatrolScene>>(`/spaces/${spaceId}/patrol-scenes`, { params: { page, size } })

export const getPatrolScene = (spaceId: number, sceneId: number) =>
  get<PatrolScene>(`/spaces/${spaceId}/patrol-scenes/${sceneId}`)

export const createPatrolScene = (spaceId: number, payload: PatrolScenePayload) =>
  post<PatrolScene>(`/spaces/${spaceId}/patrol-scenes`, payload)

export const updatePatrolScene = (spaceId: number, sceneId: number, payload: PatrolScenePayload) =>
  put<PatrolScene>(`/spaces/${spaceId}/patrol-scenes/${sceneId}`, payload)

export const copyPatrolScene = (spaceId: number, sceneId: number) =>
  post<PatrolScene>(`/spaces/${spaceId}/patrol-scenes/${sceneId}/copy`)

export const deletePatrolScene = (spaceId: number, sceneId: number) =>
  del(`/spaces/${spaceId}/patrol-scenes/${sceneId}`)

export const runPatrolScene = (spaceId: number, sceneId: number) =>
  post<PatrolTask>(`/spaces/${spaceId}/patrol-scenes/${sceneId}/run`)

export const listSceneTasks = (spaceId: number, sceneId: number, page = 1, size = 10) =>
  get<PageResult<PatrolTask>>(`/spaces/${spaceId}/patrol-scenes/${sceneId}/tasks`, {
    params: { page, size },
  })

export const getSceneTaskSummary = (spaceId: number, sceneId: number) =>
  get<PatrolTaskSummary>(`/spaces/${spaceId}/patrol-scenes/${sceneId}/tasks/summary`)

// ───────── 巡页任务 ─────────

export const listPatrolTasks = (spaceId: number, page = 1, size = 10) =>
  get<PageResult<PatrolTask>>(`/spaces/${spaceId}/patrol-tasks`, { params: { page, size } })

export const getPatrolTask = (spaceId: number, taskId: number) =>
  get<PatrolTask>(`/spaces/${spaceId}/patrol-tasks/${taskId}`)

export const getPatrolTaskDetail = (spaceId: number, taskId: number) =>
  get<PatrolTaskDetail>(`/spaces/${spaceId}/patrol-tasks/${taskId}/detail`)

export const getPatrolTaskModelLogs = (spaceId: number, taskId: number) =>
  get<PatrolModelLog[]>(`/spaces/${spaceId}/patrol-tasks/${taskId}/model-logs`)

export const listTaskCases = (spaceId: number, taskId: number) =>
  get<PatrolCase[]>(`/spaces/${spaceId}/patrol-tasks/${taskId}/cases`)

export const getTaskReport = (spaceId: number, taskId: number) =>
  get<PatrolReport>(`/spaces/${spaceId}/patrol-tasks/${taskId}/report`)

export const approvePatrolTask = (
  spaceId: number,
  taskId: number,
  payload: PatrolApprovalPayload,
) => post<PatrolTask>(`/spaces/${spaceId}/patrol-tasks/${taskId}/approve`, payload)

export const cancelPatrolTask = (spaceId: number, taskId: number) =>
  post<void>(`/spaces/${spaceId}/patrol-tasks/${taskId}/cancel`)

export const deletePatrolTask = (spaceId: number, taskId: number) =>
  del(`/spaces/${spaceId}/patrol-tasks/${taskId}`)

/** 证据统一走后端流式读取，key 由后端校验归属关系。 */
export const patrolEvidenceUrl = (spaceId: number, taskId: number, key: string) =>
  `/api/spaces/${spaceId}/patrol-tasks/${taskId}/evidence?key=${encodeURIComponent(key)}`
