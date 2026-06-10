import { defineComponent, h, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TaskDetailView from './TaskDetailView.vue'

const ElCardStub = {
  template: '<div><slot name="header" /><slot /></div>',
}

const ElButtonStub = {
  props: ['disabled'],
  template: '<button :disabled="disabled"><slot /></button>',
}

const ElEmptyStub = {
  props: ['description'],
  template: '<div>{{ description }}</div>',
}

const ElTableStub = defineComponent({
  name: 'ElTableStub',
  props: {
    data: {
      type: Array,
      default: () => [],
    },
    emptyText: {
      type: String,
      default: '',
    },
  },
  provide() {
    return {
      tableData: this.data,
    }
  },
  render() {
    const rows = this.$props.data as unknown[]
    if (!rows.length) {
      return h('div', this.$props.emptyText)
    }
    return h('div', this.$slots.default?.())
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
    return h('div', [h('span', this.label), ...content])
  },
})

const ElLinkStub = {
  props: ['href'],
  template: '<a :href="href"><slot /></a>',
}

function mountView() {
  return mount(TaskDetailView, {
    global: {
      stubs: {
        'el-card': ElCardStub,
        'el-button': ElButtonStub,
        'el-empty': ElEmptyStub,
        'el-table': ElTableStub,
        'el-table-column': ElTableColumnStub,
        'el-link': ElLinkStub,
      },
    },
  })
}

const taskStoreState = reactive({
  current: {
    id: 1,
    sceneId: 2,
    repoId: 3,
    status: 'SUCCESS',
    triggerType: 'MANUAL',
    branch: 'main',
    durationMs: 1234,
    runnerName: 'centralized-runner',
    reportUrl: 'http://localhost:9000/report/1/index.html',
    logUrl: null,
    artifactCount: 2,
    hasArtifacts: true,
    reportReady: true,
  },
  report: {
    taskId: 1,
    reportUrl: 'http://localhost:9000/report/1/index.html',
  },
  artifacts: [
    {
      id: 1,
      taskId: 1,
      artifactType: 'REPORT_FILE',
      bucket: 'qa',
      objectKey: 'runs/1/artifacts/index.html',
      size: 512,
      url: 'http://localhost:9000/index.html',
    },
    {
      id: 2,
      taskId: 1,
      artifactType: 'REPORT_FILE',
      bucket: 'qa',
      objectKey: 'runs/1/artifacts/data/trace.zip',
      size: 1536,
      url: 'http://localhost:9000/trace.zip',
    },
  ],
  fetchDetail: vi.fn(async () => undefined),
})

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { id: '1' },
  }),
}))

vi.mock('../../stores/task', () => ({
  useTaskStore: () => taskStoreState,
}))

describe('TaskDetailView', () => {
  beforeEach(() => {
    taskStoreState.fetchDetail.mockClear()
    taskStoreState.current = {
      id: 1,
      sceneId: 2,
      repoId: 3,
      status: 'SUCCESS',
      triggerType: 'MANUAL',
      branch: 'main',
      durationMs: 1234,
      runnerName: 'centralized-runner',
      reportUrl: 'http://localhost:9000/report/1/index.html',
      logUrl: null,
      artifactCount: 2,
      hasArtifacts: true,
      reportReady: true,
    }
    taskStoreState.artifacts = [
      {
        id: 1,
        taskId: 1,
        artifactType: 'REPORT_FILE',
        bucket: 'qa',
        objectKey: 'runs/1/artifacts/index.html',
        size: 512,
        url: 'http://localhost:9000/index.html',
      },
      {
        id: 2,
        taskId: 1,
        artifactType: 'REPORT_FILE',
        bucket: 'qa',
        objectKey: 'runs/1/artifacts/data/trace.zip',
        size: 1536,
        url: 'http://localhost:9000/trace.zip',
      },
    ]
  })

  it('should render aggregated task detail fields', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('附件数量')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('附件状态')
    expect(wrapper.text()).toContain('已有附件')
    expect(wrapper.text()).toContain('报告状态')
    expect(wrapper.text()).toContain('已就绪')
  })

  it('should render artifact display metadata and actions', async () => {
    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('报告文件')
    expect(wrapper.text()).toContain('index.html')
    expect(wrapper.text()).toContain('trace.zip')
    expect(wrapper.text()).toContain('512 B')
    expect(wrapper.text()).toContain('1.5 KB')
    expect(wrapper.text()).toContain('打开')
    expect(wrapper.text()).toContain('下载')
  })

  it('should render improved artifact empty state', async () => {
    taskStoreState.artifacts = []
    taskStoreState.current = {
      ...taskStoreState.current!,
      artifactCount: 0,
      hasArtifacts: false,
    }

    const wrapper = mountView()

    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('任务未产出附件，或附件仍在归档中')
  })
})
