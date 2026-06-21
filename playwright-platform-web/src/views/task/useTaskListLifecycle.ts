import { computed, onBeforeUnmount, onMounted, ref, watch, type ComputedRef } from 'vue'
import type { TaskRecord } from '../../types/task'

const DEFAULT_POLLING_INTERVAL_MS = 3000
const DEFAULT_DURATION_TICK_MS = 1000

interface UseTaskListLifecycleOptions {
  rows: ComputedRef<TaskRecord[]>
  sceneId: ComputedRef<number | null>
  loadTaskList: (page?: number, size?: number) => Promise<unknown>
  refreshTaskListSilently: () => Promise<unknown>
  onLoadError: (error: unknown) => void
  pollingIntervalMs?: number
  durationTickMs?: number
  now?: () => number
}

/**
 * Owns task-list lifecycle effects: initial loading, running-task polling, and duration ticks.
 *
 * Polling is enabled only while queued or running tasks exist, and in-flight
 * refreshes are de-duplicated to avoid overlapping list requests.
 */
export function useTaskListLifecycle({
  rows,
  sceneId,
  loadTaskList,
  refreshTaskListSilently,
  onLoadError,
  pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS,
  durationTickMs = DEFAULT_DURATION_TICK_MS,
  now = () => Date.now(),
}: UseTaskListLifecycleOptions) {
  const nowTimestamp = ref(now())
  const hasRunningTasks = computed(() => rows.value.some((item) => item.status === 'RUNNING' || item.status === 'QUEUED'))

  let pollingTimer: ReturnType<typeof setInterval> | null = null
  let durationTimer: ReturnType<typeof setInterval> | null = null
  let pollingInFlight = false

  function stopPolling() {
    if (pollingTimer !== null) {
      clearInterval(pollingTimer)
      pollingTimer = null
    }
  }

  function stopDurationTicker() {
    if (durationTimer !== null) {
      clearInterval(durationTimer)
      durationTimer = null
    }
  }

  function stopAll() {
    stopPolling()
    stopDurationTicker()
  }

  async function pollTaskList() {
    if (pollingInFlight) {
      return
    }
    pollingInFlight = true
    try {
      await refreshTaskListSilently()
    } finally {
      pollingInFlight = false
    }
  }

  function ensurePolling() {
    if (pollingTimer !== null || !hasRunningTasks.value) {
      return
    }
    pollingTimer = setInterval(() => {
      void pollTaskList()
    }, pollingIntervalMs)
  }

  function ensureDurationTicker() {
    if (durationTimer !== null || !hasRunningTasks.value) {
      return
    }
    durationTimer = setInterval(() => {
      nowTimestamp.value = now()
    }, durationTickMs)
  }

  async function loadWithErrorHandling() {
    try {
      nowTimestamp.value = now()
      await loadTaskList()
    } catch (error) {
      onLoadError(error)
    }
  }

  onMounted(() => {
    void loadWithErrorHandling()
  })

  watch(hasRunningTasks, (value) => {
    if (value) {
      ensurePolling()
      ensureDurationTicker()
      return
    }
    stopAll()
  }, { immediate: true })

  watch(sceneId, (value, previousValue) => {
    if (previousValue === undefined || value === previousValue) {
      return
    }
    stopAll()
    void loadWithErrorHandling()
  })

  onBeforeUnmount(() => {
    stopAll()
  })

  return {
    nowTimestamp,
  }
}
