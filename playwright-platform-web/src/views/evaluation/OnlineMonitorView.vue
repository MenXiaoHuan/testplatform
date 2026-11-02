<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { get, post, del } from '../../api/http'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import ListPageShell from '../../components/list/ListPageShell.vue'
import CellTooltip from '../../components/list/CellTooltip.vue'
import EvaluatorConfigEditor from '../../components/evaluation/EvaluatorConfigEditor.vue'
import TraceDetailDrawer from '../../components/evaluation/TraceDetailDrawer.vue'
import { formatBeijingDateTime as fmtTime, toBeijingDayStart, toBeijingDayEnd } from '../../utils/datetime'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isSuperAdmin = computed(() => authStore.isSuperAdmin)
const spaceId = computed(() => Number(route.params.spaceId))

interface AgentSummary {
  targetId: number
  name: string
  agentKey: string
  status: 'CONNECTED' | 'DISABLED'
  lastSeenAt?: string
  traceCount: number
}

interface EvalTargetSummary {
  id: number
  name: string
  targetType: 'BUILTIN_AGENT' | 'LLM_API' | 'HTTP_AGENT'
}

interface TraceRow {
  traceId: string
  name?: string | null
  targetId?: string | null
  sessionId?: string | null
  input?: string | null
  output?: string | null
  inputTokens?: number | null
  outputTokens?: number | null
  durationMs?: number | null
  startTime?: string | null
  endTime?: string | null
  tags?: string[]
  success?: boolean
  metadata?: Record<string, unknown>
}

interface OnlinePolicy {
  id: number
  spaceId: number
  targetId: number
  name: string
  status: 'ACTIVE' | 'PAUSED'
  filterConfig: string
  evaluatorConfigs: string
  alertingConfig: string
  rateLimitConfig: string
  scanIntervalMinutes?: number
  nextScanAt?: string
  createdAt?: string
}

const activeTab = ref<'traces' | 'policies'>('traces')
const agents = ref<AgentSummary[]>([])
const evalTargets = ref<EvalTargetSummary[]>([])
const targetId = ref(String(route.query.targetId ?? ''))
const from = ref(daysAgoDate(6))
const to = ref(new Date())
const traceIdQuery = ref('')
const statusFilter = ref<'all' | 'success' | 'failed'>('all')
const categoryFilter = ref<'all' | 'online' | 'offline' | 'builtin'>('all')
const page = ref(1)
const size = ref(20)
const traces = ref<TraceRow[]>([])
const total = ref(0)
const tracesLoading = ref(false)
const metrics = ref<any>(null)
const metricsLoading = ref(false)
const errorMsg = ref('')

const policies = ref<OnlinePolicy[]>([])
const policiesLoading = ref(false)
const policyDialogVisible = ref(false)
const policySaving = ref(false)
const policyForm = reactive({
  name: '',
  targetId: '',
  evaluatorConfigs: '[{"type":"LLM_AS_JUDGE","config":{"requirement":"请从准确性、完整性和相关性三个维度评价 Agent 输出。"},"weight":1}]',
  lookbackHours: 24,
  limit: 20,
  lowScoreThreshold: 60,
  scanIntervalMinutes: 5,
})

const drawerVisible = ref(false)
const activeTraceId = ref<string | null>(null)

const availableAgents = computed(() => {
  const options = evalTargets.value.map((target) => ({
    label: `${target.name}（${targetTypeLabel(target.targetType)}）`,
    value: String(target.id),
  }))
  const seen = new Set(options.map((option) => option.value))
  for (const agent of agents.value) {
    const value = String(agent.targetId)
    if (seen.has(value)) continue
    options.push({ label: agent.name, value })
    seen.add(value)
  }
  return options
})

const builtinTargetIds = computed(() =>
  new Set(evalTargets.value.filter((target) => target.targetType === 'BUILTIN_AGENT').map((target) => String(target.id))),
)

function targetTypeLabel(type: EvalTargetSummary['targetType']) {
  if (type === 'BUILTIN_AGENT') return '内置 Agent'
  if (type === 'LLM_API') return 'LLM Agent'
  return 'HTTP Agent'
}

/** 内置 Agent 的 Trace 在 Langfuse 统一归档为 target_id=builtin_agent。 */
const traceQueryTargetId = computed(() =>
  builtinTargetIds.value.has(targetId.value) ? 'builtin_agent' : targetId.value.trim(),
)

function daysAgoDate(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d
}

async function loadAgents() {
  try {
    const list = await get<AgentSummary[]>('/evaluation/agents', { params: { spaceId: spaceId.value } })
    agents.value = list ?? []
  } catch {
    // 外部 Agent 列表失败不阻塞 Trace 查询
  }
  try {
    const targets = await get<EvalTargetSummary[]>('/evaluation/targets', {
      params: { spaceId: spaceId.value },
    })
    evalTargets.value = targets ?? []
  } catch {
    evalTargets.value = []
  }
}

async function loadTraces() {
  tracesLoading.value = true
  errorMsg.value = ''
  try {
    const result = await get<{ items: TraceRow[]; total: number }>('/evaluation/online/traces', {
      params: {
        targetId: traceQueryTargetId.value || undefined,
        traceId: traceIdQuery.value.trim() || undefined,
        from: from.value ? toBeijingDayStart(from.value).toISOString() : undefined,
        to: to.value ? toBeijingDayEnd(to.value).toISOString() : undefined,
        page: page.value,
        size: size.value,
        status: statusFilter.value === 'all' ? undefined : statusFilter.value,
        category:
          categoryFilter.value === 'all' || (categoryFilter.value === 'builtin' && !isSuperAdmin.value)
            ? undefined
            : categoryFilter.value,
      },
    })
    traces.value = result?.items ?? []
    total.value = result?.total ?? 0
  } catch (e: any) {
    traces.value = []
    total.value = 0
    errorMsg.value = toErrorMessage(e, '操作失败，请稍后重试')
    showAppToast(`Trace 查询失败: ${errorMsg.value}`, 'error')
  } finally {
    tracesLoading.value = false
  }
}

async function loadMetrics() {
  metricsLoading.value = true
  try {
    metrics.value = await get<any>('/evaluation/online/monitor/metrics', {
      params: {
        targetId: traceQueryTargetId.value || '',
        from: from.value ? toBeijingDayStart(from.value).toISOString() : toBeijingDayStart(daysAgoDate(6)).toISOString(),
        to: to.value ? toBeijingDayEnd(to.value).toISOString() : toBeijingDayEnd(new Date()).toISOString(),
        granularity: 'HOUR',
      },
    })
  } catch {
    metrics.value = null
  } finally {
    metricsLoading.value = false
  }
}

async function loadPolicies() {
  policiesLoading.value = true
  try {
    policies.value = await get<OnlinePolicy[]>('/evaluation/online/policies', {
      params: { spaceId: spaceId.value },
    }) ?? []
  } catch {
    policies.value = []
  } finally {
    policiesLoading.value = false
  }
}

function openPolicyDialog() {
  policyForm.name = ''
  policyForm.targetId = targetId.value || (agents.value[0] ? String(agents.value[0].targetId) : '')
  policyForm.evaluatorConfigs = '[{"type":"LLM_AS_JUDGE","config":{"requirement":"请从准确性、完整性和相关性三个维度评价 Agent 输出。"},"weight":1}]'
  policyForm.lookbackHours = 24
  policyForm.limit = 20
  policyForm.lowScoreThreshold = 60
  policyForm.scanIntervalMinutes = 5
  policyDialogVisible.value = true
}

function isEvaluatorArray(raw: string) {
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed)
  } catch {
    return false
  }
}

async function createPolicy() {
  if (!policyForm.name.trim()) {
    showAppToast('请填写策略名称', 'warning')
    return
  }
  if (!policyForm.targetId) {
    showAppToast('请选择目标 Agent', 'warning')
    return
  }
  if (!isEvaluatorArray(policyForm.evaluatorConfigs)) {
    showAppToast('评分器配置必须是 JSON 数组', 'warning')
    return
  }
  policySaving.value = true
  try {
    await post('/evaluation/online/policies', {
      spaceId: spaceId.value,
      targetId: Number(policyForm.targetId),
      name: policyForm.name.trim(),
      filterConfig: JSON.stringify({
        lookbackHours: policyForm.lookbackHours,
        limit: policyForm.limit,
      }),
      evaluatorConfigs: policyForm.evaluatorConfigs,
      alertingConfig: JSON.stringify({ lowScoreThreshold: policyForm.lowScoreThreshold }),
      scanIntervalMinutes: policyForm.scanIntervalMinutes,
    })
    policyDialogVisible.value = false
    await loadPolicies()
    showAppToast('在线策略已创建', 'success')
  } catch (e: any) {
    showAppToast(`创建失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    policySaving.value = false
  }
}

async function togglePolicy(policy: OnlinePolicy) {
  const next = policy.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
  try {
    await post(`/evaluation/online/policies/${policy.id}/status`, undefined, {
      params: { spaceId: spaceId.value, status: next },
    })
    await loadPolicies()
    showAppToast(next === 'ACTIVE' ? '策略已启用' : '策略已暂停', 'success')
  } catch (e: any) {
    showAppToast(`操作失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

async function scanPolicy(policy: OnlinePolicy) {
  try {
    const result = await post<any>(`/evaluation/online/policies/${policy.id}/scan`, undefined, {
      params: { spaceId: spaceId.value },
    })
    await Promise.all([loadPolicies(), loadTraces()])
    showAppToast(
      `扫描完成：扫描 ${result?.scanned ?? 0}，评分 ${result?.scored ?? 0}，跳过 ${result?.skipped ?? 0}，低分 ${result?.lowScore ?? 0}，失败 ${result?.failed ?? 0}`,
      'success',
    )
  } catch (e: any) {
    showAppToast(`扫描失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

async function deletePolicy(policy: OnlinePolicy) {
  try {
    await ElMessageBox.confirm(`确定删除在线策略「${policy.name}」？已产生的评分留痕会保留。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await del(`/evaluation/online/policies/${policy.id}`, {
      params: { spaceId: spaceId.value },
    })
    await loadPolicies()
    showAppToast('策略已删除', 'success')
  } catch (e: any) {
    showAppToast(`删除失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

function policyTargetName(policy: OnlinePolicy) {
  return agents.value.find((agent) => String(agent.targetId) === String(policy.targetId))?.name
    ?? `#${policy.targetId}`
}

function policyStatusTag(status: string) {
  return status === 'ACTIVE' ? 'success' : 'info'
}

function query() {
  page.value = 1
  loadTraces()
  loadMetrics()
}

function handlePageChange(value: number) {
  page.value = value
  loadTraces()
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
  loadTraces()
}

function goAgentManagement() {
  router.push(`/spaces/${spaceId.value}/evaluation/targets`)
}

function openTrace(row: TraceRow) {
  activeTraceId.value = row.traceId
  drawerVisible.value = true
}

function preview(value?: string | null, limit = 100) {
  if (!value) return '—'
  const text = String(value).replace(/\s+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

function fmtDelay(value?: number | null) {
  if (value == null) return '—'
  return (value / 1000).toFixed(2)
}

const latencyText = computed(() => {
  const latency = metrics.value?.latency
  return [latency?.p50Ms, latency?.p95Ms, latency?.p99Ms]
    .map((value: number | null | undefined) =>
      typeof value === 'number' && Number.isFinite(value) ? (value / 1000).toFixed(2) : '0.00')
    .join(' / ')
})

async function copyTraceId(traceId: string) {
  try {
    await navigator.clipboard.writeText(traceId)
    showAppToast('Trace ID 已复制', 'success')
  } catch {
    showAppToast('复制失败，请手动选择复制', 'error')
  }
}

watch(
  () => route.query.targetId,
  (value) => {
    if (value) {
      targetId.value = String(value)
      page.value = 1
      loadTraces()
      loadMetrics()
    }
  },
)

let autoRefreshTimer: number | undefined

/** 30 秒自动刷新：Trace 列表与指标；策略页刷新策略列表。 */
function autoRefresh() {
  if (document.hidden) return
  if (activeTab.value === 'traces') {
    loadTraces()
    loadMetrics()
  } else {
    loadPolicies()
  }
}

onMounted(async () => {
  await loadAgents()
  await Promise.all([loadTraces(), loadMetrics(), loadPolicies()])
  autoRefreshTimer = window.setInterval(autoRefresh, 30_000)
})

onUnmounted(() => {
  if (autoRefreshTimer != null) {
    window.clearInterval(autoRefreshTimer)
  }
})
</script>

<template>
  <div class="page">
    <ListPageShell>
      <template #header-left>
        <div class="head-left">
          <span class="panel-title">在线监控</span>
        </div>
      </template>

      <el-alert
        v-if="!availableAgents.length && !targetId"
        type="info"
        :closable="false"
        show-icon
        class="alert"
      >
        <template #title>当前空间还没有可监控的 Agent</template>
        请先到 Agent 管理页接入外部 Agent 或创建内置 Agent，完成一次上报后再查看 Trace。
        <el-button link type="primary" @click="goAgentManagement">去 Agent 管理</el-button>
      </el-alert>

      <el-alert v-if="errorMsg" type="error" :closable="false" show-icon class="alert">
        {{ errorMsg }}
      </el-alert>

      <el-tabs v-model="activeTab" class="monitor-tabs">
        <el-tab-pane label="Trace 浏览器" name="traces">
          <div class="filter-grid">
            <div class="filter-field span-4">
              <label class="filter-label">Agent</label>
              <el-select v-model="targetId" filterable placeholder="全部 Agent" @change="query">
                <el-option label="全部 Agent" value="" />
                <el-option
                  v-for="agent in availableAgents"
                  :key="agent.value"
                  :label="agent.label"
                  :value="agent.value"
                />
              </el-select>
            </div>
            <div class="filter-field span-4">
              <label class="filter-label">从</label>
              <el-date-picker
                v-model="from"
                type="date"
                format="YYYY-MM-DD"
                placeholder="起始日期"
                @change="query"
              />
            </div>
            <div class="filter-field span-4">
              <label class="filter-label">到</label>
              <el-date-picker
                v-model="to"
                type="date"
                format="YYYY-MM-DD"
                placeholder="结束日期"
                @change="query"
              />
            </div>
            <div class="filter-field span-4">
              <label class="filter-label">Trace ID</label>
              <el-input v-model="traceIdQuery" placeholder="traceId" clearable @keyup.enter="query" />
            </div>
            <div class="filter-field span-2">
              <label class="filter-label">状态</label>
              <el-select v-model="statusFilter" @change="query">
                <el-option label="全部" value="all" />
                <el-option label="成功" value="success" />
                <el-option label="失败" value="failed" />
              </el-select>
            </div>
            <div class="filter-field span-2">
              <label class="filter-label">类别</label>
              <el-select v-model="categoryFilter" @change="query">
                <el-option label="全部" value="all" />
                <el-option label="在线" value="online" />
                <el-option label="离线" value="offline" />
                <el-option v-if="isSuperAdmin" label="内部 Agent" value="builtin" />
              </el-select>
            </div>
            <div class="filter-field filter-actions span-4">
              <el-button type="primary" :loading="tracesLoading" @click="query">查询</el-button>
            </div>
          </div>

          <div v-loading="metricsLoading" class="metric-strip">
            <el-tooltip :content="`延迟 P50 / P95 / P99（s）：${latencyText}`" placement="top">
              <div class="metric-item">
                <span class="metric-label">延迟 P50 / P95 / P99（s）</span>
                <span class="metric-value">{{ latencyText }}</span>
              </div>
            </el-tooltip>
            <div class="metric-item">
              <span class="metric-label">Token 输入 / 输出</span>
              <span class="metric-value">
                {{ metrics?.tokens?.inputTotal ?? 0 }} / {{ metrics?.tokens?.outputTotal ?? 0 }}
              </span>
            </div>
            <div class="metric-item">
              <span class="metric-label">请求数</span>
              <span class="metric-value">{{ metrics?.tokens?.requestCount ?? 0 }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">自动评分 P50 / P95</span>
              <span class="metric-value">
                {{ metrics?.autoScore?.p50 ?? '—' }} / {{ metrics?.autoScore?.p95 ?? '—' }}
              </span>
            </div>
          </div>

          <el-table
            v-loading="tracesLoading"
            :data="traces"
            stripe
            class="trace-table"
            empty-text="当前筛选条件下没有 Trace"
            @row-click="openTrace"
          >
            <el-table-column label="时间" width="190">
              <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="名称" min-width="160">
              <template #default="{ row }">
                <div class="trace-name">
                  <CellTooltip :content="row.name || '(未命名)'" />
                  <el-tag v-if="row.success === false" type="danger" size="small" effect="plain">失败</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Trace ID" width="220">
              <template #default="{ row }">
                <span class="trace-id" @click.stop="copyTraceId(row.traceId)">
                  <CellTooltip :content="row.traceId" mono />
                </span>
              </template>
            </el-table-column>
            <el-table-column label="输入" min-width="180">
              <template #default="{ row }">
                <CellTooltip :content="row.input" :display="preview(row.input)" />
              </template>
            </el-table-column>
            <el-table-column label="输出" min-width="180">
              <template #default="{ row }">
                <CellTooltip :content="row.output" :display="preview(row.output)" />
              </template>
            </el-table-column>
            <el-table-column label="延迟（s）" width="128" align="center">
              <template #default="{ row }">{{ fmtDelay(row.durationMs) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click.stop="openTrace(row)">查看链路</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="page"
              :page-size="size"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="在线策略" name="policies">
          <div class="tab-toolbar">
            <div>
              <span class="tab-title">策略列表</span>
              <span class="tab-sub">共 {{ policies.length }} 条，定时扫描线上 Trace 并自动评分</span>
            </div>
            <el-button size="small" type="primary" plain @click="openPolicyDialog">+ 新建策略</el-button>
          </div>

          <el-table v-loading="policiesLoading" :data="policies" size="small" empty-text="暂无在线策略">
            <el-table-column label="名称" min-width="180">
              <template #default="{ row }">
                <CellTooltip :content="row.name" />
              </template>
            </el-table-column>
            <el-table-column label="目标 Agent" min-width="150">
              <template #default="{ row }">
                <CellTooltip :content="policyTargetName(row)" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="policyStatusTag(row.status)" size="small" effect="plain">
                  {{ row.status === 'ACTIVE' ? '运行中' : '已暂停' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="间隔" width="90">
              <template #default="{ row }">{{ row.scanIntervalMinutes ?? 5 }} 分钟</template>
            </el-table-column>
            <el-table-column label="下次扫描" width="190">
              <template #default="{ row }">{{ fmtTime(row.nextScanAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="scanPolicy(row)">立即扫描</el-button>
                <el-button link size="small" @click="togglePolicy(row)">
                  {{ row.status === 'ACTIVE' ? '暂停' : '启用' }}
                </el-button>
                <el-button link type="danger" size="small" @click="deletePolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </ListPageShell>

    <el-dialog
      v-model="policyDialogVisible"
      title="新建在线策略"
      width="860px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="policyForm" label-position="top">
        <section class="form-section">
          <div class="section-head">
            <span class="section-title">基础配置</span>
          </div>
          <div class="form-grid">
            <el-form-item label="策略名称" required class="span-6">
              <el-input v-model="policyForm.name" placeholder="例如：客服回答质量巡检" maxlength="256" show-word-limit />
            </el-form-item>
            <el-form-item label="目标 Agent" required class="span-6">
              <el-select v-model="policyForm.targetId" filterable placeholder="选择已接入 Trace 的 Agent" style="width: 100%">
                <el-option
                  v-for="agent in availableAgents"
                  :key="agent.value"
                  :label="agent.label"
                  :value="agent.value"
                />
              </el-select>
              <div v-if="availableAgents.length === 0" class="field-hint">还没有可用的 Agent，请先到 Agent 管理创建 LLM Agent、内置 Agent 或接入外部 Agent。</div>
            </el-form-item>
            <el-form-item label="扫描间隔" class="span-6">
              <el-input-number v-model="policyForm.scanIntervalMinutes" :min="1" :max="1440" controls-position="right" />
              <span class="unit">分钟</span>
            </el-form-item>
          </div>
        </section>

        <section class="form-section evaluator-section">
          <EvaluatorConfigEditor v-model="policyForm.evaluatorConfigs" />
        </section>

        <section class="form-section">
          <div class="section-head">
            <span class="section-title">扫描与告警</span>
          </div>
          <div class="form-grid">
            <el-form-item label="回看小时数" class="span-6">
              <el-input-number v-model="policyForm.lookbackHours" :min="1" :max="720" controls-position="right" />
            </el-form-item>
            <el-form-item label="单次最多扫描" class="span-6">
              <el-input-number v-model="policyForm.limit" :min="1" :max="100" controls-position="right" />
            </el-form-item>
            <el-form-item label="低分阈值" class="span-6">
              <el-input-number v-model="policyForm.lowScoreThreshold" :min="0" :max="100" controls-position="right" />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="policyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="createPolicy">创建策略</el-button>
      </template>
    </el-dialog>

    <TraceDetailDrawer v-model="drawerVisible" :trace-id="activeTraceId" />
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1400px; }
.head-left { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.panel-title { font-size: 14px; font-weight: 600; color: #0f172a; }
.alert { margin-bottom: 12px; }

.monitor-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.filter-grid {
  display: grid; grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 10px 12px; padding: 14px; border-radius: 10px; background: #f8fafc;
}
.span-2 { grid-column: span 2; }
.span-4 { grid-column: span 4; }
.span-6 { grid-column: span 6; }
.filter-field { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.filter-field :deep(.el-select),
.filter-field :deep(.el-date-editor),
.filter-field :deep(.el-input) { width: 100%; }
.filter-label { font-size: 12px; color: #64748b; }
.filter-check { justify-content: flex-end; }
.filter-actions { justify-content: flex-end; }
.filter-actions :deep(.el-button) { width: 100%; }

.metric-strip {
  display: grid; grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px; margin: 14px 0; border: 1px solid #e2e8f0;
  border-radius: 10px; overflow: hidden; background: #e2e8f0;
}
.metric-item { display: flex; flex-direction: column; gap: 6px; padding: 12px 14px; background: #fff; min-width: 0; }
.metric-label { font-size: 12px; color: #64748b; }
.metric-value { font-size: 18px; font-weight: 700; color: #0f766e; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.metric-value em { font-size: 11px; font-weight: 400; color: #94a3b8; font-style: normal; }

.trace-table :deep(.el-table__row) { cursor: pointer; }
.trace-table :deep(.el-table__cell .cell) { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.trace-name { display: flex; align-items: center; gap: 6px; min-width: 0; white-space: nowrap; }
.trace-name :deep(.cell-tooltip) { flex: 1; min-width: 0; font-weight: 600; color: #0f172a; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px; }
.trace-id {
  display: block; color: #475569; cursor: copy;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.trace-id:hover { color: #0f766e; text-decoration: underline; }
.preview { color: #475569; font-size: 12.5px; }
.pager { display: flex; justify-content: flex-end; padding-top: 16px; }

.tab-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.tab-title { font-size: 14px; font-weight: 600; color: #0f172a; }
.tab-sub { margin-left: 8px; font-size: 12px; color: #94a3b8; }

.form-section { padding: 16px 18px; border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc; }
.form-section + .form-section { margin-top: 14px; }
.evaluator-section { background: #fff; }
.section-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 12px; }
.section-title { position: relative; padding-left: 10px; font-size: 14px; font-weight: 600; color: #0f172a; }
.section-title::before {
  content: ""; position: absolute; left: 0; top: 3px; bottom: 3px;
  width: 3px; border-radius: 2px; background: #0f766e;
}
.form-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 0 16px; }
.unit { margin-left: 8px; font-size: 12px; color: #94a3b8; }
.field-hint { margin-top: 2px; font-size: 12px; color: #94a3b8; line-height: 1.6; }

@media (max-width: 1100px) {
  .filter-grid { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  /* 窄屏下日期筛选独占整行，状态 + 类别各半行，合计仍等于单个日期筛选宽度 */
  .span-4 { grid-column: span 6; }
  .span-2 { grid-column: span 3; }
  .metric-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>

