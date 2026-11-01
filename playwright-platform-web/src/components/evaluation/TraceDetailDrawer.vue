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
        <section class="trace-summary">
          <div class="trace-summary__top">
            <div class="trace-summary__title">
              <h3 class="trace-name">{{ detail.trace.name || '未命名 Trace' }}</h3>
              <el-tag
                :type="detail.trace.success ? 'success' : 'danger'"
                size="small"
                effect="light"
                round
              >
                {{ detail.trace.success ? '成功' : '失败' }}
              </el-tag>
            </div>
            <div class="trace-id">
              <span class="trace-id__label">Trace ID</span>
              <span class="mono trace-id__value">{{ detail.trace.traceId }}</span>
              <el-button link type="primary" size="small" @click="copyTraceId(detail.trace.traceId)">复制</el-button>
            </div>
          </div>

          <div class="trace-stats">
            <div class="trace-stat">
              <span class="trace-stat__label">延迟</span>
              <span class="trace-stat__value">{{ fmtDuration(detail.trace.durationMs) }}</span>
            </div>
            <div class="trace-stat">
              <span class="trace-stat__label">Token 输入 / 输出</span>
              <span class="trace-stat__value">{{ detail.trace.inputTokens ?? 0 }} / {{ detail.trace.outputTokens ?? 0 }}</span>
            </div>
            <div class="trace-stat">
              <span class="trace-stat__label">会话</span>
              <span class="trace-stat__value mono trace-stat__value--truncate" :title="detail.trace.sessionId || ''">
                {{ detail.trace.sessionId || '—' }}
              </span>
            </div>
            <div class="trace-stat">
              <span class="trace-stat__label">开始时间</span>
              <span class="trace-stat__value">{{ fmtTime(detail.trace.startTime) }}</span>
            </div>
          </div>

          <div v-if="(detail.trace.tags || []).length" class="trace-tags">
            <el-tag
              v-for="tag in detail.trace.tags || []"
              :key="tag"
              size="small"
              effect="plain"
              round
              class="tag-item"
            >{{ tag }}</el-tag>
          </div>
        </section>

        <section class="trace-section">
          <div class="trace-section__head"><h4>输入</h4></div>
          <pre class="json-block">{{ prettyJson(detail.trace.input) || '—' }}</pre>
        </section>

        <section class="trace-section">
          <div class="trace-section__head"><h4>输出</h4></div>
          <pre class="json-block">{{ prettyJson(detail.trace.output) || '—' }}</pre>
        </section>

        <section class="trace-section trace-section--chain">
          <div class="trace-section__head">
            <h4>链路</h4>
            <span class="trace-section__count">{{ detail.observations?.length ?? 0 }} 个步骤</span>
          </div>

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
            <div class="obs-detail">
              <div class="obs-detail__head">
                <el-tag :type="obsTypeMeta(selectedObservation.type).tag" size="small" effect="plain">
                  {{ obsTypeMeta(selectedObservation.type).label }}
                </el-tag>
                <span class="obs-detail__name">{{ selectedObservation.name || selectedObservation.observationId }}</span>
                <el-tag
                  :type="selectedObservation.level === 'ERROR' ? 'danger' : 'success'"
                  size="small"
                  effect="light"
                  round
                >{{ selectedObservation.level || 'DEFAULT' }}</el-tag>
              </div>

              <dl class="obs-facts">
                <div class="obs-fact">
                  <dt>模型</dt>
                  <dd>{{ selectedObservation.model || '—' }}</dd>
                </div>
                <div class="obs-fact">
                  <dt>开始</dt>
                  <dd>{{ fmtTime(selectedObservation.startTime) }}</dd>
                </div>
                <div class="obs-fact">
                  <dt>延迟</dt>
                  <dd>{{ fmtDuration(selectedObservation.latencyMs) }}</dd>
                </div>
                <div class="obs-fact">
                  <dt>Token 输入 / 输出 / 合计</dt>
                  <dd>
                    {{ selectedObservation.inputTokens ?? 0 }} /
                    {{ selectedObservation.outputTokens ?? 0 }} /
                    {{ selectedObservation.totalTokens ?? 0 }}
                  </dd>
                </div>
              </dl>

              <div v-if="selectedObservation.statusMessage" class="obs-status">
                {{ selectedObservation.statusMessage }}
              </div>

              <div v-if="selectedObservation.metadata && Object.keys(selectedObservation.metadata).length" class="obs-subsection">
                <span class="obs-subsection__label">元数据</span>
                <pre class="json-block">{{ prettyJson(selectedObservation.metadata) }}</pre>
              </div>

              <div class="obs-subsection">
                <span class="obs-subsection__label">输入</span>
                <pre class="json-block">{{ prettyJson(selectedObservation.input) || '—' }}</pre>
              </div>

              <div class="obs-subsection">
                <span class="obs-subsection__label">输出</span>
                <pre class="json-block">{{ prettyJson(selectedObservation.output) || '—' }}</pre>
              </div>
            </div>
          </template>
        </template>
        </section>
      </template>

      <el-empty v-else description="没有 Trace 数据" :image-size="80" />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-body {
  display: grid;
  gap: 18px;
  padding: 4px 4px 28px;
}

.trace-alert { margin-bottom: 0; }

.trace-summary {
  padding: 18px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 62%);
}

.trace-summary__top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px 20px;
}

.trace-summary__title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.trace-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}

.trace-id {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.trace-id__label {
  font-size: 12px;
  color: #94a3b8;
}

.trace-id__value {
  color: #334155;
  word-break: break-all;
}

.trace-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.trace-stat {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #ffffff;
}

.trace-stat__label {
  font-size: 12px;
  color: #94a3b8;
}

.trace-stat__value {
  font-size: 13.5px;
  font-weight: 600;
  color: #0f172a;
}

.trace-stat__value--truncate {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.trace-section {
  display: grid;
  gap: 10px;
}

.trace-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.trace-section__head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.trace-section__count {
  font-size: 12px;
  color: #94a3b8;
}

.trace-section--chain {
  padding-top: 16px;
  border-top: 1px solid #eef2f7;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
}

.tag-item { margin: 0; }

.json-block {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid #e8edf4;
  border-radius: 10px;
  background: #f8fafc;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
  transition: border-color 160ms ease;
}

.json-block:hover {
  border-color: #cbd5e1;
}

.obs-tree {
  background: transparent;
}

.obs-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 38px;
  padding: 2px 6px;
  border-radius: 8px;
}

.obs-tree :deep(.el-tree-node__content:hover) {
  background: #f1f5f9;
}

.obs-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: rgba(20, 184, 166, 0.1);
}

.obs-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.obs-node__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: #0f172a;
  font-size: 13px;
}

.obs-node__model {
  font-size: 12px;
  color: #64748b;
}

.obs-node__meta {
  margin-left: auto;
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

.obs-detail {
  display: grid;
  gap: 14px;
  margin-top: 8px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #ffffff;
}

.obs-detail__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.obs-detail__name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}

.obs-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 20px;
  margin: 0;
}

.obs-fact {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.obs-fact dt {
  font-size: 12px;
  color: #94a3b8;
}

.obs-fact dd {
  margin: 0;
  font-size: 13px;
  color: #0f172a;
  word-break: break-all;
}

.obs-status {
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12.5px;
  line-height: 1.6;
}

.obs-subsection {
  display: grid;
  gap: 6px;
}

.obs-subsection__label {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 900px) {
  .trace-stats,
  .obs-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
