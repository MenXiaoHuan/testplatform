<script setup lang="ts"> 
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useRepositoryStore } from '../../stores/repository'
import type { RepositoryForm, RepositoryRecord } from '../../types/repository'
import { toErrorMessage } from '../../utils/error'
import { isRequired } from '../../utils/validators'

const store = useRepositoryStore()
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<RepositoryForm>(store.createEmptyForm())

const rows = computed(() => store.items)
const loading = computed(() => store.loading)
const pagination = computed(() => ({
  page: store.page,
  size: store.size,
  total: store.total,
}))
const repositoryFieldHelp = {
  workingDirectory: '为空时直接在仓库根目录执行；多模块仓库可填写子目录，例如 playwright_framework。',
  installCommand:
    '默认使用 Playwright 原生命令，例如 npm install && npx playwright install；如仓库封装了脚本，也可以改成自定义安装命令。',
  runCommandTemplate:
    '默认使用 Playwright 原生命令，例如 npx playwright test；如仓库封装了脚本，也可以改成自定义执行命令。若使用 wrapper 模式，必须写成可透传参数的形式，例如 npm run test:e2e --，这样平台追加测试目标时才能继续向后透传。',
  testRoot: '相对工作目录填写测试目录，例如 tests。',
  reportRelativePath: '相对工作目录填写报告目录，例如 reports/allure-report。',
}
const formRules: FormRules<RepositoryForm> = {
  name: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  gitUrl: [{ required: true, message: '请输入 Git 地址', trigger: 'blur' }],
  defaultBranch: [{ required: true, message: '请输入默认分支', trigger: 'blur' }],
  installCommand: [{ required: true, message: '请输入安装命令', trigger: 'blur' }],
  runCommandTemplate: [{ required: true, message: '请输入测试执行命令', trigger: 'blur' }],
  testRoot: [{ required: true, message: '请输入测试目录', trigger: 'blur' }],
  reportRelativePath: [{ required: true, message: '请输入报告目录', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  Object.assign(form, store.createEmptyForm())
  dialogVisible.value = true
}

function openEdit(row: RepositoryRecord) {
  editingId.value = row.id
  Object.assign(form, store.createEmptyForm(), { ...row, workingDirectory: row.workingDirectory ?? '' })
  dialogVisible.value = true
}

async function submit() {
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) {
      ElMessage.warning('请先完善必填字段')
      return
    }
  }

  if (!isRequired(form.name) || !isRequired(form.gitUrl)) {
    ElMessage.warning('请完善仓库名称和 Git 地址')
    return
  }

  saving.value = true
  try {
    await store.save(editingId.value, { ...form })
    dialogVisible.value = false
    ElMessage.success(editingId.value === null ? '仓库已创建' : '仓库已更新')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '仓库保存失败'))
  } finally {
    saving.value = false
  }
}

async function remove(row: RepositoryRecord) {
  try {
    await ElMessageBox.confirm(`确认删除仓库“${row.name}”吗？`, '删除仓库', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await store.remove(row.id)
    ElMessage.success('仓库已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(toErrorMessage(error, '仓库删除失败'))
  }
}

async function toggleEnabled(row: RepositoryRecord, enabled: boolean) {
  togglingId.value = row.id
  try {
    await store.save(row.id, {
      ...row,
      workingDirectory: row.workingDirectory ?? '',
      enabled,
    })
    ElMessage.success(enabled ? '仓库已启用' : '仓库已停用')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '仓库状态更新失败'))
  } finally {
    togglingId.value = null
  }
}

onMounted(() => {
  void store.fetchAll().catch((error) => {
    ElMessage.error(toErrorMessage(error, '仓库列表加载失败'))
  })
})

async function handlePageChange(page: number) {
  try {
    await store.fetchAll(page, store.size)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '仓库列表加载失败'))
  }
}

async function handleSizeChange(size: number) {
  try {
    await store.fetchAll(1, size)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '仓库列表加载失败'))
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
      <el-button type="primary" @click="openCreate">新增仓库</el-button>
    </template>

    <el-table class="list-table" :data="rows" :loading="loading" empty-text="暂无仓库">
      <el-table-column label="On/Off" width="104">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :loading="togglingId === row.id"
            @change="(value: boolean) => toggleEnabled(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="仓库名称" min-width="200" />
      <el-table-column prop="gitUrl" label="Git 地址" min-width="280" />
      <el-table-column prop="defaultBranch" label="默认分支" width="140" />
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

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增仓库' : '编辑仓库'" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="form-grid">
        <el-form-item label="仓库名称" prop="name" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="Git 地址" prop="gitUrl" required><el-input v-model="form.gitUrl" /></el-form-item>
        <el-form-item label="默认分支" prop="defaultBranch" required><el-input v-model="form.defaultBranch" /></el-form-item>
        <el-form-item>
          <template #label>
            <span class="form-label-with-help">
              <span>工作目录</span>
              <el-tooltip :content="repositoryFieldHelp.workingDirectory" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.workingDirectory" placeholder="" />
        </el-form-item>
        <el-form-item prop="installCommand" required>
          <template #label>
            <span class="form-label-with-help">
              <span>安装命令</span>
              <el-tooltip :content="repositoryFieldHelp.installCommand" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.installCommand" placeholder="npm install && npx playwright install" />
        </el-form-item>
        <el-form-item prop="runCommandTemplate" required>
          <template #label>
            <span class="form-label-with-help">
              <span>测试执行命令</span>
              <el-tooltip :content="repositoryFieldHelp.runCommandTemplate" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.runCommandTemplate" placeholder="npx playwright test" />
        </el-form-item>
        <el-form-item prop="testRoot" required>
          <template #label>
            <span class="form-label-with-help">
              <span>测试目录</span>
              <el-tooltip :content="repositoryFieldHelp.testRoot" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.testRoot" placeholder="tests" />
        </el-form-item>
        <el-form-item prop="reportRelativePath" required>
          <template #label>
            <span class="form-label-with-help">
              <span>报告目录</span>
              <el-tooltip :content="repositoryFieldHelp.reportRelativePath" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.reportRelativePath" placeholder="reports/allure-report" />
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
