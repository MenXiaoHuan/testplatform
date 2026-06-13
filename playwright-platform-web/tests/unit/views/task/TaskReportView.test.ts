import { reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskReportView from '../../../../src/views/task/TaskReportView.vue'

const push = vi.fn()
const fetchReportSummaryMock = vi.fn(async () => undefined)

const routeState = reactive({
  params: { id: '101' },
})

const storeState = reactive<any>({
  reportSummary: {
    task: {
      id: 101,
      status: 'FAILED',
      resolvedBrowser: 'chromium',
      resolvedMatchValue: 'tests/login.spec.ts',
      resolvedRunCommand: 'npx playwright test tests/login.spec.ts --project chromium',
    },
    reportStatus: 'READY',
    reportUrl: 'http://localhost/report/index.html',
    artifacts: [
      {
        id: 1,
        taskId: 101,
        artifactType: 'TRACE',
        objectKey: 'runs/101/trace.zip',
        url: 'http://localhost/trace.zip',
      },
    ],
    caseResults: [
      {
        id: 1,
        taskId: 101,
        fullName: 'login should fail',
        status: 'FAILED',
        projectName: 'chromium',
        artifactCount: 2,
        artifacts: [
          { artifactType: 'TRACE', label: 'trace', scope: 'CASE', url: 'http://localhost/trace.zip' },
        ],
      },
    ],
    caseSummary: {
      total: 1,
      passed: 0,
      failed: 1,
      skipped: 0,
    },
    artifactSummary: {
      videoCount: 0,
      traceCount: 1,
      screenshotCount: 0,
      logCount: 0,
      otherCount: 0,
    },
    projectStats: [{ projectName: 'chromium', total: 1 }],
    artifactCount: 1,
    reportReady: true,
  },
  fetchReportSummary: fetchReportSummaryMock,
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
  },
}))

function mountView() {
  return mount(TaskReportView, {
    global: {
      stubs: {
        'el-button': {
          props: ['type', 'plain', 'disabled', 'link'],
          template: '<button class="button-stub"><slot /></button>',
        },
        'el-card': {
          template: '<section><slot name="header" /><slot /></section>',
        },
        'el-table': {
          template: '<div><slot /></div>',
        },
        'el-table-column': {
          template: '<div><slot :row="{}" /></div>',
        },
      },
    },
  })
}

describe('TaskReportView', () => {
  beforeEach(() => {
    push.mockReset()
    fetchReportSummaryMock.mockClear()
  })

  it('acts as a task report page with a back-to-detail entry', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(fetchReportSummaryMock).toHaveBeenCalledWith(101)
    expect(wrapper.text()).toContain('任务报告')
    expect(wrapper.text()).toContain('返回任务详情')
  })
})
