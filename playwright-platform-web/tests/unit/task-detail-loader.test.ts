import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, defineComponent, ref } from 'vue'
import { useTaskDetailLoader } from '../../src/views/task/useTaskDetailLoader'

describe('useTaskDetailLoader', () => {
  it('should load task detail on mount and when task id changes', async () => {
    const taskId = ref(101)
    const loadTaskDetailPage = vi.fn().mockResolvedValue(undefined)
    const onInvalidTaskId = vi.fn()
    const onLoadError = vi.fn()

    mount(defineComponent({
      setup() {
        useTaskDetailLoader({
          taskId: computed(() => taskId.value),
          loadTaskDetailPage,
          onInvalidTaskId,
          onLoadError,
        })
        return {}
      },
      template: '<div />',
    }))

    await flushPromises()
    taskId.value = 202
    await flushPromises()

    expect(loadTaskDetailPage).toHaveBeenNthCalledWith(1, 101)
    expect(loadTaskDetailPage).toHaveBeenNthCalledWith(2, 202)
    expect(onInvalidTaskId).not.toHaveBeenCalled()
    expect(onLoadError).not.toHaveBeenCalled()
  })

  it('should report invalid task id without sending request', async () => {
    const loadTaskDetailPage = vi.fn().mockResolvedValue(undefined)
    const onInvalidTaskId = vi.fn()

    mount(defineComponent({
      setup() {
        useTaskDetailLoader({
          taskId: computed(() => Number.NaN),
          loadTaskDetailPage,
          onInvalidTaskId,
          onLoadError: vi.fn(),
        })
        return {}
      },
      template: '<div />',
    }))

    await flushPromises()

    expect(onInvalidTaskId).toHaveBeenCalledTimes(1)
    expect(loadTaskDetailPage).not.toHaveBeenCalled()
  })
})
