<script setup lang="ts">
import { ref, watch } from 'vue'

type EvaluatorType =
  | 'RULE_MATCH'
  | 'EXACT_MATCH'
  | 'LLM_AS_JUDGE'
  | 'TRACE_ASSERTION'
  | 'TRACE_SNAPSHOT'
  | 'JSON_SCHEMA'

interface EvaluatorItem {
  type: EvaluatorType
  weight: number
  config: Record<string, any>
}

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>()

const TYPE_OPTIONS: Array<{ value: EvaluatorType; label: string; hint: string }> = [
  { value: 'RULE_MATCH', label: '关键词匹配', hint: '按关键词命中比例或全部命中打分' },
  { value: 'EXACT_MATCH', label: '精确匹配', hint: '输出与参考答案完全一致' },
  { value: 'LLM_AS_JUDGE', label: 'LLM as Judge', hint: '调用平台 ChatModel 按评分要求打分' },
  { value: 'TRACE_ASSERTION', label: 'Trace 断言', hint: '检查步骤数、Span 名称、错误和延迟' },
  { value: 'TRACE_SNAPSHOT', label: 'Trace 快照', hint: '与历史基线 Trace 的步骤结构对比' },
  { value: 'JSON_SCHEMA', label: 'JSON Schema', hint: '校验输出 JSON 的 required 字段' },
]

/** 每种评估器的适用场景与配置说明，帮助用户选对模式。 */
const TYPE_GUIDES: Record<EvaluatorType, { usage: string; config: string }> = {
  RULE_MATCH: {
    usage: '适合输出中必须出现特定关键词的场景，例如「下单成功」「已完成」「错误码 404」。命中关键词即得分，适合做通过率快速校验。',
    config: '关键词可留空：样本自带「判定关键词」时优先使用，样本未配置才回退到这里填写的全局关键词；多个关键词任一命中即得分，开启「全部命中」后要求全部出现。填写正则后会优先使用正则匹配。',
  },
  EXACT_MATCH: {
    usage: '适合答案唯一且格式固定的场景，例如选择题字母、固定枚举值、短数值答案。可先用正则从输出中抽取答案，再与参考答案比较。',
    config: '「输出抽取正则」用于从 Agent 输出里提取答案（如从「答案是 B，因为…」提取 B）；勾选「取最后一个匹配」适合多步算式取最终数值；「参考答案抽取正则」用于 gsm8k 的「#### 72」这类格式。留空则直接整段比较。',
  },
  LLM_AS_JUDGE: {
    usage: '适合开放式问答、摘要、翻译、写作等无法用规则判定的场景，由平台 ChatModel 按你的评分要求阅读并打分。',
    config: '在「评分要求」中写清评分维度、满分标准和扣分规则；会消耗平台模型额度，预算耗尽时自动降级为关键词匹配。',
  },
  TRACE_ASSERTION: {
    usage: '适合验证 Agent 的执行链路，例如必须调用某个工具、步骤数达标、无报错、耗时在阈值内。评分依赖在线 Trace 数据。',
    config: '设置最少步骤数、最多 ERROR 步骤、最大延迟和必须出现的 Span 名称；「Trace 缺失时忽略」开启则无 Trace 不计入失败。',
  },
  TRACE_SNAPSHOT: {
    usage: '适合做回归对比：把本次 Trace 的步骤结构与一条历史基线 Trace 对比，发现步骤缺失或新增导致的回归。',
    config: '填写基线 Trace ID；开启「允许额外步骤」时，多出来的步骤不算失败，缺失步骤仍会扣分。',
  },
  JSON_SCHEMA: {
    usage: '适合要求 Agent 输出结构化 JSON 的场景，例如工具调用参数、结构化报告、字段抽取结果。',
    config: '粘贴 JSON Schema，平台会校验 required 字段与类型；输出不是合法 JSON 时直接判为失败。',
  },
}

function defaultConfig(type: EvaluatorType): Record<string, any> {
  switch (type) {
    case 'RULE_MATCH':
      return { keywords: [], matchAll: false, scoreOnMatch: 100, pattern: '' }
    case 'EXACT_MATCH':
      return { trim: true, ignoreCase: false, extractPattern: '', extractLast: false, goldenExtractPattern: '' }
    case 'LLM_AS_JUDGE':
      return { requirement: '请从准确性、完整性和相关性三个维度评价 Agent 输出。' }
    case 'TRACE_ASSERTION':
      return {
        minObservations: 1,
        requiredSpanNames: [],
        maxErrorCount: 0,
        maxLatencyMs: 5000,
        requireSuccess: true,
        allowMissingTrace: false,
      }
    case 'TRACE_SNAPSHOT':
      return { baselineTraceId: '', allowExtraSpans: true }
    case 'JSON_SCHEMA':
      return { schemaRef: '{"type":"object","required":["answer"]}' }
  }
}

function normalizeType(type: unknown): EvaluatorType {
  const value = String(type ?? '').toUpperCase() as EvaluatorType
  return TYPE_OPTIONS.some((item) => item.value === value) ? value : 'LLM_AS_JUDGE'
}

function normalizeItem(raw: any): EvaluatorItem {
  const type = normalizeType(raw?.type)
  return {
    type,
    weight: Number(raw?.weight) > 0 ? Number(raw.weight) : 1,
    config: { ...defaultConfig(type), ...(raw?.config && typeof raw.config === 'object' ? raw.config : {}) },
  }
}

function parseItems(value: string): EvaluatorItem[] {
  try {
    const parsed = JSON.parse(value || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.map(normalizeItem)
  } catch {
    return []
  }
}

const items = ref<EvaluatorItem[]>(parseItems(props.modelValue))

 watch(
  items,
  () => {
    const next = JSON.stringify(items.value)
    if (next !== props.modelValue) emit('update:modelValue', next)
  },
  { deep: true },
)

watch(
  () => props.modelValue,
  (value) => {
    if (value === JSON.stringify(items.value)) return
    items.value = parseItems(value)
  },
)

function addEvaluator(type: EvaluatorType) {
  items.value.push({ type, weight: 1, config: defaultConfig(type) })
}

function removeEvaluator(index: number) {
  items.value.splice(index, 1)
}

function changeType(item: EvaluatorItem) {
  item.config = defaultConfig(item.type)
}

function typeHint(type: EvaluatorType) {
  return TYPE_OPTIONS.find((item) => item.value === type)?.hint ?? ''
}

function typeGuide(type: EvaluatorType) {
  return TYPE_GUIDES[type]
}
</script>

<template>
  <div class="evaluator-editor">
    <div class="evaluator-editor__head">
      <div>
        <div class="evaluator-editor__title">评估器</div>
        <div class="evaluator-editor__hint">
          多个评估器按权重加权；LLM as Judge 会调用平台 ChatModel，预算耗尽时自动降级为关键词匹配。
        </div>
      </div>
      <div class="evaluator-editor__actions">
        <el-dropdown trigger="click" @command="addEvaluator">
          <el-button size="small" type="primary" plain>+ 添加评估器</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="option in TYPE_OPTIONS" :key="option.value" :command="option.value">
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div v-if="!items.length" class="evaluator-empty">
      暂未配置评估器；启动后只记录输出，不产生评分。
    </div>

    <div v-for="(item, index) in items" :key="index" class="evaluator-item">
      <div class="evaluator-item__head">
        <el-select v-model="item.type" size="small" style="width: 190px" @change="changeType(item)">
          <el-option v-for="option in TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <span class="evaluator-hint">{{ typeHint(item.type) }}</span>
        <span class="weight-label">权重</span>
        <el-input-number v-model="item.weight" :min="0.1" :max="10" :step="0.1" size="small" controls-position="right" />
        <el-button link type="danger" size="small" @click="removeEvaluator(index)">删除</el-button>
      </div>

      <div class="evaluator-guide">
        <div class="evaluator-guide__row"><span class="evaluator-guide__label">适用</span>{{ typeGuide(item.type).usage }}</div>
        <div class="evaluator-guide__row"><span class="evaluator-guide__label">配置</span>{{ typeGuide(item.type).config }}</div>
      </div>

      <div v-if="item.type === 'LLM_AS_JUDGE'" class="judge-box">
        <div class="field">
          <label class="field-label">评分要求</label>
          <el-input
            v-model="item.config.requirement"
            type="textarea"
            :rows="2"
            placeholder="例如：从准确性、完整性和相关性三个维度评分，并说明理由"
          />
        </div>
      </div>

      <div v-else-if="item.type === 'RULE_MATCH'" class="config-grid">
        <div class="field">
          <label class="field-label">关键词</label>
          <el-select
            v-model="item.config.keywords"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="可留空；样本自带关键词时优先使用，输入后回车添加全局关键词"
          />
        </div>
        <div class="field field-switch">
          <label class="field-label">全部命中</label>
          <el-switch v-model="item.config.matchAll" />
        </div>
        <div class="field">
          <label class="field-label">命中得分</label>
          <el-input-number v-model="item.config.scoreOnMatch" :min="0" :max="100" controls-position="right" style="width: 100%" />
        </div>
        <div class="field">
          <label class="field-label">正则（可选）</label>
          <el-input v-model="item.config.pattern" placeholder="填写后优先使用正则匹配" />
        </div>
      </div>

      <div v-else-if="item.type === 'EXACT_MATCH'" class="config-grid">
        <div class="field">
          <label class="field-label">输出抽取正则（可选）</label>
          <el-input v-model="item.config.extractPattern" placeholder="例如 ([A-D]) 或 (-?[0-9]+)" />
        </div>
        <div class="field field-switch">
          <label class="field-label">取最后一个匹配</label>
          <el-switch v-model="item.config.extractLast" />
        </div>
        <div class="field">
          <label class="field-label">参考答案抽取正则（可选）</label>
          <el-input v-model="item.config.goldenExtractPattern" placeholder="例如 ####[ ]*(-?[0-9]+)" />
        </div>
        <div class="field field-switch">
          <label class="field-label">忽略首尾空格</label>
          <el-switch v-model="item.config.trim" />
        </div>
        <div class="field field-switch">
          <label class="field-label">忽略大小写</label>
          <el-switch v-model="item.config.ignoreCase" />
        </div>
      </div>

      <div v-else-if="item.type === 'TRACE_ASSERTION'" class="config-grid">
        <div class="field">
          <label class="field-label">最少步骤数</label>
          <el-input-number v-model="item.config.minObservations" :min="1" controls-position="right" style="width: 100%" />
        </div>
        <div class="field">
          <label class="field-label">最多 ERROR 步骤</label>
          <el-input-number v-model="item.config.maxErrorCount" :min="0" controls-position="right" style="width: 100%" />
        </div>
        <div class="field">
          <label class="field-label">最大延迟 (ms)</label>
          <el-input-number v-model="item.config.maxLatencyMs" :min="0" :step="100" controls-position="right" style="width: 100%" />
        </div>
        <div class="field">
          <label class="field-label">必须包含 Span</label>
          <el-select
            v-model="item.config.requiredSpanNames"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入后回车添加 Span 名称"
          />
        </div>
        <div class="field field-switch">
          <label class="field-label">要求 Trace 成功</label>
          <el-switch v-model="item.config.requireSuccess" />
        </div>
        <div class="field field-switch">
          <label class="field-label">Trace 缺失时忽略</label>
          <el-switch v-model="item.config.allowMissingTrace" />
        </div>
      </div>

      <div v-else-if="item.type === 'TRACE_SNAPSHOT'" class="config-grid">
        <div class="field">
          <label class="field-label">基线 Trace ID</label>
          <el-input v-model="item.config.baselineTraceId" placeholder="历史 Trace ID，留空则不对比基线" />
        </div>
        <div class="field field-switch">
          <label class="field-label">允许额外步骤</label>
          <el-switch v-model="item.config.allowExtraSpans" />
        </div>
      </div>

      <div v-else-if="item.type === 'JSON_SCHEMA'" class="config-grid">
        <div class="field">
          <label class="field-label">JSON Schema</label>
          <el-input
            v-model="item.config.schemaRef"
            type="textarea"
            :rows="3"
            placeholder='{"type":"object","required":["answer"]}'
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.evaluator-editor { display: flex; flex-direction: column; gap: 12px; }
.evaluator-editor__head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.evaluator-editor__title { font-size: 14px; font-weight: 600; color: #0f172a; }
.evaluator-editor__hint { margin-top: 3px; font-size: 12px; color: #94a3b8; line-height: 1.6; }
.evaluator-editor__actions { display: flex; gap: 8px; flex-shrink: 0; }
.evaluator-empty {
  padding: 24px; text-align: center; color: #94a3b8; font-size: 13px;
  border: 1px dashed #cbd5e1; border-radius: 10px; background: #f8fafc;
}
.evaluator-item { padding: 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
.evaluator-item + .evaluator-item { margin-top: 10px; }
.evaluator-item__head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.evaluator-hint { flex: 1; min-width: 120px; font-size: 12px; color: #94a3b8; }
.weight-label { font-size: 12px; color: #64748b; }
.evaluator-guide {
  margin-top: 10px; padding: 8px 10px; border-radius: 8px;
  background: #f0fdfa; border: 1px solid #ccfbf1;
  display: flex; flex-direction: column; gap: 4px;
}
.evaluator-guide__row { font-size: 12px; color: #475569; line-height: 1.6; }
.evaluator-guide__label {
  display: inline-block; min-width: 28px; margin-right: 6px;
  color: #0f766e; font-weight: 600;
}
.judge-box { margin-top: 12px; padding: 12px; border-radius: 8px; background: #ecfdf5; }
.config-grid {
  display: grid; grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px; margin-top: 10px;
}
.field { min-width: 0; }
.field-label { display: block; margin-bottom: 5px; font-size: 12px; color: #64748b; }
.field-switch { display: flex; flex-direction: column; justify-content: flex-start; }
</style>
