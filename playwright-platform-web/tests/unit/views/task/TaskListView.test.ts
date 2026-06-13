import { defineComponent, h, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskListView from '../../../../src/views/task/TaskListView.vue'

const push = vi.fn()

const routeState = reactive<{
  params: Record<string, string>
}>({
  params: { id: '11' },
})

const taskStoreState = reactive({
  items: [
    {
      id: 201,
      sceneId: 11,
      repoId: 7,
      status: 'RUNNING',
      triggerType: 'MANUAL',
      durationMs: 2000,
      failedCount: 0,
      passedCount: 0,
      skippedCount: 0,
    },
    {
      id: 202,
      sceneId: 11,
      repoId: 7,
      status: 'FAILED',
      triggerType: 'MANUAL',
      durationMs: 3100,
      failedCount: 1,
      passedCount: 0,
      skippedCount: 0,
    },
  ],
  loading: false,
  page: 1,
  size: 10,
  total: 2,
  totalPages: 1,
  fetchAll: vi.fn(async () => undefined),
  fetchByScene: vi.fn(async () => undefined),
  refreshList: vi.fn(async () => undefined),
  executeScene: vi.fn(async () => undefined),
})

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push }),
}))

vi.mock('@/stores/task', () => ({
  useTaskStore: () => taskStoreState,
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

const ElTableStub = defineComponent({
  name: 'ElTableStub',
  props: {
    data: {
      type: Array,
      default: () => [],
    },
  },
  provide() {
    return {
      tableData: this.data,
    }
  },
  render() {
    return h('div', { class: 'table-stub' }, this.$slots.default?.())
  },
})

const ElTableColumnStub = defineComponent({
  name: 'ElTableColumnStub',
  props: {
    label: {
      type: String,
      default: '',
    },
    prop: {
      type: String,
      default: '',
    },
  },
  inject: ['tableData'],
  render() {
    const rows = (this.tableData as Record<string, unknown>[] | undefined) ?? []
    const content = rows.flatMap((row) => {
      if (this.$slots.default) {
        return this.$slots.default({ row }) ?? []
      }
      return [h('span', String(row[this.prop] ?? ''))]
    })
    return h('div', { class: 'task-table-column' }, [
      h('span', { class: 'task-table-column__label' }, this.label),
      ...content,
    ])
  },
})

function mountView() {
  return mount(TaskListView, {
    global: {
      stubs: {
        'el-button': {
          props: ['type', 'link', 'disabled', 'title', 'plain', 'loading'],
          template:
            '<button class="button-stub" :class="$attrs.class" :data-type="type" :data-disabled="String(disabled)" :title="title"><slot /></button>',
        },
        'el-table': ElTableStub,
        'el-table-column': ElTableColumnStub,
        'el-tag': {
          props: ['type'],
          template: '<span :data-tag-type="type"><slot /></span>',
        },
        'el-pagination': {
          props: ['currentPage', 'pageSize', 'total'],
          template: '<div class="pagination-stub" />',
        },
      },
    },
  })
}

describe('TaskListView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    push.mockReset()
    taskStoreState.fetchAll.mockClear()
    taskStoreState.fetchByScene.mockClear()
    taskStoreState.refreshList.mockClear()
    taskStoreState.refreshList.mockImplementation(async () => undefined)
    taskStoreState.executeScene.mockClear()
    taskStoreState.items = [
      {
        id: 201,
        sceneId: 11,
        repoId: 7,
        status: 'RUNNING',
        triggerType: 'MANUAL',
        durationMs: 2000,
        failedCount: 0,
        passedCount: 0,
        skippedCount: 0,
      },
      {
        id: 202,
        sceneId: 11,
        repoId: 7,
        status: 'FAILED',
        triggerType: 'MANUAL',
        durationMs: 3100,
        failedCount: 1,
        passedCount: 0,
        skippedCount: 0,
      },
    ]
    taskStoreState.page = 1
    taskStoreState.size = 10
    taskStoreState.total = taskStoreState.items.length
    taskStoreState.totalPages = 1
    routeState.params = { id: '11' }
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders the back action in the left header slot and the execute action in the right header slot', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(wrapper.find('.content-panel__header-left').text()).toContain('返回场景中心')
    expect(wrapper.find('.content-panel__header-right').text()).toContain('执行任务')
  })

  it('disables detail entry for running tasks and keeps finished tasks clickable', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    const detailButtons = wrapper.findAll('button').filter((button) => button.text() === '详情')
    const detailTriggers = wrapper.findAll('.table-action-trigger')
    expect(detailButtons).toHaveLength(2)
    expect(detailTriggers).toHaveLength(2)

    expect(detailButtons[0].attributes('data-disabled')).toBe('true')
    expect(detailTriggers[0].attributes('title')).toBe('任务执行中，完成后可查看详情')

    expect(detailButtons[1].attributes('data-disabled')).toBe('false')
    expect(detailTriggers[1].attributes('title')).toBe('查看任务详情')
  })

  it('updates running task duration in real time', async () => {
    taskStoreState.items = [
      {
        id: 201,
        sceneId: 11,
        repoId: 7,
        status: 'RUNNING',
        triggerType: 'MANUAL',
        durationMs: 0,
        startedAt: new Date(Date.now() - 2000).toISOString(),
        failedCount: 0,
        passedCount: 0,
        skippedCount: 0,
      },
    ]

    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('00min02s')

    await vi.advanceTimersByTimeAsync(2000)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('00min04s')
  })

  it('polls the current scene tasks only when there are running tasks', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    taskStoreState.refreshList.mockClear()

    await vi.advanceTimersByTimeAsync(3000)

    expect(taskStoreState.refreshList).toHaveBeenCalledWith(11)
    wrapper.unmount()
  })

  it('does not poll when there are no running tasks', async () => {
    taskStoreState.items = [
      {
        id: 202,
        sceneId: 11,
        repoId: 7,
        status: 'FAILED',
        triggerType: 'MANUAL',
        durationMs: 3100,
        failedCount: 1,
        passedCount: 0,
        skippedCount: 0,
      },
    ]

    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    taskStoreState.refreshList.mockClear()

    await vi.advanceTimersByTimeAsync(3000)

    expect(taskStoreState.refreshList).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('stops polling after running tasks finish', async () => {
    taskStoreState.refreshList.mockImplementation(async () => {
      taskStoreState.items = [
        {
          id: 203,
          sceneId: 11,
          repoId: 7,
          status: 'FAILED',
          triggerType: 'MANUAL',
          durationMs: 4000,
          failedCount: 1,
          passedCount: 0,
          skippedCount: 0,
        },
      ]
    })

    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    taskStoreState.refreshList.mockClear()

    await vi.advanceTimersByTimeAsync(3000)
    await wrapper.vm.$nextTick()
    await vi.advanceTimersByTimeAsync(3000)

    expect(taskStoreState.refreshList).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('polls the global task list when the route has no scene id', async () => {
    routeState.params = {}

    const wrapper = mountView()

    await wrapper.vm.$nextTick()
    expect(taskStoreState.fetchAll).toHaveBeenCalledTimes(1)
    taskStoreState.refreshList.mockClear()

    await vi.advanceTimersByTimeAsync(3000)

    expect(wrapper.find('.content-panel__header-left').text()).toBe('')
    expect(taskStoreState.refreshList).toHaveBeenCalledWith(null)
    wrapper.unmount()
  })
})
