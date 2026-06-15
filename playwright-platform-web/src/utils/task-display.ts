import type { TaskRecord } from '../types/task'

type TaskLike = Pick<TaskRecord, 'status' | 'cancelRequested'>
type TaskDurationLike = Pick<TaskRecord, 'status' | 'startedAt' | 'queuedAt' | 'createdAt' | 'durationMs'>

function normalizeEnum(value?: string | null) {
  return (value ?? '').trim().toUpperCase()
}

function isCancelingTask(task: TaskLike) {
  const status = normalizeEnum(task.status)
  return task.cancelRequested === true && (status === 'QUEUED' || status === 'RUNNING')
}

export function taskStatusText(task: TaskLike) {
  if (isCancelingTask(task)) {
    return '取消中'
  }

  const status = normalizeEnum(task.status)
  if (status === 'QUEUED') return '排队中'
  if (status === 'RUNNING') return '运行中'
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'TIMEOUT') return '超时'
  if (status === 'CANCELED') return '已取消'
  return status || '未知状态'
}

export function taskStatusType(task: TaskLike) {
  if (isCancelingTask(task)) return 'warning'

  const status = normalizeEnum(task.status)
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'QUEUED' || status === 'RUNNING') return 'warning'
  return 'info'
}

export function taskStageText(stage?: string | null) {
  const normalized = normalizeEnum(stage)
  if (!normalized) return '暂无'
  if (normalized === 'QUEUED') return '排队中'
  if (normalized === 'PREPARING') return '准备环境'
  if (normalized === 'INSTALLING') return '安装依赖'
  if (normalized === 'TESTING') return '执行测试'
  if (normalized === 'REPORTING') return '后处理'
  if (normalized === 'ARCHIVING') return '归档产物'
  if (normalized === 'FINISHED') return '执行完成'
  return normalized
}

export function taskResultCodeText(resultCode?: string | null) {
  const normalized = normalizeEnum(resultCode)
  if (!normalized) return '暂无'
  if (normalized === 'SUCCESS') return '执行成功'
  if (normalized === 'INSTALL_FAILED') return '安装失败'
  if (normalized === 'TEST_FAILED') return '测试失败'
  if (normalized === 'ARCHIVE_FAILED') return '归档失败'
  if (normalized === 'PREPARE_FAILED') return '准备失败'
  if (normalized === 'TIMEOUT') return '任务超时'
  if (normalized === 'CANCELED') return '已取消'
  if (normalized === 'SYSTEM_ABORTED') return '系统中断'
  if (normalized === 'SYSTEM_BUSY') return '系统繁忙'
  return normalized
}

export function taskTriggerTypeText(triggerType?: string | null) {
  const normalized = normalizeEnum(triggerType)
  if (!normalized) return '暂无'
  if (normalized === 'MANUAL') return '手动触发'
  if (normalized === 'SCHEDULED') return '定时触发'
  if (normalized === 'API') return '接口触发'
  return normalized
}

export function formatDuration(durationMs: number | null | undefined) {
  const totalSeconds = Math.max(0, Math.floor((durationMs ?? 0) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}min${String(seconds).padStart(2, '0')}s`
}

export function resolveTaskStartTimestamp(task: TaskDurationLike) {
  const raw = task.startedAt ?? task.queuedAt ?? task.createdAt
  if (!raw) {
    return null
  }
  const timestamp = new Date(raw).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

export function taskDurationText(task: TaskDurationLike, nowTimestamp: number) {
  const status = normalizeEnum(task.status)
  if (status !== 'RUNNING' && status !== 'QUEUED') {
    return formatDuration(task.durationMs)
  }

  const startTimestamp = resolveTaskStartTimestamp(task)
  if (startTimestamp === null) {
    return formatDuration(task.durationMs)
  }

  const elapsedMs = Math.max(task.durationMs ?? 0, nowTimestamp - startTimestamp)
  return formatDuration(elapsedMs)
}

export function caseStatusText(status?: string | null) {
  const normalized = normalizeEnum(status)
  if (!normalized) return '未知'
  if (normalized === 'PASSED') return '通过'
  if (normalized === 'FAILED') return '失败'
  if (normalized === 'SKIPPED') return '跳过'
  if (normalized === 'TIMEOUT') return '超时'
  return normalized
}

export function caseStatusType(status?: string | null) {
  const normalized = normalizeEnum(status)
  if (normalized === 'PASSED') return 'success'
  if (normalized === 'FAILED' || normalized === 'TIMEOUT') return 'danger'
  return 'info'
}

export function caseFilterLabel(status: 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED') {
  if (status === 'ALL') return '全部'
  if (status === 'FAILED') return '失败'
  if (status === 'PASSED') return '通过'
  return '跳过'
}
