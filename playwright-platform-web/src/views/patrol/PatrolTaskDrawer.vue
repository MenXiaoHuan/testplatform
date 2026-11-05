 
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  approvePatrolTask,
  cancelPatrolTask,
  getPatrolTaskDetail,
  getPatrolTaskModelLogs,
  patrolEvidenceUrl,
} from '../../api/patrol'
import type { PatrolCase, PatrolModelLog, PatrolRisk, PatrolRun, PatrolTaskDetail } from '../../types/patrol'
import CellTooltip from '../../components/list/CellTooltip.vue'
import { useAuthStore } from '../../stores/auth'
import { formatBeijingDateTime } from '../../utils/datetime'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'

const props = defineProps<{
  modelValue: boolean
  spaceId: number
  taskId: number | null
}>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'changed'): void
}>()

const authStore = useAuthStore()
const isSuperAdmin = computed(() => authStore.isSuperAdmin)
const loading = ref(false)
const detail = ref<PatrolTaskDetail | null>(null)
const activeTab = ref('overview')
const modelLogs = ref<PatrolModelLog[]>([])
const modelLogsLoading = ref(false)
const allowWrite = ref(true)
const allowDestructive = ref(false)
const approving = ref(false)
const timer = ref<number | null>(null)

interface ConsoleMessage {
  type: string
  text: string
  ts?: string
}

interface MutationRequest {
  method: string
  url: string
  blocked?: boolean
}

const previewVisible = ref(false)
const previewType = ref<'image' | 'console' | 'dom'>('image')
const previewTitle = ref('')
const previewCaseId = ref<number | null>(null)
const previewImageUrl = ref('')
const previewText = ref('')
const previewLoading = ref(false)
const consoleMessages = ref<ConsoleMessage[]>([])
const consoleRequests = ref<MutationRequest[]>([])
const consoleErrorCount = ref(0)
const consoleRaw = ref('')

const caseFilter = ref<'ALL' | 'PASS' | 'FAIL'>('ALL')
const runFilter = ref('ALL')
const expandedCaseIds = ref<number[]>([])

const runs = computed<PatrolRun[]>(() => detail.value?.runs ?? [])

function runLabel(run: PatrolRun) {
  if (run.entryType === 'MAIN') return '主入口'
  return run.entryLabel?.trim() || `补充入口 #${run.runId}`
}

function runStatusText(status: string) {
  return {
    QUEUED: '排队中',
    RUNNING: '执行中',
    WAITING_APPROVAL: '待审批',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELED: '已取消',
    UNREACHABLE: '不可达',
  }[status] ?? status
}

function caseRunKey(item: PatrolCase) {
  return item.runId == null ? 'ALL' : String(item.runId)
}

const filteredCases = computed(() => {
  let cases = detail.value?.cases ?? []
  if (runFilter.value !== 'ALL') {
    cases = cases.filter((item) => caseRunKey(item) === runFilter.value)
  }
  if (caseFilter.value === 'ALL') return cases
  return cases.filter((item) => item.status === caseFilter.value)
})

function isCaseExpanded(caseId: number) {
  return expandedCaseIds.value.includes(caseId)
}

function toggleCase(caseId: number) {
  expandedCaseIds.value = isCaseExpanded(caseId)
    ? expandedCaseIds.value.filter((id) => id !== caseId)
    : [...expandedCaseIds.value, caseId]
}

function resetCaseView() {
  caseFilter.value = 'ALL'
  runFilter.value = 'ALL'
  expandedCaseIds.value = []
  activeTab.value = 'overview'
  modelLogs.value = []
}

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const task = computed(() => detail.value?.task ?? null)
const isActive = computed(() => {
  const status = task.value?.status
  return !!status && !['COMPLETED', 'FAILED', 'CANCELED', 'UNREACHABLE'].includes(status)
})

interface RuntimeHint {
  type: 'warning' | 'error' | 'info'
  title: string
  text: string
}

const runtimeHints = computed<RuntimeHint[]>(() => {
  const current = task.value
  if (!current) return []
  const hints: RuntimeHint[] = []
  if (current.degraded) {
    hints.push({
      type: 'warning',
      title: '本次运行发生降级',
      text: (current.planningNote || '').trim()
        || '模型阶段已降级为规则兜底，详见用例来源与执行信息。',
    })
  }
  const advice = Array.isArray(current.advice) ? current.advice.filter((item) => item?.trim()) : []
  if (advice.length) {
    hints.push({
      type: current.status === 'FAILED' ? 'error' : 'info',
      title: '解决建议',
      text: advice.map((item, index) => `${index + 1}. ${item}`).join('\n'),
    })
  }
  const snapshot = parseSnapshotSummary()
  if (snapshot?.truncated) {
    hints.push({
      type: 'warning',
      title: '页面可见文本已截断',
      text: `仅前 ${snapshot.keptLength} 字（原文 ${snapshot.visibleTextLength} 字）送入模型，`
        + '规划与判定可能遗漏页面内容。',
    })
  }
  return hints
})

function parseSnapshotSummary(): { visibleTextLength: number; keptLength: number; truncated: boolean; hasLength: boolean } | null {
  const raw = detail.value?.snapshotJson
  if (!raw) return null
  try {
    const data = JSON.parse(raw) as {
      visibleText?: string
      visibleTextLength?: number
      visibleTextTruncated?: boolean
    }
    const keptLength = data.visibleText ? String(data.visibleText).length : 0
    const hasLength = typeof data.visibleTextLength === 'number'
    const visibleTextLength = hasLength ? Number(data.visibleTextLength) : keptLength
    return {
      visibleTextLength,
      keptLength,
      truncated: Boolean(data.visibleTextTruncated) || visibleTextLength > keptLength,
      hasLength,
    }
  } catch {
    return null
  }
}

const visibleTextStats = computed(() => {
  const snapshot = parseSnapshotSummary()
  if (!snapshot) return '未记录'
  if (snapshot.truncated) {
    return `${snapshot.keptLength} 字 / 原文 ${snapshot.visibleTextLength} 字（已截断）`
  }
  if (!snapshot.hasLength) return `${snapshot.keptLength} 字（未记录原文长度）`
  return `${snapshot.keptLength} 字（未截断）`
})

watch(
  () => props.modelValue,
  (value) => {
    if (value && props.taskId) {
      resetCaseView()
      void load()
      startPolling()
    } else {
      previewVisible.value = false
      previewCaseId.value = null
      stopPolling()
    }
  },
)

watch(
  () => props.taskId,
  () => {
    if (props.modelValue && props.taskId) {
      resetCaseView()
      void load()
      startPolling()
    }
  },
)

function startPolling() {
  stopPolling()
  timer.value = window.setInterval(() => {
    if (props.modelValue && props.taskId && isActive.value) {
      void load(true)
      if (activeTab.value === 'models') void loadModelLogs(true)
    }
  }, 2000)
}

function stopPolling() {
  if (timer.value !== null) {
    window.clearInterval(timer.value)
    timer.value = null
  }
}

async function load(silent = false) {
  if (!props.taskId) return
  if (!silent) loading.value = true
  try {
    detail.value = await getPatrolTaskDetail(props.spaceId, props.taskId)
  } catch (error) {
    if (!silent) showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    loading.value = false
  }
}

async function loadModelLogs(silent = false) {
  if (!props.taskId || !isSuperAdmin.value) return
  if (!silent) modelLogsLoading.value = true
  try {
    modelLogs.value = await getPatrolTaskModelLogs(props.spaceId, props.taskId)
  } catch (error) {
    if (!silent) showAppToast(toErrorMessage(error, '模型日志加载失败'), 'error')
  } finally {
    modelLogsLoading.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'models') void loadModelLogs()
})

function evidenceUrl(key?: string | null) {
  if (!key || !props.taskId) return ''
  return patrolEvidenceUrl(props.spaceId, props.taskId, key)
}

async function scrollPreviewIntoView() {
  await nextTick()
  document.querySelector('.evidence-panel')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function openCaseImage(caseItem: PatrolCase, key?: string | null, title = '截图预览') {
  const url = evidenceUrl(key)
  if (!url) return
  previewCaseId.value = caseItem.id
  previewType.value = 'image'
  previewTitle.value = title
  previewImageUrl.value = url
  previewVisible.value = true
  void scrollPreviewIntoView()
}

async function openConsole(caseItem: PatrolCase) {
  if (!caseItem.consoleKey) return
  previewCaseId.value = caseItem.id
  previewType.value = 'console'
  previewTitle.value = `控制台日志 · ${caseItem.name}`
  previewVisible.value = true
  void scrollPreviewIntoView()
  previewLoading.value = true
  consoleMessages.value = []
  consoleRequests.value = []
  consoleErrorCount.value = 0
  consoleRaw.value = ''
  try {
    const response = await fetch(evidenceUrl(caseItem.consoleKey), { credentials: 'include' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const text = await response.text()
    consoleRaw.value = text
    const data = JSON.parse(text)
    consoleErrorCount.value = Number(data.consoleErrors ?? 0)
    consoleMessages.value = Array.isArray(data.messages) ? data.messages : []
    consoleRequests.value = Array.isArray(data.requests) ? data.requests : []
  } catch (error) {
    consoleRaw.value = `日志读取失败：${String(error)}`
  } finally {
    previewLoading.value = false
  }
}

async function openDom(caseItem: PatrolCase) {
  if (!caseItem.domKey) return
  previewCaseId.value = caseItem.id
  previewType.value = 'dom'
  previewTitle.value = `DOM 结构 · ${caseItem.name}`
  previewVisible.value = true
  void scrollPreviewIntoView()
  previewLoading.value = true
  previewText.value = ''
  try {
    const response = await fetch(evidenceUrl(caseItem.domKey), { credentials: 'include' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const text = await response.text()
    previewText.value = text.length > 80000
      ? `${text.slice(0, 80000)}\n\n...（内容过长，仅展示前 80000 字符）`
      : text
  } catch (error) {
    previewText.value = `DOM 读取失败：${String(error)}`
  } finally {
    previewLoading.value = false
  }
}

function consoleTagType(type: string) {
  return type === 'error' || type === 'pageerror' ? 'danger' : 'info'
}

function riskTag(risk: PatrolRisk) {
  return { READ: 'info', WRITE: 'warning', DESTRUCTIVE: 'danger', AUTH: 'warning' }[risk] ?? 'info'
}

function statusTag(status?: string) {
  if (!status) return 'info'
  if (status === 'PASS' || status === 'SUCCESS' || status === 'COMPLETED') return 'success'
  if (status === 'FAIL' || status === 'FAILED' || status === 'BLOCKED') return 'danger'
  if (status === 'SKIP' || status === 'SKIPPED' || status === 'WAITING_APPROVAL'
    || status === 'UNREACHABLE') return 'warning'
  return 'info'
}

function caseStatusText(status: string) {
  return {
    PENDING: '待执行',
    PASS: '通过',
    FAIL: '失败',
    SKIP: '跳过',
    SUCCESS: '执行成功',
    FAILED: '执行失败',
    SKIPPED: '已跳过',
    BLOCKED: '已拦截',
  }[status] ?? status
}

function caseCardClass(status: string) {
  if (status === 'PASS') return 'case-card case-card--pass'
  if (status === 'FAIL') return 'case-card case-card--fail'
  if (status === 'SKIP') return 'case-card case-card--skip'
  return 'case-card'
}

async function approve(allowAll: boolean) {
  if (!props.taskId) return
  approving.value = true
  try {
    await approvePatrolTask(props.spaceId, props.taskId, {
      allowWrite: allowAll && allowWrite.value,
      allowDestructive: allowAll && allowDestructive.value,
      approvedCaseIds: [],
    })
    showAppToast(allowAll ? '审批已提交，开始执行' : '已拒绝写操作，仅执行只读用例')
    await load()
    emit('changed')
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    approving.value = false
  }
}

async function cancelTask() {
  if (!props.taskId) return
  try {
    await cancelPatrolTask(props.spaceId, props.taskId)
    showAppToast('已请求取消')
    await load()
    emit('changed')
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

function modelSummary(): string {
  if (!detail.value?.modelSnapshotJson) return '—'
  try {
    const snapshot = JSON.parse(detail.value.modelSnapshotJson)
    const parts: string[] = []
    if (snapshot.resolve?.modelId) parts.push(`解析 ${snapshot.resolve.modelId}`)
    if (snapshot.plan?.modelId) parts.push(`规划 ${snapshot.plan.modelId}`)
    if (snapshot.supplement?.modelId) parts.push(`补全 ${snapshot.supplement.modelId}`)
    if (snapshot.judge?.modelId) {
      parts.push(`判定 ${snapshot.judge.modelId}${snapshot.judge.vision ? '（视觉）' : ''}`)
    } else {
      parts.push('判定未配置（规则降级）')
    }
    return parts.join(' · ')
  } catch {
    return '—'
  }
}

function caseVideo(caseItem: PatrolCase) {
  return evidenceUrl(caseItem.videoKey)
}

const STAGE_LABELS: Record<string, string> = {
  PLAN: '用例规划',
  COVERAGE: '覆盖率补全',
  JUDGE: '视觉判定',
  RESOLVE: '前置解析',
}

function stageLabel(stage: string) {
  return STAGE_LABELS[stage] ?? stage
}

function logStatusText(status: string) {
  return {
    SUCCESS: '成功',
    DEGRADED: '降级',
    FAILED: '失败',
    SKIPPED: '已跳过',
  }[status] ?? status
}

function logStatusTag(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'DEGRADED' || status === 'SKIPPED') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function logTokens(log: PatrolModelLog) {
  return log.promptTokens + log.completionTokens
}

function formatDuration(ms: number) {
  if (!ms) return '0s'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)}s`
}
</script>

<template>
  <el-drawer v-model="visible" size="920px" title="巡页任务">
    <div v-loading="loading" class="task-drawer">
      <template v-if="task">
        <header class="task-hero">
          <div class="task-hero__main">
            <div class="task-hero__eyebrow">
              {{ task.triggerType === 'SCHEDULED' ? '定时触发' : '手动触发' }}
              <span class="dot-sep">·</span>
              {{ formatBeijingDateTime(task.createdAt) }}
            </div>
            <div class="task-hero__title">
              <span>{{ task.sceneName }}</span>
              <el-tag :type="statusTag(task.status)" size="small">{{ task.status }}</el-tag>
              <el-tag v-if="task.degraded" type="warning" size="small">降级</el-tag>
            </div>
            <div class="task-hero__url">{{ task.startUrl }}</div>
            <div class="task-hero__badges">
              <span class="mini-badge">用例 {{ task.caseCount }}</span>
              <span class="mini-badge">通过 {{ task.casePassCount }}</span>
              <span class="mini-badge">失败 {{ task.caseFailCount }}</span>
              <span class="mini-badge">Token {{ task.llmTokenUsed }}</span>
            </div>
          </div>
          <div v-if="isActive" class="task-hero__actions">
            <el-button type="warning" plain @click="cancelTask">取消任务</el-button>
          </div>
        </header>

        <el-alert
          v-if="task.resultMessage && task.status !== 'COMPLETED'"
          class="result-alert"
          :closable="false"
          :type="task.status === 'FAILED' ? 'error' : 'info'"
          :title="task.resultMessage"
          show-icon
        />

        <el-card v-if="task.status === 'WAITING_APPROVAL'" class="approval-card" shadow="never">
          <div class="approval-head">
            <div class="approval-title">
              <span class="approval-icon">!</span>
              执行前审批
              <el-tag type="warning" size="small">
                {{ detail?.report.pendingApprovalCaseIds.length ?? 0 }} 条写操作用例
              </el-tag>
              <el-tag
                v-if="detail?.report.pendingSupplementLabels?.length"
                type="info"
                size="small"
              >
                {{ detail?.report.pendingSupplementLabels?.length }} 个补充入口待审批
              </el-tag>
            </div>
            <span class="approval-sub">未授权步骤会被步骤守卫拦截，未授权变更请求会被网络护栏阻断</span>
            <span
              v-if="detail?.report.pendingSupplementLabels?.length"
              class="approval-sub"
            >
              以下补充入口含写操作前置，审批通过后才会执行前置并生成状态快照：
              {{ detail?.report.pendingSupplementLabels?.join('、') }}
            </span>
          </div>
          <div class="approval-switches">
            <div class="approval-switch-card">
              <div>
                <div class="approval-switch-title">允许写操作</div>
                <div class="approval-switch-hint">创建、保存、提交</div>
              </div>
              <el-switch v-model="allowWrite" />
            </div>
            <div class="approval-switch-card approval-switch-card--danger">
              <div>
                <div class="approval-switch-title">允许破坏性操作</div>
                <div class="approval-switch-hint">删除、清空、支付</div>
              </div>
              <el-switch v-model="allowDestructive" />
            </div>
          </div>
          <div class="approval-actions">
            <el-button type="primary" :loading="approving" @click="approve(true)">批准并执行</el-button>
            <el-button :loading="approving" @click="approve(false)">全部拒绝（仅只读）</el-button>
          </div>
        </el-card>

        <el-tabs v-model="activeTab" class="task-tabs">
          <el-tab-pane label="概览" name="overview">
            <div class="stat-grid">
              <div class="stat-card stat-card--green">
                <div class="stat-label">用例通过</div>
                <div class="stat-value">{{ task.casePassCount }}<span class="stat-total">/{{ task.caseCount }}</span></div>
                <div class="stat-sub">失败 {{ task.caseFailCount }} · 跳过 {{ task.caseSkipCount }}</div>
              </div>
              <div class="stat-card stat-card--purple">
                <div class="stat-label">元素覆盖</div>
                <div class="stat-value">
                  {{ detail?.report.coverage.coveredElements ?? 0 }}<span class="stat-total">/{{ detail?.report.coverage.totalElements ?? 0 }}</span>
                </div>
                <div class="stat-sub">覆盖率 {{ detail?.report.coverage.coverageRate ?? 0 }}%</div>
              </div>
              <div class="stat-card stat-card--red">
                <div class="stat-label">失败用例</div>
                <div class="stat-value">{{ task.caseFailCount }}<span class="stat-total">/{{ task.caseCount }}</span></div>
                <div class="stat-sub">跳过 {{ task.caseSkipCount }}</div>
              </div>
              <div class="stat-card stat-card--blue">
                <div class="stat-label">LLM Token</div>
                <div class="stat-value">{{ task.llmTokenUsed }}</div>
                <div class="stat-sub">{{ modelSummary() }}</div>
              </div>
              <div class="stat-card stat-card--amber">
                <div class="stat-label">保留至</div>
                <div class="stat-value">{{ formatBeijingDateTime(task.retentionUntil) }}</div>
                <div class="stat-sub">任务与全部证据保留 90 天</div>
              </div>
            </div>

            <div v-if="runtimeHints.length" class="runtime-hints">
              <el-alert
                v-for="(hint, index) in runtimeHints"
                :key="index"
                :type="hint.type"
                :closable="false"
                show-icon
                :title="hint.title"
              >
                <div class="runtime-hint__text">{{ hint.text }}</div>
              </el-alert>
            </div>

            <div class="overview-grid">
              <section class="info-card">
                <h4>执行信息</h4>
                <div class="info-row"><span>审批人</span><strong>{{ task.approvedBy || '—' }}</strong></div>
                <div class="info-row"><span>审批时间</span><strong>{{ formatBeijingDateTime(task.approvedAt) }}</strong></div>
                <div class="info-row"><span>开始时间</span><strong>{{ formatBeijingDateTime(task.startedAt) }}</strong></div>
                <div class="info-row"><span>结束时间</span><strong>{{ formatBeijingDateTime(task.finishedAt) }}</strong></div>
                <div class="info-row"><span>当前阶段</span><strong>{{ task.currentStage }}</strong></div>
                <div class="info-row"><span>入口页文本</span><strong>{{ visibleTextStats }}</strong></div>
              </section>
              <section class="info-card">
                <h4>模型</h4>
                <div class="model-block">
                  <div class="model-block__label">执行所用模型</div>
                  <div class="model-block__value">{{ modelSummary() }}</div>
                </div>
              </section>
            </div>
            <section v-if="runs.length" class="info-card run-card">
              <h4>Run 执行情况</h4>
              <div v-for="run in runs" :key="run.runId" class="run-row">
                <div class="run-row__head">
                  <span class="run-row__label">{{ runLabel(run) }}</span>
                  <el-tag :type="statusTag(run.status)" size="small">{{ runStatusText(run.status) }}</el-tag>
                  <span class="run-row__meta">
                    用例 {{ run.casePassCount }}/{{ run.caseCount }} · 失败 {{ run.caseFailCount }} ·
                    覆盖 {{ run.coverageRate }}% · {{ run.llmTokenUsed }} tokens
                  </span>
                </div>
                <div v-if="run.resultMessage" class="run-row__message">{{ run.resultMessage }}</div>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane :label="`用例`" name="cases">
            <div class="case-toolbar">
              <el-select
                v-if="runs.length"
                v-model="runFilter"
                size="small"
                class="case-toolbar__run"
              >
                <el-option label="全部 Run" value="ALL" />
                <el-option
                  v-for="run in runs"
                  :key="run.runId"
                  :label="runLabel(run)"
                  :value="String(run.runId)"
                />
              </el-select>
              <el-radio-group v-model="caseFilter" size="small">
                <el-radio-button value="ALL">全部</el-radio-button>
                <el-radio-button value="PASS">成功</el-radio-button>
                <el-radio-button value="FAIL">失败</el-radio-button>
              </el-radio-group>
              <span class="case-toolbar__count">共 {{ filteredCases.length }} 条</span>
            </div>
            <div v-for="item in filteredCases" :key="item.id" :class="caseCardClass(item.status)">
              <div class="case-head case-head--clickable" @click="toggleCase(item.id)">
                <div class="case-title">
                  <span class="case-seq">#{{ item.seq }}</span>
                  <span class="case-name">{{ item.name }}</span>
                  <el-tag v-if="item.entryLabel" type="warning" size="small">{{ item.entryLabel }}</el-tag>
                  <el-tag :type="statusTag(item.status)" size="small">{{ caseStatusText(item.status) }}</el-tag>
                  <el-tag :type="riskTag(item.worstRisk)" size="small">{{ item.worstRisk }}</el-tag>
                  <el-tag type="info" size="small">{{ item.source }}</el-tag>
                </div>
                <div class="case-head__right">
                  <span class="case-meta">{{ caseStatusText(item.execStatus) }} · {{ item.tokens }} tokens</span>
                  <span class="case-toggle">{{ isCaseExpanded(item.id) ? '收起 ▴' : '展开 ▾' }}</span>
                </div>
              </div>
              <template v-if="isCaseExpanded(item.id)">
                <div class="case-expected">预期：{{ item.expected || '—' }}</div>
                <div v-if="item.judgeReason" class="case-reason">判定：{{ item.judgeReason }}</div>

                <video
                  v-if="item.videoKey"
                  class="case-video"
                  :src="caseVideo(item)"
                  controls
                  preload="metadata"
                />
                <div v-else class="case-no-video">本次未生成录屏（巡页关闭了录屏或执行未开始）</div>

                <el-table :data="item.steps" size="small" border class="step-table">
                  <el-table-column prop="seq" label="#" width="50" />
                  <el-table-column label="动作" width="110">
                    <template #default="{ row }">
                      <CellTooltip :content="row.action" />
                    </template>
                  </el-table-column>
                  <el-table-column label="目标" min-width="170">
                    <template #default="{ row }">
                      <div><CellTooltip :content="row.targetLabel || row.targetSelector" /></div>
                      <div v-if="row.inputValue" class="muted">
                        值：<CellTooltip :content="row.inputValue" />
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="风险" width="120">
                    <template #default="{ row }">
                      <el-tag :type="riskTag(row.risk)" size="small">{{ row.risk }}</el-tag>
                      <el-tag v-if="row.approved" type="success" size="small">已授权</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="结果" width="110">
                    <template #default="{ row }">
                      <el-tag :type="statusTag(row.status)" size="small">{{ caseStatusText(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="截图" width="80">
                    <template #default="{ row }">
                      <el-button
                        v-if="row.screenshotKey"
                        link
                        type="primary"
                        @click="openCaseImage(item, row.screenshotKey, '步骤截图')"
                      >
                        查看
                      </el-button>
                    </template>
                  </el-table-column>
                  <el-table-column label="信息" min-width="160">
                    <template #default="{ row }">
                      <CellTooltip :content="row.message" />
                    </template>
                  </el-table-column>
                </el-table>

                <div v-if="item.consoleKey || item.domKey || item.screenshotKey" class="evidence-row">
                  <span class="evidence-label">证据</span>
                  <el-button
                    v-if="item.screenshotKey"
                    link
                    type="primary"
                    @click="openCaseImage(item, item.screenshotKey, '最终截图')"
                  >
                    最终截图
                  </el-button>
                  <el-button v-if="item.consoleKey" link type="primary" @click="openConsole(item)">
                    控制台日志
                  </el-button>
                  <el-button v-if="item.domKey" link type="primary" @click="openDom(item)">
                    DOM 结构
                  </el-button>
                </div>

                <section
                  v-if="previewVisible && previewCaseId === item.id"
                  class="evidence-panel"
                >
                  <div class="evidence-panel__head">
                    <span class="evidence-panel__title">{{ previewTitle }}</span>
                    <el-button link type="primary" @click="previewVisible = false">关闭</el-button>
                  </div>
                  <div v-loading="previewLoading" class="evidence-preview">
                    <img v-if="previewType === 'image'" class="evidence-image" :src="previewImageUrl" alt="证据截图" />

                    <template v-else-if="previewType === 'console'">
                      <div class="console-summary">
                        控制台错误 <b>{{ consoleErrorCount }}</b> 个 · 变更请求 <b>{{ consoleRequests.length }}</b> 个
                      </div>
                      <div class="console-section-title">控制台消息</div>
                      <el-table :data="consoleMessages" size="small" border max-height="260" empty-text="没有控制台消息">
                        <el-table-column label="类型" width="110">
                          <template #default="{ row }">
                            <el-tag :type="consoleTagType(row.type)" size="small">{{ row.type }}</el-tag>
                          </template>
                        </el-table-column>
                        <el-table-column label="内容" min-width="360">
                          <template #default="{ row }">
                            <CellTooltip :content="row.text" />
                          </template>
                        </el-table-column>
                        <el-table-column label="时间" width="190">
                          <template #default="{ row }">
                            <CellTooltip :content="row.ts" />
                          </template>
                        </el-table-column>
                      </el-table>

                      <div v-if="consoleRequests.length" class="console-section-title">变更请求</div>
                      <el-table v-if="consoleRequests.length" :data="consoleRequests" size="small" border max-height="220">
                        <el-table-column prop="method" label="方法" width="90" />
                        <el-table-column label="URL" min-width="360">
                          <template #default="{ row }">
                            <CellTooltip :content="row.url" mono />
                          </template>
                        </el-table-column>
                        <el-table-column label="结果" width="110">
                          <template #default="{ row }">
                            <el-tag :type="row.blocked ? 'danger' : 'success'" size="small">
                              {{ row.blocked ? '已拦截' : '已放行' }}
                            </el-tag>
                          </template>
                        </el-table-column>
                      </el-table>

                      <el-collapse v-if="consoleRaw" class="raw-collapse">
                        <el-collapse-item title="查看原始 JSON" name="raw">
                          <pre class="raw-json">{{ consoleRaw }}</pre>
                        </el-collapse-item>
                      </el-collapse>
                    </template>

                    <template v-else>
                      <pre class="dom-view">{{ previewText }}</pre>
                    </template>
                  </div>
                </section>
              </template>
            </div>
            <el-empty
              v-if="!filteredCases.length"
              :description="detail?.cases.length ? '当前筛选下没有用例' : '尚未规划出用例'"
            />
          </el-tab-pane>

          <el-tab-pane v-if="isSuperAdmin" :label="`模型日志`" name="models">
            <div class="log-toolbar">
              <span class="log-toolbar__hint">记录规划、覆盖补全与判定阶段收到的完整上下文及模型原始输出</span>
              <el-button link type="primary" :loading="modelLogsLoading" @click="loadModelLogs()">刷新</el-button>
            </div>

            <el-empty
              v-if="!modelLogsLoading && !modelLogs.length"
              description="暂无模型日志（任务尚未调用模型或已被清理）"
            />

            <article
              v-for="log in modelLogs"
              :key="log.id"
              class="model-log"
              :class="`model-log--${log.status.toLowerCase()}`"
            >
              <header class="model-log__head">
                <div class="model-log__title">
                  <el-tag :type="logStatusTag(log.status)" size="small">{{ logStatusText(log.status) }}</el-tag>
                  <span class="model-log__stage">{{ stageLabel(log.stage) }}</span>
                  <span v-if="log.caseName" class="model-log__case">{{ log.caseName }}</span>
                </div>
                <div class="model-log__meta">
                  <span>{{ log.modelId || '未配置模型' }}</span>
                  <span v-if="log.status !== 'SKIPPED'">{{ logTokens(log).toLocaleString() }} tokens · {{ formatDuration(log.durationMs) }}</span>
                </div>
              </header>
              <div v-if="log.errorMessage" class="model-log__error">{{ log.errorMessage }}</div>
              <el-collapse class="model-log__body">
                <el-collapse-item title="模型上下文（system + user）" name="context">
                  <div class="model-log__sub">SYSTEM</div>
                  <pre class="model-log__pre">{{ log.systemPrompt || '（无）' }}</pre>
                  <div class="model-log__sub">USER</div>
                  <pre class="model-log__pre">{{ log.userPrompt || '（无）' }}</pre>
                </el-collapse-item>
                <el-collapse-item title="原始输出" name="output">
                  <pre class="model-log__pre">{{ log.rawOutput || '（无输出）' }}</pre>
                </el-collapse-item>
              </el-collapse>
              <footer class="model-log__foot">{{ formatBeijingDateTime(log.createdAt) }}</footer>
            </article>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="任务不存在或已被清理" />
    </div>
  </el-drawer>
</template>

<style scoped>
.task-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.task-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid #e8edf3;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 55%);
}
.task-hero__main {
  min-width: 0;
}
.task-hero__eyebrow {
  color: #94a3b8;
  font-size: 12px;
  letter-spacing: 0.02em;
}
.dot-sep {
  margin: 0 6px;
}
.task-hero__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  flex-wrap: wrap;
}
.task-hero__url {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  word-break: break-all;
}
.task-hero__badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.mini-badge {
  padding: 3px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
}
.task-hero__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 0 0 auto;
}
.evidence-panel {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #fbfdff;
}
.evidence-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.evidence-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.result-alert {
  border-radius: 10px;
}

/* 审批 */
.approval-card {
  border-radius: 14px;
  border-color: #fde68a;
  background: #fffdf5;
}
.approval-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.approval-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #92400e;
}
.approval-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.approval-sub {
  color: #a16207;
  font-size: 12px;
}
.approval-switches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.approval-switch-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid #e8edf3;
  border-radius: 12px;
  background: #fff;
}
.approval-switch-card--danger {
  border-color: #fecaca;
  background: #fff7f7;
}
.approval-switch-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.approval-switch-hint {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}
.approval-actions {
  display: flex;
  gap: 8px;
}

/* 概览 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.stat-card {
  position: relative;
  padding: 14px 16px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: #cbd5e1;
}
.stat-card--green::before { background: #22c55e; }
.stat-card--red::before { background: #ef4444; }
.stat-card--blue::before { background: #3b82f6; }
.stat-card--amber::before { background: #f59e0b; }
.stat-card--purple::before { background: #8b5cf6; }
.stat-label {
  color: #64748b;
  font-size: 12px;
}
.stat-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
}
.stat-total {
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
}
.stat-sub {
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
}
.runtime-hints {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}
.runtime-hint__text {
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
  word-break: break-word;
  white-space: pre-wrap;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.info-card {
  border: 1px solid #e8edf3;
  border-radius: 14px;
  padding: 16px 18px;
  background: #fff;
}
.info-card h4 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.info-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 7px 0;
  border-bottom: 1px dashed #eef2f7;
  font-size: 13px;
}
.info-row:last-child {
  border-bottom: 0;
}
.info-row span {
  color: #94a3b8;
}
.info-row strong {
  color: #334155;
  font-weight: 600;
  text-align: right;
}
.model-block {
  margin-top: 6px;
}
.model-block__label {
  color: #94a3b8;
  font-size: 12px;
}
.model-block__value {
  margin-top: 4px;
  color: #334155;
  font-size: 13px;
  word-break: break-all;
}

/* 用例 */
.case-card {
  position: relative;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  padding: 16px 18px;
  margin-bottom: 14px;
  background: #fff;
}
.case-card--pass {
  border-color: #bbf7d0;
}
.case-card--fail {
  border-color: #fecaca;
}
.case-card--skip {
  border-color: #e2e8f0;
  background: #fbfcfe;
}
.case-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.case-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.case-seq {
  color: #94a3b8;
  font-size: 13px;
  font-weight: 600;
}
.case-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.case-meta {
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}
.case-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.case-toolbar__run {
  width: 200px;
  margin-right: auto;
}
.run-card {
  margin-top: 14px;
}
.run-row {
  padding: 10px 0;
  border-bottom: 1px dashed #e8edf3;
}
.run-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.run-row__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.run-row__label {
  font-weight: 600;
  color: #0f172a;
}
.run-row__meta {
  color: #64748b;
  font-size: 12px;
}
.run-row__message {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
  word-break: break-all;
}
.case-toolbar__count {
  color: #94a3b8;
  font-size: 12px;
}
.case-head--clickable {
  cursor: pointer;
  user-select: none;
}
.case-head__right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.case-toggle {
  color: #2563eb;
  font-size: 12px;
  white-space: nowrap;
}
.case-expected {
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}
.case-reason {
  margin-top: 6px;
  color: #b45309;
  font-size: 12px;
  line-height: 1.6;
}
.case-video {
  width: 100%;
  max-height: 320px;
  margin-top: 12px;
  border-radius: 10px;
  background: #0f172a;
}
.case-no-video {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 12px;
}
.step-table {
  margin-top: 12px;
}
.evidence-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.evidence-label {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}
.muted {
  color: #94a3b8;
  font-size: 12px;
}
/* 证据预览 */
.evidence-preview {
  min-height: 180px;
}
.evidence-image {
  display: block;
  max-width: 100%;
  max-height: 68vh;
  margin: 0 auto;
  border-radius: 10px;
  border: 1px solid #e8edf3;
}
.console-summary {
  margin-bottom: 10px;
  padding: 8px 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
}
.console-summary b {
  color: #0f172a;
}
.console-section-title {
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.raw-collapse {
  margin-top: 12px;
}
.raw-json,
.dom-view {
  margin: 0;
  max-height: 55vh;
  overflow: auto;
  padding: 14px;
  border-radius: 10px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
.dom-view {
  max-height: 68vh;
}

.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.log-toolbar__hint {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}
.model-log {
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid #e8edf3;
  border-left: 3px solid #94a3b8;
  border-radius: 12px;
  background: #fff;
}
.model-log--success {
  border-left-color: #22c55e;
}
.model-log--degraded {
  border-left-color: #f59e0b;
}
.model-log--failed {
  border-left-color: #ef4444;
}
.model-log--skipped {
  border-left-color: #94a3b8;
}
.model-log__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.model-log__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.model-log__stage {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.model-log__case {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-log__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}
.model-log__error {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}
.model-log__body {
  margin-top: 8px;
}
.model-log__sub {
  margin: 6px 0 4px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
}
.model-log__pre {
  margin: 0 0 8px;
  max-height: 300px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-all;
}
.model-log__foot {
  margin-top: 6px;
  color: #b0b8c4;
  font-size: 11px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
