export interface SceneRecord {
  id: number
  repoId: number
  name: string
  branch: string
  testSelectorType: string
  testSelectorValue: string
  projectName?: string | null
  browser?: string | null
  envJson?: string | null
  runCommand: string
  enabled: boolean
}

export interface SceneForm {
  repoId: number
  name: string
  branch: string
  testSelectorType: string
  testSelectorValue: string
  projectName?: string
  browser?: string
  envJson?: string
  runCommand: string
  enabled: boolean
}

export const createSceneForm = (): SceneForm => ({
  repoId: 0,
  name: '',
  branch: 'main',
  testSelectorType: 'file',
  testSelectorValue: '',
  projectName: 'chromium',
  browser: 'chromium',
  envJson: '',
  runCommand: 'node ./scripts/run-e2e.cjs',
  enabled: true,
})
