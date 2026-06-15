import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, ref } from 'vue'
import TaskListView from '../../src/views/task/TaskListView.vue'
import { useTaskStore } from '../../src/stores/task'

const { pushMock, messageCallMock, messageSuccessMock, messageErrorMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  messageCallMock: vi.fn(),
  messageSuccessMock: vi.fn(),
  messageErrorMock: vi.fn(),
}))

const routeParams = ref<Record<string, unknown>>({})
const nowTimestampRef = ref(Date.now())
let pinia: ReturnType<typeof createPinia>

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
  useRoute: () => ({
    params: routeParams.value,
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  const messageMock = Object.assign(messageCallMock, {
    success: messageSuccessMock,
    error: messageErrorMock,
  })
  return {
    ...actual,
    ElMessage: messageMock,
  }
})

vi.mock('../../src/views/task/useTaskListLifecycle', () => ({
  useTaskListLifecycle: () => ({
    nowTimestamp: nowTimestampRef,
  }),
}))

const ListPageShellStub = defineComponent({
  template: '<div><slot name="header-left" /><slot name="header-right" /><slot /></div>',
})

const ElTooltipStub = defineComponent({
  props: {
    content: {
      type: String,
      default: '',
    },
  },
  template: `
    <div>
      <div>{{ content }}</div>
      <slot />
    </div>
  `,
})

describe('TaskListView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    routeParams.value = {}
    nowTimestampRef.value = Date.now()
    pushMock.mockReset()
    messageCallMock.mockReset()
    messageSuccessMock.mockReset()
    messageErrorMock.mockReset()
    pinia = createPinia()
    setActivePinia(pinia)
    const store = useTaskStore()
    store.fetchAll = vi.fn().mockResolvedValue(undefined)
    store.fetchByScene = vi.fn().mockResolvedValue(undefined)
    store.refreshList = vi.fn().mockResolvedValue(undefined)
    store.executeScene = vi.fn().mockResolvedValue({
      id: 102,
      sceneId: 11,
      repoId: 21,
      status: 'QUEUED',
      detailAvailable: false,
      triggerType: 'MANUAL',
      currentStage: 'QUEUED',
      resultCode: null,
      cancelRequested: false,
      branch: 'main',
    })
    store.items = [{
      id: 101,
      sceneId: 11,
      repoId: 21,
      status: 'RUNNING',
      detailAvailable: true,
      triggerType: 'SCHEDULED',
      currentStage: 'TESTING',
      resultCode: null,
      cancelRequested: true,
      branch: 'main',
      queuedAt: '2026-06-13T10:00:00',
      failedCount: 1,
      passedCount: 3,
      skippedCount: 0,
    }]
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should show toast message when executing current scene fails because an active task exists', async () => {
    routeParams.value = { id: '11' }
    setActivePinia(pinia)
    const store = useTaskStore()
    store.executeScene = vi.fn().mockRejectedValue({
      response: {
        data: {
          code: 'CONFLICT',
          msg: '当前场景已有执行中的任务，请稍后再试',
        },
      },
    })

    const wrapper = mount(TaskListView, {
      global: {
        plugins: [pinia],
        stubs: {
          ListPageShell: ListPageShellStub,
          'el-tooltip': ElTooltipStub,
        },
      },
    })

    const executeButton = wrapper.findAll('button').find((item) => item.text().includes('执行任务'))
    expect(executeButton).toBeDefined()
    await executeButton!.trigger('click')

    expect(store.executeScene).toHaveBeenCalledWith(11, 11)
    expect(messageSuccessMock).not.toHaveBeenCalled()
    expect(messageCallMock).toHaveBeenCalledWith({
      type: 'warning',
      message: '当前场景已有执行中的任务，请稍后再试',
      customClass: 'app-toast app-toast--warning',
      showClose: false,
      duration: 3000,
      offset: 28,
    })
    expect(messageErrorMock).not.toHaveBeenCalled()
  })
})
