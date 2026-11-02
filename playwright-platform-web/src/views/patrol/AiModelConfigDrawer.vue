 
<script setup lang="ts">
import { type FormInstance, type FormRules } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import {
  createModelConfig,
  deleteModelConfig,
  listModelConfigs,
  testModelConfig,
  updateModelConfig,
} from '../../api/patrol'
import type { AiModelConfig, AiModelConfigPayload } from '../../types/patrol'
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

const form = reactive<AiModelConfigPayload>({
  name: '',
  providerType: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKey: '',
  modelId: '',
  supportsVision: true,
  supportsTools: true,
  supportsJson: true,
  maxTokens: 4500,
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
      void load()
    }
  },
)

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
    maxTokens: 4500,
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
    maxTokens: row.maxTokens,
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
  <el-drawer v-model="visible" title="模型配置" size="800px">
    <div class="model-drawer">
      <div class="model-hero">
        <div>
          <div class="model-hero__title">用户自配置多模态模型</div>
          <p class="model-hero__text">
            模型由当前空间自行新建并维护；规划模型与判定模型都需要在这里配置，判定模型必须支持视觉输入。
          </p>
        </div>
        <div class="model-hero__actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
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
            <span class="model-card__meta">max {{ row.maxTokens }} · t {{ row.temperature }}</span>
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
            <el-form-item label="maxTokens" class="span-4">
              <el-input-number v-model="form.maxTokens" :min="256" :max="32768" :step="500" />
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
  </el-drawer>
</template>

<style scoped>
.model-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
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
  .model-grid {
    grid-template-columns: 1fr;
  }
}
</style>
