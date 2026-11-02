<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { del, get, post } from '../../api/http'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import ListPageShell from '../../components/list/ListPageShell.vue'
import CellTooltip from '../../components/list/CellTooltip.vue'
import EvaluatorConfigEditor from '../../components/evaluation/EvaluatorConfigEditor.vue'
import TraceDetailDrawer from '../../components/evaluation/TraceDetailDrawer.vue'
import { formatBeijingDateTime as formatDateTime } from '../../utils/datetime'

const route = useRoute()
const spaceId = computed(() => Number(route.params.spaceId))

const items = ref<any[]>([])
const targets = ref<any[]>([])
const datasets = ref<any[]>([])
const loading = ref(false)
const starting = ref(false)
const startDialogVisible = ref(false)
const page = ref(1)
const size = ref(10)
const pagedItems = computed(() => {
  const start = (page.value - 1) * size.value
  return items.value.slice(start, start + size.value)
})
const pagination = computed(() => ({ page: page.value, size: size.value, total: items.value.length }))

const resultsVisible = ref(false)
const resultsLoading = ref(false)
const results = ref<any[]>([])
const currentRun = ref<any>(null)
const traceDrawerVisible = ref(false)
const activeTraceId = ref<string | null>(null)

const form = reactive({
  targetId: undefined as number | undefined,
  datasetId: undefined as number | undefined,
  evaluatorConfigs: '[{"type":"RULE_MATCH","config":{"keywords":[],"matchAll":false,"scoreOnMatch":100,"pattern":""},"weight":1}]',
  maxConcurrency: 2,
})

const ACTIVE_STATUSES = new Set(['PENDING', 'RUNNING'])

/** 用例范围：默认执行评测集全部用例，也可只执行选中的用例。 */
const sampleMode = ref<'ALL' | 'SELECTED'>('ALL')
const selectedSampleIds = ref<number[]>([])
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const pickerRows = ref<any[]>([])
const pickerTotal = ref(0)
const pickerPage = ref(1)
const pickerSize = ref(10)
const pickerTableRef = ref<any>(null)

function resetSampleSelection() {
  sampleMode.value = 'ALL'
  selectedSampleIds.value = []
  pickerRows.value = []
  pickerTotal.value = 0
  pickerPage.value = 1
  pickerTableRef.value?.clearSelection()
}

watch(() => form.datasetId, () => resetSampleSelection())

async function loadPickerSamples() {
  if (!form.datasetId) return
  pickerLoading.value = true
  try {
    const result = await get<{ items: any[]; total: number }>(
      `/evaluation/datasets/${form.datasetId}/samples`,
      { params: { spaceId: spaceId.value, page: pickerPage.value, size: pickerSize.value } },
    )
    pickerRows.value = result?.items ?? []
    pickerTotal.value = result?.total ?? 0
  } catch (e: any) {
    pickerRows.value = []
    showAppToast(`加载用例失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    pickerLoading.value = false
  }
}

function openSamplePicker() {
  if (!form.datasetId) {
    showAppToast('请先选择评测集', 'warning')
    return
  }
  pickerVisible.value = true
  pickerPage.value = 1
  loadPickerSamples()
}

function handlePickerSelectionChange(rows: any[]) {
  selectedSampleIds.value = rows.map((row) => Number(row.id))
}

function handlePickerPageChange(value: number) {
  pickerPage.value = value
  loadPickerSamples()
}

function clearPickerSelection() {
  pickerTableRef.value?.clearSelection()
  selectedSampleIds.value = []
}

const sampleViewVisible = ref(false)
const sampleViewRow = ref<any>(null)

function openSampleView(row: any) {
  sampleViewRow.value = row
  sampleViewVisible.value = true
}

function sampleKeywordsOf(row: any): string[] {
  if (!row?.keywords) return []
  try {
    const parsed = JSON.parse(String(row.keywords))
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

function handleSampleModeChange(value: any) {
  if (value === 'SELECTED' && selectedSampleIds.value.length === 0) {
    openSamplePicker()
  }
}

/** 选择评测集时应用其推荐评估器（内置集预置了答案抽取规则）。 */
function handleDatasetChange(id: number) {
  const preset = datasets.value.find((d: any) => d.id === id)?.recommendedEvaluators
  if (!preset || !String(preset).trim()) return
  try {
    form.evaluatorConfigs = JSON.stringify(JSON.parse(String(preset)))
    showAppToast('已应用该评测集推荐的评估器，可继续手动调整', 'success')
  } catch {
    form.evaluatorConfigs = String(preset)
  }
}
const RUN_STATUS_LABELS: Record<string, string> = {
  PENDING: '排队中',
  RUNNING: '运行中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

function runStatusTag(status?: string): any {
  switch (status) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'warning'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'CANCELLED': return 'info'
    default: return 'info'
  }
}

function runStatusLabel(status?: string) {
  if (!status) return '—'
  return RUN_STATUS_LABELS[status] ?? status
}

/** 重跑：覆盖原任务记录，沿用原任务固定的用例范围与评估器配置，不会新建任务、也不会重跑评测集全集。 */
async function rerun(row: any) {
  try {
    await ElMessageBox.confirm(
      `将用原配置（${row.totalSamples ?? 0} 条用例）覆盖本次任务并重新执行，上一次的样本结果会被清除。是否继续？`,
      '确认重跑',
      { type: 'warning', confirmButtonText: '重跑', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await post(`/evaluation/runs/${row.id}/rerun`, { triggerUser: 'web-ui' }, { params: { spaceId: spaceId.value } })
    showAppToast('已重跑，正在覆盖原任务结果', 'success')
    if (resultsVisible.value && currentRun.value?.id === row.id) await loadResults(currentRun.value, true)
    await load()
  } catch (e: any) {
    showAppToast(`重跑失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

/** 终止执行中的任务：停止后续用例并把状态置为已取消，已产生的部分结果保留。 */
async function terminateRun(row: any) {
  try {
    await ElMessageBox.confirm(
      '确定终止本次任务？已执行完成的用例结果会保留，未执行的用例不再执行。',
      '确认终止',
      { type: 'warning', confirmButtonText: '终止', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await post(`/evaluation/runs/${row.id}/cancel`, undefined, { params: { spaceId: spaceId.value } })
    showAppToast('已终止任务', 'success')
    if (resultsVisible.value && currentRun.value?.id === row.id) await loadResults(currentRun.value, true)
    await load()
  } catch (e: any) {
    showAppToast(`终止失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

/** 外部 Agent 的离线评测能力由 adapter_config.endpoint 决定；Endpoint 为空时仅在线 Trace。 */
function hasOfflineEndpoint(target: any): boolean {
  let cfg: any = target?.adapterConfig
  if (typeof cfg === 'string') {
    try {
      cfg = JSON.parse(cfg)
    } catch {
      return false
    }
  }
  return typeof cfg?.endpoint === 'string' && cfg.endpoint.trim().length > 0
}

function nameOf(list: any[], id: number | null | undefined): string {
  if (!id) return '-'
  return list.find((item) => item.id === id)?.name ?? `#${id}`
}

async function loadOptions() {
  try {
    const [t, d] = await Promise.all([
      get<any[]>('/evaluation/targets', { params: { spaceId: spaceId.value } }),
      get<any[]>('/evaluation/datasets', { params: { spaceId: spaceId.value } }),
    ])
    // 只展示具备离线调用能力的 Agent：外部 Agent 必须有 Endpoint
    targets.value = (t ?? []).filter((item) => item.targetType !== 'HTTP_AGENT' || hasOfflineEndpoint(item))
    datasets.value = d ?? []
  } catch (e: any) {
    showAppToast(`加载目标/评测集失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

async function load(silent = false) {
  if (!Number.isFinite(spaceId.value)) {
    loading.value = false
    showAppToast('空间 ID 无效，请刷新页面', 'error')
    return
  }
  loading.value = true
  try {
    const list = await get<any[]>('/evaluation/runs', { params: { spaceId: spaceId.value } })
    items.value = list ?? []
  } catch (e: any) {
    if (!silent) showAppToast(`加载失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    loading.value = false
    ensureAutoRefresh()
  }
}

let autoRefreshTimer: ReturnType<typeof setInterval> | null = null
let refreshing = false

function hasActiveRuns() {
  return items.value.some((item) => ACTIVE_STATUSES.has(item.status))
}

function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

/** 有排队/运行中的任务时自动轮询，任务结束后自动停止。 */
function ensureAutoRefresh() {
  if (!hasActiveRuns()) {
    stopAutoRefresh()
    return
  }
  if (autoRefreshTimer) return
  autoRefreshTimer = setInterval(refreshTick, 4000)
}

async function refreshTick() {
  if (refreshing) return
  refreshing = true
  try {
    await load(true)
    if (resultsVisible.value && currentRun.value) {
      const latest = items.value.find((item) => item.id === currentRun.value.id)
      if (latest) currentRun.value = { ...currentRun.value, ...latest }
      await loadResults(currentRun.value, true)
    }
  } finally {
    refreshing = false
  }
}

async function startRun() {
  if (!form.targetId) { showAppToast('请选择 Agent', 'warning'); return }
  if (!form.datasetId) { showAppToast('请选择评测集', 'warning'); return }
  if (sampleMode.value === 'SELECTED' && selectedSampleIds.value.length === 0) {
    showAppToast('请选择至少 1 条用例，或切换为「全部用例」', 'warning')
    return
  }
  starting.value = true
  try {
    await post('/evaluation/runs', {
      spaceId: spaceId.value,
      targetId: form.targetId,
      datasetId: form.datasetId,
      evaluatorConfigs: form.evaluatorConfigs,
      maxConcurrency: form.maxConcurrency,
      triggerUser: 'web-ui',
      sampleIds: sampleMode.value === 'SELECTED' ? selectedSampleIds.value : null,
    })
    showAppToast(
      sampleMode.value === 'SELECTED' ? `已启动评测（${selectedSampleIds.value.length} 条用例）` : '离线评测已启动 — 异步执行中',
      'success',
    )
    startDialogVisible.value = false
    page.value = 1
    await load()
  } catch (e: any) {
    showAppToast(`启动失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally { starting.value = false }
}

function openStartDialog() {
  startDialogVisible.value = true
}

function handlePageChange(value: number) {
  page.value = value
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
}

async function openResults(row: any) {
  currentRun.value = row
  resultsVisible.value = true
  await loadResults(row)
  ensureAutoRefresh()
}

async function loadResults(run = currentRun.value, silent = false) {
  if (!run?.id) return
  resultsLoading.value = true
  try {
    results.value = await get<any[]>(`/evaluation/runs/${run.id}/results`, {
      params: { spaceId: spaceId.value },
    }) ?? []
  } catch (e: any) {
    results.value = []
    if (!silent) showAppToast(`加载样本结果失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    resultsLoading.value = false
  }
}

function resultStatusTag(status?: string) {
  return status === 'SUCCESS' ? 'success' : 'danger'
}

function resultStatusLabel(status?: string) {
  if (!status) return '—'
  return status === 'SUCCESS' ? '成功' : '失败'
}

function durationText(ms?: number | null) {
  if (ms == null) return '—'
  return ms < 1000 ? `${Math.round(ms)}ms` : `${(ms / 1000).toFixed(2)}s`
}

function previewText(value?: string | null, limit = 120) {
  if (!value) return '—'
  const text = String(value).replace(/\s+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

function scoreText(row: any) {
  return row?.weightedScore != null ? Number(row.weightedScore).toFixed(1) : '—'
}

function scoreTagType(row: any) {
  const score = row?.weightedScore
  if (score == null) return 'info'
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'danger'
}

function evaluatorEntries(row: any): any[] {
  if (!row?.evaluatorScores) return []
  try {
    const list = JSON.parse(row.evaluatorScores)
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function evaluatorSummary(row: any) {
  return evaluatorEntries(row)
    .map((item: any) => `${item.type}: ${item.score ?? '—'}${item.reason ? `（${item.reason}）` : ''}`)
    .join('；')
}

function evaluatorChecks(item: any): any[] {
  const checks = item?.metadata?.checks
  return Array.isArray(checks) ? checks : []
}

function evaluatorMismatches(item: any): any[] {
  const mismatches = item?.metadata?.mismatches
  return Array.isArray(mismatches) ? mismatches : []
}

function evaluatorScoreText(item: any) {
  const score = item?.normalizedScore ?? item?.score
  return score != null ? Number(score).toFixed(1) : '—'
}

function openTrace(row: any) {
  if (!row?.langfuseTraceId) return
  activeTraceId.value = row.langfuseTraceId
  traceDrawerVisible.value = true
}

async function removeRun(row: any) {
  try {
    await ElMessageBox.confirm('确定删除本次任务及其全部样本结果？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await del(`/evaluation/runs/${row.id}`, { params: { spaceId: spaceId.value } })
    showAppToast('已删除离线任务', 'success')
    if (currentRun.value?.id === row.id) resultsVisible.value = false
    await load()
  } catch (e: any) {
    showAppToast(`删除失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

onMounted(async () => {
  if (!Number.isFinite(spaceId.value)) {
    showAppToast('空间 ID 无效，请刷新页面', 'error')
    return
  }
  await loadOptions()
  await load()
})

onBeforeUnmount(stopAutoRefresh)
</script>

<template>
  <div class="page">
    <ListPageShell
      :pagination="items.length ? pagination : null"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #header-left>
        <span class="panel-title">离线任务（{{ items.length }}）</span>
      </template>
      <template #header-right>
        <span v-if="hasActiveRuns()" class="auto-refresh-hint">状态自动更新中…</span>
        <el-button size="small" type="primary" @click="openStartDialog">+ 启动评测</el-button>
      </template>

      <el-table
        v-loading="loading"
        :data="pagedItems"
        stripe
        empty-text="暂无离线任务，点击右上角「启动评测」"
        class="run-table"
      >
        <el-table-column label="序号" width="90">
          <template #default="{ $index }">
            <span class="nowrap">{{ (page - 1) * size + $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Agent" min-width="140">
          <template #default="{ row }">
            <CellTooltip :content="nameOf(targets, row.targetId)" />
          </template>
        </el-table-column>
        <el-table-column label="评测集" min-width="190">
          <template #default="{ row }">
            <CellTooltip :content="nameOf(datasets, row.datasetId)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="runStatusTag(row.status)" effect="plain" class="nowrap-tag">
              {{ runStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="100">
          <template #default="{ row }">
            <strong class="score">{{ row.overallScore != null ? row.overallScore : '—' }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <CellTooltip :content="formatDateTime(row.createdAt)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openResults(row)">结果</el-button>
            <!-- 执行中只提供「终止」，结束后回到「重跑」，两个操作均需二次确认 -->
            <el-button
              v-if="ACTIVE_STATUSES.has(row.status)"
              link
              type="warning"
              size="small"
              @click="terminateRun(row)"
            >终止</el-button>
            <el-button
              v-else
              link
              type="primary"
              size="small"
              @click="rerun(row)"
            >重跑</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="ACTIVE_STATUSES.has(row.status)"
              @click="removeRun(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ListPageShell>

    <el-dialog
      v-model="startDialogVisible"
      title="启动离线评测"
      width="900px"
      top="4vh"
      class="app-form-dialog"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="form" label-position="top" class="app-form">
        <section class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot" />
            <div>
              <h4>基础配置</h4>
              <p>同一个 Agent 的离线结果会关联到在线 Trace 的 target_id</p>
            </div>
          </div>
          <div class="app-form-grid">
            <el-form-item label="Agent" required class="app-span-6">
              <el-select v-model="form.targetId" filterable placeholder="选择具备离线调用能力的 Agent" style="width: 100%">
                <el-option v-for="t in targets" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
              <div v-if="targets.length === 0" class="field-hint">还没有可离线调用的 Agent，请先到「Agent 管理」创建。</div>
            </el-form-item>
            <el-form-item label="评测集" required class="app-span-6">
              <el-select v-model="form.datasetId" filterable placeholder="选择评测集" style="width: 100%" @change="handleDatasetChange">
                <el-option
                  v-for="d in datasets"
                  :key="d.id"
                  :label="`${d.name}（${d.version ?? 'v1'} · ${d.totalSamples ?? 0} 条）`"
                  :value="d.id"
                />
              </el-select>
              <div v-if="datasets.length === 0" class="field-hint">还没有评测集，请先到「评测集管理」创建或导入样本。</div>
            </el-form-item>
            <el-form-item label="用例范围" class="app-span-6">
              <div class="sample-mode">
                <el-radio-group v-model="sampleMode" @change="handleSampleModeChange">
                  <el-radio-button value="ALL">全部用例</el-radio-button>
                  <el-radio-button value="SELECTED">指定用例</el-radio-button>
                </el-radio-group>
                <el-button v-if="sampleMode === 'SELECTED'" link type="primary" @click="openSamplePicker">
                  {{ selectedSampleIds.length ? `已选 ${selectedSampleIds.length} 条 · 重新选择` : '选择用例' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item label="并发数" class="app-span-6">
              <el-input-number v-model="form.maxConcurrency" :min="1" :max="16" controls-position="right" />
            </el-form-item>
          </div>
        </section>

        <section class="app-form-section app-form-section--last evaluator-section">
          <EvaluatorConfigEditor v-model="form.evaluatorConfigs" />
        </section>
      </el-form>
      <template #footer>
        <div class="app-form-footer">
          <span class="app-form-hint">启动后可在列表中查看进度与样本明细</span>
          <div>
            <el-button @click="startDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="starting" @click="startRun">启动评测</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="pickerVisible" title="选择要执行的用例" width="820px" :close-on-click-modal="false">
      <div class="picker-head">
        <span>已选 {{ selectedSampleIds.length }} 条 / 共 {{ pickerTotal }} 条（可跨页勾选）</span>
        <el-button link type="primary" :disabled="selectedSampleIds.length === 0" @click="clearPickerSelection">清空选择</el-button>
      </div>
      <el-table
        ref="pickerTableRef"
        v-loading="pickerLoading"
        :data="pickerRows"
        row-key="id"
        height="400"
        class="picker-table"
        @selection-change="handlePickerSelectionChange"
      >
        <el-table-column type="selection" width="44" reserve-selection />
        <el-table-column label="序号" width="90">
          <template #default="{ row }"><span class="nowrap">{{ (row.sampleIndex ?? 0) + 1 }}</span></template>
        </el-table-column>
        <el-table-column label="输入" min-width="300">
          <template #default="{ row }">
            <CellTooltip :content="row.inputText" />
          </template>
        </el-table-column>
        <el-table-column label="参考答案" min-width="200">
          <template #default="{ row }">
            <CellTooltip :content="row.goldenAnswer" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSampleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="picker-pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="pickerTotal"
          :page-size="pickerSize"
          :current-page="pickerPage"
          @current-change="handlePickerPageChange"
        />
      </div>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" @click="pickerVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sampleViewVisible" title="用例详情" width="720px" destroy-on-close>
      <div v-if="sampleViewRow" class="sample-view">
        <div class="sample-view__meta">序号 #{{ (sampleViewRow.sampleIndex ?? 0) + 1 }}</div>
        <div class="sample-field">
          <span class="sample-field__label">输入</span>
          <pre class="sample-field__text">{{ sampleViewRow.inputText || '—' }}</pre>
        </div>
        <div class="sample-field">
          <span class="sample-field__label">参考答案</span>
          <pre class="sample-field__text">{{ sampleViewRow.goldenAnswer || '—' }}</pre>
        </div>
        <div class="sample-field">
          <span class="sample-field__label">判定关键词</span>
          <pre class="sample-field__text">{{ sampleKeywordsOf(sampleViewRow).join('、') || '—' }}</pre>
        </div>
        <div class="sample-field">
          <span class="sample-field__label">参考上下文</span>
          <pre class="sample-field__text">{{ sampleViewRow.referenceContext || '—' }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="sampleViewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    <el-drawer
      v-model="resultsVisible"
      :title="'样本结果明细'"
      size="72%"
      destroy-on-close
    >
      <div v-loading="resultsLoading" class="results-body">
        <el-descriptions :column="3" border size="small" class="run-summary">
          <el-descriptions-item label="状态">
            <el-tag :type="runStatusTag(currentRun?.status)" size="small" effect="plain" class="nowrap-tag">
              {{ runStatusLabel(currentRun?.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">
            {{ (currentRun?.completedSamples ?? 0) + (currentRun?.failedSamples ?? 0) }}
            / {{ currentRun?.totalSamples ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="总分">
            {{ currentRun?.overallScore != null ? currentRun.overallScore : '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="results-toolbar">
          <span>共 {{ results.length }} 条样本结果</span>
          <span v-if="currentRun && ACTIVE_STATUSES.has(currentRun.status)" class="auto-refresh-hint">
            执行中，结果自动更新…
          </span>
        </div>

        <el-table :data="results" stripe empty-text="暂无样本结果，Run 可能仍在执行" class="result-table">
          <el-table-column type="expand" width="36">
            <template #default="{ row }">
              <div class="eval-detail">
                <el-empty
                  v-if="!evaluatorEntries(row).length"
                  description="无评分明细"
                  :image-size="60"
                />
                <div v-for="(item, idx) in evaluatorEntries(row)" :key="idx" class="eval-item">
                  <div class="eval-head">
                    <el-tag size="small" effect="plain">{{ item.type }}</el-tag>
                    <strong class="eval-score">{{ evaluatorScoreText(item) }}</strong>
                    <span class="eval-reason">{{ item.reason }}</span>
                    <span v-if="item.traceFetched" class="eval-fetched">远端 Trace</span>
                  </div>
                  <div v-if="evaluatorChecks(item).length" class="eval-checks">
                    <div
                      v-for="check in evaluatorChecks(item)"
                      :key="check.name"
                      class="eval-check"
                      :class="check.passed ? 'pass' : 'fail'"
                    >
                      <span>{{ check.passed ? '✓' : '✗' }}</span>
                      <code>{{ check.name }}</code>
                      <span>{{ check.detail }}</span>
                    </div>
                  </div>
                  <div v-if="evaluatorMismatches(item).length" class="eval-mismatches">
                    <div class="eval-mismatch-title">快照差异</div>
                    <div
                      v-for="(mismatch, mi) in evaluatorMismatches(item)"
                      :key="mi"
                      class="eval-check fail"
                    >
                      <span>✗</span>
                      <code>#{{ mi + 1 }}</code>
                      <span>期望 {{ mismatch.expected ?? '—' }} / 实际 {{ mismatch.actual ?? '—' }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="序号" width="80">
            <template #default="{ $index }">
              <span class="nowrap">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="输入" min-width="180">
            <template #default="{ row }">
              <CellTooltip :content="row.sampleInputText" :display="previewText(row.sampleInputText)" />
            </template>
          </el-table-column>
          <el-table-column label="Agent 输出" min-width="220">
            <template #default="{ row }">
              <CellTooltip :content="row.agentOutput" :display="previewText(row.agentOutput)" />
            </template>
          </el-table-column>
          <el-table-column label="得分" width="90">
            <template #default="{ row }">
              <el-tooltip v-if="evaluatorSummary(row)" :content="evaluatorSummary(row)" placement="top">
                <el-tag :type="scoreTagType(row)" size="small" effect="plain">{{ scoreText(row) }}</el-tag>
              </el-tooltip>
              <el-tag v-else :type="scoreTagType(row)" size="small" effect="plain">{{ scoreText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ durationText(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column label="Token 入 / 出" width="150">
            <template #default="{ row }">{{ row.tokensIn ?? 0 }} / {{ row.tokensOut ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
                <el-tag :type="resultStatusTag(row.status)" size="small" effect="plain" class="nowrap-tag">
                  {{ resultStatusLabel(row.status) }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else :type="resultStatusTag(row.status)" size="small" effect="plain" class="nowrap-tag">
                {{ resultStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="链路" width="130" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.langfuseTraceId"
                link
                type="primary"
                size="small"
                @click="openTrace(row)"
              >查看 Trace</el-button>
              <span v-else class="muted">无 Trace</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <TraceDetailDrawer v-model="traceDrawerVisible" :trace-id="activeTraceId" />
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1240px; }
.panel-title { font-size: 14px; font-weight: 600; color: #0f172a; }
.auto-refresh-hint { font-size: 12px; color: #0f766e; margin-right: 8px; }
.nowrap { white-space: nowrap; }
.nowrap-tag { white-space: nowrap; }
/* 表头不换行：避免「总分」「Token 入 / 出」等短标题被压成两行 */
.run-table :deep(.el-table__header .cell),
.result-table :deep(.el-table__header .cell) { white-space: nowrap; }
.muted { color: #94a3b8; margin-left: 4px; }
.score { color: #0f766e; }

.evaluator-section { background: #fff; }
.field-hint { margin-top: 2px; font-size: 12px; color: #94a3b8; line-height: 1.6; }
.sample-mode { display: flex; align-items: center; gap: 12px; }
.picker-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 13px; color: #64748b; }
.picker-pager { display: flex; justify-content: flex-end; margin-top: 10px; }
.picker-table :deep(.el-table__header .cell) { white-space: nowrap; }

.sample-view { display: grid; gap: 14px; }
.sample-view__meta { font-size: 12px; color: #64748b; }
.sample-field { display: grid; gap: 6px; }
.sample-field__label { font-size: 12px; font-weight: 600; color: #334155; }
.sample-field__text {
  margin: 0; padding: 10px 12px;
  max-height: 180px; overflow: auto;
  border: 1px solid #e2e8f0; border-radius: 8px;
  background: #f8fafc; color: #0f172a;
  font-family: inherit; font-size: 13px; line-height: 1.7;
  white-space: pre-wrap; word-break: break-word;
}

.results-body { padding: 0 8px 24px; }
.run-summary { margin-bottom: 16px; }
.results-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px; font-size: 13px; color: #64748b;
}

.eval-detail { padding: 8px 16px 12px 44px; background: #f8fafc; }
.eval-item + .eval-item { margin-top: 12px; padding-top: 12px; border-top: 1px dashed #e2e8f0; }
.eval-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.eval-score { color: #0f766e; font-size: 14px; }
.eval-reason { color: #475569; font-size: 12.5px; }
.eval-fetched { color: #2563eb; font-size: 12px; }
.eval-checks { margin-top: 6px; display: flex; flex-direction: column; gap: 3px; }
.eval-check { display: flex; align-items: baseline; gap: 8px; font-size: 12.5px; }
.eval-check code {
  background: #f1f5f9; color: #475569; padding: 0 4px; border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.eval-check.pass span:first-child { color: #16a34a; }
.eval-check.fail span:first-child { color: #dc2626; }
.eval-check.fail { color: #b91c1c; }
.eval-mismatches { margin-top: 8px; }
.eval-mismatch-title { font-size: 12.5px; color: #b45309; font-weight: 600; margin-bottom: 4px; }
</style>
