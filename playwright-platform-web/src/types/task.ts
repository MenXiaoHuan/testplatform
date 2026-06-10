export interface TaskRecord {
  id: number
  sceneId: number
  repoId: number
  status: string
  triggerType: string
  triggerUser?: string | null
  branch: string
  commitSha?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  runnerName?: string | null
  reportUrl?: string | null
  logUrl?: string | null
  artifactCount?: number
  hasArtifacts?: boolean
  reportReady?: boolean
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
