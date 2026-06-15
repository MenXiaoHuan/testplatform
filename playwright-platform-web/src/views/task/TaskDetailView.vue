<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTaskStore } from '../../stores/task'
import type { CaseArtifactLinkRecord, CaseResultRecord } from '../../types/report'
import type { TaskStageLogRecord } from '../../types/task'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import {
  caseFilterLabel,
  caseStatusText,
  caseStatusType,
  taskStageText,
  taskStatusText,
  taskStatusType,
} from '../../utils/task-display'
import { useTaskDetailLoader } from './useTaskDetailLoader'

const route = useRoute()
const router = useRouter()
const store = useTaskStore()
const TRACE_VIEWER_BASE_URL = 'https://trace.playwright.dev/'

const activeStatus = ref<'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED'>('ALL')
const expandedCaseIds = ref<number[]>([])
const stageLogContentMap = ref<Record<number, string>>({})
const stageLogLoadingMap = ref<Record<number, boolean>>({})

const task = computed(() => store.current)
const caseResults = computed(() => store.caseResults)
const stageLogs = computed(() => store.stageLogs)
const cancelLoading = ref(false)
const caseStatusFilters: Array<'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED'> = ['ALL', 'FAILED', 'PASSED', 'SKIPPED']
const caseSummary = computed(() => {
  let passed = 0
  let failed = 0
  let skipped = 0
  caseResults.value.forEach((item) => {
    if (item.status === 'PASSED') {
      passed += 1
    } else if (item.status === 'FAILED') {
      failed += 1
    } else if (item.status === 'SKIPPED') {
      skipped += 1
    }
  })
  return {
    total: caseResults.value.length,
    passed,
    failed,
    skipped,
  }
})

const filteredCaseResults = computed(() => {
  if (activeStatus.value === 'ALL') {
    return caseResults.value
  }
  return caseResults.value.filter((item) => item.status === activeStatus.value)
})

function normalizeArtifactType(value?: string | null) {
  return (value ?? '').trim().toUpperCase()
}

function findCaseArtifactUrl(
  item: CaseResultRecord | undefined,
  artifactType: string,
  fallback?: string | null,
) {
  if (fallback) {
    return fallback
  }
  return item?.artifacts?.find((artifact: CaseArtifactLinkRecord) => normalizeArtifactType(artifact.artifactType) === artifactType)?.url ?? null
}

function formatDuration(durationMs: number | null | undefined) {
  const totalSeconds = Math.max(0, Math.floor((durationMs ?? 0) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}min${String(seconds).padStart(2, '0')}s`
}

function caseDisplayTitle(item: CaseResultRecord) {
  const storyName = item.storyName?.trim()
  if (storyName) {
    return storyName
  }

  const sections = item.fullName
    .split(' / ')
    .map((part: string) => part.trim())
    .filter(Boolean)
  const section = sections.length > 0 ? sections[sections.length - 1] : item.fullName

  const leafParts = section
    .split('::')
    .map((part: string) => part.trim())
    .filter(Boolean)
  const leaf = leafParts.length > 0 ? leafParts[leafParts.length - 1] : ''

  return leaf || section.trim()
}

function artifactUrlsByType(item: CaseResultRecord, artifactType: string) {
  return (item.artifacts ?? [])
    .filter((artifact: CaseArtifactLinkRecord) => normalizeArtifactType(artifact.artifactType) === artifactType)
    .map((artifact: CaseArtifactLinkRecord) => artifact.url)
    .filter((value): value is string => Boolean(value))
}

function uniqueUrls(urls: Array<string | null | undefined>) {
  return [...new Set(urls.filter((value): value is string => Boolean(value)))]
}

function openUrl(url?: string | null) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function openTraceViewer(traceUrl?: string | null) {
  if (!traceUrl) {
    return
  }
  const viewerUrl = `${TRACE_VIEWER_BASE_URL}?trace=${encodeURIComponent(traceUrl)}`
  window.open(viewerUrl, '_blank', 'noopener')
}

function stageLogText(logId: number) {
  return stageLogContentMap.value[logId] ?? '暂无日志内容'
}

async function loadStageLogContent(item: TaskStageLogRecord) {
  if (stageLogContentMap.value[item.id] || stageLogLoadingMap.value[item.id]) {
    return
  }

  if (!item.downloadUrl) {
    stageLogContentMap.value = {
      ...stageLogContentMap.value,
      [item.id]: item.previewText || '暂无日志内容',
    }
    return
  }

  stageLogLoadingMap.value = {
    ...stageLogLoadingMap.value,
    [item.id]: true,
  }

  try {
    const response = await fetch(item.downloadUrl)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const text = (await response.text()).replace(/\r\n/g, '\n')
    stageLogContentMap.value = {
      ...stageLogContentMap.value,
      [item.id]: text || item.previewText || '暂无日志内容',
    }
  } catch {
    stageLogContentMap.value = {
      ...stageLogContentMap.value,
      [item.id]: item.previewText || '日志暂时无法加载，请使用下载日志查看完整内容。',
    }
  } finally {
    stageLogLoadingMap.value = {
      ...stageLogLoadingMap.value,
      [item.id]: false,
    }
  }
}

async function rerunTask() {
  const sceneId = task.value?.sceneId
  if (typeof sceneId !== 'number') {
    return
  }
  try {
    await store.executeScene(sceneId, sceneId)
    showAppToast('任务已触发', 'success')
    void router.push(`/scenes/${sceneId}/tasks`)
  } catch (error) {
    showAppToast(toErrorMessage(error, '任务执行失败'), 'error')
  }
}

async function cancelTaskRun() {
  const taskId = task.value?.id
  if (typeof taskId !== 'number' || cancelLoading.value) {
    return
  }
  cancelLoading.value = true
  try {
    await store.cancelCurrentTask(taskId)
    showAppToast('已提交取消请求', 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '取消任务失败'), 'error')
  } finally {
    cancelLoading.value = false
  }
}

function backToPrevious() {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  const sceneId = typeof route.query.sceneId === 'string'
    ? Number(route.query.sceneId)
    : task.value?.sceneId

  if (from === 'scene' && Number.isFinite(sceneId)) {
    void router.push(`/scenes/${sceneId}/tasks`)
    return
  }

  void router.push('/tasks')
}

function caseVideoUrl(item: CaseResultRecord) {
  return findCaseArtifactUrl(item, 'VIDEO', item.videoUrl)
}

function caseTraceUrl(item: CaseResultRecord) {
  return findCaseArtifactUrl(item, 'TRACE', item.traceUrl)
}

function caseScreenshotUrls(item: CaseResultRecord) {
  return uniqueUrls([
    ...(item.screenshotUrls ?? []),
    ...artifactUrlsByType(item, 'SCREENSHOT'),
  ])
}

function casePrimaryScreenshotUrl(item: CaseResultRecord) {
  return caseScreenshotUrls(item)[0] ?? null
}

function hasCaseRuntime(item: CaseResultRecord) {
  return Boolean(
    caseVideoUrl(item)
      || caseTraceUrl(item)
      || caseScreenshotUrls(item).length,
  )
}

function isCaseExpanded(itemId: number) {
  return expandedCaseIds.value.includes(itemId)
}

function toggleCaseRuntime(item: CaseResultRecord) {
  if (isCaseExpanded(item.id)) {
    expandedCaseIds.value = expandedCaseIds.value.filter((id) => id !== item.id)
    return
  }

  expandedCaseIds.value = [...expandedCaseIds.value, item.id]
}

function setActiveStatus(status: 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED') {
  activeStatus.value = status
}

const routeTaskId = computed(() => {
  const raw = route.params.id
  return typeof raw === 'string' ? Number(raw) : Number.NaN
})

useTaskDetailLoader({
  taskId: routeTaskId,
  loadTaskDetailPage: store.fetchTaskDetailPage,
  onInvalidTaskId: () => {
    showAppToast('任务 ID 无效', 'error')
  },
  onLoadError: (error) => {
    showAppToast(toErrorMessage(error, '任务详情加载失败'), 'error')
  },
})

watch(stageLogs, (logs) => {
  logs.forEach((item) => {
    void loadStageLogContent(item)
  })
}, { immediate: true })
</script>

<template>
  <section class="page-grid">
    <div class="glass-card content-panel scene-panel task-detail-panel">
      <div class="content-panel__header">
        <div class="content-panel__header-left content-panel__header-side content-panel__header-side--left">
          <el-button plain @click="backToPrevious">返回任务列表</el-button>
        </div>
        <div class="content-panel__header-right content-panel__header-side content-panel__header-side--right">
          <el-button
            v-if="task && (task.status === 'QUEUED' || task.status === 'RUNNING')"
            plain
            :loading="cancelLoading"
            @click="cancelTaskRun"
          >
            取消任务
          </el-button>
          <el-button type="primary" @click="rerunTask">重新执行</el-button>
        </div>
      </div>

      <div v-if="task" class="content-panel__body task-detail-body">
        <div class="task-detail-grid">
          <section class="task-detail-card task-detail-card--full">
            <div class="task-detail-card__header">
              <div>
                <p class="eyebrow">Task Detail</p>
                <h2>结果总览</h2>
              </div>
              <el-tag :type="taskStatusType(task)">{{ taskStatusText(task) }}</el-tag>
            </div>
            <div class="task-summary-stats">
              <div class="task-summary-stat">
                <span>总数</span>
                <strong>{{ caseSummary.total }}</strong>
              </div>
              <div class="task-summary-stat">
                <span>通过</span>
                <strong>{{ caseSummary.passed }}</strong>
              </div>
              <div class="task-summary-stat">
                <span>失败</span>
                <strong>{{ caseSummary.failed }}</strong>
              </div>
              <div class="task-summary-stat">
                <span>跳过</span>
                <strong>{{ caseSummary.skipped }}</strong>
              </div>
            </div>
          </section>

          <section class="task-detail-card task-detail-card--full">
            <div class="task-detail-card__header">
              <div>
                <p class="eyebrow">Cases</p>
                <h2>用例结果</h2>
              </div>
              <div class="task-filter-group">
                <el-button
                  v-for="status in caseStatusFilters"
                  :key="status"
                  link
                  type="primary"
                  :class="{ 'task-filter-button--active': activeStatus === status }"
                  @click="setActiveStatus(status as 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED')"
                >
                  {{ caseFilterLabel(status) }}
                </el-button>
              </div>
            </div>
            <div v-if="filteredCaseResults.length > 0" class="task-case-list">
              <article v-for="item in filteredCaseResults" :key="item.id" class="task-case-card">
                <div class="task-case-card__header">
                  <div>
                    <h3>{{ caseDisplayTitle(item) }}</h3>
                  </div>
                  <el-tag :type="caseStatusType(item.status)">{{ caseStatusText(item.status) }}</el-tag>
                </div>
                <p v-if="item.errorMessage" class="task-case-card__error">{{ item.errorMessage }}</p>
                <div class="task-case-card__meta">
                  <span>{{ item.projectName ?? 'unknown project' }}</span>
                  <span>耗时 {{ formatDuration(item.durationMs) }}</span>
                </div>
                <div class="task-case-card__actions">
                  <el-tooltip
                    v-if="hasCaseRuntime(item)"
                    content="点击查看过程"
                    placement="top"
                    effect="dark"
                  >
                    <span class="table-action-trigger">
                      <el-button
                        link
                        type="primary"
                        @click="toggleCaseRuntime(item)"
                      >
                        {{ isCaseExpanded(item.id) ? '收起运行过程' : '查看运行过程' }}
                      </el-button>
                    </span>
                  </el-tooltip>
                </div>
                <div v-if="isCaseExpanded(item.id)" class="task-case-runtime">
                  <div class="task-runtime-grid">
                    <section class="task-runtime-panel">
                      <div class="task-runtime-panel__header">
                        <strong class="task-runtime-section-title">截图 / 视频</strong>
                      </div>

                      <div class="task-runtime-media-grid">
                        <div class="task-runtime-media-panel">
                          <img
                            v-if="casePrimaryScreenshotUrl(item)"
                            class="task-runtime-screenshot"
                            :src="casePrimaryScreenshotUrl(item) ?? undefined"
                            :alt="`${item.fullName} 截图`"
                          >
                          <div v-else class="page-empty-text task-runtime-log__empty">暂无截图</div>
                        </div>

                        <div class="task-runtime-media-panel">
                          <video
                            v-if="caseVideoUrl(item)"
                            class="task-runtime-video"
                            :src="caseVideoUrl(item) ?? undefined"
                            controls
                            preload="metadata"
                          />
                          <div v-else class="page-empty-text task-runtime-log__empty">暂无视频</div>
                        </div>
                      </div>
                    </section>

                    <section class="task-runtime-panel task-runtime-panel--log">
                      <div class="task-runtime-panel__header">
                        <strong class="task-runtime-section-title">执行轨迹</strong>
                        <div class="task-case-runtime__toolbar">
                          <el-button link type="primary" :disabled="!caseTraceUrl(item)" @click="openTraceViewer(caseTraceUrl(item))">查看 Trace</el-button>
                        </div>
                      </div>

                      <div class="task-runtime-log">
                        <div class="task-runtime-log__header">
                          <strong>Trace Viewer</strong>
                          <span class="muted">通过 Playwright 轨迹查看器查看完整执行过程</span>
                        </div>
                        <div v-if="caseTraceUrl(item)" class="task-runtime-log__viewer-hint">
                          <span>点击右上角的“查看 Trace”后，将在新标签页打开 Playwright Trace Viewer。</span>
                          <span>你可以查看时间线、页面快照、操作步骤、网络请求和控制台信息。</span>
                        </div>
                        <div v-else class="page-empty-text task-runtime-log__empty">暂无可查看的 Trace 轨迹</div>
                      </div>
                    </section>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="page-empty-text task-detail-empty">暂无用例结果</div>
          </section>

          <section class="task-detail-card task-detail-card--full">
            <div class="task-detail-card__header">
              <div>
                <p class="eyebrow">Stage Logs</p>
                <h2>阶段日志</h2>
              </div>
            </div>
            <div v-if="stageLogs.length" class="task-stage-log-list">
              <article v-for="item in stageLogs" :key="item.id" class="task-stage-log-card">
                <div class="task-stage-log-card__header">
                  <div>
                    <h3>{{ taskStageText(item.stage) }}</h3>
                    <p>{{ item.streamType }} · {{ item.lineCount }} 行</p>
                  </div>
                  <el-button
                    link
                    type="primary"
                    :disabled="!item.downloadUrl"
                    @click="openUrl(item.downloadUrl)"
                  >
                    下载日志
                  </el-button>
                </div>
                <div v-if="stageLogLoadingMap[item.id]" class="page-empty-text task-stage-log-card__empty">日志加载中...</div>
                <pre v-else class="task-stage-log-card__preview">{{ stageLogText(item.id) }}</pre>
              </article>
            </div>
            <div v-else class="page-empty-text task-detail-empty">暂无阶段日志</div>
          </section>
        </div>
      </div>

      <div v-else class="content-panel__body">
        <el-empty description="暂无任务详情" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.task-detail-panel {
  min-height: calc(100vh - 96px);
}

.content-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.content-panel__header-side {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1 1 0;
}

.content-panel__header-side--left {
  justify-content: flex-start;
}

.content-panel__header-side--right {
  justify-content: flex-end;
}

.task-detail-body {
  display: grid;
}

.task-detail-grid {
  display: grid;
  gap: 16px;
}

.task-detail-card {
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-surface);
}

.task-detail-card--full {
  grid-column: 1 / -1;
}

.task-detail-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.task-detail-card__header h2,
.task-case-card__header h3 {
  margin: 8px 0 0;
}

.task-summary-stat,
.task-artifact-item {
  display: grid;
  gap: 6px;
}

.task-summary-stat span,
.task-case-card__meta,
.task-artifact-item p {
  color: var(--app-text-secondary);
}

.task-summary-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.task-summary-stat {
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--app-surface-muted);
}

.task-evidence-actions,
.task-filter-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.task-case-list {
  display: grid;
  gap: 12px;
}

.task-case-card,
.task-artifact-item {
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--app-border);
  background: #fff;
  display: grid;
  gap: 12px;
}

.task-case-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.task-case-card__header p,
.task-case-card__error,
.task-artifact-item p {
  margin: 6px 0 0;
}

.task-case-card__error {
  color: #dc2626;
  font-size: 14px;
}

.task-case-card__meta {
  display: flex;
  gap: 12px;
  font-size: 13px;
}

.task-detail-empty {
  min-height: 120px;
  padding: 0;
}

.task-filter-button--active {
  color: var(--app-accent-hover);
  font-weight: 700;
}

.task-case-card__actions {
  display: flex;
  justify-content: flex-start;
}

.task-case-runtime {
  display: grid;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  background: var(--app-surface-muted);
}

.task-runtime-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  align-items: start;
}

.task-runtime-panel {
  display: grid;
  gap: 12px;
  align-content: start;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--app-border);
  background: #fff;
}

.task-runtime-panel--log {
  grid-column: 1 / -1;
}

.task-runtime-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-case-runtime__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.task-runtime-video {
  width: 100%;
  border-radius: 16px;
  background: #000;
}

.task-runtime-media-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.task-runtime-media-panel {
  display: grid;
  align-items: start;
  min-height: 220px;
}

.task-runtime-section-title {
  color: var(--app-text-primary);
  font-size: 14px;
}

.task-runtime-screenshot {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 16px;
  border: 1px solid var(--app-border);
  background: #fff;
}

.task-runtime-log {
  display: grid;
  gap: 10px;
}

.task-runtime-log__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-runtime-log__content {
  margin: 0;
  padding: 14px 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid var(--app-border);
  color: var(--app-text-primary);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow: auto;
}

.task-runtime-log__empty {
  min-height: 56px;
  padding: 0;
}

.task-runtime-log__viewer-hint {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--app-border);
  background: #fff;
  color: var(--app-text-primary);
  font-size: 13px;
  line-height: 1.6;
}

.task-stage-log-list {
  display: grid;
  gap: 12px;
}

.task-stage-log-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--app-border);
  background: #fff;
}

.task-stage-log-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.task-stage-log-card__header h3 {
  margin: 0;
}

.task-stage-log-card__header p {
  margin: 6px 0 0;
  color: var(--app-text-secondary);
}

.task-stage-log-card__preview {
  margin: 0;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--app-surface-muted);
  color: var(--app-text-primary);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow: auto;
}

.task-stage-log-card__empty {
  min-height: 56px;
  padding: 0;
}

@media (max-width: 1080px) {
  .task-runtime-grid {
    grid-template-columns: 1fr;
  }

  .task-runtime-panel--log {
    grid-column: auto;
  }

  .task-runtime-media-grid {
    grid-template-columns: 1fr;
  }
}
</style>
