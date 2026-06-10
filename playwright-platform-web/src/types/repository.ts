export interface RepositoryRecord {
  id: number
  name: string
  gitUrl: string
  defaultBranch: string
  packageManager: string
  installCommand: string
  runCommandTemplate: string
  testRoot: string
  reportRelativePath: string
  nodeVersion: string
  enabled: boolean
}

export interface RepositoryForm {
  name: string
  gitUrl: string
  defaultBranch: string
  packageManager: string
  installCommand: string
  runCommandTemplate: string
  testRoot: string
  reportRelativePath: string
  nodeVersion: string
  enabled: boolean
}

export const createRepositoryForm = (): RepositoryForm => ({
  name: '',
  gitUrl: '',
  defaultBranch: 'main',
  packageManager: 'npm',
  installCommand: 'npm install',
  runCommandTemplate: 'node ./scripts/run-e2e.cjs',
  testRoot: 'tests',
  reportRelativePath: 'reports/allure-report',
  nodeVersion: '21',
  enabled: true,
})
