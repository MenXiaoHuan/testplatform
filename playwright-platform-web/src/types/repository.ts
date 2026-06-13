export interface RepositoryRecord {
  id: number
  name: string
  gitUrl: string
  defaultBranch: string
  workingDirectory?: string
  installCommand: string
  runCommandTemplate: string
  testRoot: string
  reportRelativePath: string
  enabled: boolean
}

export interface RepositoryForm {
  name: string
  gitUrl: string
  defaultBranch: string
  workingDirectory: string
  installCommand: string
  runCommandTemplate: string
  testRoot: string
  reportRelativePath: string
  enabled: boolean
}

export const createRepositoryForm = (): RepositoryForm => ({
  name: '',
  gitUrl: '',
  defaultBranch: 'main',
  workingDirectory: '',
  installCommand: 'npm install && npx playwright install',
  runCommandTemplate: 'npx playwright test',
  testRoot: 'tests',
  reportRelativePath: 'reports/allure-report',
  enabled: true,
})
