export interface CaseArtifactLinkRecord {
  artifactType: string
  label: string
  scope: string
  url: string
}

export interface CaseResultRecord {
  id: number
  taskId: number
  historyId?: string | null
  fullName: string
  suiteName?: string | null
  storyName?: string | null
  status: string
  durationMs?: number | null
  projectName?: string | null
  errorMessage?: string | null
  videoUrl?: string | null
  traceUrl?: string | null
  screenshotUrls?: string[]
  logUrl?: string | null
  artifactCount: number
  artifacts?: CaseArtifactLinkRecord[]
}

export interface TaskCaseSummary {
  passed: number
  failed: number
  skipped: number
  total: number
}

export interface TaskArtifactSummaryRecord {
  videoCount: number
  traceCount: number
  screenshotCount: number
  logCount: number
  otherCount: number
}

export interface TaskProjectStatRecord {
  projectName: string
  total: number
}
