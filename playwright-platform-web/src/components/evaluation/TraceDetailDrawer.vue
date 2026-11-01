<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { get } from '../../api/http'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import { formatBeijingDateTime } from '../../utils/datetime'

interface TraceRow {
  traceId: string
  name?: string | null
  sessionId?: string | null
  input?: string | null
  output?: string | null
  inputTokens?: number | null
  outputTokens?: number | null
  durationMs?: number | null
  startTime?: string | null
  tags?: string[]
  success?: boolean
}

interface Observation {
  observationId: string
  parentObservationId?: string | null
  type?: string | null
  name?: string | null
  model?: string | null
  input?: string | null
  output?: string | null
  inputTokens?: number | null
  outputTokens?: number | null
  totalTokens?: number | null
  latencyMs?: number | null
  startTime?: string | null
  level?: string | null
  statusMessage?: string | null
  metadata?: Record<string, unknown> | null
}

interface ObservationNode extends Observation {
  children: ObservationNode[]
}

interface TraceDetail {
  trace: TraceRow
  observations: Observation[]
}

const props = defineProps<{
  modelValue: boolean
  traceId?: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const detail = ref<TraceDetail | null>(null)
const observationTree = ref<ObservationNode[]>([])
const selectedObservation = ref<ObservationNode | null>(null)
const errorMsg = ref('')

async function load(traceId: string) {
  loading.value = true
  errorMsg.value = ''
  detail.value = null
  observationTree.value = []
  selectedObservation.value = null
  try {
    const result = await get<TraceDetail>(`/evaluation/online/traces/${encodeURIComponent(traceId)}`)
    detail.value = result
    observationTree.value = buildObservationTree(result?.observations ?? [])
    selectedObservation.value = observationTree.value[0] ?? null
  } catch (e: any) {
    errorMsg.value = toErrorMessage(e, 'Trace 详情加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  if (props.traceId) load(props.traceId)
}

function buildObservationTree(observations: Observation[]): ObservationNode[] {
  const nodes = new Map<string, ObservationNode>()
  for (const observation of observations) {
    nodes.set(observation.observationId, { ...observation, children: [] })
  }
  const roots: ObservationNode[] = []
  for (const observation of observations) {
    const node = nodes.get(observation.observationId)
    if (!node) continue
    const parent = observation.parentObservationId ? nodes.get(observation.parentObservationId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  const sortByStart = (list: ObservationNode[]) => {
    list.sort((a, b) => String(a.startTime ?? '').localeCompare(String(b.startTime ?? '')))
    list.forEach((item) => sortByStart(item.children))
  }
  sortByStart(roots)
  return roots
}

function selectObservation(node: ObservationNode) {
  selectedObservation.value = node
}

function obsTypeMeta(type?: string | null) {
  if (type === 'GENERATION') return { label: 'LLM', tag: 'warning' }
  if (type === 'EVENT') return { label: 'Event', tag: 'success' }
  if (type === 'SPAN') return { label: 'Span', tag: 'info' }
  return { label: type || 'Span', tag: 'info' }
}

function prettyJson(value?: string | Record<string, unknown> | null) {
  if (value == null) return ''
  if (typeof value === 'string') {
    if (!value) return ''
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function fmtTime(value?: string | null) {
  return formatBeijingDateTime(value)
}

function fmtDuration(value?: number | null) {
  if (value == null) return '—'
  if (value < 1000) return `${Math.round(value)}ms`
  return `${(value / 1000).toFixed(2)}s`
}

async function copyTraceId(traceId: string) {
  try {
    await navigator.clipboard.writeText(traceId)
    showAppToast('Trace ID 已复制', 'success')
  } catch {
    showAppToast('复制失败，请手动选择复制', 'error')
  }
}

watch(
  () => [props.modelValue, props.traceId] as const,
  ([open, traceId]) => {
    if (open && traceId) load(String(traceId))
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer v-model="visible" title="Trace 详情" size="62%" destroy-on-close>
    <div v-loading="loading" class="drawer-body">
      <el-alert
        v-if="errorMsg"
        type="error"
        :closable="false"
        show-icon
        title="Trace 暂不可用"
        class="trace-alert"
      >
        {{ errorMsg }}
        <el-button link type="primary" @click="reload">重试</el-button>
      </el-alert>

      <template v-else-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="Trace ID" :span="2">
            <span class="mono">{{ detail.trace.traceId }}</span>
            <el-button link type="primary" size="small" @click="copyTraceId(detail.trace.traceId)">复制</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.trace.name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.trace.success ? 'success' : 'danger'" size="small" effect="plain">
              {{ detail.trace.success ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ fmtTime(detail.trace.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="延迟">{{ fmtDuration(detail.trace.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="Token 输入 / 输出">
            {{ detail.trace.inputTokens ?? 0 }} / {{ detail.trace.outputTokens ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="会话">{{ detail.trace.sessionId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <el-tag
              v-for="tag in detail.trace.tags || []"
              :key="tag"
              size="small"
              effect="plain"
              class="tag-item"
            >{{ tag }}</el-tag>
            <span v-if="!(detail.trace.tags || []).length">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="输入" :span="2">
            <pre class="json-block">{{ prettyJson(detail.trace.input) || '—' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="输出" :span="2">
            <pre class="json-block">{{ prettyJson(detail.trace.output) || '—' }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">
          链路（{{ detail.observations?.length ?? 0 }} 个步骤）
        </el-divider>

        <el-empty
          v-if="!observationTree.length"
          description="这条 trace 没有上报 observation"
          :image-size="80"
        />
        <template v-else>
          <el-tree
            :data="observationTree"
            node-key="observationId"
            default-expand-all
            :expand-on-click-node="false"
            highlight-current
            class="obs-tree"
            @node-click="selectObservation"
          >
            <template #default="{ data }">
              <div class="obs-node">
                <el-tag :type="obsTypeMeta(data.type).tag" size="small" effect="plain">
                  {{ obsTypeMeta(data.type).label }}
                </el-tag>
                <span class="obs-node__name">{{ data.name || data.observationId }}</span>
                <span v-if="data.model" class="obs-node__model">{{ data.model }}</span>
                <span class="obs-node__meta">
                  {{ fmtDuration(data.latencyMs) }}
                  <template v-if="data.totalTokens"> · {{ data.totalTokens }} tokens</template>
                </span>
                <el-tag v-if="data.level === 'ERROR'" type="danger" size="small">ERROR</el-tag>
              </div>
            </template>
          </el-tree>

          <template v-if="selectedObservation">
            <el-divider content-position="left">
              步骤详情 · {{ selectedObservation.name || selectedObservation.observationId }}
            </el-divider>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="类型">{{ obsTypeMeta(selectedObservation.type).label }}</el-descriptions-item>
              <el-descriptions-item label="模型">{{ selectedObservation.model || '—' }}</el-descriptions-item>
              <el-descriptions-item label="开始">{{ fmtTime(selectedObservation.startTime) }}</el-descriptions-item>
              <el-descriptions-item label="延迟">{{ fmtDuration(selectedObservation.latencyMs) }}</el-descriptions-item>
              <el-descriptions-item label="Token 输入 / 输出 / 合计">
                {{ selectedObservation.inputTokens ?? 0 }} /
                {{ selectedObservation.outputTokens ?? 0 }} /
                {{ selectedObservation.totalTokens ?? 0 }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag
                  :type="selectedObservation.level === 'ERROR' ? 'danger' : 'success'"
                  size="small"
                  effect="plain"
                >{{ selectedObservation.level || 'DEFAULT' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="selectedObservation.statusMessage" label="状态信息" :span="2">
                {{ selectedObservation.statusMessage }}
              </el-descriptions-item>
              <el-descriptions-item v-if="selectedObservation.metadata && Object.keys(selectedObservation.metadata).length" label="元数据" :span="2">
                <pre class="json-block">{{ prettyJson(selectedObservation.metadata) }}</pre>
              </el-descriptions-item>
              <el-descriptions-item label="输入" :span="2">
                <pre class="json-block">{{ prettyJson(selectedObservation.input) || '—' }}</pre>
              </el-descriptions-item>
              <el-descriptions-item label="输出" :span="2">
                <pre class="json-block">{{ prettyJson(selectedObservation.output) || '—' }}</pre>
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
      </template>

      <el-empty v-else description="没有 Trace 数据" :image-size="80" />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-body { padding: 0 8px 24px; }
.trace-alert { margin-bottom: 16px; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px; }
.tag-item { margin: 2px 4px 2px 0; }
.json-block {
  margin: 0; padding: 10px 12px; background: #f8fafc; border: 1px solid #e2e8f0;
  border-radius: 8px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px; color: #0f172a; line-height: 1.6;
  white-space: pre-wrap; word-break: break-all; max-height: 320px; overflow: auto;
}
.obs-tree { background: transparent; }
.obs-node { display: flex; align-items: center; gap: 8px; padding: 2px 0; }
.obs-node__name { font-weight: 600; color: #0f172a; font-size: 13px; }
.obs-node__model { font-size: 12px; color: #64748b; }
.obs-node__meta { font-size: 12px; color: #94a3b8; }
</style>
