<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useScheduleEventStore } from '../../stores/schedule-event'
import type { ScheduleEventIssueRecord, ScheduleEventType } from '../../types/schedule-event'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'

const route = useRoute()
const router = useRouter()
const store = useScheduleEventStore()

const scheduleTypeFilter = ref<ScheduleEventType>('AGENT')
const sceneNameInput = ref('')
const traceIdInput = ref('')

const isAgentType = computed(() => scheduleTypeFilter.value === 'AGENT')

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

function statusTagType(status?: string | null) {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'RUNNING':
      return 'primary'
    case 'FAILED':
      return 'danger'
    case 'ABANDONED':
      return 'info'
    case 'RETRYING':
      return 'warning'
    default:
      return 'info'
  }
}

async function loadEvents(page?: number, size?: number) {
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

function viewTrace(row: ScheduleEventIssueRecord) {
  if (!row.traceId) {
    return
  }
  router.push({ name: 'agent-trace-detail', params: { traceId: row.traceId } })
}

async function handlePageChange(page: number) {
  try {
    await loadEvents(page, store.size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '调度事件加载失败'), 'error')
  }
}

async function handleSizeChange(size: number) {
  try {
    await loadEvents(1, size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '调度事件加载失败'), 'error')
  }
}

function applyFilters() {
  store.setScheduleTypeFilter(scheduleTypeFilter.value)
  store.setSceneNameFilter(sceneNameInput.value)
  store.setTraceIdFilter(traceIdInput.value)
  loadEvents(1, store.size)
}

function resetFilters() {
  scheduleTypeFilter.value = 'AGENT'
  sceneNameInput.value = ''
  traceIdInput.value = ''
  store.setScheduleTypeFilter('AGENT')
  store.setSceneNameFilter('')
  store.setTraceIdFilter('')
  loadEvents(1, store.size)
}

function formatLongText(text?: string | null, headLen = 20, tailLen = 8) {
  if (!text) return '-'
  if (text.length <= headLen + tailLen + 3) return text
  return text.substring(0, headLen) + '…' + text.substring(text.length - tailLen)
}

onMounted(async () => {
  const routeType = typeof route.query.scheduleType === 'string' ? route.query.scheduleType : ''
  if (routeType === 'CRON' || routeType === 'AGENT' || routeType === 'MANUAL') {
    scheduleTypeFilter.value = routeType
    store.setScheduleTypeFilter(routeType)
  }
  const routeTraceId = typeof route.query.traceId === 'string' ? route.query.traceId : ''
  if (routeTraceId) {
    traceIdInput.value = routeTraceId
    store.setTraceIdFilter(routeTraceId)
  }
  const routeSceneName = typeof route.query.sceneName === 'string' ? route.query.sceneName : ''
  if (routeSceneName) {
    sceneNameInput.value = routeSceneName
    store.setSceneNameFilter(routeSceneName)
  }
  try {
    await loadEvents()
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
    <div class="event-filters">
      <label class="event-filter">
        <span class="event-filter__label">类型</span>
        <el-radio-group
          v-model="scheduleTypeFilter"
          class="event-filter__control"
          @change="applyFilters"
        >
          <el-radio-button value="AGENT">Agent调度</el-radio-button>
          <el-radio-button value="CRON">定时调度</el-radio-button>
          <el-radio-button value="MANUAL">手动调度</el-radio-button>
        </el-radio-group>
      </label>

      <label v-if="isAgentType" class="event-filter">
        <span class="event-filter__label">TraceID</span>
        <el-input
          v-model="traceIdInput"
          class="event-filter__control"
          placeholder="如：415ea52d-b707-4cb9-9c7c-322faa840f10"
          clearable
          @change="applyFilters"
          @clear="applyFilters"
        />
      </label>

      <label v-else class="event-filter">
        <span class="event-filter__label">场景名称</span>
        <el-input
          v-model="sceneNameInput"
          class="event-filter__control"
          placeholder="模糊搜索场景名称"
          clearable
          @change="applyFilters"
          @clear="applyFilters"
        />
      </label>

      <el-button class="event-filter__reset" @click="resetFilters">重置</el-button>
    </div>

    <!-- Agent 类型表格 -->
    <el-table v-if="isAgentType" class="list-table" :data="rows" :loading="loading" empty-text="暂无调度事件">
      <el-table-column label="触发时间" min-width="200">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.plannedFireAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Trace ID" width="200">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.traceId"
            :content="row.traceId"
            placement="top"
            :show-after="300"
            effect="dark"
          >
            <el-button
              link
              type="primary"
              class="trace-link"
              @click="viewTrace(row)"
            >
              {{ row.traceId.substring(0, 12) }}…{{ row.traceId.substring(row.traceId.length - 6) }}
            </el-button>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="用户消息" min-width="300">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.userMessage"
            :content="row.userMessage"
            placement="top"
            :show-after="300"
            effect="dark"
          >
            <span class="cell-ellipsis">{{ formatLongText(row.userMessage) }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="错误信息" min-width="260">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.errorMessage"
            :content="row.errorMessage"
            placement="top"
            :show-after="300"
            effect="dark"
          >
            <span class="cell-ellipsis">{{ formatLongText(row.errorMessage, 18, 6) }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="action-cell">
            <el-button
              v-if="row.traceId"
              link
              type="primary"
              @click="viewTrace(row)"
            >
              查看Trace
            </el-button>
            <el-button
              v-if="row.status === 'FAILED' || row.status === 'ABANDONED'"
              link
              type="danger"
              :loading="isRetrying(row)"
              @click="retryEvent(row)"
            >
              重试
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 定时/手动调度表格 -->
    <el-table v-else class="list-table" :data="rows" :loading="loading" empty-text="暂无调度事件">
      <el-table-column label="场景名称" min-width="240">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.sceneName"
            :content="row.sceneName"
            placement="top"
            :show-after="300"
            effect="dark"
          >
            <span class="cell-ellipsis">{{ row.sceneName }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="触发时间" width="180">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.plannedFireAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发原因" min-width="220">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.triggerReason"
            :content="row.triggerReason"
            placement="top"
            :show-after="300"
            effect="dark"
          >
            <span class="cell-ellipsis">{{ formatLongText(row.triggerReason, 18, 6) }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="错误信息" min-width="260">
        <template #default="{ row }">
          <el-tooltip
            v-if="row.errorMessage"
            :content="row.errorMessage"
            placement="top"
            :show-after="300"
            effect="dark"
          >
            <span class="cell-ellipsis">{{ formatLongText(row.errorMessage, 18, 6) }}</span>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="action-cell">
            <el-button
              v-if="row.traceId"
              link
              type="primary"
              @click="viewTrace(row)"
            >
              查看Trace
            </el-button>
            <el-button
              v-if="row.status === 'FAILED' || row.status === 'ABANDONED'"
              link
              type="danger"
              :loading="isRetrying(row)"
              @click="retryEvent(row)"
            >
              重试
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </ListPageShell>
</template>

<style scoped>
.event-filters {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.event-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 200px;
}

.event-filter__label {
  flex: 0 0 auto;
  min-width: 64px;
  line-height: 34px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.event-filter__control {
  flex: 1 1 auto;
  min-width: 220px;
}

.event-filter__reset {
  margin-left: auto;
}

.trace-link {
  font-family: monospace;
  font-size: 12px;
}

.action-cell {
  display: flex;
  gap: 4px;
  align-items: center;
  white-space: nowrap;
}

.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

@media (max-width: 768px) {
  .event-filters {
    flex-direction: column;
    align-items: stretch;
  }

  .event-filter {
    min-width: 0;
    width: 100%;
  }
}
</style>
