export type PatrolAuthType = 'NONE' | 'COOKIE' | 'TOKEN' | 'LOCAL_STORAGE'
export type PatrolRisk = 'READ' | 'WRITE' | 'DESTRUCTIVE' | 'AUTH'
export type PatrolCaseStatus = 'PENDING' | 'PASS' | 'FAIL' | 'SKIP'
export type PatrolExecStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'BLOCKED'

export interface AiModelConfig {
  id: number
  spaceId: number | null
  name: string
  providerType: string
  baseUrl: string
  apiKeyMasked: string
  modelId: string
  supportsVision: boolean
  supportsTools: boolean
  supportsJson: boolean
  maxTokens: number
  temperature: number
  enabled: boolean
  lastTestStatus?: string | null
  lastTestMessage?: string | null
  lastTestedAt?: string | null
  createdAt?: string | null
}

export interface AiModelOption {
  id: number
  name: string
  modelId: string
  supportsVision: boolean
}

export interface AiModelConfigPayload {
  name: string
  providerType: string
  baseUrl: string
  apiKey?: string
  modelId: string
  supportsVision: boolean
  supportsTools: boolean
  supportsJson: boolean
  maxTokens: number
  temperature: number
  enabled: boolean
}

export interface PatrolScene {
  id: number
  spaceId: number
  name: string
  description?: string | null
  startUrl: string
  browser: string
  headless: boolean
  authType: PatrolAuthType
  authConfigured: boolean
  planModelId?: number | null
  planModelName?: string | null
  judgeModelId?: number | null
  judgeModelName?: string | null
  taskBrief?: string | null
  budgetDurationMinutes: number
  budgetLlmTokens: number
  visibleTextLimit: number
  budgetCases: number
  scrollScan: boolean
  recordVideo: boolean
  autoApproveWrite: boolean
  autoApproveDestructive: boolean
  scheduleEnabled: boolean
  cronExpression?: string | null
  nextRunAt?: string | null
  lastRunAt?: string | null
  lastTaskId?: number | null
  lastTaskStatus?: string | null
  totalTokenUsed: number
  createdAt?: string | null
}

export interface PatrolScenePayload {
  name: string
  description?: string | null
  startUrl: string
  browser: string
  headless: boolean
  authType: PatrolAuthType
  authCookie?: string
  authToken?: string
  authTokenHeader?: string
  authLocalStorage?: string
  planModelId?: number | null
  judgeModelId?: number | null
  taskBrief?: string | null
  budgetDurationMinutes: number
  budgetLlmTokens: number
  visibleTextLimit: number
  scrollScan: boolean
  recordVideo: boolean
  autoApproveWrite: boolean
  autoApproveDestructive: boolean
  scheduleEnabled: boolean
  cronExpression?: string | null
}

export interface PatrolTask {
  id: number
  spaceId: number
  sceneId: number
  sceneName: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: string
  currentStage: string
  startUrl: string
  taskBrief?: string | null
  budgetDurationMinutes: number
  budgetLlmTokens: number
  budgetCases: number
  caseCount: number
  casePassCount: number
  caseFailCount: number
  caseSkipCount: number
  findingP0Count: number
  findingP1Count: number
  findingP2Count: number
  llmTokenUsed: number
  degraded: boolean
  resultMessage?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  retentionUntil?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  createdBy?: string | null
  createdAt?: string | null
  coverageTotal: number
  coverageCovered: number
  coverageRate: number
}

export interface PatrolTaskSummary {
  taskCount: number
  avgLlmTokens: number
  avgCoverageRate: number
  avgDurationMinutes: number
}

export interface PatrolCaseStep {
  id: number
  seq: number
  action: string
  targetSelector?: string | null
  targetLabel?: string | null
  inputValue?: string | null
  operation: PatrolRisk
  risk: PatrolRisk
  approved: boolean
  status: PatrolExecStatus
  message?: string | null
  screenshotKey?: string | null
  requestJson?: string | null
  durationMs?: number | null
}

export interface PatrolCase {
  id: number
  seq: number
  name: string
  intent?: string | null
  expected?: string | null
  priority: string
  worstRisk: PatrolRisk
  status: PatrolCaseStatus
  execStatus: PatrolExecStatus
  source: 'LLM' | 'RULE'
  judgeReason?: string | null
  videoKey?: string | null
  consoleKey?: string | null
  domKey?: string | null
  screenshotKey?: string | null
  tokens: number
  durationMs?: number | null
  degraded: boolean
  steps: PatrolCaseStep[]
}

export interface PatrolReport {
  taskId: number
  status: string
  resultMessage?: string | null
  degraded: boolean
  visionConfigured: boolean
  cases: {
    total: number
    pass: number
    fail: number
    skip: number
    passRate: number
  }
  coverage: {
    totalElements: number
    coveredElements: number
    coverageRate: number
  }
  tokens: { used: number; budget: number }
  pendingApprovalCaseIds: number[]
}

export interface PatrolTaskDetail {
  task: PatrolTask
  snapshotJson?: string | null
  snapshotScreenshotKey?: string | null
  modelSnapshotJson?: string | null
  cases: PatrolCase[]
  report: PatrolReport
}

export interface PatrolModelLog {
  id: number
  taskId: number
  caseId?: number | null
  caseName?: string | null
  stage: 'PLAN' | 'COVERAGE' | 'JUDGE' | string
  purpose: string
  modelId?: string | null
  status: 'SUCCESS' | 'DEGRADED' | 'FAILED' | 'SKIPPED' | string
  systemPrompt?: string | null
  userPrompt?: string | null
  rawOutput?: string | null
  promptTokens: number
  completionTokens: number
  durationMs: number
  errorMessage?: string | null
  createdAt: string
}

export interface PatrolApprovalPayload {
  allowWrite: boolean
  allowDestructive: boolean
  approvedCaseIds?: number[]
}
