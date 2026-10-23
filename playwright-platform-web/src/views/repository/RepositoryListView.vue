<script setup lang="ts">
import { type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useRepositoryStore } from '../../stores/repository'
import type { RepositoryForm, RepositoryRecord } from '../../types/repository'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'
import { isRequired } from '../../utils/validators'

const store = useRepositoryStore()
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<RepositoryForm>(store.createEmptyForm())
const editingCredentialConfigured = ref(false)
const editingAuthType = ref<RepositoryForm['authType'] | null>(null)

const isPrivateRepository = computed(() => form.authType !== 'PUBLIC')
const repositoryVisibility = computed({
  get: () => (form.authType === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE'),
  set: (value: string) => {
    if (value === 'PUBLIC') {
      form.authType = 'PUBLIC'
      return
    }
    if (form.authType === 'PUBLIC') {
      form.authType = 'HTTPS_TOKEN'
    }
  },
})
const canReuseExistingCredential = computed(
  () => editingCredentialConfigured.value && editingAuthType.value === form.authType,
)

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
      '默认 npm install；仓库有 package-lock.json 时建议改成 npm ci，保证依赖版本稳定。',
  runCommandTemplate:
    'Playwright 项目默认使用 npx playwright test；需要按场景传测试文件/浏览器时可用 {target}、{project}，例如 npx playwright test {target} --project {project}。自定义 npm script 也请自己使用占位符接收参数。',
  testRoot: '相对工作目录填写测试目录，例如 tests。',
  resultsIndexRelativePath: '相对工作目录填写结果索引文件，例如 test-results/.playwright-results.json。',
  artifactRootRelativePath: '相对工作目录填写运行产物目录，例如 .playwright-artifacts。',
  authType:
    '公有仓库不需要凭据；私有仓库选择 HTTPS Token 或 SSH Deploy Key。凭据只保存在服务端加密存储，不会回显。',
  credentialUsername:
    'HTTPS 认证的用户名。GitHub 可填 x-access-token，GitLab 可填 oauth2；不确定时使用默认值。',
  credentialSecret:
    'HTTPS 填 Token / Password；SSH 填无口令的 Deploy Key 私钥。编辑时留空表示保持原凭据。',
}
const formRules: FormRules<RepositoryForm> = {
  name: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  gitUrl: [{ required: true, message: '请输入 Git 地址', trigger: 'blur' }],
  defaultBranch: [{ required: true, message: '请输入默认分支', trigger: 'blur' }],
  installCommand: [{ required: true, message: '请输入安装命令', trigger: 'blur' }],
  runCommandTemplate: [{ required: true, message: '请输入测试执行命令', trigger: 'blur' }],
  testRoot: [{ required: true, message: '请输入测试目录', trigger: 'blur' }],
  resultsIndexRelativePath: [{ required: true, message: '请输入结果索引文件', trigger: 'blur' }],
  artifactRootRelativePath: [{ required: true, message: '请输入运行产物目录', trigger: 'blur' }],
}

function toRepositoryPayload(): RepositoryForm {
  return {
    name: form.name,
    gitUrl: form.gitUrl,
    defaultBranch: form.defaultBranch,
    workingDirectory: form.workingDirectory,
    installCommand: form.installCommand,
    runCommandTemplate: form.runCommandTemplate,
    testRoot: form.testRoot,
    resultsIndexRelativePath: form.resultsIndexRelativePath,
    artifactRootRelativePath: form.artifactRootRelativePath,
    authType: form.authType,
    credentialUsername: form.credentialUsername,
    credentialSecret: form.credentialSecret,
    enabled: form.enabled,
  }
}

function openCreate() {
  editingId.value = null
  editingCredentialConfigured.value = false
  editingAuthType.value = null
  Object.assign(form, store.createEmptyForm())
  dialogVisible.value = true
}

function recordToForm(row: RepositoryRecord): RepositoryForm {
  return {
    ...store.createEmptyForm(),
    name: row.name,
    gitUrl: row.gitUrl,
    defaultBranch: row.defaultBranch,
    workingDirectory: row.workingDirectory ?? '',
    installCommand: row.installCommand,
    runCommandTemplate: row.runCommandTemplate,
    testRoot: row.testRoot,
    resultsIndexRelativePath: row.resultsIndexRelativePath,
    artifactRootRelativePath: row.artifactRootRelativePath,
    authType: row.authType,
    credentialUsername: row.credentialUsername ?? '',
    credentialSecret: '',
    enabled: row.enabled,
  }
}

function fillFormFromRecord(row: RepositoryRecord) {
  Object.assign(form, recordToForm(row))
}

function openCopy(row: RepositoryRecord) {
  editingId.value = null
  editingCredentialConfigured.value = false
  editingAuthType.value = null
  fillFormFromRecord(row)
  dialogVisible.value = true
}

function openEdit(row: RepositoryRecord) {
  editingId.value = row.id
  editingCredentialConfigured.value = Boolean(row.credentialConfigured)
  editingAuthType.value = row.authType
  fillFormFromRecord(row)
  dialogVisible.value = true
}

function isNameConflict(error: unknown) {
  if (typeof error === 'object' && error !== null) {
    const response = Reflect.get(error, 'response')
    if (typeof response === 'object' && response !== null) {
      const data = Reflect.get(response, 'data')
      if (typeof data === 'object' && data !== null) {
        return Reflect.get(data, 'code') === 'CONFLICT'
      }
    }
  }
  return false
}

function validateAuthConfig() {
  if (form.authType === 'PUBLIC') {
    return true
  }
  const hasSecret = form.credentialSecret.trim().length > 0
  if (!hasSecret && !canReuseExistingCredential.value) {
    showAppToast('私有仓库需要配置 Token 或 SSH 私钥', 'warning')
    return false
  }
  if (form.authType === 'HTTPS_TOKEN' && !form.gitUrl.trim().startsWith('https://')) {
    showAppToast('HTTPS Token 认证需要 https:// 开头的 Git 地址', 'warning')
    return false
  }
  if (form.authType === 'SSH_KEY'
    && !form.gitUrl.trim().startsWith('ssh://')
    && !/^[\w.-]+@[\w.-]+:.+$/.test(form.gitUrl.trim())) {
    showAppToast('SSH Key 认证需要 ssh:// 或 git@host:path 形式的 Git 地址', 'warning')
    return false
  }
  return true
}

async function submit() {
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) {
      showAppToast('请先完善必填字段', 'warning')
      return
    }
  }

  if (!isRequired(form.name) || !isRequired(form.gitUrl)) {
    showAppToast('请完善仓库名称和 Git 地址', 'warning')
    return
  }
  if (!validateAuthConfig()) {
    return
  }

  saving.value = true
  try {
    await store.save(editingId.value, toRepositoryPayload())
    dialogVisible.value = false
    showAppToast(editingId.value === null ? '仓库已创建' : '仓库已更新', 'success')
  } catch (error) {
    if (isNameConflict(error)) {
      showAppToast(toErrorMessage(error, '仓库名称已存在，请更换后重试'), 'warning')
      return
    }
    showAppToast(toErrorMessage(error, '仓库保存失败'), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(row: RepositoryRecord) {
  try {
    const confirmed = await confirmDangerAction({
      title: '删除仓库',
      message: `确认删除仓库“${row.name}”吗？`,
      confirmButtonText: '删除',
    })
    if (!confirmed) {
      return
    }
    await store.remove(row.id)
    showAppToast('仓库已删除', 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '仓库删除失败'), 'error')
  }
}

async function toggleEnabled(row: RepositoryRecord, enabled: boolean) {
  togglingId.value = row.id
  try {
    await store.save(row.id, { ...recordToForm(row), enabled })
    showAppToast(enabled ? '仓库已启用' : '仓库已停用', 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '仓库状态更新失败'), 'error')
  } finally {
    togglingId.value = null
  }
}

onMounted(() => {
  void store.fetchAll().catch((error) => {
    showAppToast(toErrorMessage(error, '仓库列表加载失败'), 'error')
  })
})

async function handlePageChange(page: number) {
  try {
    await store.fetchAll(page, store.size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '仓库列表加载失败'), 'error')
  }
}

async function handleSizeChange(size: number) {
  try {
    await store.fetchAll(1, size)
  } catch (error) {
    showAppToast(toErrorMessage(error, '仓库列表加载失败'), 'error')
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
      <el-table-column label="开关" width="90" class-name="col-switch" :show-overflow-tooltip="false">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :loading="togglingId === row.id"
            @change="(value: boolean) => toggleEnabled(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="仓库名称" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="row.name" placement="top" :show-after="300" effect="dark">
            <span class="cell-tooltip">{{ row.name }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="Git 地址" min-width="280">
        <template #default="{ row }">
          <el-tooltip :content="row.gitUrl" placement="top" :show-after="300" effect="dark">
            <span class="cell-tooltip cell-tooltip--mono">{{ row.gitUrl }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="访问方式" width="150">
        <template #default="{ row }">
          <el-tag v-if="!row.authType || row.authType === 'PUBLIC'" type="info" effect="plain">公有</el-tag>
          <el-tag v-else-if="row.authType === 'HTTPS_TOKEN'" type="success" effect="plain">HTTPS Token</el-tag>
          <el-tag v-else type="warning" effect="plain">SSH Key</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认分支" width="140">
        <template #default="{ row }">
          <el-tooltip :content="row.defaultBranch" placement="top" :show-after="300" effect="dark">
            <span class="cell-tooltip cell-tooltip--mono">{{ row.defaultBranch }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button class="table-action-button" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button class="table-action-button" link type="primary" @click="openCopy(row)">复制</el-button>
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
        <el-form-item class="form-grid-full">
          <template #label>
            <span class="form-label-with-help">
              <span>仓库类型</span>
              <el-tooltip :content="repositoryFieldHelp.authType" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-radio-group v-model="repositoryVisibility">
            <el-radio-button value="PUBLIC">公有仓库</el-radio-button>
            <el-radio-button value="PRIVATE">私有仓库</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="isPrivateRepository">
          <el-form-item class="form-grid-full" label="认证方式">
            <el-radio-group v-model="form.authType">
              <el-radio-button value="HTTPS_TOKEN">HTTPS Token</el-radio-button>
              <el-radio-button value="SSH_KEY">SSH Deploy Key</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.authType === 'HTTPS_TOKEN'" class="form-grid-full">
            <template #label>
              <span class="form-label-with-help">
                <span>用户名（可选）</span>
                <el-tooltip :content="repositoryFieldHelp.credentialUsername" placement="top">
                  <span class="field-help-icon">?</span>
                </el-tooltip>
              </span>
            </template>
            <el-input v-model="form.credentialUsername" placeholder="x-access-token；GitLab 可用 oauth2" />
          </el-form-item>
          <el-form-item class="form-grid-full">
            <template #label>
              <span class="form-label-with-help">
                <span>{{ form.authType === 'HTTPS_TOKEN' ? 'Token / Password' : 'SSH 私钥' }}</span>
                <el-tooltip :content="repositoryFieldHelp.credentialSecret" placement="top">
                  <span class="field-help-icon">?</span>
                </el-tooltip>
              </span>
            </template>
            <el-input
              v-if="form.authType === 'HTTPS_TOKEN'"
              v-model="form.credentialSecret"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="editingCredentialConfigured ? '留空表示保持原凭据' : '只读 Token，不会回显'"
            />
            <el-input
              v-else
              v-model="form.credentialSecret"
              type="textarea"
              :rows="5"
              :placeholder="editingCredentialConfigured ? '留空表示保持原凭据' : '-----BEGIN OPENSSH PRIVATE KEY-----'"
            />
            <div v-if="editingCredentialConfigured" class="form-hint">
              已配置凭据；留空保存不会覆盖。
            </div>
            <div v-if="form.authType === 'SSH_KEY'" class="form-hint form-hint--warning">
              SSH 模式请使用无口令的只读 Deploy Key；私钥只在服务端临时解密使用。
            </div>
          </el-form-item>
        </template>
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
          <el-input v-model="form.installCommand" placeholder="npm install" />
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
        <el-form-item prop="resultsIndexRelativePath" required>
          <template #label>
            <span class="form-label-with-help">
              <span>结果索引文件</span>
              <el-tooltip :content="repositoryFieldHelp.resultsIndexRelativePath" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.resultsIndexRelativePath" placeholder="test-results/.playwright-results.json" />
        </el-form-item>
        <el-form-item prop="artifactRootRelativePath" required>
          <template #label>
            <span class="form-label-with-help">
              <span>运行产物目录</span>
              <el-tooltip :content="repositoryFieldHelp.artifactRootRelativePath" placement="top">
                <span class="field-help-icon">?</span>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="form.artifactRootRelativePath" placeholder=".playwright-artifacts" />
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

.form-grid-full {
  grid-column: 1 / -1;
}

.form-hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.form-hint--warning {
  color: var(--el-color-warning);
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

:deep(.col-switch .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell-tooltip {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.cell-tooltip--mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
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
