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

export type AiPromptLabel = 'PLAN' | 'SUPPLEMENT' | 'JUDGE' | 'RESOLVE'

export interface AiPromptConfig {
  id: number | null
  label: AiPromptLabel
  labelName: string
  /** 当前生效内容：自定义覆盖或内置默认 */
  content: string
  /** 内置默认内容，用于恢复与对比 */
  defaultContent: string
  custom: boolean
  updatedBy?: string | null
  updatedAt?: string | null
}

export interface AiPromptConfigPayload {
  label: AiPromptLabel
  content: string
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

export interface PatrolSupplementStep {
  action: string
  selector?: string | null
  frame?: string[] | null
  target?: string | null
  value?: string | null
  confidence?: number | null
  reason?: string | null
}

/** 场景补充：一个补充入口（带前置条件的起始状态）。 */
export interface PatrolSceneSupplement {
  id?: number | null
  name: string
  entryType: 'ACTION' | 'URL'
  preconditionText?: string | null
  startUrl?: string | null
  focusText?: string | null
  enabled: boolean
  steps?: PatrolSupplementStep[]
  confidence?: number | null
  unresolvedReason?: string | null
}

export interface PatrolSupplementResolveResult {
  steps: PatrolSupplementStep[]
  confidence: number
  unresolvedReason?: string | null
  source?: string | null
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
  resolveModelId?: number | null
  resolveModelName?: string | null
  supplementModelId?: number | null
  supplementModelName?: string | null
  supplements?: PatrolSceneSupplement[]
  budgetDurationMinutes: number
  visibleTextLimit: number
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
  resolveModelId?: number | null
  supplementModelId?: number | null
  supplements?: PatrolSceneSupplement[]
  budgetDurationMinutes: number
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
  parentTaskId?: number | null
  entryType?: 'MAIN' | 'SUPPLEMENT' | string
  entryLabel?: string | null
  sceneName: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: string
  currentStage: string
  startUrl: string
  taskBrief?: string | null
  budgetDurationMinutes: number
  caseCount: number
  casePassCount: number
  caseFailCount: number
  caseSkipCount: number
  findingP0Count: number
  findingP1Count: number
  findingP2Count: number
  llmTokenUsed: number
  degraded: boolean
  /** 规划/补全/判定的停止与降级原因，用于概览提示 */
  planningNote?: string | null
  resultMessage?: string | null
  /** 失败/降级的可执行解决建议，由后端按错误类型生成 */
  advice?: string[] | null
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
  /** 所属 Run（主入口或补充入口） */
  runId?: number | null
  /** 补充入口名称，主入口为空 */
  entryLabel?: string | null
}

export interface PatrolRun {
  runId: number
  entryType: 'MAIN' | 'SUPPLEMENT' | string
  entryLabel?: string | null
  startUrl: string
  status: string
  currentStage: string
  resultMessage?: string | null
  caseCount: number
  casePassCount: number
  caseFailCount: number
  caseSkipCount: number
  findingP0Count: number
  findingP1Count: number
  findingP2Count: number
  llmTokenUsed: number
  degraded: boolean
  startedAt?: string | null
  finishedAt?: string | null
  durationMs?: number | null
  coverageTotal: number
  coverageCovered: number
  coverageRate: number
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
  tokens: { used: number }
  pendingApprovalCaseIds: number[]
  /** 待审批的补充入口名称（写操作前置，审批通过后才执行） */
  pendingSupplementLabels?: string[]
}

export interface PatrolTaskDetail {
  task: PatrolTask
  snapshotJson?: string | null
  snapshotScreenshotKey?: string | null
  modelSnapshotJson?: string | null
  cases: PatrolCase[]
  report: PatrolReport
  /** 配置了补充入口时返回：主入口 + 各补充入口的执行情况 */
  runs?: PatrolRun[]
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
