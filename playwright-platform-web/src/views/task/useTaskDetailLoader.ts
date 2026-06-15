import { onMounted, watch, type ComputedRef } from 'vue'

interface UseTaskDetailLoaderOptions {
  taskId: ComputedRef<number>
  loadTaskDetailPage: (taskId: number) => Promise<unknown>
  onInvalidTaskId: () => void
  onLoadError: (error: unknown) => void
}

export function useTaskDetailLoader({
  taskId,
  loadTaskDetailPage,
  onInvalidTaskId,
  onLoadError,
}: UseTaskDetailLoaderOptions) {
  async function loadCurrentTaskDetail() {
    if (!Number.isFinite(taskId.value)) {
      onInvalidTaskId()
      return
    }
    try {
      await loadTaskDetailPage(taskId.value)
    } catch (error) {
      onLoadError(error)
    }
  }

  onMounted(() => {
    void loadCurrentTaskDetail()
  })

  watch(taskId, (value, previousValue) => {
    if (previousValue === undefined || value === previousValue) {
      return
    }
    void loadCurrentTaskDetail()
  })

  return {
    loadCurrentTaskDetail,
  }
}
