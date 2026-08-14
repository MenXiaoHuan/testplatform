import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, provide } from 'vue'
import EventListView from '../../src/views/event/EventListView.vue'
import { useScheduleEventStore } from '../../src/stores/schedule-event'

const { pushMock, messageCallMock } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  messageCallMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
  useRoute: () => ({
    query: { scheduleType: 'AGENT' },
  }),
}))

vi.mock('../../src/utils/ui-feedback', () => ({
  showAppToast: (...args: unknown[]) => messageCallMock(...args),
  confirmDangerAction: vi.fn().mockResolvedValue(true),
}))

const ListPageShellStub = defineComponent({
  template: '<div><slot name="header-left" /><slot name="header-right" /><slot /></div>',
})

const ElButtonStub = defineComponent({
  props: {
    loading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

const ElTableStub = defineComponent({
  props: {
    data: {
      type: Array,
      default: () => [],
    },
  },
  setup(props, { slots }) {
    provide('tableRows', props.data)
    return () => slots.default?.()
  },
})

describe('EventListView', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    pushMock.mockReset()
    messageCallMock.mockReset()
    const store = useScheduleEventStore()
    store.fetchAll = vi.fn().mockResolvedValue(undefined)
    store.retry = vi.fn().mockResolvedValue({
      id: 101,
      sceneId: 11,
      repoId: 21,
      status: 'QUEUED',
      triggerType: 'SCHEDULED',
      branch: 'main',
    })
    store.items = [{
      id: 7,
      sceneId: 11,
      sceneName: '测试场景',
      plannedFireAt: '2026-07-02T12:00:00',
      status: 'FAILED',
      scheduleType: 'CRON',
      retryCount: 1,
      triggerReason: 'cron:0 */5 * * * *',
      errorMessage: 'system busy',
      taskId: null,
    }]
    store.page = 1
    store.size = 20
    store.total = 1
  })

  it('should load schedule events on mount using route scheduleType query', async () => {
    const wrapper = mount(EventListView, {
      global: {
        plugins: [pinia],
        stubs: {
          ListPageShell: ListPageShellStub,
          'el-button': ElButtonStub,
          'el-table': ElTableStub,
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio-button': { template: '<label><slot /></label>' },
          'el-input': { template: '<input />' },
        },
      },
    })

    await flushPromises()
    const store = useScheduleEventStore()
    expect(store.setScheduleTypeFilter).toBeDefined()
    expect(store.fetchAll).toHaveBeenCalled()
    expect(wrapper.text()).toContain('调度事件')
  })

  it('should retry issue event when clicking retry action', async () => {
    const wrapper = mount(EventListView, {
      global: {
        plugins: [pinia],
        stubs: {
          ListPageShell: ListPageShellStub,
          'el-button': ElButtonStub,
          'el-table': ElTableStub,
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio-button': { template: '<label><slot /></label>' },
          'el-input': { template: '<input />' },
        },
      },
    })

    await flushPromises()
    const button = wrapper.findAll('button').find((item) => item.text().includes('重试'))
    expect(button).toBeDefined()
    await button!.trigger('click')

    const store = useScheduleEventStore()
    expect(store.retry).toHaveBeenCalledWith(7, {
      operatorName: 'anonymous',
      comment: 'manual retry from web',
    })
    expect(messageCallMock).toHaveBeenCalled()
  })
})
