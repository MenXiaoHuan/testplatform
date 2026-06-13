import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskDetailView from '../../../../src/views/task/TaskDetailView.vue'

const push = vi.fn()
const fetchTaskDetailPageMock = vi.fn(async () => undefined)
const executeSceneMock = vi.fn(async () => undefined)
const fetchMock = vi.fn(async () => ({
  ok: true,
  text: async () => [
    '# Instructions',
    '- Following Playwright test failed.',
    '# Test info',
    '- Name:',
    'tests/interview_agent/login/login.spec.ts >> 登录模块 >> 输入正确的账号和密码',
    '- Location:',
    'tests/interview_agent/login/login.spec.ts:23:7',
    '# Error details',
    'Error:',
    'expect(page).toHaveURL(expected) failed',
    'Expected: "https://localhost:5173/#/pages/home/index"',
    'Received: "https://localhost:5173/#/pages/login/index"',
    'Timeout: 5000ms',
  ].join('\n'),
}))

const routeState = reactive({
  params: { id: '101' },
  query: { from: 'scene', sceneId: '11' } as Record<string, string>,
})

const storeState = reactive<any>({
  current: {
    id: 101,
    sceneId: 11,
    sceneName: '登录场景',
    repositoryName: '智能面试平台端到端测试',
    status: 'SUCCESS',
    detailAvailable: true,
    triggerType: 'MANUAL',
    runnerName: 'centralized-runner',
    branch: 'main',
    resolvedBrowser: 'chromium',
    resolvedRunCommand: 'npx playwright test tests/login.spec.ts --project chromium',
    resolvedMatchValue: 'tests/login.spec.ts',
    durationMs: 35000,
    startedAt: '2026-06-12T14:20:00',
    finishedAt: '2026-06-12T14:20:35',
    environmentVariableCount: 1,
  },
  reportSummary: {
    reportStatus: 'NOT_READY',
    reportUrl: null,
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
  },
  caseResults: [
    {
      id: 1,
      taskId: 101,
      fullName: 'login should succeed',
      status: 'PASSED',
      projectName: 'chromium',
      artifactCount: 4,
      videoUrl: 'http://localhost/video.webm',
      traceUrl: 'http://localhost/trace.zip',
      screenshotUrls: ['http://localhost/screenshot.png'],
      logUrl: 'http://localhost/log.txt',
      errorMessage: null,
      artifacts: [],
    },
  ],
  artifacts: [],
  fetchTaskDetailPage: fetchTaskDetailPageMock,
  executeScene: executeSceneMock,
})

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push }),
}))

vi.mock('@/stores/task', () => ({
  useTaskStore: () => storeState,
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.stubGlobal('fetch', fetchMock)

function mountView() {
  return mount(TaskDetailView, {
    global: {
      stubs: {
        'el-button': {
          props: ['type', 'link', 'disabled', 'plain', 'loading', 'title'],
          template: '<button class="button-stub" :title="title" :data-disabled="String(disabled)"><slot /></button>',
        },
        'el-tag': {
          props: ['type'],
          template: '<span class="tag-stub"><slot /></span>',
        },
        'el-empty': {
          props: ['description'],
          template: '<div class="empty-stub">{{ description }}</div>',
        },
      },
    },
  })
}

describe('TaskDetailView', () => {
  beforeEach(() => {
    push.mockReset()
    fetchTaskDetailPageMock.mockClear()
    executeSceneMock.mockClear()
    fetchMock.mockClear()
    routeState.params = { id: '101' }
    routeState.query = { from: 'scene', sceneId: '11' }
    storeState.current.status = 'SUCCESS'
    storeState.caseResults = [
      {
        id: 1,
        taskId: 101,
        fullName: 'login should succeed',
        status: 'PASSED',
        projectName: 'chromium',
        artifactCount: 4,
        videoUrl: 'http://localhost/video.webm',
        traceUrl: 'http://localhost/trace.zip',
        screenshotUrls: ['http://localhost/screenshot.png'],
        logUrl: 'http://localhost/log.txt',
        errorMessage: null,
        artifacts: [],
      },
    ]
  })

  it('renders the left back action and the right rerun action', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(fetchTaskDetailPageMock).toHaveBeenCalledWith(101)
    expect(wrapper.text()).toContain('返回场景中心')
    expect(wrapper.text()).toContain('重新执行')
    expect(wrapper.text()).toContain('结果总览')
    expect(wrapper.text()).not.toContain('执行证据')
    expect(wrapper.text()).not.toContain('附件归档')
    expect(wrapper.text()).not.toContain('打开报告')
    expect(wrapper.text()).not.toContain('任务基础信息')
    expect(wrapper.text()).not.toContain('查看深度报告')
  })

  it('renders case runtime actions and inline screenshots inside the expandable runtime section', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    const toggleButton = wrapper.findAll('button').find((button) => button.text() === '查看运行记录')
    expect(toggleButton).toBeDefined()

    await toggleButton!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('报告暂未生成')
    expect(wrapper.text()).toContain('视频')
    expect(wrapper.text()).toContain('Trace')
    expect(wrapper.text()).toContain('截图')
    expect(wrapper.text()).toContain('日志')
    expect(wrapper.text()).toContain('截图 / 视频')
    expect(wrapper.text()).toContain('Trace / 日志')
    expect(wrapper.text()).toContain('login should succeed')
    expect(wrapper.find('img.task-runtime-screenshot').attributes('src')).toBe('http://localhost/screenshot.png')
    expect(wrapper.find('video.task-runtime-video').attributes('src')).toBe('http://localhost/video.webm')
    expect(wrapper.text()).toContain('定位摘要')
    expect(wrapper.text()).toContain('展开原始日志')
  })

  it('keeps evidence visible for failed cases and highlights the error summary', async () => {
    storeState.current.status = 'FAILED'
    storeState.caseResults = [
      {
        id: 2,
        taskId: 101,
        fullName: 'login should fail loudly',
        status: 'FAILED',
        projectName: 'chromium',
        artifactCount: 4,
        videoUrl: 'http://localhost/video.webm',
        traceUrl: 'http://localhost/trace.zip',
        screenshotUrls: ['http://localhost/screenshot.png'],
        logUrl: 'http://localhost/log.txt',
        errorMessage: 'Expected login success but redirected to /error',
        artifacts: [],
      },
    ]

    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('login should fail loudly')
    expect(wrapper.text()).toContain('Expected login success but redirected to /error')
    expect(wrapper.text()).toContain('查看运行记录')
  })

  it('uses case artifact links as runtime fallback when direct urls are missing and loads log preview', async () => {
    storeState.current.status = 'FAILED'
    storeState.caseResults = [
      {
        id: 3,
        taskId: 101,
        fullName: 'login fallback artifacts',
        status: 'FAILED',
        projectName: 'chromium',
        artifactCount: 4,
        videoUrl: null,
        traceUrl: null,
        screenshotUrls: [],
        logUrl: null,
        errorMessage: 'fallback links should still be clickable',
        artifacts: [
          { artifactType: 'VIDEO', label: 'video', scope: 'CASE', url: 'http://localhost/fallback-video.webm' },
          { artifactType: 'TRACE', label: 'trace', scope: 'CASE', url: 'http://localhost/fallback-trace.zip' },
          { artifactType: 'SCREENSHOT', label: 'screenshot', scope: 'CASE', url: 'http://localhost/fallback-screenshot.png' },
          { artifactType: 'REPORT', label: 'report', scope: 'CASE', url: 'http://localhost/fallback-log.txt' },
        ],
      },
    ]

    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    const toggleButton = wrapper.findAll('button').find((button) => button.text() === '查看运行记录')
    expect(toggleButton).toBeDefined()

    await toggleButton!.trigger('click')
    await wrapper.vm.$nextTick()
    await Promise.resolve()
    await wrapper.vm.$nextTick()

    expect(fetchMock).toHaveBeenCalledWith('http://localhost/fallback-log.txt')
    const runtimeButtons = wrapper.findAll('button').filter((button) => ['视频', 'Trace', '日志'].includes(button.text()))
    expect(runtimeButtons.length).toBeGreaterThan(0)
    runtimeButtons.forEach((button) => {
      expect(button.attributes('data-disabled')).toBe('false')
    })
    expect(wrapper.find('img.task-runtime-screenshot').attributes('src')).toBe('http://localhost/fallback-screenshot.png')
    expect(wrapper.text()).toContain('expect(page).toHaveURL(expected) failed')
    expect(wrapper.text()).not.toContain('Following Playwright test failed.')

    const rawLogButton = wrapper.findAll('button').find((button) => button.text() === '展开原始日志')
    expect(rawLogButton).toBeDefined()
    await rawLogButton!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Following Playwright test failed.')
  })
})
