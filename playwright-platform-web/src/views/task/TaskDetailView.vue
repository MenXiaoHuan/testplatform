<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTaskStore } from '../../stores/task'
import type { CaseArtifactLinkRecord, CaseResultRecord } from '../../types/report'
import { toErrorMessage } from '../../utils/error'

const route = useRoute()
const router = useRouter()
const store = useTaskStore()

const activeStatus = ref<'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED'>('ALL')
const expandedCaseIds = ref<number[]>([])
const caseLogPreviewMap = ref<Record<number, string>>({})
const caseLogLoadingMap = ref<Record<number, boolean>>({})
const rawLogExpandedMap = ref<Record<number, boolean>>({})

const task = computed(() => store.current)
const reportSummary = computed(() => store.reportSummary)
const caseResults = computed(() => store.caseResults)

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

function statusType(status: string) {
  if (status === 'SUCCESS' || status === 'PASSED') return 'success'
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

function reportStatusText(status?: string | null) {
  if (status === 'READY') return '报告已就绪'
  if (status === 'NOT_READY') return '报告暂未生成'
  if (status === 'PARSE_FAILED') return '报告解析失败'
  return '暂无报告'
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

function normalizeLogText(value?: string | null) {
  return (value ?? '').replace(/\r\n/g, '\n').trim()
}

function isHtmlLikeLog(value?: string | null) {
  const text = normalizeLogText(value)
  return /^<!doctype html>/i.test(text) || /^<html[\s>]/i.test(text)
}

function buildLogHighlights(value?: string | null) {
  const text = normalizeLogText(value)
  if (!text || isHtmlLikeLog(text)) {
    return []
  }

  const lines = text.split('\n').map((line) => line.trim())
  const highlights: string[] = []
  let currentSection = ''

  for (const line of lines) {
    if (!line || line === '```' || line.startsWith('```')) {
      continue
    }

    if (line.startsWith('#')) {
      currentSection = line.replace(/^#+\s*/, '').trim().toLowerCase()
      continue
    }

    if (currentSection === 'instructions') {
      continue
    }

    if (
      /^error:/i.test(line)
      || /^expected:/i.test(line)
      || /^received:/i.test(line)
      || /^timeout:/i.test(line)
      || /^location:/i.test(line)
      || /^call log:/i.test(line)
      || /^name:/i.test(line)
      || /\.spec\.[tj]s:\d+:\d+$/i.test(line)
      || line.includes('expect(')
      || /^-\s+Expected/i.test(line)
      || /^-\s+unexpected/i.test(line)
    ) {
      highlights.push(line)
      continue
    }

    if (currentSection === 'error details' && highlights.length < 6) {
      highlights.push(line)
    }
  }

  if (highlights.length > 0) {
    return [...new Set(highlights)].slice(0, 6)
  }

  return lines
    .filter((line) => line && !line.startsWith('#') && !line.startsWith('```'))
    .slice(0, 5)
}

function caseLogHighlights(item: CaseResultRecord) {
  return buildLogHighlights(caseLogPreviewMap.value[item.id])
}

function caseReasonText(item: CaseResultRecord) {
  return item.errorMessage ?? caseLogHighlights(item)[0] ?? null
}

function hasReadableLog(item: CaseResultRecord) {
  const text = caseLogPreviewMap.value[item.id]
  return Boolean(text && !isHtmlLikeLog(text))
}

function shouldShowRawLogToggle(item: CaseResultRecord) {
  return hasReadableLog(item)
}

function isRawLogExpanded(itemId: number) {
  return rawLogExpandedMap.value[itemId] === true
}

function toggleRawLog(itemId: number) {
  rawLogExpandedMap.value = {
    ...rawLogExpandedMap.value,
    [itemId]: !rawLogExpandedMap.value[itemId],
  }
}

async function loadDetail() {
  const taskId = Number(route.params.id)
  if (!Number.isFinite(taskId)) {
    ElMessage.error('任务 ID 无效')
    return
  }
  try {
    await store.fetchTaskDetailPage(taskId)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '任务详情加载失败'))
  }
}

function openUrl(url?: string | null) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

async function rerunTask() {
  const sceneId = task.value?.sceneId
  if (typeof sceneId !== 'number') {
    return
  }
  try {
    await store.executeScene(sceneId, sceneId)
    ElMessage.success('任务已触发')
    void router.push(`/scenes/${sceneId}/tasks`)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '任务执行失败'))
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

function caseLogUrl(item: CaseResultRecord) {
  return findCaseArtifactUrl(item, 'LOG', item.logUrl)
}

function caseReportUrl(item: CaseResultRecord) {
  return findCaseArtifactUrl(item, 'REPORT')
}

function caseScreenshotUrls(item: CaseResultRecord) {
  return uniqueUrls([
    ...(item.screenshotUrls ?? []),
    ...artifactUrlsByType(item, 'SCREENSHOT'),
  ])
}

function caseArtifactsCount(item: CaseResultRecord) {
  return uniqueUrls([
    caseVideoUrl(item),
    caseTraceUrl(item),
    caseLogUrl(item),
    caseReportUrl(item),
    ...caseScreenshotUrls(item),
  ]).length
}

function hasCaseRuntime(item: CaseResultRecord) {
  return Boolean(
    caseVideoUrl(item)
      || caseTraceUrl(item)
      || caseLogUrl(item)
      || caseReportUrl(item)
      || caseScreenshotUrls(item).length,
  )
}

function isCaseExpanded(itemId: number) {
  return expandedCaseIds.value.includes(itemId)
}

async function loadCaseLogPreview(item: CaseResultRecord) {
  const previewUrl = caseLogUrl(item) ?? caseReportUrl(item)
  if (!previewUrl || caseLogPreviewMap.value[item.id] || caseLogLoadingMap.value[item.id]) {
    return
  }

  caseLogLoadingMap.value = {
    ...caseLogLoadingMap.value,
    [item.id]: true,
  }

  try {
    const response = await fetch(previewUrl)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const text = await response.text()
    caseLogPreviewMap.value = {
      ...caseLogPreviewMap.value,
      [item.id]: text.trim() || '暂无日志内容',
    }
  } catch {
    caseLogPreviewMap.value = {
      ...caseLogPreviewMap.value,
      [item.id]: '日志暂时无法加载，请使用下方链接直接查看。',
    }
  } finally {
    caseLogLoadingMap.value = {
      ...caseLogLoadingMap.value,
      [item.id]: false,
    }
  }
}

function toggleCaseRuntime(item: CaseResultRecord) {
  if (isCaseExpanded(item.id)) {
    expandedCaseIds.value = expandedCaseIds.value.filter((id) => id !== item.id)
    rawLogExpandedMap.value = {
      ...rawLogExpandedMap.value,
      [item.id]: false,
    }
    return
  }

  expandedCaseIds.value = [...expandedCaseIds.value, item.id]
  void loadCaseLogPreview(item)
}

function setActiveStatus(status: 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED') {
  activeStatus.value = status
}

onMounted(() => {
  void loadDetail()
})

watch(() => route.params.id, () => {
  void loadDetail()
})
</script>

<template>
  <section class="page-grid">
    <div class="glass-card content-panel scene-panel task-detail-panel">
      <div class="content-panel__header">
        <div class="content-panel__header-left content-panel__header-side content-panel__header-side--left">
          <el-button plain @click="backToPrevious">返回场景中心</el-button>
        </div>
        <div class="content-panel__header-right content-panel__header-side content-panel__header-side--right">
          <el-button type="primary" @click="rerunTask">重新执行</el-button>
        </div>
      </div>

      <div v-if="task" class="content-panel__body task-detail-body">
        <div class="task-detail-grid">
          <section class="task-detail-card task-detail-card--full">
            <div class="task-detail-card__header">
              <div>
                <p class="eyebrow">Task Report</p>
                <h2>结果总览</h2>
              </div>
              <el-tag :type="statusType(task.status)">{{ task.status }}</el-tag>
            </div>
            <div class="task-summary-stats">
              <div class="task-summary-stat">
                <span>总数</span>
                <strong>{{ reportSummary?.caseSummary.total ?? 0 }}</strong>
              </div>
              <div class="task-summary-stat">
                <span>通过</span>
                <strong>{{ reportSummary?.caseSummary.passed ?? 0 }}</strong>
              </div>
              <div class="task-summary-stat">
                <span>失败</span>
                <strong>{{ reportSummary?.caseSummary.failed ?? 0 }}</strong>
              </div>
              <div class="task-summary-stat">
                <span>跳过</span>
                <strong>{{ reportSummary?.caseSummary.skipped ?? 0 }}</strong>
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
                  v-for="status in ['ALL', 'FAILED', 'PASSED', 'SKIPPED']"
                  :key="status"
                  link
                  type="primary"
                  :class="{ 'task-filter-button--active': activeStatus === status }"
                  @click="setActiveStatus(status as 'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED')"
                >
                  {{ status }}
                </el-button>
              </div>
            </div>
            <div v-if="filteredCaseResults.length > 0" class="task-case-list">
              <article v-for="item in filteredCaseResults" :key="item.id" class="task-case-card">
                <div class="task-case-card__header">
                  <div>
                    <h3>{{ item.fullName }}</h3>
                    <p>{{ item.projectName ?? 'unknown project' }}</p>
                  </div>
                  <el-tag :type="statusType(item.status)">{{ item.status }}</el-tag>
                </div>
                <p v-if="item.errorMessage" class="task-case-card__error">{{ item.errorMessage }}</p>
                <div class="task-case-card__meta">
                  <span>耗时 {{ formatDuration(item.durationMs) }}</span>
                  <span>运行记录 {{ caseArtifactsCount(item) }}</span>
                </div>
                <div class="task-case-card__actions">
                  <el-button
                    v-if="hasCaseRuntime(item)"
                    link
                    type="primary"
                    @click="toggleCaseRuntime(item)"
                  >
                    {{ isCaseExpanded(item.id) ? '收起运行记录' : '查看运行记录' }}
                  </el-button>
                </div>
                <div v-if="isCaseExpanded(item.id)" class="task-case-runtime">
                  <div class="task-runtime-grid">
                    <section class="task-runtime-panel">
                      <div class="task-runtime-panel__header">
                        <strong class="task-runtime-section-title">截图 / 视频</strong>
                        <div class="task-case-runtime__toolbar">
                          <el-button link type="primary" :disabled="!caseVideoUrl(item)" @click="openUrl(caseVideoUrl(item))">视频</el-button>
                        </div>
                      </div>

                      <div v-if="caseScreenshotUrls(item).length" class="task-runtime-screenshot-group">
                        <div class="task-runtime-screenshots">
                          <img
                            v-for="(url, index) in caseScreenshotUrls(item)"
                            :key="`${item.id}-${index}`"
                            class="task-runtime-screenshot"
                            :src="url"
                            :alt="`${item.fullName} 截图 ${index + 1}`"
                          >
                        </div>
                      </div>

                      <div v-if="caseVideoUrl(item)" class="task-runtime-video-wrap">
                        <video
                          class="task-runtime-video"
                          :src="caseVideoUrl(item) ?? undefined"
                          controls
                          preload="metadata"
                        />
                      </div>

                      <div v-else-if="!caseVideoUrl(item)" class="page-empty-text task-runtime-log__empty">暂无截图或视频</div>
                    </section>

                    <section class="task-runtime-panel task-runtime-panel--log">
                      <div class="task-runtime-panel__header">
                        <strong class="task-runtime-section-title">Trace / 日志</strong>
                        <div class="task-case-runtime__toolbar">
                          <el-button link type="primary" :disabled="!caseTraceUrl(item)" @click="openUrl(caseTraceUrl(item))">Trace</el-button>
                          <el-button
                            link
                            type="primary"
                            :disabled="!caseLogUrl(item) && !caseReportUrl(item)"
                            @click="openUrl(caseLogUrl(item) ?? caseReportUrl(item))"
                          >
                            日志
                          </el-button>
                        </div>
                      </div>

                      <div class="task-runtime-log">
                        <div class="task-runtime-log__header">
                          <strong>定位摘要</strong>
                          <span class="muted">{{ reportStatusText(reportSummary?.reportStatus) }}</span>
                        </div>
                        <div v-if="caseLogLoadingMap[item.id]" class="page-empty-text task-runtime-log__empty">日志加载中...</div>
                        <ul v-else-if="caseLogHighlights(item).length" class="task-runtime-log__highlights">
                          <li v-for="(line, index) in caseLogHighlights(item)" :key="`${item.id}-highlight-${index}`">
                            {{ line }}
                          </li>
                        </ul>
                        <div v-else-if="caseLogPreviewMap[item.id] && isHtmlLikeLog(caseLogPreviewMap[item.id])" class="page-empty-text task-runtime-log__empty">
                          当前返回的是报告页面内容，建议直接使用上方入口查看完整报告或 Trace。
                        </div>
                        <div v-else class="page-empty-text task-runtime-log__empty">暂无可提炼的日志摘要</div>

                        <div v-if="shouldShowRawLogToggle(item)" class="task-runtime-log__actions">
                          <el-button link type="primary" @click="toggleRawLog(item.id)">
                            {{ isRawLogExpanded(item.id) ? '收起原始日志' : '展开原始日志' }}
                          </el-button>
                        </div>

                        <pre
                          v-if="isRawLogExpanded(item.id) && hasReadableLog(item)"
                          class="task-runtime-log__content"
                        >{{ caseLogPreviewMap[item.id] }}</pre>
                      </div>
                    </section>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="page-empty-text task-detail-empty">暂无用例结果</div>
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
  max-width: 720px;
  border-radius: 16px;
  background: #000;
}

.task-runtime-video-wrap {
  display: flex;
  justify-content: center;
}

.task-runtime-screenshot-group {
  display: grid;
  gap: 10px;
}

.task-runtime-screenshots {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.task-runtime-section-title {
  color: var(--app-text-primary);
  font-size: 14px;
}

.task-runtime-screenshot {
  width: 100%;
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

.task-runtime-log__highlights {
  margin: 0;
  padding: 0 0 0 18px;
  display: grid;
  gap: 8px;
  color: var(--app-text-primary);
  font-size: 13px;
  line-height: 1.6;
}

.task-runtime-log__actions {
  display: flex;
  justify-content: flex-start;
}

@media (max-width: 1080px) {
  .task-runtime-grid {
    grid-template-columns: 1fr;
  }

  .task-runtime-panel--log {
    grid-column: auto;
  }
}
</style>
