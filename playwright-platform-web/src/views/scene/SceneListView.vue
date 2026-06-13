<script setup lang="ts">
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useRepositoryStore } from '../../stores/repository'
import { useSceneStore } from '../../stores/scene'
import type { SceneForm, SceneRecord } from '../../types/scene'
import { toErrorMessage } from '../../utils/error'
import { isPositiveId, isRequired } from '../../utils/validators'

const repositoryStore = useRepositoryStore()
const sceneStore = useSceneStore()
const router = useRouter()
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SceneForm>(sceneStore.createEmptyForm())
const descriptionAutosize = { minRows: 1, maxRows: 4 }
const envJsonAutosize = { minRows: 1, maxRows: 6 }
const browserOptions = ['chromium', 'firefox', 'webkit']

const rows = computed(() => sceneStore.items)
const repositories = computed(() => repositoryStore.options)
const enabledRepositories = computed(() => repositories.value.filter((item) => item.enabled))
const selectableRepositories = computed(() => {
  const candidates = new Map(enabledRepositories.value.map((item) => [item.id, item]))
  const selected = repositories.value.find((item) => item.id === form.repoId)
  if (selected) {
    candidates.set(selected.id, selected)
  }
  return Array.from(candidates.values())
})
const loading = computed(() => sceneStore.loading)
const pagination = computed(() => ({
  page: sceneStore.page,
  size: sceneStore.size,
  total: sceneStore.total,
}))
const repositoryNameById = computed(() =>
  new Map(repositories.value.map((item) => [item.id, item.name])),
)
const repositorySelectValue = computed({
  get: () => (selectableRepositories.value.length === 0 || !isPositiveId(form.repoId) ? undefined : form.repoId),
  set: (value: string | number | undefined) => {
    if (typeof value === 'number') {
      form.repoId = value
      return
    }
    form.repoId = value ? Number(value) || 0 : 0
  },
})
const selectorPlaceholder = computed(() => {
  const repository = selectedRepository(form.repoId)
  const testRoot = repository?.testRoot?.trim()

  if (testRoot) {
    return `相对测试目录 ${testRoot}，如：login.spec.ts，regression`
  }
  return '如：login.spec.ts，regression'
})
const matchValueHelpText = computed(() => {
  const repository = selectedRepository(form.repoId)
  const testRoot = repository?.testRoot?.trim()

  if (testRoot) {
    return `为空时执行测试目录 ${testRoot} 下全部测试；填写文件时例如 login.spec.ts，填写目录时例如 regression。`
  }
  return '为空时执行测试目录下全部测试；填写文件时例如 login.spec.ts，填写目录时例如 regression。'
})
const formRules: FormRules<SceneForm> = {
  name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  repoId: [{
    validator: (_rule, value, callback) => {
      if (isPositiveId(Number(value))) {
        callback()
        return
      }
      callback(new Error('请选择所属仓库'))
    },
    trigger: 'change',
  }],
  browser: [{ required: true, message: '请选择浏览器', trigger: 'change' }],
  cronExpression: [{
    validator: (_rule, value, callback) => {
      if (!form.scheduleEnabled || isRequired(String(value ?? ''))) {
        callback()
        return
      }
      callback(new Error('请输入 Cron 表达式'))
    },
    trigger: 'blur',
  }],
}

if (!form.repoId) {
  form.repoId = enabledRepositories.value[0]?.id ?? 0
}

function selectedRepository(repoId: number) {
  return repositories.value.find((item) => item.id === repoId) ?? null
}

function openCreate() {
  editingId.value = null
  Object.assign(form, sceneStore.createEmptyForm())
  form.repoId = enabledRepositories.value[0]?.id ?? 0
  dialogVisible.value = true
}

function formatLastRunAt(value: string | null) {
  if (!value) {
    return '未执行'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function executionMode(row: SceneRecord) {
  return row.scheduleEnabled ? '定时 + 手动' : '手动'
}

async function openEdit(row: SceneRecord) {
  editingId.value = row.id
  try {
    const detail = await sceneStore.fetchOne(row.id)
    Object.assign(form, sceneStore.createEmptyForm(), detail)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景详情加载失败'))
  }
}

function openTasks(row: SceneRecord) {
  void router.push(`/scenes/${row.id}/tasks`)
}

function repositoryName(row: SceneRecord) {
  return repositoryNameById.value.get(row.repoId) ?? `仓库 #${row.repoId}`
}

async function submit() {
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请先完善必填字段')
      return
    }
  }

  if (!isPositiveId(form.repoId) || !isRequired(form.name)) {
    ElMessage.warning('请完善所属仓库和场景名称')
    return
  }

  if (!isRequired(form.browser ?? '')) {
    ElMessage.warning('请完善浏览器')
    return
  }

  const repository = selectedRepository(form.repoId)
  if (repository === null) {
    ElMessage.warning('请选择有效的所属仓库')
    return
  }
  if (!repository.enabled) {
    ElMessage.warning('所属仓库已停用，请先启用仓库')
    return
  }

  if (form.scheduleEnabled && !isRequired(form.cronExpression)) {
    ElMessage.warning('启用定时执行时请填写 Cron 表达式')
    return
  }

  saving.value = true
  try {
    const normalizedMatchValue = form.matchValue.trim()
    const normalizedEnvJson = form.envJson?.trim()
    await sceneStore.save(editingId.value, {
      ...form,
      envJson: normalizedEnvJson ? normalizedEnvJson : undefined,
      matchValue: normalizedMatchValue,
      branch: repository.defaultBranch,
      testSelectorType: 'file',
      testSelectorValue: normalizedMatchValue,
      projectName: form.browser?.trim() || 'chromium',
      runCommand: repository.runCommandTemplate,
    })
    dialogVisible.value = false
    ElMessage.success(editingId.value === null ? '场景已创建' : '场景已更新')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景保存失败'))
  } finally {
    saving.value = false
  }
}

async function remove(row: SceneRecord) {
  try {
    await ElMessageBox.confirm(`确认删除场景“${row.name}”吗？`, '删除场景', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await sceneStore.remove(row.id)
    ElMessage.success('场景已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(toErrorMessage(error, '场景删除失败'))
  }
}

onMounted(async () => {
  try {
    await Promise.all([repositoryStore.fetchOptions(), sceneStore.fetchAll()])
    if (!form.repoId) {
      form.repoId = enabledRepositories.value[0]?.id ?? 0
    }
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景列表加载失败'))
  }
})

async function handlePageChange(page: number) {
  try {
    await sceneStore.fetchAll(page, sceneStore.size)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景列表加载失败'))
  }
}

async function handleSizeChange(size: number) {
  try {
    await sceneStore.fetchAll(1, size)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景列表加载失败'))
  }
}
</script>

<template>
  <ListPageShell
    :pagination="pagination"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #header-right>
      <el-button type="primary" @click="openCreate">新增场景</el-button>
    </template>

    <el-table class="list-table" :data="rows" :loading="loading" empty-text="暂无场景">
      <el-table-column label="任务列表" width="120">
        <template #default="{ row }">
          <el-button class="table-action-button" link @click="openTasks(row)">查看任务</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="场景名称" min-width="200" />
      <el-table-column label="所属仓库" min-width="200">
        <template #default="{ row }">
          <span>{{ repositoryName(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="执行模式" width="140">
        <template #default="{ row }">
          <span>{{ executionMode(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近执行" min-width="200">
        <template #default="{ row }">
          <span>{{ formatLastRunAt(row.lastRunAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button class="table-action-button" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button class="table-action-button" link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </ListPageShell>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增场景' : '编辑场景'" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="form-grid">
        <el-form-item label="场景名称" prop="name" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="场景描述">
          <el-input v-model="form.description" type="textarea" :autosize="descriptionAutosize" />
        </el-form-item>
        <el-form-item label="所属仓库" prop="repoId" required>
          <el-select v-model="repositorySelectValue" placeholder="请选择仓库">
            <el-option v-for="item in selectableRepositories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="form-label-with-help">
              <span>用例路径/目录</span>
              <el-tooltip :content="matchValueHelpText" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.matchValue" :placeholder="selectorPlaceholder" />
        </el-form-item>
        <el-form-item label="浏览器" prop="browser" required>
          <el-select v-model="form.browser" placeholder="请选择浏览器">
            <el-option v-for="item in browserOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用定时"><el-switch v-model="form.scheduleEnabled" /></el-form-item>
        <el-form-item
          v-if="form.scheduleEnabled"
          label="Cron 表达式"
          prop="cronExpression"
          required
          class="form-grid__full-span"
        >
          <el-input v-model="form.cronExpression" placeholder="0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="环境参数 JSON" class="form-grid__full-span">
          <el-input v-model="form.envJson" type="textarea" :autosize="envJsonAutosize" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
</template>

<style scoped>
.form-label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.field-help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(38, 208, 191, 0.12);
  color: #26d0bf;
  font-size: 12px;
  font-weight: 600;
  cursor: help;
}

.table-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 12px;
  white-space: nowrap;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
