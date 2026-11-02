<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { get, post, put, del } from '../../api/http'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import ListPageShell from '../../components/list/ListPageShell.vue'
import CellTooltip from '../../components/list/CellTooltip.vue'
import { useAuthStore } from '../../stores/auth'
import { formatBeijingDateTime } from '../../utils/datetime'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const spaceId = computed(() => Number(route.params.spaceId))
/** 内置 Agent 属于平台级能力，仅超管可见/可配置。 */
const isSuperAdmin = computed(() => authStore.isSuperAdmin)

type TargetType = 'BUILTIN_AGENT' | 'LLM_API' | 'HTTP_AGENT'

const TYPE_META: Record<TargetType, { label: string; icon: string; tag: string; desc: string }> = {
  BUILTIN_AGENT: {
    label: '内置 Agent',
    icon: '🤖',
    tag: 'info',
    desc: '直接调用平台自己的 AgentService，适合回归测试易测内置的「知知」智能体。',
  },
  LLM_API: {
    label: 'LLM 接口',
    icon: '🌐',
    tag: 'success',
    desc: '连接任意 OpenAI 兼容接口（DeepSeek / OpenAI / DashScope 等），最常用。',
  },
  HTTP_AGENT: {
    label: '外部 Agent',
    icon: '📡',
    tag: 'warning',
    desc: '填写 Endpoint 可参与离线评测；也可用 Langfuse SDK 接入在线 Trace。两者共用同一个 target_id，Endpoint 留空时仅在线观测。',
  },
}

const EDITABLE_TYPES: TargetType[] = ['LLM_API', 'HTTP_AGENT', 'BUILTIN_AGENT']

const editableTypes = computed<TargetType[]>(() =>
  isSuperAdmin.value
    ? EDITABLE_TYPES
    : EDITABLE_TYPES.filter((type) => type !== 'BUILTIN_AGENT'),
)

interface BuiltinAgentOption {
  key: string
  displayName: string
  description: string
}

interface AgentSummary {
  targetId: number
  name: string
  agentKey: string
  publicKey: string
  status: 'CONNECTED' | 'DISABLED'
  lastSeenAt?: string
  traceCount: number
  createdAt?: string
}

interface AgentCredentials {
  targetId: number
  agentKey: string
  publicKey: string
  secretKey: string
  status: 'CONNECTED' | 'DISABLED'
  ingestHost: string
  sdkEnv: Record<string, string>
}

interface HeaderRow { key: string; value: string }

function defaultForm() {
  return {
    name: '',
    targetType: 'LLM_API' as TargetType,
    status: 'ACTIVE',
    // LLM_API
    baseUrl: '',
    apiKey: '',
    model: '',
    apiPath: '/v1/chat/completions',
    temperature: 0.7,
    maxTokens: undefined as number | undefined,
    // HTTP_AGENT
    endpoint: '',
    method: 'POST',
    headers: [] as HeaderRow[],
    bodyTemplate: '{\n  "input": "{{input}}",\n  "system": "{{system_prompt}}"\n}',
    outputPath: '',
    contentType: 'application/json',
    // HTTP_AGENT 默认开启在线 Trace，同一 target 可同时具备两种能力
    tracingEnabled: true,
    // 高级
    timeoutMs: 120000,
    maxRetries: 2,
    tags: [] as string[],
    // BUILTIN_AGENT
    agentKey: 'assistant',
  }
}

const builtinAgents = ref<BuiltinAgentOption[]>([])

const form = reactive(defaultForm())
const items = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const pagedItems = computed(() => {
  const start = (page.value - 1) * size.value
  return items.value.slice(start, start + size.value)
})
const pagination = computed(() => ({ page: page.value, size: size.value, total: items.value.length }))
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const healthMap = reactive<Record<number, { state: 'checking' | 'ok' | 'warn' | 'error'; message: string }>>({})

// 外部 Agent 接入状态（按 targetId 合并到列表行）
const agentMap = ref<Record<number, AgentSummary>>({})
const accessVisible = ref(false)
const accessMode = ref<'credentials' | 'info'>('info')
const accessTargetName = ref('')
const accessAgent = ref<AgentSummary | null>(null)
const credentials = ref<AgentCredentials | null>(null)
const lastIngestHost = ref('http://localhost:8080/api/evaluation/ingest')

const dialogTitle = computed(() => (editingId.value ? '编辑 Agent' : '新建 Agent'))

const accessTitle = computed(() =>
  accessMode.value === 'credentials'
    ? '接入凭证（仅显示一次）'
    : `接入信息 · ${accessTargetName.value || '外部 Agent'}`,
)

const currentAgent = computed<AgentSummary | null>(() => {
  if (credentials.value) {
    return {
      targetId: credentials.value.targetId,
      name: accessTargetName.value,
      agentKey: credentials.value.agentKey,
      publicKey: credentials.value.publicKey,
      status: credentials.value.status,
      lastSeenAt: accessAgent.value?.lastSeenAt,
      traceCount: accessAgent.value?.traceCount ?? 0,
      createdAt: accessAgent.value?.createdAt,
    }
  }
  return accessAgent.value
})

const sdkEnvText = computed(() => {
  const agent = currentAgent.value
  if (!agent) return ''
  const secret = credentials.value?.secretKey ?? '<已隐藏，请轮换密钥后重新获取>'
  const host = credentials.value?.ingestHost || lastIngestHost.value
  return [
    `LANGFUSE_HOST=${host}`,
    `LANGFUSE_PUBLIC_KEY=${agent.publicKey}`,
    `LANGFUSE_SECRET_KEY=${secret}`,
  ].join('\n')
})

type SampleScheme = 'inherit' | 'returnId'
type SampleLang = 'python' | 'java' | 'ts' | 'js'

const SAMPLE_LANGS: Array<{ value: SampleLang; label: string }> = [
  { value: 'python', label: 'Python' },
  { value: 'java', label: 'Java' },
  { value: 'ts', label: 'TypeScript' },
  { value: 'js', label: 'JavaScript' },
]

const SAMPLE_CODE: Record<SampleScheme, Record<SampleLang, string>> = {
  inherit: {
    python: `# pip install langfuse opentelemetry-api opentelemetry-sdk flask
from flask import Flask, request
from opentelemetry import trace
from opentelemetry.propagate import extract
from langfuse import Langfuse

# 环境变量（平台接入信息里可复制）：
# LANGFUSE_HOST / LANGFUSE_PUBLIC_KEY / LANGFUSE_SECRET_KEY
langfuse = Langfuse()  # 初始化上报；TracerProvider 需连接到平台
tracer = trace.get_tracer(__name__)
app = Flask(__name__)

@app.post("/run")
def run():
    # 关键：解析平台传来的 traceparent，作为父上下文
    parent = extract(dict(request.headers))
    with tracer.start_as_current_span("agent.run", context=parent):
        with tracer.start_as_current_span("retriever"):
            docs = retrieve(request.json["input"])
        with tracer.start_as_current_span("llm"):
            output = answer(docs)
    return {"output": output}`,
    java: `// Maven: io.opentelemetry:opentelemetry-api / opentelemetry-sdk / langfuse
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

@PostMapping("/run")
public Map<String, Object> run(@RequestBody Map<String, Object> body,
                               HttpServletRequest request) {
    // 关键：从 traceparent 解析父上下文
    Context parent = W3CTraceContextPropagator.getInstance()
            .extract(Context.current(), request, new TextMapGetter<HttpServletRequest>() {
                public Iterable<String> keys(HttpServletRequest r) {
                    return Collections.list(r.getHeaderNames());
                }
                public String get(HttpServletRequest r, String key) {
                    return r.getHeader(key);
                }
            });

    Tracer tracer = openTelemetry.getTracer("agent");
    Span span = tracer.spanBuilder("retriever").setParent(parent).startSpan();
    try (Scope scope = span.makeCurrent()) {
        String output = agent.run((String) body.get("input"));
        return Map.of("output", output);
    } finally {
        span.end();
    }
}`,
    ts: `// npm i @opentelemetry/api @opentelemetry/sdk-trace-node langfuse
import { context, propagation, trace } from '@opentelemetry/api'
import { Langfuse } from 'langfuse'

const langfuse = new Langfuse() // 读取 LANGFUSE_* 环境变量
const tracer = trace.getTracer('agent')

app.post('/run', async (req, res) => {
  // 关键：解析 traceparent，得到父上下文
  const parent = propagation.extract(context.active(), req.headers)

  await context.with(parent, async () => {
    await tracer.startActiveSpan('agent.run', async (root) => {
      const docs = await tracer.startActiveSpan('retriever', async (span) => {
        const result = await retrieve(req.body.input)
        span.end()
        return result
      })
      const output = await answer(docs)
      root.end()
      res.json({ output })
    })
  })
})`,
    js: `// npm i @opentelemetry/api @opentelemetry/sdk-trace-node langfuse
const { context, propagation, trace } = require('@opentelemetry/api')
const { Langfuse } = require('langfuse')

const langfuse = new Langfuse() // 读取 LANGFUSE_* 环境变量
const tracer = trace.getTracer('agent')

app.post('/run', async (req, res) => {
  // 关键：解析 traceparent，得到父上下文
  const parent = propagation.extract(context.active(), req.headers)

  await context.with(parent, async () => {
    await tracer.startActiveSpan('agent.run', async (root) => {
      const docs = await retrieve(req.body.input)
      const output = await answer(docs)
      root.end()
      res.json({ output })
    })
  })
})`,
  },
  returnId: {
    python: `# pip install langfuse flask
from flask import Flask, request
from langfuse import Langfuse

langfuse = Langfuse()  # 读取 LANGFUSE_* 环境变量
app = Flask(__name__)

@app.post("/run")
def run():
    payload = request.json
    trace = langfuse.trace(name="agent.run", input=payload["input"])

    span = trace.span(name="retriever")
    docs = retrieve(payload["input"])
    span.end()

    output = answer(docs)
    trace.update(output=output)

    # 关键：把本次 Trace ID 返回给平台，平台会优先使用它
    return {"output": output, "langfuseTraceId": trace.id}`,
    java: `// Maven: com.langfuse:langfuse-java（或对应 Langfuse Java SDK）
@PostMapping("/run")
public Map<String, Object> run(@RequestBody Map<String, Object> body) {
    Trace trace = langfuse.trace(TraceBody.builder()
            .name("agent.run")
            .input(body.get("input"))
            .build());

    Span span = trace.span(SpanBody.builder().name("retriever").build());
    String output = agent.run((String) body.get("input"));
    span.end();

    trace.update(TraceBody.builder().output(output).build());

    // 关键：响应里返回 Trace ID，平台会优先使用它
    return Map.of("output", output, "langfuseTraceId", trace.getId());
}`,
    ts: `// npm i langfuse
import { Langfuse } from 'langfuse'

const langfuse = new Langfuse() // 读取 LANGFUSE_* 环境变量

app.post('/run', async (req, res) => {
  const trace = langfuse.trace({ name: 'agent.run', input: req.body.input })

  const span = trace.span({ name: 'retriever' })
  const docs = await retrieve(req.body.input)
  span.end()

  const output = await answer(docs)
  trace.update({ output })

  // 关键：响应里返回 Trace ID，平台会优先使用它
  res.json({ output, langfuseTraceId: trace.id })
})`,
    js: `// npm i langfuse
const { Langfuse } = require('langfuse')

const langfuse = new Langfuse() // 读取 LANGFUSE_* 环境变量

app.post('/run', async (req, res) => {
  const trace = langfuse.trace({ name: 'agent.run', input: req.body.input })

  const span = trace.span({ name: 'retriever' })
  const docs = await retrieve(req.body.input)
  span.end()

  const output = await answer(docs)
  trace.update({ output })

  // 关键：响应里返回 Trace ID，平台会优先使用它
  res.json({ output, langfuseTraceId: trace.id })
})`,
  },
}

const sampleVisible = ref(false)
const sampleScheme = ref<SampleScheme>('inherit')
const sampleLang = ref<SampleLang>('python')

function sampleCodeOf(scheme: SampleScheme, lang: SampleLang) {
  return SAMPLE_CODE[scheme][lang]
}

function openSampleDialog() {
  sampleVisible.value = true
}

async function loadBuiltinAgents() {
  if (!isSuperAdmin.value) {
    builtinAgents.value = []
    return
  }
  try {
    builtinAgents.value = (await get<BuiltinAgentOption[]>('/evaluation/builtin-agents')) ?? []
  } catch {
    builtinAgents.value = []
  }
}

async function load() {
  if (!Number.isFinite(spaceId.value)) {
    loading.value = false
    showAppToast('空间 ID 无效，请刷新页面', 'error')
    return
  }
  loading.value = true
  try {
    const [list, agents] = await Promise.all([
      get<any[]>('/evaluation/targets', { params: { spaceId: spaceId.value } }),
      get<AgentSummary[]>('/evaluation/agents', { params: { spaceId: spaceId.value } }).catch(() => [] as AgentSummary[]),
    ])
    items.value = list ?? []
    agentMap.value = Object.fromEntries((agents ?? []).map((agent) => [agent.targetId, agent]))
    const maxPage = Math.max(1, Math.ceil(items.value.length / size.value))
    if (page.value > maxPage) page.value = maxPage
  } catch (e: any) {
    showAppToast(`加载失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    loading.value = false
  }
  await loadBuiltinAgents()
}

function parseJson(value: any): Record<string, any> {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(String(value)) ?? {}
  } catch {
    return {}
  }
}

function openDialog(row?: any, presetType?: TargetType) {
  Object.assign(form, defaultForm())
  editingId.value = row?.id ?? null
  if (!row && presetType && editableTypes.value.includes(presetType)) {
    form.targetType = presetType
  }
  if (row) {
    form.name = row.name ?? ''
    form.targetType = (row.targetType ?? 'LLM_API') as TargetType
    form.status = row.status ?? 'ACTIVE'
    const cfg = parseJson(row.adapterConfig)
    if (form.targetType === 'LLM_API') {
      form.baseUrl = cfg.baseUrl ?? ''
      form.apiKey = cfg.apiKey ?? ''
      form.model = cfg.model ?? ''
      form.apiPath = cfg.apiPath ?? '/v1/chat/completions'
      form.temperature = cfg.temperature ?? 0.7
      form.maxTokens = cfg.maxTokens ?? undefined
    } else if (form.targetType === 'HTTP_AGENT') {
      form.endpoint = cfg.endpoint ?? ''
      form.method = cfg.method ?? 'POST'
      form.bodyTemplate = cfg.bodyTemplate ?? defaultForm().bodyTemplate
      form.outputPath = cfg.outputPath ?? ''
      form.contentType = cfg.contentType ?? 'application/json'
      const headers = cfg.headers && typeof cfg.headers === 'object' ? cfg.headers : {}
      form.headers = Object.entries(headers).map(([key, value]) => ({ key, value: String(value) }))
    } else if (form.targetType === 'BUILTIN_AGENT') {
      form.agentKey = cfg.agentKey ?? 'assistant'
    }
    const runtime = parseJson(row.runtimeOptions)
    form.timeoutMs = runtime.timeoutMs ?? 120000
    form.maxRetries = runtime.maxRetries ?? 2
    const tags = parseJson(row.tags)
    form.tags = Array.isArray(tags) ? tags.map(String) : []
    form.tracingEnabled = !!agentInfo(row)
  }
  dialogVisible.value = true
}

function applyTemplate(type: TargetType) {
  Object.assign(form, defaultForm())
  form.targetType = type
  if (type === 'LLM_API') {
    form.name = form.name || 'DeepSeek 聊天接口'
    form.baseUrl = 'https://api.deepseek.com'
    form.model = 'deepseek-chat'
    form.apiKey = 'REF:PLATFORM_DEEPSEEK_API_KEY'
    form.apiPath = '/v1/chat/completions'
    form.temperature = 0.7
    form.maxTokens = 2048
  } else if (type === 'HTTP_AGENT') {
    form.name = form.name || '外部 Agent'
    form.endpoint = 'https://httpbin.org/post'
    form.method = 'POST'
    form.headers = [{ key: 'X-Demo', value: 'eval-platform' }]
    form.bodyTemplate = '{\n  "input": "{{input}}",\n  "system": "{{system_prompt}}"\n}'
    form.outputPath = '$.json.input'
  } else if (type === 'BUILTIN_AGENT') {
    form.name = form.name || '平台内置 Agent'
    form.agentKey = builtinAgents.value[0]?.key ?? 'assistant'
  }
  showAppToast('已填入示例，按需修改后保存', 'success')
}

// ================= 外部 Agent 接入 =================

function agentInfo(row: any): AgentSummary | undefined {
  return row?.id ? agentMap.value[Number(row.id)] : undefined
}

function agentStatus(row: any): 'CONNECTED' | 'DISABLED' {
  return agentInfo(row)?.status ?? 'CONNECTED'
}

function statusTagOf(row: any) {
  return row.status === 'ACTIVE' ? 'success' : 'info'
}

function statusLabelOf(row: any) {
  return row.status === 'ACTIVE' ? '启用' : '停用'
}

/** 外部 Agent 是否具备离线评测能力（有 Endpoint）；LLM / 内置 Agent 始终具备。 */
function canOffline(row: any): boolean {
  if (row?.targetType !== 'HTTP_AGENT') return true
  const cfg = parseJson(row.adapterConfig)
  return typeof cfg.endpoint === 'string' && cfg.endpoint.trim().length > 0
}

function isTraceOnly(row: any): boolean {
  return row?.targetType === 'HTTP_AGENT' && !canOffline(row)
}

function fmtTime(value?: string) {
  return formatBeijingDateTime(value)
}

function openAgentInfo(row: any) {
  accessMode.value = 'info'
  accessTargetName.value = row?.name ?? ''
  accessAgent.value = agentInfo(row) ?? null
  credentials.value = null
  accessVisible.value = true
}

async function rotateAgent(row: any) {
  const targetId = Number(row?.id ?? row?.targetId)
  if (!targetId) return
  try {
    await ElMessageBox.confirm(
      `轮换后旧密钥立即失效，Agent 需要更新 LANGFUSE_SECRET_KEY 才能继续上报。确认轮换「${row.name ?? accessTargetName.value}」？`,
      '确认轮换密钥',
      { type: 'warning', confirmButtonText: '轮换', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const result = await post<AgentCredentials>(`/evaluation/agents/${targetId}/rotate`, undefined, {
      params: { spaceId: spaceId.value },
    })
    credentials.value = result
    accessTargetName.value = row.name ?? accessTargetName.value
    accessMode.value = 'credentials'
    accessVisible.value = true
    lastIngestHost.value = result.ingestHost || lastIngestHost.value
    await load()
    accessAgent.value = agentInfo({ id: targetId }) ?? accessAgent.value
    showAppToast('已轮换密钥，旧密钥立即失效', 'success')
  } catch (e: any) {
    showAppToast(`轮换失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

/** 给已有外部 Agent 挂载在线 Trace，返回一次性凭证。 */
async function attachTracing(row: any, targetName?: string) {
  const targetId = Number(row?.id)
  if (!targetId) return null
  try {
    const result = await post<AgentCredentials>(`/evaluation/agents/${targetId}/connection`, undefined, {
      params: { spaceId: spaceId.value },
    })
    accessTargetName.value = targetName ?? row.name ?? ''
    accessAgent.value = agentInfo({ id: targetId }) ?? null
    credentials.value = result
    accessMode.value = 'credentials'
    accessVisible.value = true
    lastIngestHost.value = result.ingestHost || lastIngestHost.value
    await load()
    accessAgent.value = agentInfo({ id: targetId }) ?? accessAgent.value
    showAppToast('已接入在线 Trace，Secret 只显示一次', 'success')
    return result
  } catch (e: any) {
    showAppToast(`接入 Trace 失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
    return null
  }
}

function handleTraceCommand(command: string, row: any) {
  if (command === 'info') openAgentInfo(row)
  else if (command === 'rotate') rotateAgent(row)
  else if (command === 'toggle') toggleAgentStatus(row)
  else if (command === 'monitor') openMonitor(row)
}

function traceTooltip(row: any) {
  const info = agentInfo(row)
  if (!info) return ''
  return `${info.agentKey} · 最近上报 ${fmtTime(info.lastSeenAt)} · 累计 ${info.traceCount} 条 trace`
}

async function rotateCurrentAgent() {
  if (!currentAgent.value) return
  await rotateAgent({
    id: currentAgent.value.targetId,
    name: accessTargetName.value,
  })
}

async function toggleAgentStatus(row: any) {
  const info = agentInfo(row)
  if (!info) {
    showAppToast('接入信息未加载，请刷新后重试', 'warning')
    return
  }
  const next = info.status === 'CONNECTED' ? 'DISABLED' : 'CONNECTED'
  const action = next === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      next === 'DISABLED'
        ? `停用后 Agent 的上报请求会被拒绝（403），历史 trace 不受影响。确认停用「${row.name}」？`
        : `启用后 Agent 可以继续上报 trace。确认启用「${row.name}」？`,
      `确认${action}`,
      { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await post(`/evaluation/agents/${row.id}/status`, undefined, {
      params: { spaceId: spaceId.value, status: next },
    })
    await load()
    showAppToast(`已${action}`, 'success')
  } catch (e: any) {
    showAppToast(`${action}失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

function openMonitor(row: any) {
  router.push({
    path: `/spaces/${spaceId.value}/evaluation/monitor`,
    query: { targetId: String(row.id) },
  })
}

async function copyText(text: string, label = '内容') {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    showAppToast(`${label}已复制`, 'success')
  } catch {
    showAppToast('复制失败，请手动选择复制', 'error')
  }
}

function headersToObject(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const h of form.headers) {
    if (h.key.trim()) result[h.key.trim()] = h.value
  }
  return result
}

function buildAdapterConfig(): Record<string, any> {
  if (form.targetType === 'LLM_API') {
    const cfg: Record<string, any> = {
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      model: form.model.trim(),
      apiPath: form.apiPath.trim() || '/v1/chat/completions',
      temperature: form.temperature,
    }
    if (form.maxTokens != null && form.maxTokens > 0) cfg.maxTokens = form.maxTokens
    return cfg
  }
  if (form.targetType === 'HTTP_AGENT') {
    return {
      endpoint: form.endpoint.trim(),
      method: form.method,
      headers: headersToObject(),
      bodyTemplate: form.bodyTemplate,
      outputPath: form.outputPath.trim(),
      contentType: form.contentType,
    }
  }
  return { spaceId: spaceId.value, agentKey: form.agentKey || 'assistant' }
}

function validateForm(): string | null {
  if (!form.name.trim()) return '请填写目标名称'
  if (form.targetType === 'LLM_API') {
    if (!form.baseUrl.trim()) return '请填写 Base URL'
    if (!/^https?:\/\//i.test(form.baseUrl.trim())) return 'Base URL 需要以 http:// 或 https:// 开头'
    if (!form.model.trim()) return '请填写模型名（如 deepseek-chat）'
    if (!form.apiKey.trim()) return '请填写 API Key，或使用 REF:环境变量名'
  }
  if (form.targetType === 'HTTP_AGENT') {
    if (form.endpoint.trim() && !/^https?:\/\//i.test(form.endpoint.trim())) {
      return 'Endpoint 需要以 http:// 或 https:// 开头'
    }
    if (!form.endpoint.trim() && !form.tracingEnabled) {
      return '外部 Agent 至少需要一种能力：填写离线评测 Endpoint，或开启在线 Trace'
    }
  }
  return null
}

function buildPayload() {
  const runtime: Record<string, any> = {}
  if (form.timeoutMs > 0) runtime.timeoutMs = form.timeoutMs
  if (form.maxRetries >= 0) runtime.maxRetries = form.maxRetries
  return {
    spaceId: spaceId.value,
    name: form.name.trim(),
    targetType: form.targetType,
    status: form.status,
    adapterConfig: JSON.stringify(buildAdapterConfig()),
    runtimeOptions: JSON.stringify(runtime),
    tags: JSON.stringify(form.tags),
  }
}

async function submit() {
  const error = validateForm()
  if (error) {
    showAppToast(error, 'warning')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const wasEditing = editingId.value != null
    const saved = editingId.value
      ? await put<any>(`/evaluation/targets/${editingId.value}`, payload)
      : await post<any>('/evaluation/targets', payload)
    const targetId = saved?.id ?? editingId.value
    const targetName = form.name.trim()
    const hasOfflineEndpoint = form.targetType !== 'HTTP_AGENT' || !!form.endpoint.trim()
    if (!wasEditing) page.value = 1
    dialogVisible.value = false
    await load()
    showAppToast(
      wasEditing
        ? '已保存'
        : hasOfflineEndpoint
          ? '已创建，正在检测连接…'
          : '已创建，等待 Agent 上报在线 Trace…',
      'success',
    )
    if (targetId) {
      if (hasOfflineEndpoint) await runHealthCheck(Number(targetId), false)
      // 新建/编辑外部 Agent 时可选同时挂载在线 Trace
      if (form.targetType === 'HTTP_AGENT' && form.tracingEnabled
          && !agentInfo({ id: Number(targetId) })) {
        await attachTracing({ id: Number(targetId), name: targetName }, targetName)
      }
    }
  } catch (e: any) {
    showAppToast(`保存失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    saving.value = false
  }
}

async function runHealthCheck(id: number, notify = true) {
  healthMap[id] = { state: 'checking', message: '检测中…' }
  try {
    const result = await post<any>(`/evaluation/targets/${id}/health-check`)
    const status = result?.status ?? 'UNKNOWN'
    const ok = status === 'REACHABLE'
    const warn = status === 'AUTH_FAILED' || status === 'UNAVAILABLE'
    healthMap[id] = {
      state: ok ? 'ok' : warn ? 'warn' : 'error',
      message: `${status}${result?.latencyMs ? ` · ${result.latencyMs}ms` : ''} · ${result?.message ?? ''}`,
    }
    if (notify) showAppToast(healthMap[id].message, ok ? 'success' : 'warning')
  } catch (e: any) {
    healthMap[id] = { state: 'error', message: toErrorMessage(e, '操作失败，请稍后重试') }
    if (notify) showAppToast(`检测失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

async function remove(row: any) {
  const hasTrace = !!agentInfo(row)
  const message = hasTrace
    ? `确定删除「${row.name}」？删除后离线评测与在线 Trace 接入密钥同时失效，历史 trace 仍保留在 Langfuse。`
    : `确定删除目标「${row.name}」？删除后关联的评测任务将无法再使用它。`
  try {
    await ElMessageBox.confirm(message, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await del(`/evaluation/targets/${row.id}`)
    showAppToast('已删除', 'success')
    await load()
  } catch (e: any) {
    showAppToast(`删除失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

function summarize(row: any): string {
  const cfg = parseJson(row.adapterConfig)
  switch (row.targetType as TargetType) {
    case 'LLM_API':
      return [cfg.model, cfg.baseUrl].filter(Boolean).join(' · ') || '未配置'
    case 'HTTP_AGENT': {
      if (cfg.endpoint) return [cfg.method ?? 'POST', cfg.endpoint].filter(Boolean).join(' ')
      const info = agentInfo(row)
      if (!info) return '仅在线 Trace（尚未接入）'
      return `${info.agentKey} · 最近上报 ${info.lastSeenAt ? fmtTime(info.lastSeenAt) : '暂无'} · 累计 ${info.traceCount} 条 trace`
    }
    case 'BUILTIN_AGENT':
      return `平台内置 Agent · ${cfg.agentKey ?? 'assistant'}`
    default:
      return '-'
  }
}

function metaOf(type: string) {
  return TYPE_META[type as TargetType] ?? { label: type, icon: '', tag: 'info', desc: '' }
}

function tagList(row: any): string[] {
  const tags = parseJson(row.tags)
  return Array.isArray(tags) ? tags.map(String) : []
}

function handlePageChange(value: number) {
  page.value = value
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
}

onMounted(load)
</script>

<template>
  <ListPageShell
    :pagination="pagination"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #header-right>
      <el-button type="primary" @click="openDialog()">新增 Agent</el-button>
    </template>

    <el-table class="list-table" :data="pagedItems" :loading="loading" empty-text="暂无 Agent">
      <el-table-column label="名称" min-width="180">
        <template #default="{ row }">
          <CellTooltip :content="row.name" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="150">
        <template #default="{ row }">
          <el-tag :type="metaOf(row.targetType).tag" size="small">
            {{ metaOf(row.targetType).icon }} {{ metaOf(row.targetType).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagOf(row)" size="small" effect="plain">
            {{ statusLabelOf(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="能力" width="140">
        <template #default="{ row }">
          <div class="capability-line">
            <el-tag v-if="canOffline(row)" type="success" size="small" effect="plain">可离线</el-tag>
            <el-tag v-else-if="isTraceOnly(row)" type="info" size="small" effect="plain">仅在线</el-tag>
            <el-tooltip v-if="agentInfo(row)" :content="traceTooltip(row)" placement="top">
              <el-tag :type="agentStatus(row) === 'CONNECTED' ? 'primary' : 'info'" size="small" effect="plain">
                {{ agentStatus(row) === 'CONNECTED' ? 'Trace' : 'Trace 已停用' }}
              </el-tag>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="连接配置" min-width="220">
        <template #default="{ row }">
          <CellTooltip :content="summarize(row)" mono />
          <div v-if="tagList(row).length" class="tags-line">
            <el-tag v-for="tag in tagList(row)" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right" class-name="action-cell">
        <template #default="{ row }">
          <template v-if="agentInfo(row)">
            <el-button v-if="canOffline(row)" link size="small" @click="runHealthCheck(row.id)">测试</el-button>
            <el-button link size="small" @click="openDialog(row)">编辑</el-button>
            <el-dropdown trigger="click" @command="handleTraceCommand($event, row)">
              <el-button link type="primary" size="small" class="trace-command">Trace ▾</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="info">接入信息</el-dropdown-item>
                  <el-dropdown-item command="rotate">轮换密钥</el-dropdown-item>
                  <el-dropdown-item command="toggle">
                    {{ agentStatus(row) === 'CONNECTED' ? '停用上报' : '启用上报' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="monitor">查看监控</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
          <template v-else>
            <el-button v-if="canOffline(row)" link size="small" @click="runHealthCheck(row.id)">测试</el-button>
            <el-button link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button
              v-if="row.targetType === 'HTTP_AGENT'"
              link
              type="primary"
              size="small"
              @click="attachTracing(row, row.name)"
            >接入 Trace</el-button>
            <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- ============ 创建 / 编辑 ============ -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="900px"
      top="4vh"
      class="app-form-dialog"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="form" label-position="top" class="app-form" autocomplete="off">
        <section class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot" />
            <div>
              <h4>基础信息</h4>
              <p>Agent 名称、类型与启用状态</p>
            </div>
            <el-button class="app-section-action" link type="primary" @click="applyTemplate(form.targetType)">一键填入示例配置</el-button>
          </div>
          <div class="app-form-grid">
            <el-form-item label="Agent 名称" required class="app-span-8">
              <el-input v-model="form.name" placeholder="例如：DeepSeek 客服模型 v3" maxlength="256" show-word-limit />
            </el-form-item>
            <el-form-item label="启用状态" class="app-span-4">
              <el-switch
                v-model="form.status"
                active-value="ACTIVE"
                inactive-value="INACTIVE"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
            <el-form-item label="Agent 类型" required class="app-span-12">
              <div class="type-picker">
                <div
                  v-for="type in editableTypes"
                  :key="type"
                  class="type-option"
                  :class="{ 'type-option--active': form.targetType === type }"
                  @click="form.targetType = type"
                >
                  <span class="type-option__icon">{{ TYPE_META[type].icon }}</span>
                  <span class="type-option__label">{{ TYPE_META[type].label }}</span>
                </div>
              </div>
            </el-form-item>
            <el-form-item v-if="form.targetType === 'HTTP_AGENT'" label="在线 Trace" class="app-span-12">
              <div class="switch-line">
                <el-switch
                  v-model="form.tracingEnabled"
                  :disabled="editingId != null && !!agentInfo({ id: editingId })"
                  active-text="接入"
                  inactive-text="关闭"
                />
                <span class="switch-desc">
                  开启后生成 Langfuse 密钥；Endpoint 留空时仅接收在线 Trace。
                </span>
              </div>
            </el-form-item>
          </div>
        </section>

        <!-- ---------- LLM_API ---------- -->
        <section v-if="form.targetType === 'LLM_API'" class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot app-section-dot--blue" />
            <div>
              <h4>LLM 连接配置</h4>
              <p>OpenAI 兼容接口的地址、模型与密钥</p>
            </div>
          </div>
          <div class="app-form-grid">
            <el-form-item label="Base URL" required class="app-span-8">
              <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
              <div class="field-hint">只填域名或前缀，不要带接口路径。</div>
            </el-form-item>
            <el-form-item label="模型名" required class="app-span-4">
              <el-input v-model="form.model" placeholder="deepseek-chat" autocomplete="off" />
            </el-form-item>
            <el-form-item label="API Key" required class="app-span-6">
              <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-... 或 REF:MY_API_KEY" autocomplete="new-password" />
              <div class="field-hint">推荐使用 <code>REF:环境变量名</code>，避免明文入库。</div>
            </el-form-item>
            <el-form-item label="请求路径" class="app-span-6">
              <el-input v-model="form.apiPath" placeholder="/v1/chat/completions" />
            </el-form-item>
            <el-form-item label="Temperature" class="app-span-6">
              <div class="range-line">
                <el-slider v-model="form.temperature" :min="0" :max="1" :step="0.05" />
                <el-input-number
                  v-model="form.temperature"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :precision="2"
                  controls-position="right"
                  class="range-line__input"
                />
              </div>
              <div class="field-hint">数值越高越发散，回归测试建议 0.1 ~ 0.3。</div>
            </el-form-item>
            <el-form-item label="Max Tokens" class="app-span-6">
              <el-input-number
                v-model="form.maxTokens"
                :min="1"
                :max="128000"
                :step="256"
                controls-position="right"
                style="width: 100%"
              />
              <div class="field-hint">留空使用服务端默认值。</div>
            </el-form-item>
          </div>
        </section>

        <!-- ---------- HTTP_AGENT ---------- -->
        <section v-else-if="form.targetType === 'HTTP_AGENT'" class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot app-section-dot--amber" />
            <div>
              <h4>外部 Agent 配置</h4>
              <p>Endpoint、鉴权头与请求体模板</p>
            </div>
          </div>
          <div class="app-form-grid">
            <el-form-item label="离线评测 Endpoint" class="app-span-12">
              <el-input v-model="form.endpoint" placeholder="https://your-agent.example.com/chat（留空则仅在线 Trace）" />
              <div class="field-hint">
                填写后可离线评测；留空仅接收在线 Trace。保存时会做 SSRF 校验。
              </div>
            </el-form-item>
            <el-form-item label="请求方法" class="app-span-4">
              <el-radio-group v-model="form.method">
                <el-radio-button value="POST">POST</el-radio-button>
                <el-radio-button value="PUT">PUT</el-radio-button>
                <el-radio-button value="GET">GET</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="答案 JSONPath" class="app-span-8">
              <el-input v-model="form.outputPath" placeholder="$.data.answer" />
              <div class="field-hint">留空则把完整响应作为答案。</div>
            </el-form-item>
            <el-form-item label="请求头" class="app-span-12">
              <div class="header-editor">
                <div v-for="(h, i) in form.headers" :key="i" class="header-row">
                  <el-input v-model="h.key" placeholder="Header 名，如 X-Auth" />
                  <el-input v-model="h.value" placeholder="值，支持 REF:ENV" />
                  <el-button link type="danger" @click="form.headers.splice(i, 1)">删除</el-button>
                </div>
                <el-button link type="primary" @click="form.headers.push({ key: '', value: '' })">+ 添加 Header</el-button>
              </div>
            </el-form-item>
            <el-form-item label="请求体模板" class="app-span-12">
              <el-input v-model="form.bodyTemplate" type="textarea" :rows="5" />
              <div class="field-hint">
                支持 <code v-pre>{{input}}</code> 和 <code v-pre>{{system_prompt}}</code>，JSON 内容会自动转义。
              </div>
            </el-form-item>
          </div>
        </section>

        <!-- ---------- BUILTIN_AGENT ---------- -->
        <section v-else-if="form.targetType === 'BUILTIN_AGENT'" class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot app-section-dot--green" />
            <div>
              <h4>内置 Agent</h4>
              <p>直接调用平台内置智能体参与评测</p>
            </div>
          </div>
          <div class="app-form-grid">
            <el-form-item label="Agent 选择" required class="app-span-12">
              <el-select v-model="form.agentKey" placeholder="请选择内置 Agent" style="width: 100%">
                <el-option
                  v-for="agent in builtinAgents"
                  :key="agent.key"
                  :label="agent.displayName"
                  :value="agent.key"
                >
                  <div class="builtin-agent-option">
                    <span class="builtin-agent-option__name">{{ agent.displayName }}</span>
                    <span class="builtin-agent-option__desc">{{ agent.description }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </div>
          <el-alert
            type="success"
            :closable="false"
            show-icon
            title="内置 Agent 会写入平台统一链路"
            description="评测时直接调用所选平台内置 Agent；对话与在线 Trace 数据统一写入 Langfuse。"
          />
        </section>

        <section class="app-form-section app-form-section--last">
          <div class="app-section-head">
            <span class="app-section-dot" />
            <div>
              <h4>高级选项</h4>
              <p>超时与失败重试策略</p>
            </div>
          </div>
          <div class="app-form-grid">
            <el-form-item label="单样本超时（ms）" class="app-span-6">
              <el-input-number
                v-model="form.timeoutMs"
                :min="1000"
                :max="600000"
                :step="1000"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="失败重试次数" class="app-span-6">
              <el-input-number v-model="form.maxRetries" :min="0" :max="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <template #footer>
        <div class="app-form-footer">
          <span class="app-form-hint">保存后可直接用于离线评测与在线 Trace 观测</span>
          <div>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ============ 外部 Agent 接入 ============ -->
    <el-dialog
      v-model="accessVisible"
      :title="accessTitle"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        v-if="!currentAgent"
        type="warning"
        :closable="false"
        show-icon
        title="接入信息尚未加载，请关闭后刷新页面重试"
      />
      <template v-else>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Agent">
              {{ accessTargetName }} <span class="mono">#{{ currentAgent.targetId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="接入状态">
              <el-tag :type="currentAgent.status === 'CONNECTED' ? 'success' : 'info'" size="small" effect="plain">
                {{ currentAgent.status === 'CONNECTED' ? '正常接入' : '已停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Agent Key">
              <span class="mono">{{ currentAgent.agentKey }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="Public Key">
              <span class="mono">{{ currentAgent.publicKey }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="credentials" label="Secret Key" :span="2">
              <span class="mono secret-key">{{ credentials.secretKey }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="最近上报">{{ fmtTime(currentAgent.lastSeenAt) }}</el-descriptions-item>
            <el-descriptions-item label="累计 Trace">{{ currentAgent.traceCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="credentials"
            type="warning"
            :closable="false"
            show-icon
            class="secret-alert"
            title="Secret 只显示这一次，关闭后无法再次查看；旧密钥已立即失效。"
          />

          <div class="env-block">
            <div class="env-head">
              <span>Agent 侧 SDK 环境变量</span>
              <el-button v-if="credentials" link type="primary" @click="copyText(sdkEnvText, '环境变量')">复制</el-button>
              <span v-else class="env-hint">Secret 已隐藏，轮换后可重新获取</span>
            </div>
            <pre>{{ sdkEnvText }}</pre>
          </div>
          <div class="attach-tips">
            <div class="attach-tips__head">
              <span>接入说明</span>
              <el-button link type="primary" size="small" @click="openSampleDialog">查看接入示例</el-button>
            </div>
            <ol class="attach-tips__list">
              <li>把上方环境变量配置到 Agent 运行环境，重启后 SDK 生效。</li>
              <li>
                SDK 会把 Span 上报到
                <code>{{ credentials?.ingestHost || lastIngestHost }}/api/public/ingestion</code>；
                使用 OTLP 时需设置 <code>OTEL_EXPORTER_OTLP_PROTOCOL=http/json</code>（当前不支持 protobuf）。
              </li>
              <li>
                Agent 与平台不在同一台机器时，请把 <code>localhost</code> 替换为平台可访问的域名或 IP。
              </li>
              <li>
                离线评测请求会带 <code>traceparent</code> / <code>X-Eval-Trace-Id</code>；
                Agent 侧需以该上下文为父级创建 Span（或返回自己的 <code>langfuseTraceId</code>），平台才能读取内部步骤。
              </li>
            </ol>
          </div>
      </template>

      <template #footer>
        <el-button v-if="currentAgent" @click="rotateCurrentAgent">轮换密钥</el-button>
        <el-button type="primary" @click="accessVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sampleVisible"
      title="接入示例"
      width="860px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="sample-alert"
        title="示例仅演示链路接入方式；SDK 从环境变量读取 LANGFUSE_HOST / LANGFUSE_PUBLIC_KEY / LANGFUSE_SECRET_KEY，请按实际 SDK 版本调整细节。"
      />

      <el-radio-group v-model="sampleScheme" class="sample-scheme">
        <el-radio-button value="inherit">方案 A · 继承 traceparent（推荐）</el-radio-button>
        <el-radio-button value="returnId">方案 B · 返回 traceId</el-radio-button>
      </el-radio-group>

      <p class="sample-desc">
        <template v-if="sampleScheme === 'inherit'">
          从请求头解析 <code>traceparent</code>，把它作为父上下文创建 Agent 内部 Span，链路会挂在平台这次评测的 Trace 下，一条链路即可看到全部步骤。
        </template>
        <template v-else>
          不继承 <code>traceparent</code>，由 Agent 自建 Trace，并在响应 JSON 中返回 <code>langfuseTraceId</code>；平台会优先使用该 ID 读取内部步骤（平台边界记录与 Agent 链路分成两条）。
        </template>
      </p>

      <el-tabs v-model="sampleLang" class="sample-tabs">
        <el-tab-pane v-for="lang in SAMPLE_LANGS" :key="lang.value" :label="lang.label" :name="lang.value">
          <div class="code-block">
            <div class="code-block__head">
              <span>{{ lang.label }} 示例</span>
              <el-button link type="primary" size="small" @click="copyText(sampleCodeOf(sampleScheme, lang.value), '示例代码')">复制</el-button>
            </div>
            <pre class="code-sample">{{ sampleCodeOf(sampleScheme, lang.value) }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button type="primary" @click="sampleVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </ListPageShell>
</template>

<style scoped>
.capability-line { display: flex; gap: 4px; flex-wrap: wrap; }
.trace-command { padding: 0; }
/* 操作列：按钮间距加大、外框收窄，避免按钮紧贴或与相邻列挤压 */
.list-table :deep(.action-cell .cell) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 8px;
}
.list-table :deep(.action-cell .el-button) { margin-left: 0; padding: 0; }
.list-table :deep(.action-cell .el-dropdown) { margin-left: 0; }
.list-table :deep(.el-table__header .cell) { white-space: nowrap; }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px; color: #475569; }
.tags-line { display: flex; gap: 4px; margin-top: 4px; flex-wrap: wrap; }

.type-picker { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; width: 100%; }
.type-option {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 9px 10px; border: 1px solid #e2e8f0; border-radius: 8px; cursor: pointer;
  font-size: 13px; color: #475569; background: #fff; transition: all 0.15s;
}
.type-option:hover { border-color: #99f6e4; }
.type-option--active { border-color: #0f766e; background: #ecfdf5; color: #0f766e; font-weight: 600; }
.type-option__icon { font-size: 15px; }

.switch-line { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.switch-desc { font-size: 12px; color: #64748b; line-height: 1.6; }
.range-line { display: flex; align-items: center; gap: 12px; width: 100%; }
.range-line :deep(.el-slider) { flex: 1; min-width: 0; }
.range-line__input { flex: 0 0 110px; width: 110px; }

.field-hint { font-size: 12px; color: #94a3b8; line-height: 1.6; margin-top: 2px; }
.field-hint code { background: #f1f5f9; color: #0f766e; padding: 0 4px; border-radius: 4px; }
.header-editor { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.header-row { display: flex; gap: 8px; align-items: center; }

.secret-alert { margin-top: 16px; }
.secret-key { color: #b45309; font-weight: 600; }
.env-block { margin-top: 16px; border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; background: #f8fafc; }
.env-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #f1f5f9; font-size: 13px; color: #334155;
}
.env-hint { font-size: 12px; color: #94a3b8; }
.env-block pre {
  margin: 0; padding: 12px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px; color: #0f172a; line-height: 1.7; white-space: pre-wrap; word-break: break-all;
}

/* 接入说明：分条列出，避免大段灰色提示难以扫读 */
.attach-tips {
  margin-top: 14px; padding: 12px 14px;
  border: 1px solid #e2e8f0; border-radius: 10px; background: #f8fafc;
}
.attach-tips__head {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 8px;
}
.attach-tips__list {
  margin: 0; padding-left: 18px; font-size: 12px; color: #64748b; line-height: 1.8;
}
.attach-tips__list li { margin-bottom: 2px; }
.attach-tips code {
  background: #eef2f7; color: #0f766e; padding: 0 4px; border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

/* 接入示例弹窗 */
.sample-alert { margin-bottom: 14px; }
.sample-scheme { margin-bottom: 10px; }
.sample-desc { margin: 0 0 12px; font-size: 12.5px; color: #64748b; line-height: 1.7; }
.sample-desc code {
  background: #f1f5f9; color: #0f766e; padding: 0 4px; border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.code-block { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; background: #0f172a; }
.code-block__head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px; background: #1e293b; color: #cbd5e1; font-size: 12px;
}
.code-sample {
  margin: 0; padding: 14px; max-height: 420px; overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px; line-height: 1.7; color: #e2e8f0; white-space: pre;
}

/* 内置 Agent 下拉：展示名 + 说明 */
.builtin-agent-option {
  display: flex; flex-direction: column; gap: 2px; padding: 2px 0;
}
.builtin-agent-option__name { font-size: 13px; color: #0f172a; }
.builtin-agent-option__desc { font-size: 12px; color: #94a3b8; }
</style>
