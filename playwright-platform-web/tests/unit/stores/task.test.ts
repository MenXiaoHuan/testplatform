import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTaskStore } from '../../../src/stores/task'

const getTaskMock = vi.fn()
const getTaskReportSummaryMock = vi.fn()
const listTaskCasesMock = vi.fn()
const listTasksMock = vi.fn()
const fetchSceneTasksMock = vi.fn()
const getTaskReportMock = vi.fn()
const listArtifactsMock = vi.fn()
const runSceneMock = vi.fn()

vi.mock('@/api/task', () => ({
  listTasks: (...args: unknown[]) => listTasksMock(...args),
  fetchSceneTasks: (...args: unknown[]) => fetchSceneTasksMock(...args),
  getTask: (...args: unknown[]) => getTaskMock(...args),
  runScene: (...args: unknown[]) => runSceneMock(...args),
  getTaskReport: (...args: unknown[]) => getTaskReportMock(...args),
  getTaskReportSummary: (...args: unknown[]) => getTaskReportSummaryMock(...args),
  listArtifacts: (...args: unknown[]) => listArtifactsMock(...args),
  listTaskCases: (...args: unknown[]) => listTaskCasesMock(...args),
}))

const detail = {
  id: 101,
  sceneId: 11,
  repoId: 7,
  sceneName: '登录场景',
  repositoryName: '智能面试平台端到端测试',
  status: 'SUCCESS',
  detailAvailable: true,
  triggerType: 'MANUAL',
  branch: 'main',
}

const caseResults = [
  {
    id: 1,
    taskId: 101,
    fullName: 'login should succeed',
    status: 'PASSED',
    artifactCount: 4,
    videoUrl: 'http://localhost/video.webm',
    traceUrl: 'http://localhost/trace.zip',
    screenshotUrls: ['http://localhost/screenshot.png'],
    logUrl: 'http://localhost/log.txt',
  },
]

const reportSummary = {
  task: detail,
  reportStatus: 'NOT_READY',
  reportUrl: null,
  artifacts: [],
  caseResults,
  caseSummary: {
    total: 1,
    passed: 1,
    failed: 0,
    skipped: 0,
  },
  artifactSummary: {
    videoCount: 1,
    traceCount: 1,
    screenshotCount: 1,
    logCount: 1,
    otherCount: 0,
  },
  projectStats: [{ projectName: 'chromium', total: 1 }],
  artifactCount: 4,
  reportReady: false,
}

describe('task store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads task detail page data from detail summary and case result endpoints', async () => {
    getTaskMock.mockResolvedValue(detail)
    getTaskReportSummaryMock.mockResolvedValue(reportSummary)
    listTaskCasesMock.mockResolvedValue(caseResults)

    const store = useTaskStore()
    await store.fetchTaskDetailPage(101)

    expect(getTaskMock).toHaveBeenCalledWith(101)
    expect(getTaskReportSummaryMock).toHaveBeenCalledWith(101)
    expect(listTaskCasesMock).toHaveBeenCalledWith(101)
    expect(store.current?.sceneName).toBe('登录场景')
    expect(store.reportSummary?.reportStatus).toBe('NOT_READY')
    expect(store.caseResults).toHaveLength(1)
  })

  it('keeps detail and case results when report summary loading fails', async () => {
    getTaskMock.mockResolvedValue(detail)
    getTaskReportSummaryMock.mockRejectedValue(new Error('summary failed'))
    listTaskCasesMock.mockResolvedValue(caseResults)

    const store = useTaskStore()
    await store.fetchTaskDetailPage(101)

    expect(store.current?.id).toBe(101)
    expect(store.caseResults).toHaveLength(1)
    expect(store.reportSummary).toBeNull()
  })

  it('prepends the newly created running task without waiting for a list refresh', async () => {
    runSceneMock.mockResolvedValue({
      id: 303,
      sceneId: 11,
      repoId: 7,
      status: 'RUNNING',
      triggerType: 'MANUAL',
      branch: 'main',
    })

    const store = useTaskStore()
    store.items = [
      {
        id: 202,
        sceneId: 11,
        repoId: 7,
        status: 'FAILED',
        triggerType: 'MANUAL',
        branch: 'main',
      },
    ]

    await store.executeScene(11, 11)

    expect(runSceneMock).toHaveBeenCalledWith(11)
    expect(fetchSceneTasksMock).not.toHaveBeenCalled()
    expect(listTasksMock).not.toHaveBeenCalled()
    expect(store.items.map((item) => item.id)).toEqual([303, 202])
    expect(store.items[0].status).toBe('RUNNING')
  })
})
