<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useTaskStore } from '../../stores/task'
import type { TaskRecord } from '../../types/task'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import {
  taskDurationText,
  taskResultCodeText,
  taskStageText,
  taskStatusText,
  taskStatusType,
  taskTriggerTypeText,
} from '../../utils/task-display'
import { useTaskListLifecycle } from './useTaskListLifecycle'

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
const sceneId = computed(() => {
  const raw = route.params.id
  const value = typeof raw === 'string' ? Number(raw) : Number.NaN
  return Number.isFinite(value) ? value : null
})

function displayDuration(row: TaskRecord) {
  return taskDurationText(row, nowTimestamp.value)
}

function isActiveTask(row: TaskRecord) {
  return row.status === 'RUNNING' || row.status === 'QUEUED'
}

function canOpenDetail(row: TaskRecord) {
  if (isActiveTask(row)) {
    return false
  }
  if (typeof row.detailAvailable === 'boolean') {
    return row.detailAvailable
  }
  return true
}

function detailButtonTitle(row: TaskRecord) {
  if (row.status === 'QUEUED') {
    return '任务排队中，请耐心等待'
  }
  if (row.status === 'RUNNING') {
    return '任务运行中，完成后即可查看详情'
  }
  return canOpenDetail(row) ? '查看任务详情' : '任务详情暂不可查看'
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

function isTaskRunningConflict(error: unknown) {
  if (typeof error === 'object' && error !== null) {
    const response = Reflect.get(error, 'response')
    if (typeof response === 'object' && response !== null) {
      const data = Reflect.get(response, 'data')
      if (typeof data === 'object' && data !== null) {
        return Reflect.get(data, 'code') === 'CONFLICT'
      }
    }
  }
  return false
}

async function executeCurrentScene() {
  if (sceneId.value === null) {
    return
  }
  try {
    await store.executeScene(sceneId.value, sceneId.value)
    showAppToast('任务已触发', 'success')
  } catch (error) {
    const errorMessage = toErrorMessage(error, '场景执行失败')
    if (isTaskRunningConflict(error)) {
      showAppToast(errorMessage, 'warning')
      return
    }
    showAppToast(errorMessage, 'error')
  }
}

async function loadTaskList(page?: number, size?: number) {
  return sceneId.value === null ? store.fetchAll(page, size) : store.fetchByScene(sceneId.value, page, size)
}

async function refreshTaskListSilently() {
  return store.refreshList(sceneId.value)
}

async function handlePageChange(page: number) {
  try {
    await loadTaskList(page, store.size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '任务列表加载失败'), 'error')
  }
}

async function handleSizeChange(size: number) {
  try {
    await loadTaskList(1, size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '任务列表加载失败'), 'error')
  }
}

const { nowTimestamp } = useTaskListLifecycle({
  rows,
  sceneId,
  loadTaskList,
  refreshTaskListSilently,
  onLoadError: (error) => {
    showAppToast(toErrorMessage(error, '任务列表加载失败'), 'error')
  },
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
          <el-tooltip
            :content="detailButtonTitle(row)"
            placement="top"
            effect="dark"
          >
            <span class="table-action-trigger">
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
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="taskStatusType(row)">{{ taskStatusText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发方式" width="120">
        <template #default="{ row }">
          <span>{{ taskTriggerTypeText(row.triggerType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="当前阶段" min-width="140">
        <template #default="{ row }">
          <span>{{ taskStageText(row.currentStage) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果归因" min-width="140">
        <template #default="{ row }">
          <span>{{ taskResultCodeText(row.resultCode) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="140">
        <template #default="{ row }">
          <span>{{ displayDuration(row) }}</span>
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
