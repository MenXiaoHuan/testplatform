import { onBeforeUnmount, onMounted, watch, type ComputedRef } from 'vue'

interface UseTaskDetailLoaderOptions {
  taskId: ComputedRef<number>
  loadTaskDetailPage: (taskId: number) => Promise<unknown>
  onInvalidTaskId: () => void
  onLoadError: (error: unknown) => void
  /** 仅当任务处于 QUEUED/RUNNING 时轮询，终态自动停止。 */
  shouldPoll?: () => boolean
  pollingIntervalMs?: number
}

/**
 * Loads task detail whenever the route-derived task id becomes available or changes.
 *
 * 任务详情页不能只在 mounted 时加载一次：执行中的任务状态、日志、用例都在变化。
 * 这里在任务处于 QUEUED/RUNNING 时做轻量轮询，终态自动停止；页面隐藏时跳过请求，
 * 避免后台标签页持续打接口。
 */
export function useTaskDetailLoader({
  taskId,
  loadTaskDetailPage,
  onInvalidTaskId,
  onLoadError,
  shouldPoll,
  pollingIntervalMs = 3000,
}: UseTaskDetailLoaderOptions) {
  let pollingTimer: ReturnType<typeof setInterval> | null = null
  let inFlight = false

  function stopPolling() {
    if (pollingTimer !== null) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
  }

  function syncPolling() {
    if (!shouldPoll?.()) {
      stopPolling()
      return
    }
    if (pollingTimer !== null) {
      return
    }
    pollingTimer = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) {
        return
      }
      void loadCurrentTaskDetail(true)
    }, pollingIntervalMs)
  }

  async function loadCurrentTaskDetail(silent = false) {
    if (!Number.isFinite(taskId.value)) {
      if (!silent) {
        onInvalidTaskId()
      }
      return
    }
    if (inFlight) {
      return
    }
    inFlight = true
    try {
      await loadTaskDetailPage(taskId.value)
    } catch (error) {
      if (!silent) {
        onLoadError(error)
      }
    } finally {
      inFlight = false
      syncPolling()
    }
  }

  onMounted(() => {
    void loadCurrentTaskDetail()
  })

  watch(taskId, (value, previousValue) => {
    if (previousValue === undefined || value === previousValue) {
      return
    }
    stopPolling()
    void loadCurrentTaskDetail()
  })

  onBeforeUnmount(() => {
    stopPolling()
  })

  return {
    loadCurrentTaskDetail,
  }
}
