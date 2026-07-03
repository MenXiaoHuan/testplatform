<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useScheduleEventStore } from '../../stores/schedule-event'
import type { ScheduleEventIssueRecord } from '../../types/schedule-event'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'

const route = useRoute()
const store = useScheduleEventStore()
const statusFilter = ref<'FAILED,ABANDONED' | 'FAILED' | 'ABANDONED'>('FAILED,ABANDONED')
const sceneIdInput = ref('')

const rows = computed(() => store.items)
const loading = computed(() => store.loading)
const pagination = computed(() => ({
  page: store.page,
  size: store.size,
  total: store.total,
}))

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function isRetrying(row: ScheduleEventIssueRecord) {
  return store.retryingIds.includes(row.id)
}

async function loadIssueEvents(page?: number, size?: number) {
  await store.fetchAll(page, size)
}

async function retryEvent(row: ScheduleEventIssueRecord) {
  const confirmed = await confirmDangerAction({
    title: '重试调度事件',
    message: `确认重试事件 #${row.id} 吗？`,
    confirmButtonText: '重试',
  })
  if (!confirmed) {
    return
  }
  try {
    const task = await store.retry(row.id, {
      operatorName: 'anonymous',
      comment: 'manual retry from web',
    })
    showAppToast(`已重试并创建任务 #${task.id}`, 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '调度事件重试失败'), 'error')
  }
}

async function handlePageChange(page: number) {
  try {
    await loadIssueEvents(page, store.size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '调度事件加载失败'), 'error')
  }
}

async function handleSizeChange(size: number) {
  try {
    await loadIssueEvents(1, size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '调度事件加载失败'), 'error')
  }
}

onMounted(async () => {
  const routeSceneId = typeof route.query.sceneId === 'string' ? route.query.sceneId : ''
  if (routeSceneId) {
    sceneIdInput.value = routeSceneId
    store.setSceneIdFilter(Number(routeSceneId) || null)
  }
  try {
    await loadIssueEvents()
  } catch (error) {
    showAppToast(toErrorMessage(error, '调度事件加载失败'), 'error')
  }
})
</script>

<template>
  <ListPageShell
    :pagination="pagination"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <div class="schedule-event-filters">
      <label class="schedule-event-filter">
        <span class="schedule-event-filter__label">状态</span>
        <el-select v-model="statusFilter" class="schedule-event-filter__control">
          <el-option label="全部异常" value="FAILED,ABANDONED" />
          <el-option label="失败中" value="FAILED" />
          <el-option label="已放弃" value="ABANDONED" />
        </el-select>
      </label>
      <label class="schedule-event-filter">
        <span class="schedule-event-filter__label">场景ID</span>
        <el-input
          v-model="sceneIdInput"
          class="schedule-event-filter__control"
          placeholder="如：11"
        />
      </label>
    </div>

    <el-table class="list-table" :data="rows" :loading="loading" empty-text="暂无调度事件">
      <el-table-column prop="id" label="事件ID" width="100" />
      <el-table-column prop="sceneId" label="场景ID" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <span>{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划触发" min-width="180">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.plannedFireAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="下次重试" min-width="180">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.nextRetryAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试次数" width="120" />
      <el-table-column prop="triggerReason" label="触发原因" min-width="220" />
      <el-table-column prop="errorMessage" label="错误信息" min-width="240" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            class="table-action-button"
            link
            type="primary"
            :loading="isRetrying(row)"
            @click="retryEvent(row)"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ListPageShell>
</template>

<style scoped>
.schedule-event-filters {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  align-items: flex-end;
}

.schedule-event-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 280px;
}

.schedule-event-filter__label {
  flex: 0 0 auto;
  min-width: 56px;
  line-height: 34px;
}

.schedule-event-filter__control {
  flex: 1 1 auto;
}

@media (max-width: 768px) {
  .schedule-event-filters {
    flex-direction: column;
    align-items: stretch;
  }

  .schedule-event-filter {
    min-width: 0;
    width: 100%;
  }
}
</style>
