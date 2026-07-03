import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import TaskDetailView from '../../src/views/task/TaskDetailView.vue'
import { useTaskStore } from '../../src/stores/task'

const { createTraceShareUrlMock, windowOpenMock } = vi.hoisted(() => ({
  createTraceShareUrlMock: vi.fn(),
  windowOpenMock: vi.fn(),
}))

vi.mock('../../src/api/task', () => ({
  createTraceShareUrl: (...args: unknown[]) => createTraceShareUrlMock(...args),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { id: '101', spaceId: '7' },
    query: {},
  }),
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

vi.mock('../../src/views/task/useTaskDetailLoader', () => ({
  useTaskDetailLoader: () => {},
}))

const ElButtonStub = defineComponent({
  props: {
    loading: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
})

const ElTagStub = defineComponent({
  template: '<span><slot /></span>',
})

const ElEmptyStub = defineComponent({
  template: '<div><slot /></div>',
})

describe('TaskDetailView trace viewer', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    const store = useTaskStore()
    store.current = {
      id: 101,
      sceneId: 11,
      repoId: 21,
      status: 'FAILED',
      triggerType: 'MANUAL',
      branch: 'main',
      currentStage: 'FINISHED',
      resultCode: 'TEST_FAILED',
      cancelRequested: false,
    }
    store.caseResults = [
      {
        id: 1,
        taskId: 101,
        fullName: 'checkout :: should pay successfully',
        storyName: 'should pay successfully',
        suiteName: 'checkout',
        status: 'FAILED',
        artifactCount: 1,
        traceUrl: '/api/spaces/7/tasks/101/artifacts/11/download',
        artifacts: [],
      },
    ]
    createTraceShareUrlMock.mockReset()
    createTraceShareUrlMock.mockResolvedValue({
      shareUrl: '/api/public/traces/download?token=trace-token',
      expiresAt: '2026-07-03T10:15:30Z',
    })
    windowOpenMock.mockReset()
    vi.stubGlobal('open', windowOpenMock)
  })

  it('should request a share url before opening Trace Viewer', async () => {
    const wrapper = mount(TaskDetailView, {
      global: {
        plugins: [pinia],
        stubs: {
          'el-button': ElButtonStub,
          'el-tag': ElTagStub,
          'el-empty': ElEmptyStub,
        },
      },
    })

    const runtimeButton = wrapper.findAll('button').find((item) => item.text().includes('查看运行过程'))

    expect(runtimeButton).toBeDefined()

    await runtimeButton!.trigger('click')
    await flushPromises()

    const traceButton = wrapper.findAll('button').find((item) => item.text().includes('查看 Trace'))

    expect(traceButton).toBeDefined()

    await traceButton!.trigger('click')
    await flushPromises()

    expect(createTraceShareUrlMock).toHaveBeenCalledWith(7, 101, 11)
    const expectedTraceUrl = encodeURIComponent(`${window.location.origin}/api/public/traces/download?token=trace-token`)
    expect(windowOpenMock).toHaveBeenCalledWith(
      `https://trace.playwright.dev/?trace=${expectedTraceUrl}`,
      '_blank',
      'noopener',
    )
  })
})
