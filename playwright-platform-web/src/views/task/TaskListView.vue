<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useTaskStore } from '../../stores/task'
import type { TaskRecord } from '../../types/task'
import { toErrorMessage } from '../../utils/error'

const TASK_POLLING_INTERVAL_MS = 3000
const TASK_DURATION_TICK_MS = 1000

const router = useRouter()
const route = useRoute()
const store = useTaskStore()
const rows = computed(() => store.items)
const loading = computed(() => store.loading)
const pagination = computed(() => ({
  page: store.page,
  size: store.size,
  total: store.total,
}))
const hasRunningTasks = computed(() => rows.value.some((item) => item.status === 'RUNNING'))
const nowTimestamp = ref(Date.now())
const sceneId = computed(() => {
  const raw = route.params.id
  const value = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isFinite(value) ? value : null
})

let pollingTimer: ReturnType<typeof setInterval> | null = null
let durationTimer: ReturnType<typeof setInterval> | null = null
let pollingInFlight = false

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function formatDuration(durationMs: number | null | undefined) {
  const totalSeconds = Math.max(0, Math.floor((durationMs ?? 0) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}min${String(seconds).padStart(2, '0')}s`
}

function resolveTaskStartTimestamp(row: TaskRecord) {
  const raw = row.startedAt ?? row.createdAt
  if (!raw) {
    return null
  }
  const timestamp = new Date(raw).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

function displayDuration(row: TaskRecord) {
  if (row.status !== 'RUNNING') {
    return formatDuration(row.durationMs)
  }

  const startTimestamp = resolveTaskStartTimestamp(row)
  if (startTimestamp === null) {
    return formatDuration(row.durationMs)
  }

  const elapsedMs = Math.max(
    row.durationMs ?? 0,
    nowTimestamp.value - startTimestamp,
  )
  return formatDuration(elapsedMs)
}

function canOpenDetail(row: TaskRecord) {
  if (typeof row.detailAvailable === 'boolean') {
    return row.detailAvailable
  }
  return row.status !== 'RUNNING'
}

function detailButtonTitle(row: TaskRecord) {
  return canOpenDetail(row) ? '查看任务详情' : '任务执行中，完成后可查看详情'
}

function openDetail(row: TaskRecord) {
  if (!canOpenDetail(row)) {
    return
  }

  const query = sceneId.value === null
    ? { from: 'tasks' }
    : { from: 'scene', sceneId: String(sceneId.value) }

  void router.push({
    path: `/tasks/${row.id}`,
    query,
  })
}

function backToScenes() {
  void router.push('/scenes')
}

async function executeCurrentScene() {
  if (sceneId.value === null) {
    return
  }
  try {
    await store.executeScene(sceneId.value, sceneId.value)
    ElMessage.success('任务已触发')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景执行失败'))
  }
}

async function loadTaskList(page?: number, size?: number) {
  return sceneId.value === null ? store.fetchAll(page, size) : store.fetchByScene(sceneId.value, page, size)
}

async function refreshTaskListSilently() {
  return store.refreshList(sceneId.value)
}

function stopPolling() {
  if (pollingTimer !== null) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function stopDurationTicker() {
  if (durationTimer !== null) {
    window.clearInterval(durationTimer)
    durationTimer = null
  }
}

function ensurePolling() {
  if (pollingTimer !== null || !hasRunningTasks.value) {
    return
  }
  pollingTimer = setInterval(() => {
    void pollTaskList()
  }, TASK_POLLING_INTERVAL_MS)
}

function ensureDurationTicker() {
  if (durationTimer !== null || !hasRunningTasks.value) {
    return
  }
  durationTimer = setInterval(() => {
    nowTimestamp.value = Date.now()
  }, TASK_DURATION_TICK_MS)
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

async function handlePageChange(page: number) {
  try {
    await loadTaskList(page, store.size)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '任务列表加载失败'))
  }
}

async function handleSizeChange(size: number) {
  try {
    await loadTaskList(1, size)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '任务列表加载失败'))
  }
}

onMounted(() => {
  const request = loadTaskList()
  void request.catch((error) => {
    ElMessage.error(toErrorMessage(error, '任务列表加载失败'))
  })
})

watch(hasRunningTasks, (value) => {
  if (value) {
    ensurePolling()
    ensureDurationTicker()
    return
  }
  stopPolling()
  stopDurationTicker()
}, { immediate: true })

onBeforeUnmount(() => {
  stopPolling()
  stopDurationTicker()
})
</script>

<template>
  <ListPageShell
    :pagination="pagination"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #header-left>
      <el-button v-if="sceneId !== null" plain @click="backToScenes">返回场景中心</el-button>
    </template>
    <template #header-right>
      <el-button v-if="sceneId !== null" type="primary" @click="executeCurrentScene">
        执行任务
      </el-button>
    </template>

    <el-table class="list-table" :data="rows" :loading="loading" empty-text="暂无任务">
      <el-table-column label="任务详情" width="120">
        <template #default="{ row }">
          <span
            class="table-action-trigger"
            :title="detailButtonTitle(row)"
          >
            <el-button
              class="table-action-button"
              link
              type="primary"
              :disabled="!canOpenDetail(row)"
              @click="openDetail(row)"
            >
              详情
            </el-button>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggerType" label="触发方式" width="120" />
      <el-table-column label="耗时" width="140">
        <template #default="{ row }">
          <span>{{ displayDuration(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="220">
        <template #default="{ row }">
          <div class="card-header">
            <el-tag type="danger">失败 {{ row.failedCount ?? 0 }}</el-tag>
            <el-tag type="success">通过 {{ row.passedCount ?? 0 }}</el-tag>
            <el-tag type="info">跳过 {{ row.skippedCount ?? 0 }}</el-tag>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </ListPageShell>
</template>

<style scoped>
.table-action-trigger {
  display: inline-flex;
}
</style>
