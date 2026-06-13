export interface SceneRecord {
  id: number
  repoId: number
  name: string
  description: string
  branch: string
  scheduleEnabled: boolean
  cronExpression: string
  lastTaskStatus: string | null
  lastRunAt: string | null
  environmentVariableCount: number
}

export interface SceneDetail {
  id: number
  repoId: number
  name: string
  description?: string | null
  branch?: string | null
  testSelectorType?: string | null
  testSelectorValue?: string | null
  matchValue?: string | null
  projectName?: string | null
  browser?: string | null
  envJson?: string | null
  runCommand?: string | null
  scheduleEnabled?: boolean | null
  cronExpression?: string | null
}

export interface SceneForm {
  repoId: number
  name: string
  description: string
  matchValue: string
  projectName?: string
  browser?: string
  envJson?: string
  scheduleEnabled: boolean
  cronExpression: string
  branch?: string
  testSelectorType?: string
  testSelectorValue?: string
  runCommand?: string
}

export const createSceneForm = (): SceneForm => ({
  repoId: 0,
  name: '',
  description: '',
  matchValue: '',
  projectName: 'chromium',
  browser: 'chromium',
  envJson: '',
  scheduleEnabled: false,
  cronExpression: '',
  branch: 'main',
  testSelectorType: 'file',
  testSelectorValue: '',
  runCommand: 'node ./scripts/run-e2e.cjs',
})
