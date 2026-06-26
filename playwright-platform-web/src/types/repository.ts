export interface RepositoryRecord {
  id: number
  name: string
  gitUrl: string
  defaultBranch: string
  workingDirectory?: string
  installCommand: string
  runCommandTemplate: string
  testRoot: string
  resultsIndexRelativePath: string
  artifactRootRelativePath: string
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
  resultsIndexRelativePath: string
  artifactRootRelativePath: string
  enabled: boolean
}

export const createRepositoryForm = (): RepositoryForm => ({
  name: '',
  gitUrl: '',
  defaultBranch: 'main',
  workingDirectory: '',
  installCommand: 'npm install',
  runCommandTemplate: 'npx playwright test',
  testRoot: 'tests',
  resultsIndexRelativePath: 'test-results/.playwright-results.json',
  artifactRootRelativePath: '.playwright-artifacts',
  enabled: true,
})
