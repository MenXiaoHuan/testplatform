<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTrace, type TraceLogEntry } from '../../api/ai'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'

const STAGE_LABELS: Record<string, string> = {
  REQUEST_RECEIVED: '请求接收',
  SANITIZATION_FAILED: '输入校验失败',
  SESSION_READY: '会话就绪',
  CONTEXT_READY: '上下文就绪',
  CONTEXT_COMPRESSED: '上下文压缩',
  SYSTEM_PROMPT_LOADED: '系统提示加载',
  PROMPT_TOKEN_BUDGET: 'Token预算检查',
  PROMPT_TRUNCATED: '提示截断',
  PROMPT_BUILT: '提示构建完成',
  AGENT_CALL_STARTING: 'Agent调用开始',
  MODEL_CALL_STARTING: '模型调用开始',
  MODEL_CALL_COMPLETED: '模型调用完成',
  MODEL_CALL_FAILED: '模型调用失败',
  TOOL_CALL_STARTING: '工具调用开始',
  TOOL_CALL_COMPLETED: '工具调用完成',
  TOOL_CALL_FAILED: '工具调用失败',
  AGENT_CALL_SUCCESS: 'Agent调用成功',
  AGENT_CALL_FAILED: 'Agent调用失败',
  OUTPUT_PARSED: '输出解析完成',
  OUTPUT_PARSE_FALLBACK: '输出解析兜底',
  REQUEST_COMPLETED: '请求完成',
  UNEXPECTED_ERROR: '未知错误',
}

const STAGE_ORDER = [
  'REQUEST_RECEIVED',
  'SANITIZATION_FAILED',
  'SESSION_READY',
  'CONTEXT_COMPRESSED',
  'CONTEXT_READY',
  'SYSTEM_PROMPT_LOADED',
  'PROMPT_TOKEN_BUDGET',
  'PROMPT_TRUNCATED',
  'PROMPT_BUILT',
  'AGENT_CALL_STARTING',
  'AGENT_CALL_SUCCESS',
  'AGENT_CALL_FAILED',
  'OUTPUT_PARSE_FALLBACK',
  'OUTPUT_PARSED',
  'REQUEST_COMPLETED',
  'UNEXPECTED_ERROR',
]

const REACT_LOOP_STAGES = new Set([
  'MODEL_CALL_STARTING',
  'MODEL_CALL_COMPLETED',
  'MODEL_CALL_FAILED',
  'TOOL_CALL_STARTING',
  'TOOL_CALL_COMPLETED',
  'TOOL_CALL_FAILED',
])

const route = useRoute()
const router = useRouter()

const traceId = computed(() => {
  const raw = route.params.traceId
  return typeof raw === 'string' ? raw : Array.isArray(raw) ? raw[0] ?? '' : ''
})

const entries = ref<TraceLogEntry[]>([])
const loading = ref(false)
const loaded = ref(false)
const expandedIds = ref<Set<string>>(new Set())

function stageLabel(stage?: string | null) {
  if (!stage) return '-'
  return STAGE_LABELS[stage] ?? stage
}

const AGENT_CALL_STARTING_INDEX = STAGE_ORDER.indexOf('AGENT_CALL_STARTING')

function groupKey(stage?: string | null) {
  if (!stage) return 999
  if (REACT_LOOP_STAGES.has(stage)) {
    return AGENT_CALL_STARTING_INDEX + 1
  }
  const idx = STAGE_ORDER.indexOf(stage)
  return idx >= 0 ? idx : 999
}

const filteredEntries = computed(() => {
  return [...entries.value].sort((a, b) => {
    const ga = groupKey(a.stage)
    const gb = groupKey(b.stage)
    if (ga !== gb) return ga - gb
    const ta = a.timestamp ? new Date(a.timestamp).getTime() : 0
    const tb = b.timestamp ? new Date(b.timestamp).getTime() : 0
    return ta - tb
  })
})

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function levelTagType(level?: string | null) {
  switch (level) {
    case 'ERROR':
      return 'danger'
    case 'WARN':
      return 'warning'
    case 'INFO':
      return 'primary'
    case 'DEBUG':
      return 'info'
    default:
      return 'info'
  }
}

function toggleMetadata(id: string) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
}

function isExpanded(id: string) {
  return expandedIds.value.has(id)
}

function formatMetadata(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function hasMetadata(entry: TraceLogEntry) {
  return entry.metadata && Object.keys(entry.metadata).length > 0
}

async function loadTrace() {
  if (!traceId.value) {
    return
  }
  loading.value = true
  try {
    entries.value = await getTrace(traceId.value)
    loaded.value = true
  } catch (error) {
    showAppToast(toErrorMessage(error, 'Trace 加载失败'), 'error')
    entries.value = []
    loaded.value = true
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'home' })
  }
}

onMounted(() => {
  loadTrace()
})
</script>

<template>
  <div class="trace-detail">
    <div class="trace-detail__header">
      <div class="trace-detail__title">
        <span class="trace-detail__label">Agent Trace</span>
        <span class="trace-detail__trace-id" :title="traceId">{{ traceId }}</span>
      </div>
      <el-button link type="primary" class="trace-detail__back" @click="goBack">返回</el-button>
    </div>

    <div v-if="loaded && entries.length === 0 && !loading" class="trace-detail__empty">
      未找到该 trace 的日志记录。
    </div>

    <el-timeline v-loading="loading" class="trace-detail__timeline">
      <el-timeline-item
        v-for="entry in filteredEntries"
        :key="entry.id"
        :timestamp="formatDateTime(entry.timestamp)"
        placement="top"
        :type="entry.level === 'ERROR' ? 'danger' : entry.level === 'WARN' ? 'warning' : 'primary'"
      >
        <div class="trace-entry">
          <div class="trace-entry__head">
            <el-tag :type="levelTagType(entry.level)" size="small">{{ entry.level }}</el-tag>
            <span class="trace-entry__stage">{{ stageLabel(entry.stage) }}</span>
          </div>
          <div class="trace-entry__message">{{ entry.message }}</div>
          <div v-if="hasMetadata(entry)" class="trace-entry__meta">
            <el-button
              link
              type="primary"
              size="small"
              @click="toggleMetadata(entry.id)"
            >
              {{ isExpanded(entry.id) ? '收起元数据' : '查看元数据' }}
            </el-button>
            <pre v-if="isExpanded(entry.id)" class="trace-entry__meta-content"><code>{{ formatMetadata(entry.metadata) }}</code></pre>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.trace-detail {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
}

.trace-detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.trace-detail__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 auto;
  min-width: 0;
}

.trace-detail__label {
  font-size: 16px;
  font-weight: 600;
}

.trace-detail__trace-id {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.trace-detail__back {
  flex: 0 0 auto;
  font-size: 14px;
}

.trace-detail__empty {
  padding: 40px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.trace-detail__timeline {
  margin-top: 8px;
}

.trace-entry {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trace-entry__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-entry__stage {
  font-weight: 600;
  font-size: 14px;
}

.trace-entry__message {
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-primary);
}

.trace-entry__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trace-entry__meta-content {
  margin: 4px 0 0;
  padding: 10px;
  background: var(--el-fill-color-darker);
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
