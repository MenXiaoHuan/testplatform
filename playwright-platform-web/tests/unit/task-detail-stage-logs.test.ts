import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import TaskDetailView from '../../src/views/task/TaskDetailView.vue'
import { useTaskStore } from '../../src/stores/task'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { id: '101' },
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

describe('TaskDetailView stage logs', () => {
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
    store.caseResults = []
    store.stageLogs = [
      {
        id: 1,
        stage: 'TESTING',
        streamType: 'COMBINED',
        previewText: 'preview line',
        lineCount: 2,
        downloadUrl: 'http://localhost:10000/logs/testing.log',
      },
    ]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        text: async () => 'full log line',
      }),
    )
  })

  it('should render preview text without auto loading full stage logs', () => {
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

    expect(global.fetch).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('任务诊断')
  })

  it('should load full stage log content only after clicking the load action', async () => {
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

    const loadButton = wrapper.findAll('button').find((item) => item.text().includes('加载完整日志'))

    expect(loadButton).toBeDefined()

    await loadButton!.trigger('click')

    expect(global.fetch).toHaveBeenCalledWith('http://localhost:10000/logs/testing.log', expect.any(Object))
  })
})
