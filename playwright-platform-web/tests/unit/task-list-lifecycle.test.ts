import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, defineComponent, ref } from 'vue'
import type { TaskRecord } from '../../src/types/task'
import { useTaskListLifecycle } from '../../src/views/task/useTaskListLifecycle'

describe('useTaskListLifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should load task list on mount and keep polling queued tasks', async () => {
    const rows = ref<TaskRecord[]>([
      {
        id: 101,
        sceneId: 11,
        repoId: 21,
        status: 'QUEUED',
        detailAvailable: false,
        triggerType: 'MANUAL',
        currentStage: 'QUEUED',
        resultCode: null,
        cancelRequested: false,
        branch: 'main',
      },
    ])
    const sceneId = ref<number | null>(11)
    const loadTaskList = vi.fn().mockResolvedValue(undefined)
    const refreshTaskListSilently = vi.fn().mockResolvedValue(undefined)
    const onLoadError = vi.fn()

    mount(defineComponent({
      setup() {
        useTaskListLifecycle({
          rows: computed(() => rows.value),
          sceneId: computed(() => sceneId.value),
          loadTaskList,
          refreshTaskListSilently,
          onLoadError,
        })
        return {}
      },
      template: '<div />',
    }))

    await flushPromises()
    expect(loadTaskList).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(3100)
    expect(refreshTaskListSilently).toHaveBeenCalledTimes(1)
    expect(onLoadError).not.toHaveBeenCalled()
  })

  it('should reload task list when scene id changes', async () => {
    const rows = ref<TaskRecord[]>([])
    const sceneId = ref<number | null>(11)
    const loadTaskList = vi.fn().mockResolvedValue(undefined)

    mount(defineComponent({
      setup() {
        useTaskListLifecycle({
          rows: computed(() => rows.value),
          sceneId: computed(() => sceneId.value),
          loadTaskList,
          refreshTaskListSilently: vi.fn().mockResolvedValue(undefined),
          onLoadError: vi.fn(),
        })
        return {}
      },
      template: '<div />',
    }))

    await flushPromises()
    sceneId.value = 22
    await flushPromises()

    expect(loadTaskList).toHaveBeenCalledTimes(2)
  })
})
