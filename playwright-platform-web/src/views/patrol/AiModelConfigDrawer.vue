 
<script setup lang="ts">
import { type FormInstance, type FormRules } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import {
  createModelConfig,
  createPromptConfig,
  deleteModelConfig,
  listModelConfigs,
  listPromptConfigs,
  resetPromptConfig,
  testModelConfig,
  updateModelConfig,
  updatePromptConfig,
} from '../../api/patrol'
import type {
  AiModelConfig,
  AiModelConfigPayload,
  AiPromptConfig,
  AiPromptConfigPayload,
} from '../../types/patrol'
import { useAuthStore } from '../../stores/auth'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'

const props = defineProps<{
  modelValue: boolean
  spaceId: number
}>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'changed'): void
}>()

const loading = ref(false)
const saving = ref(false)
const rows = ref<AiModelConfig[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const testingId = ref<number | null>(null)

const activeTab = ref<'models' | 'prompts'>('models')
const authStore = useAuthStore()
/** 提示词配置仅超级管理员可见、可编辑。 */
const isSuperAdmin = computed(() => authStore.isSuperAdmin)
const prompts = ref<AiPromptConfig[]>([])
const promptLoading = ref(false)
const promptSaving = ref(false)
const promptDialogVisible = ref(false)
const promptEditingId = ref<number | null>(null)
const promptDefaultContent = ref('')
const promptForm = reactive<AiPromptConfigPayload>({
  label: 'PLAN',
  content: '',
})

const promptDialogTitle = computed(() => {
  const row = prompts.value.find((item) => item.label === promptForm.label)
  return row ? `编辑提示词 · ${row.labelName}` : '编辑提示词'
})

const MAX_TOKEN_OPTIONS = [
  { value: 8192, label: '8k（8192）' },
  { value: 16384, label: '16k（16384）' },
  { value: 32768, label: '32k（32768）' },
  { value: 65536, label: '64k（65536）' },
]
const DEFAULT_MAX_TOKENS = 16384

function nearestMaxTokens(value?: number | null) {
  const target = Number(value ?? DEFAULT_MAX_TOKENS)
  return MAX_TOKEN_OPTIONS.reduce((best, option) =>
    Math.abs(option.value - target) < Math.abs(best - target) ? option.value : best,
  MAX_TOKEN_OPTIONS[0].value)
}

function formatMaxTokens(value?: number | null) {
  const tokens = nearestMaxTokens(value)
  return `${tokens / 1024}k`
}

const form = reactive<AiModelConfigPayload>({
  name: '',
  providerType: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  supportsVision: true,
  supportsTools: true,
  supportsJson: true,
  maxTokens: DEFAULT_MAX_TOKENS,
  temperature: 0.2,
  enabled: true,
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  modelId: [{ required: true, message: '请输入模型 ID', trigger: 'blur' }],
}

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      if (!isSuperAdmin.value) activeTab.value = 'models'
      void loadAll()
    }
  },
)

async function loadAll() {
  await Promise.all([load(), loadPrompts()])
}

async function load() {
  loading.value = true
  try {
    rows.value = await listModelConfigs(props.spaceId)
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    loading.value = false
  }
}

async function loadPrompts() {
  if (!isSuperAdmin.value) return
  promptLoading.value = true
  try {
    prompts.value = await listPromptConfigs(props.spaceId)
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    promptLoading.value = false
  }
}

function promptPreview(content: string) {
  const normalized = (content || '').trim()
  return normalized.length > 420 ? `${normalized.slice(0, 420)}…` : normalized
}

function openPromptEdit(row: AiPromptConfig) {
  promptEditingId.value = row.id
  promptForm.label = row.label
  promptDefaultContent.value = row.defaultContent
  promptForm.content = row.content
  promptDialogVisible.value = true
}

function fillPromptDefault() {
  promptForm.content = promptDefaultContent.value
}

async function submitPrompt() {
  const content = promptForm.content.trim()
  if (!content) {
    showAppToast('提示词内容不能为空', 'warning')
    return
  }
  promptSaving.value = true
  try {
    if (promptEditingId.value) {
      await updatePromptConfig(props.spaceId, promptEditingId.value, {
        label: promptForm.label,
        content,
      })
    } else {
      await createPromptConfig(props.spaceId, { label: promptForm.label, content })
    }
    showAppToast(promptEditingId.value ? '提示词已更新' : '提示词已创建')
    promptDialogVisible.value = false
    await loadPrompts()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    promptSaving.value = false
  }
}

async function resetPrompt(row: AiPromptConfig) {
  if (!row.id) return
  const confirmed = await confirmDangerAction({
    title: '恢复默认提示词',
    message: `确认删除「${row.labelName}」的空间自定义内容？删除后将立即使用内置默认。`,
  })
  if (!confirmed) return
  try {
    await resetPromptConfig(props.spaceId, row.id)
    showAppToast('已恢复内置默认提示词')
    await loadPrompts()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    apiKey: '',
    modelId: '',
    supportsVision: true,
    supportsTools: true,
    supportsJson: true,
    maxTokens: DEFAULT_MAX_TOKENS,
    temperature: 0.2,
    enabled: true,
  })
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AiModelConfig) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    providerType: row.providerType || 'OPENAI_COMPATIBLE',
    baseUrl: row.baseUrl,
    apiKey: '',
    modelId: row.modelId,
    supportsVision: row.supportsVision,
    supportsTools: row.supportsTools,
    supportsJson: row.supportsJson,
    maxTokens: nearestMaxTokens(row.maxTokens),
    temperature: Number(row.temperature ?? 0.2),
    enabled: row.enabled,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateModelConfig(props.spaceId, editingId.value, { ...form })
    } else {
      await createModelConfig(props.spaceId, { ...form })
    }
    showAppToast(editingId.value ? '模型配置已更新' : '模型配置已创建')
    dialogVisible.value = false
    emit('changed')
    await load()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(row: AiModelConfig) {
  const confirmed = await confirmDangerAction({
    title: '删除模型配置',
    message: `确认删除「${row.name}」？引用该配置的巡页需要重新选择模型。`,
  })
  if (!confirmed) return
  try {
    await deleteModelConfig(props.spaceId, row.id)
    showAppToast('模型配置已删除')
    emit('changed')
    await load()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

async function test(row: AiModelConfig) {
  testingId.value = row.id
  try {
    const result = await testModelConfig(props.spaceId, row.id)
    showAppToast(result.message, result.success ? 'success' : 'error')
    await load()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    testingId.value = null
  }
}
</script>

<template>
  <el-drawer v-model="visible" title="模型配置" size="920px">
    <el-tabs v-model="activeTab" class="config-tabs">
      <el-tab-pane label="模型" name="models">
        <div class="model-drawer">
      <div class="model-hero">
        <div>
          <div class="model-hero__title">用户自配置多模态模型</div>
          <p class="model-hero__text">
            模型由当前空间自行新建并维护；规划模型与判定模型都需要在这里配置，判定模型必须支持视觉输入。
          </p>
        </div>
        <div class="model-hero__actions">
          <el-button type="primary" @click="openCreate">新增模型配置</el-button>
        </div>
      </div>

      <div v-loading="loading" class="model-grid">
        <article v-for="row in rows" :key="row.id" class="model-card">
          <div class="model-card__head">
            <div class="model-card__name">
              {{ row.name }}
            </div>
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </div>
          <div class="model-card__model">{{ row.modelId }}</div>
          <div class="model-card__url">{{ row.baseUrl }}</div>
          <div class="model-card__tags">
            <el-tag v-if="row.supportsVision" type="success" size="small">视觉</el-tag>
            <el-tag v-if="row.supportsTools" type="info" size="small">工具</el-tag>
            <el-tag v-if="row.supportsJson" type="info" size="small">JSON</el-tag>
            <span class="model-card__meta">max {{ formatMaxTokens(row.maxTokens) }} · t {{ row.temperature }}</span>
          </div>
          <div class="model-card__status">
            <template v-if="row.lastTestStatus">
              <span :class="row.lastTestStatus === 'SUCCESS' ? 'status-ok' : 'status-bad'">
                {{ row.lastTestStatus === 'SUCCESS' ? '连接正常' : '连接失败' }}
              </span>
              <span class="model-card__message">{{ row.lastTestMessage }}</span>
            </template>
            <span v-else class="status-muted">尚未测试连接</span>
          </div>
          <div class="model-card__actions">
            <el-button link type="primary" :loading="testingId === row.id" @click="test(row)">
              测试连接
            </el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </article>
        <el-empty v-if="!loading && !rows.length" description="还没有模型配置" />
      </div>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="isSuperAdmin" label="提示词" name="prompts">
        <div class="model-drawer">
          <div class="model-hero">
            <div>
              <div class="model-hero__title">规划 / 补全 / 判定 / 前置解析提示词</div>
              <p class="model-hero__text">
                默认内容随代码内置；保存后仅覆盖当前空间。同一标签只能保留一份，删除自定义即恢复默认。
                运行时还会自动追加不可编辑的数据边界与安全约束，页面内容无法改写这些规则。
              </p>
            </div>
          </div>

          <div v-loading="promptLoading" class="prompt-grid">
            <article v-for="row in prompts" :key="row.label" class="prompt-card">
              <div class="model-card__head">
                <div class="model-card__name">{{ row.labelName }}</div>
                <el-tag :type="row.custom ? 'success' : 'info'" size="small">
                  {{ row.custom ? '空间自定义' : '内置默认' }}
                </el-tag>
              </div>
              <div class="prompt-card__meta">
                <span class="prompt-tag">{{ row.label }}</span>
                <span v-if="row.custom && row.updatedAt">
                  更新于 {{ row.updatedAt }} · {{ row.updatedBy || '-' }}
                </span>
                <span v-else>跟随代码内置版本</span>
              </div>
              <pre class="prompt-preview">{{ promptPreview(row.content) }}</pre>
              <div class="model-card__actions">
                <el-button link type="primary" @click="openPromptEdit(row)">编辑</el-button>
                <el-button v-if="row.custom" link type="danger" @click="resetPrompt(row)">
                  恢复默认
                </el-button>
              </div>
            </article>
            <el-empty v-if="!promptLoading && !prompts.length" description="暂无提示词配置" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑模型配置' : '新增模型配置'"
      width="660px"
      top="7vh"
      append-to-body
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="model-form">
        <section class="form-section">
          <div class="section-head">
            <span class="section-dot" />
            <h4>基础连接</h4>
          </div>
          <div class="form-grid">
            <el-form-item label="配置名称" prop="name" class="span-6">
              <el-input v-model="form.name" placeholder="如：空间视觉模型" />
            </el-form-item>
            <el-form-item label="接入协议" class="span-6">
              <el-select v-model="form.providerType" disabled>
                <el-option label="OpenAI 兼容" value="OPENAI_COMPATIBLE" />
              </el-select>
            </el-form-item>
            <el-form-item label="Base URL" prop="baseUrl" class="span-12">
              <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
            </el-form-item>
            <el-form-item label="API Key" class="span-6">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="editingId ? '留空表示不修改' : '请输入 API Key'"
              />
            </el-form-item>
            <el-form-item label="模型 ID" prop="modelId" class="span-6">
              <el-input v-model="form.modelId" placeholder="如：deepseek-flash" />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="section-head">
            <span class="section-dot section-dot--blue" />
            <h4>能力与参数</h4>
          </div>
          <div class="form-grid">
            <el-form-item label="模型能力" class="span-12">
              <div class="checkbox-line">
                <el-checkbox v-model="form.supportsVision">支持视觉输入</el-checkbox>
                <el-checkbox v-model="form.supportsTools">支持工具调用</el-checkbox>
                <el-checkbox v-model="form.supportsJson">支持 JSON 输出</el-checkbox>
              </div>
            </el-form-item>
            <el-form-item label="maxTokens（总输出）" class="span-4">
              <el-select v-model="form.maxTokens">
                <el-option v-for="option in MAX_TOKEN_OPTIONS" :key="option.value"
                  :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="temperature" class="span-4">
              <el-input-number v-model="form.temperature" :min="0" :max="1.5" :step="0.1" />
            </el-form-item>
            <el-form-item label="启用" class="span-4">
              <div class="switch-line">
                <el-switch v-model="form.enabled" />
                <span>{{ form.enabled ? '已启用' : '已停用' }}</span>
              </div>
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="promptDialogVisible"
      :title="promptDialogTitle"
      width="780px"
      top="7vh"
      append-to-body
    >
      <div class="prompt-dialog">
        <el-form label-position="top">
          <el-form-item label="提示词内容">
            <el-input
              v-model="promptForm.content"
              type="textarea"
              :rows="18"
              maxlength="20000"
              show-word-limit
              class="prompt-editor"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="fillPromptDefault">恢复默认内容</el-button>
        <el-button @click="promptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="promptSaving" @click="submitPrompt">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.model-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.config-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}
.prompt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.prompt-card {
  border: 1px solid #e8edf3;
  border-radius: 14px;
  padding: 14px 16px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.prompt-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #94a3b8;
  font-size: 12px;
}
.prompt-tag {
  padding: 1px 6px;
  border-radius: 6px;
  background: #f0fdfa;
  color: #0f766e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.prompt-preview {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.prompt-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.prompt-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.7;
}
.model-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 60%);
}
.model-hero__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.model-hero__text {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
  max-width: 520px;
}
.model-hero__actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.model-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-height: 120px;
}
.model-card {
  border: 1px solid #e8edf3;
  border-radius: 14px;
  padding: 14px 16px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.model-card__head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.model-card__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.model-card__model {
  font-size: 12px;
  color: #0f766e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.model-card__url {
  font-size: 12px;
  color: #94a3b8;
  word-break: break-all;
}
.model-card__tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.model-card__meta {
  color: #94a3b8;
  font-size: 12px;
}
.model-card__status {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 20px;
}
.status-ok {
  color: #16a34a;
  font-size: 12px;
  font-weight: 600;
}
.status-bad {
  color: #dc2626;
  font-size: 12px;
  font-weight: 600;
}
.status-muted {
  color: #94a3b8;
  font-size: 12px;
}
.model-card__message {
  color: #94a3b8;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-card__actions {
  display: flex;
  gap: 4px;
  padding-top: 8px;
  border-top: 1px dashed #eef2f7;
}

/* 表单 */
.model-form {
  max-height: 66vh;
  overflow-y: auto;
  padding-right: 6px;
}
.form-section {
  border: 1px solid #e8edf3;
  border-radius: 14px;
  padding: 16px 18px 4px;
  margin-bottom: 14px;
  background: linear-gradient(180deg, #fbfdff 0%, #ffffff 60%);
}
.section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.section-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.section-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #14b8a6;
}
.section-dot--blue {
  background: #3b82f6;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  column-gap: 16px;
}
.span-12 { grid-column: span 12; }
.span-6 { grid-column: span 6; }
.span-4 { grid-column: span 4; }
.form-grid :deep(.el-form-item) {
  margin-bottom: 14px;
}
.form-grid :deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  line-height: 1.3;
}
.form-grid :deep(.el-input-number),
.form-grid :deep(.el-select) {
  width: 100%;
}
.checkbox-line {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  color: #475569;
  font-size: 13px;
}
@media (max-width: 1200px) {
  .model-grid,
  .prompt-grid {
    grid-template-columns: 1fr;
  }
}
</style>
