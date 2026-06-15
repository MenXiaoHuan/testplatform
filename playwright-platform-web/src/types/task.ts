export interface TaskRecord {
  id: number
  sceneId: number
  repoId: number
  sceneName?: string | null
  repositoryName?: string | null
  status: string
  detailAvailable?: boolean
  triggerType: string
  triggerUser?: string | null
  triggerReason?: string | null
  branch: string
  commitSha?: string | null
  currentStage?: string | null
  resultCode?: string | null
  resultMessage?: string | null
  cancelRequested?: boolean
  cancelRequestedBy?: string | null
  queuedAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  createdAt?: string | null
  runnerName?: string | null
  logUrl?: string | null
  resolvedBranch?: string | null
  resolvedBrowser?: string | null
  resolvedEnvJson?: string | null
  resolvedMatchValue?: string | null
  resolvedTestRoot?: string | null
  resolvedRunCommand?: string | null
  environmentVariableCount?: number
  artifactCount?: number
  hasArtifacts?: boolean
  passedCount?: number
  failedCount?: number
  skippedCount?: number
}

export interface ArtifactRecord {
  id: number
  taskId: number
  caseResultId?: number | null
  artifactType: string
  bucket: string
  objectKey: string
  contentType?: string | null
  size?: number | null
  url?: string | null
}

export interface TaskStageLogRecord {
  id: number
  stage: string
  streamType: string
  previewText?: string | null
  lineCount: number
  downloadUrl?: string | null
}
