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
  branch: string
  commitSha?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  createdAt?: string | null
  runnerName?: string | null
  reportUrl?: string | null
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
  reportReady?: boolean
  passedCount?: number
  failedCount?: number
  skippedCount?: number
}

export interface TaskReport {
  taskId: number
  reportUrl: string | null
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
