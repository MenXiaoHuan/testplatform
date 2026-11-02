 
<script setup lang="ts">
import { type FormInstance, type FormRules } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  cancelPatrolTask,
  createPatrolScene,
  deletePatrolScene,
  deletePatrolTask,
  getSceneTaskSummary,
  listModelOptions,
  listPatrolScenes,
  listSceneTasks,
  runPatrolScene,
  updatePatrolScene,
} from '../../api/patrol'
import type {
  AiModelOption,
  PatrolScene,
  PatrolScenePayload,
  PatrolTask,
  PatrolTaskSummary,
} from '../../types/patrol'
import CellTooltip from '../../components/list/CellTooltip.vue'
import ListPageShell from '../../components/list/ListPageShell.vue'
import { useSpaceStore } from '../../stores/space'
import { formatBeijingDateTime } from '../../utils/datetime'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'
import PatrolTaskDrawer from './PatrolTaskDrawer.vue'
import AiModelConfigDrawer from './AiModelConfigDrawer.vue'

const route = useRoute()
const spaceStore = useSpaceStore()
const spaceId = computed(() => Number(route.params.spaceId ?? spaceStore.currentSpaceId ?? 0))

const loading = ref(false)
const scenes = ref<PatrolScene[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const pagination = computed(() => ({ page: page.value, size: size.value, total: total.value }))
const modelOptions = ref<AiModelOption[]>([])

const sceneDialogVisible = ref(false)
const sceneFormRef = ref<FormInstance>()
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<PatrolScenePayload & { authCookie: string; authToken: string; authTokenHeader: string; authLocalStorage: string; cronExpression: string }>({
  name: '',
  description: '',
  startUrl: '',
  browser: 'chromium',
  headless: true,
  authType: 'NONE',
  authCookie: '',
  authToken: '',
  authTokenHeader: 'Authorization',
  authLocalStorage: '',
  planModelId: null,
  judgeModelId: null,
  taskBrief: '',
  budgetDurationMinutes: 15,
  budgetLlmTokens: 300000,
  visibleTextLimit: 1800,
  scrollScan: true,
  recordVideo: true,
  autoApproveWrite: false,
  autoApproveDestructive: false,
  scheduleEnabled: false,
  cronExpression: '',
})

const modelDrawerVisible = ref(false)
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyScene = ref<PatrolScene | null>(null)
const historyTasks = ref<PatrolTask[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historySize = ref(10)
const historySummary = ref<PatrolTaskSummary | null>(null)

const taskDrawerVisible = ref(false)
const activeTaskId = ref<number | null>(null)

const visionModels = computed(() => modelOptions.value.filter((item) => item.supportsVision))
const sceneRules: FormRules = {
  name: [{ required: true, message: '请输入巡页名称', trigger: 'blur' }],
  startUrl: [{ required: true, message: '请输入入口页 URL', trigger: 'blur' }],
  budgetDurationMinutes: [{ required: true, message: '请输入预算时长', trigger: 'change' }],
  budgetLlmTokens: [{ required: true, message: '请输入 Token 预算', trigger: 'change' }],
  planModelId: [{ required: true, message: '请选择规划模型', trigger: 'change' }],
  judgeModelId: [{ required: true, message: '请选择判定模型', trigger: 'change' }],
  cronExpression: [{
    validator: (_rule, value, callback) => {
      if (!form.scheduleEnabled || String(value ?? '').trim()) {
        callback()
        return
      }
      callback(new Error('开启定时后必须填写 Cron 表达式'))
    },
    trigger: 'blur',
  }],
}

let pollTimer: number | null = null

onMounted(() => {
  void loadScenes()
  void loadModels()
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
})

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    void loadScenes(true)
    if (historyVisible.value) {
      void loadHistory(true)
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function loadScenes(silent = false) {
  if (!spaceId.value) return
  if (!silent) loading.value = true
  try {
    const result = await listPatrolScenes(spaceId.value, page.value, size.value)
    scenes.value = result.items
    total.value = result.total
  } catch (error) {
    if (!silent) showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    if (!silent) loading.value = false
  }
}

async function handlePageChange(next: number) {
  page.value = next
  await loadScenes()
}

async function handleSizeChange(next: number) {
  size.value = next
  page.value = 1
  await loadScenes()
}

async function loadModels() {
  if (!spaceId.value) return
  try {
    modelOptions.value = await listModelOptions(spaceId.value)
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    description: '',
    startUrl: '',
    browser: 'chromium',
    headless: true,
    authType: 'NONE',
    authCookie: '',
    authToken: '',
    authTokenHeader: 'Authorization',
    authLocalStorage: '',
    planModelId: null,
    judgeModelId: null,
    taskBrief: '',
    budgetDurationMinutes: 15,
    budgetLlmTokens: 300000,
    visibleTextLimit: 1800,
    scrollScan: true,
    recordVideo: true,
    autoApproveWrite: false,
    autoApproveDestructive: false,
    scheduleEnabled: false,
    cronExpression: '',
  })
  sceneFormRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  sceneDialogVisible.value = true
}

function fillFormFromScene(row: PatrolScene, name: string) {
  Object.assign(form, {
    name,
    description: row.description ?? '',
    startUrl: row.startUrl,
    browser: row.browser,
    headless: row.headless,
    authType: row.authType,
    authCookie: '',
    authToken: '',
    authTokenHeader: 'Authorization',
    authLocalStorage: '',
    planModelId: modelOptions.value.some((item) => item.id === row.planModelId) ? row.planModelId ?? null : null,
    judgeModelId: visionModels.value.some((item) => item.id === row.judgeModelId) ? row.judgeModelId ?? null : null,
    taskBrief: row.taskBrief ?? '',
    budgetDurationMinutes: row.budgetDurationMinutes,
    budgetLlmTokens: row.budgetLlmTokens,
    visibleTextLimit: row.visibleTextLimit ?? 1800,
    scrollScan: row.scrollScan,
    recordVideo: row.recordVideo,
    autoApproveWrite: row.autoApproveWrite,
    autoApproveDestructive: row.autoApproveDestructive,
    scheduleEnabled: row.scheduleEnabled,
    cronExpression: row.cronExpression ?? '',
  })
  sceneFormRef.value?.clearValidate()
}

function openEdit(row: PatrolScene) {
  editingId.value = row.id
  fillFormFromScene(row, row.name)
  sceneDialogVisible.value = true
}

function copyScene(row: PatrolScene) {
  editingId.value = null
  fillFormFromScene(row, `${row.name} 副本`)
  sceneDialogVisible.value = true
}

async function submitScene() {
  if (!sceneFormRef.value) return
  const valid = await sceneFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload: PatrolScenePayload = {
      name: form.name.trim(),
      description: form.description?.trim() || null,
      startUrl: form.startUrl.trim(),
      browser: form.browser,
      headless: form.headless,
      authType: form.authType,
      authCookie: form.authCookie?.trim() || undefined,
      authToken: form.authToken?.trim() || undefined,
      authTokenHeader: form.authTokenHeader?.trim() || undefined,
      authLocalStorage: form.authLocalStorage?.trim() || undefined,
      planModelId: form.planModelId ?? null,
      judgeModelId: form.judgeModelId ?? null,
      taskBrief: form.taskBrief?.trim() || null,
      budgetDurationMinutes: form.budgetDurationMinutes,
      budgetLlmTokens: form.budgetLlmTokens,
      visibleTextLimit: form.visibleTextLimit,
      scrollScan: form.scrollScan,
      recordVideo: form.recordVideo,
      autoApproveWrite: form.autoApproveWrite,
      autoApproveDestructive: form.autoApproveDestructive,
      scheduleEnabled: form.scheduleEnabled,
      cronExpression: form.scheduleEnabled ? form.cronExpression?.trim() : null,
    }
    if (editingId.value) {
      await updatePatrolScene(spaceId.value, editingId.value, payload)
      showAppToast('巡页已更新')
    } else {
      await createPatrolScene(spaceId.value, payload)
      showAppToast('巡页已创建')
    }
    sceneDialogVisible.value = false
    await loadScenes()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    saving.value = false
  }
}

async function runScene(row: PatrolScene) {
  try {
    const task = await runPatrolScene(spaceId.value, row.id)
    showAppToast(`已创建巡页任务 #${task.id}`)
    await loadScenes(true)
    historyScene.value = row
    historyPage.value = 1
    historyVisible.value = true
    await loadHistory()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

async function removeScene(row: PatrolScene) {
  const confirmed = await confirmDangerAction({
    title: '删除巡页',
    message: `确认删除「${row.name}」？该巡页的全部执行历史与证据会一并清理。`,
  })
  if (!confirmed) return
  try {
    await deletePatrolScene(spaceId.value, row.id)
    showAppToast('巡页已删除')
    await loadScenes()
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

async function openHistory(row: PatrolScene) {
  historyScene.value = row
  historyPage.value = 1
  historySummary.value = null
  historyVisible.value = true
  await loadHistory()
}

async function loadHistory(silent = false) {
  if (!historyScene.value) return
  if (!silent) historyLoading.value = true
  try {
    const [result, summary] = await Promise.all([
      listSceneTasks(spaceId.value, historyScene.value.id, historyPage.value, historySize.value),
      getSceneTaskSummary(spaceId.value, historyScene.value.id),
    ])
    historyTasks.value = result.items
    historyTotal.value = result.total
    historySummary.value = summary
  } catch (error) {
    if (!silent) showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  } finally {
    if (!silent) historyLoading.value = false
  }
}

function openTask(task: PatrolTask) {
  activeTaskId.value = task.id
  taskDrawerVisible.value = true
}

function isTaskActive(task: PatrolTask) {
  return !['COMPLETED', 'FAILED', 'CANCELED'].includes(task.status)
}

async function cancelHistoryTask(task: PatrolTask) {
  try {
    await cancelPatrolTask(spaceId.value, task.id)
    showAppToast('已请求取消')
    await loadHistory(true)
    await loadScenes(true)
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

async function removeHistoryTask(task: PatrolTask) {
  const confirmed = await confirmDangerAction({
    title: '删除任务',
    message: '删除后任务、用例、问题以及全部录屏/截图/DOM 证据都会立即清理，不可恢复。',
  })
  if (!confirmed) return
  try {
    await deletePatrolTask(spaceId.value, task.id)
    showAppToast('任务已删除')
    await loadHistory()
    await loadScenes(true)
  } catch (error) {
    showAppToast(toErrorMessage(error, '操作失败，请稍后重试'), 'error')
  }
}

function statusType(status?: string | null) {
  if (!status) return 'info'
  if (status === 'COMPLETED' || status === 'PASS') return 'success'
  if (status === 'FAILED' || status === 'FAIL') return 'danger'
  if (status === 'WAITING_APPROVAL' || status === 'RUNNING') return 'warning'
  return 'info'
}

function formatTokens(value?: number | null) {
  return (value ?? 0).toLocaleString('zh-CN')
}

function formatTaskDuration(durationMs?: number | null) {
  if (durationMs === null || durationMs === undefined) return '—'
  return `${(durationMs / 60000).toFixed(1)} min`
}

function formatCoverage(task: PatrolTask) {
  if (!task.coverageTotal) return '—'
  return `${task.coverageCovered}/${task.coverageTotal} · ${task.coverageRate.toFixed(1)}%`
}

function onModelChanged() {
  void loadModels()
}

function rowIndex(index: number) {
  return (historyPage.value - 1) * historySize.value + index + 1
}

async function handleHistoryPageChange(next: number) {
  historyPage.value = next
  await loadHistory()
}

async function handleHistorySizeChange(next: number) {
  historySize.value = next
  historyPage.value = 1
  await loadHistory()
}
</script>

<template>
  <ListPageShell
    :pagination="pagination"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #header-right>
      <el-button @click="modelDrawerVisible = true">模型配置</el-button>
      <el-button type="primary" @click="openCreate">新建巡页</el-button>
    </template>

    <el-table v-loading="loading" :data="scenes" class="list-table patrol-table" empty-text="暂无巡页">
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <div class="stack-cell">
              <div class="scene-name"><CellTooltip :content="row.name" /></div>
              <div class="scene-sub"><CellTooltip :content="row.description || '—'" /></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规划/判定模型" min-width="220">
          <template #default="{ row }">
            <div class="stack-cell">
              <div class="model-line">
                <span class="model-key">规划</span>
                <CellTooltip :content="row.planModelName || '未配置'" />
              </div>
              <div class="model-line">
                <span class="model-key">判定</span>
                <CellTooltip :content="row.judgeModelName || '未配置'" />
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预算" width="170">
          <template #default="{ row }">
            <div class="stack-cell">
              <div>{{ row.budgetDurationMinutes }} 分钟</div>
              <div class="scene-sub">{{ row.budgetLlmTokens }} tokens</div>
              <div class="scene-sub">页面文本 {{ row.visibleTextLimit }} 字</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总 Token 消耗" width="170" align="right">
          <template #default="{ row }">{{ formatTokens(row.totalTokenUsed) }}</template>
        </el-table-column>
        <el-table-column label="定时" width="140">
          <template #default="{ row }">
            <div class="stack-cell">
              <el-tag v-if="row.scheduleEnabled" type="success" size="small">已开启</el-tag>
              <el-tag v-else type="info" size="small">手动</el-tag>
              <div v-if="row.cronExpression" class="scene-sub">
                <CellTooltip :content="row.cronExpression" />
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近执行时间" width="200">
          <template #default="{ row }">
            <div class="nowrap-cell">{{ formatBeijingDateTime(row.lastRunAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="runScene(row)">执行</el-button>
            <el-button link type="primary" @click="openHistory(row)">历史</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="copyScene(row)">复制</el-button>
            <el-button link type="danger" @click="removeScene(row)">删除</el-button>
          </template>
        </el-table-column>
    </el-table>

    <el-dialog
      v-model="sceneDialogVisible"
      :title="editingId ? '编辑巡页' : '新建巡页'"
      width="900px"
      top="4vh"
      class="patrol-form-dialog"
    >
      <el-form
        ref="sceneFormRef"
        :model="form"
        :rules="sceneRules"
        label-position="top"
        class="patrol-form"
      >
        <section class="form-section">
          <div class="section-head">
            <span class="section-dot" />
            <div>
              <h4>基础信息</h4>
              <p>入口页地址、浏览器与巡检目标</p>
            </div>
          </div>
          <div class="form-grid">
            <el-form-item label="名称" prop="name" class="span-6">
              <el-input v-model="form.name" placeholder="如：官网入口页巡检" />
            </el-form-item>
            <el-form-item label="描述" class="span-6">
              <el-input v-model="form.description" placeholder="可选，用于区分多个巡页" />
            </el-form-item>
            <el-form-item label="入口页 URL" prop="startUrl" class="span-12">
              <el-input v-model="form.startUrl" placeholder="https://example.com/" />
            </el-form-item>
            <el-form-item label="浏览器" class="span-4">
              <el-select v-model="form.browser">
                <el-option label="chromium" value="chromium" />
                <el-option label="chrome" value="chrome" />
                <el-option label="firefox" value="firefox" />
                <el-option label="edge" value="edge" />
              </el-select>
            </el-form-item>
            <el-form-item label="运行模式" class="span-4">
              <div class="switch-line">
                <el-switch v-model="form.headless" />
                <span>{{ form.headless ? '无头模式' : '有界面模式' }}</span>
              </div>
            </el-form-item>
            <el-form-item label="任务描述" class="span-12">
              <el-input
                v-model="form.taskBrief"
                type="textarea"
                :rows="2"
                placeholder="用自然语言描述关注模块与验收点，LLM 据此规划用例"
              />
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="section-head">
            <span class="section-dot section-dot--blue" />
            <div>
              <h4>模型与预算</h4>
              <p>规划与判定可分别选择模型；判定模型需要支持视觉</p>
            </div>
          </div>
          <div class="form-grid">
            <el-form-item label="规划模型" prop="planModelId" class="span-6">
              <el-select v-model="form.planModelId" placeholder="请选择规划模型">
                <el-option
                  v-for="item in modelOptions"
                  :key="item.id"
                  :label="`${item.name} / ${item.modelId}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="判定模型" prop="judgeModelId" class="span-6">
              <el-select v-model="form.judgeModelId" placeholder="请选择判定模型（需支持视觉）">
                <el-option
                  v-for="item in visionModels"
                  :key="item.id"
                  :label="`${item.name} / ${item.modelId}`"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="时长预算（分钟）" prop="budgetDurationMinutes" class="span-4">
              <el-input-number v-model="form.budgetDurationMinutes" :min="1" :max="60" />
            </el-form-item>
            <el-form-item label="Token 预算" prop="budgetLlmTokens" class="span-4">
              <el-input-number v-model="form.budgetLlmTokens" :min="10000" :max="1000000" :step="10000" />
            </el-form-item>
            <el-form-item label="模型可见页面文本字数" prop="visibleTextLimit" class="span-4">
              <el-input-number v-model="form.visibleTextLimit" :min="100" :max="100000" :step="100" />
            </el-form-item>
            <el-form-item label="采集选项" class="span-12">
              <div class="checkbox-line">
                <el-checkbox v-model="form.scrollScan">进入页面先滚动触发懒加载</el-checkbox>
                <el-checkbox v-model="form.recordVideo">每条用例录屏</el-checkbox>
              </div>
            </el-form-item>
          </div>
        </section>

        <section class="form-section">
          <div class="section-head">
            <span class="section-dot section-dot--amber" />
            <div>
              <h4>认证与授权</h4>
              <p>认证信息加密保存；未授权写操作会在执行前等待确认</p>
            </div>
          </div>
          <div class="form-grid">
            <el-form-item label="认证方式" class="span-4">
              <el-select v-model="form.authType">
                <el-option label="无" value="NONE" />
                <el-option label="Cookie" value="COOKIE" />
                <el-option label="Token" value="TOKEN" />
                <el-option label="localStorage" value="LOCAL_STORAGE" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.authType === 'COOKIE'" label="Cookie" class="span-8">
              <el-input v-model="form.authCookie" :placeholder="editingId ? '留空表示不修改' : 'name=value; name2=value2'" />
            </el-form-item>
            <el-form-item v-if="form.authType === 'TOKEN'" label="Token" class="span-5">
              <el-input v-model="form.authToken" :placeholder="editingId ? '留空表示不修改' : 'Bearer xxx'" />
            </el-form-item>
            <el-form-item v-if="form.authType === 'TOKEN'" label="Header" class="span-3">
              <el-input v-model="form.authTokenHeader" placeholder="Authorization" />
            </el-form-item>
            <el-form-item v-if="form.authType === 'LOCAL_STORAGE'" label="localStorage JSON" class="span-12">
              <el-input v-model="form.authLocalStorage" type="textarea" :rows="3" placeholder='{"token":"xxx"}' />
            </el-form-item>
            <el-form-item label="默认授权" class="span-12">
              <div class="auth-switches">
                <div class="auth-switch-card">
                  <div>
                    <div class="auth-switch-title">自动允许写操作</div>
                    <div class="auth-switch-hint">创建、保存、提交等操作</div>
                  </div>
                  <el-switch v-model="form.autoApproveWrite" />
                </div>
                <div class="auth-switch-card auth-switch-card--danger">
                  <div>
                    <div class="auth-switch-title">自动允许破坏性操作</div>
                    <div class="auth-switch-hint">删除、清空、支付等不可逆操作</div>
                  </div>
                  <el-switch v-model="form.autoApproveDestructive" />
                </div>
              </div>
              <p class="section-hint">关闭后任务会停在执行前审批，由用户一次性确认。</p>
            </el-form-item>
          </div>
        </section>

        <section class="form-section form-section--last">
          <div class="section-head">
            <span class="section-dot section-dot--green" />
            <div>
              <h4>定时执行</h4>
              <p>开启后按 Cron 自动创建巡页任务</p>
            </div>
          </div>
          <div class="form-grid">
            <el-form-item label="开启定时" class="span-4">
              <div class="switch-line">
                <el-switch v-model="form.scheduleEnabled" />
                <span>{{ form.scheduleEnabled ? '已开启' : '仅手动执行' }}</span>
              </div>
            </el-form-item>
            <el-form-item v-if="form.scheduleEnabled" label="Cron 表达式" prop="cronExpression" class="span-8">
              <el-input v-model="form.cronExpression" placeholder="0 30 2 * * *（每天 02:30）" />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <span class="dialog-footer__hint">保存后可在列表中立即执行，历史记录与证据保留 90 天</span>
          <div>
            <el-button @click="sceneDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submitScene">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="historyVisible"
      :title="historyScene ? `执行历史 · ${historyScene.name}` : '执行历史'"
      size="1240px"
      class="history-drawer"
    >
      <div v-loading="historyLoading">
        <div class="history-summary">
          <div class="summary-card">
            <span class="summary-label">平均 Token 消耗</span>
            <strong class="summary-value">{{ formatTokens(historySummary?.avgLlmTokens) }}</strong>
            <span class="summary-sub">统计 {{ historySummary?.taskCount ?? 0 }} 个已结束任务</span>
          </div>
          <div class="summary-card">
            <span class="summary-label">平均覆盖率（可交互元素）</span>
            <strong class="summary-value">{{ (historySummary?.avgCoverageRate ?? 0).toFixed(1) }}%</strong>
            <span class="summary-sub">入口页可交互元素覆盖占比</span>
          </div>
          <div class="summary-card">
            <span class="summary-label">平均耗时</span>
            <strong class="summary-value">{{ (historySummary?.avgDurationMinutes ?? 0).toFixed(1) }} min</strong>
            <span class="summary-sub">按已结束任务耗时计算</span>
          </div>
        </div>
        <el-table :data="historyTasks" border size="small" class="history-table">
          <el-table-column type="index" label="序号" width="100" :index="rowIndex" />
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-tag class="status-tag" :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="用例" min-width="140">
            <template #default="{ row }">
              {{ row.casePassCount }}/{{ row.caseCount }} 通过
              <div class="scene-sub">失败 {{ row.caseFailCount }} · 跳过 {{ row.caseSkipCount }}</div>
            </template>
          </el-table-column>
          <el-table-column label="耗时（min）" width="120">
            <template #default="{ row }">
              <span class="nowrap-cell">{{ formatTaskDuration(row.durationMs) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Token 消耗" width="115" align="right">
            <template #default="{ row }">
              <span class="nowrap-cell">{{ formatTokens(row.llmTokenUsed) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="覆盖率（可交互元素）" width="190">
            <template #default="{ row }">
              <span class="nowrap-cell">{{ formatCoverage(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="触发" width="75">
            <template #default="{ row }">{{ row.triggerType === 'SCHEDULED' ? '定时' : '手动' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="185">
            <template #default="{ row }">
              <span class="nowrap-cell">{{ formatBeijingDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="145" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTask(row)">详情</el-button>
              <el-button v-if="isTaskActive(row)" link type="warning" @click="cancelHistoryTask(row)">取消</el-button>
              <el-button v-else link type="danger" @click="removeHistoryTask(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="history-pager">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="historyPage"
            :page-size="historySize"
            :page-sizes="[10, 20, 50]"
            :total="historyTotal"
            @current-change="handleHistoryPageChange"
            @size-change="handleHistorySizeChange"
          />
        </div>
      </div>
    </el-drawer>

    <AiModelConfigDrawer
      v-model="modelDrawerVisible"
      :space-id="spaceId"
      @changed="onModelChanged"
    />
    <PatrolTaskDrawer
      v-model="taskDrawerVisible"
      :space-id="spaceId"
      :task-id="activeTaskId"
      @changed="loadScenes"
    />
  </ListPageShell>
</template>

<style scoped>
/* 多行单元格（名称+描述、预算三行）保持自适应行高 */
.patrol-table :deep(.el-table__row),
.patrol-table :deep(td.el-table__cell) {
  height: auto;
}
.stack-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.stack-cell > * {
  min-width: 0;
}
.scene-name {
  font-weight: 600;
  color: #0f172a;
}
.scene-sub {
  margin-top: 2px;
  color: #94a3b8;
  font-size: 12px;
}
.model-line {
  display: flex;
  gap: 6px;
  min-width: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.7;
}
.model-line :deep(.cell-tooltip) {
  flex: 1;
  min-width: 0;
}
.model-key {
  color: #94a3b8;
  flex: 0 0 auto;
}
.history-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--app-border);
}
.nowrap-cell {
  white-space: nowrap;
}
.status-tag {
  white-space: nowrap;
}
:global(.history-drawer) {
  max-width: 96vw;
}
.history-table {
  min-width: 1180px;
}
.history-table :deep(.el-table__header .cell) {
  white-space: nowrap;
}
.history-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}
.summary-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%);
}
.summary-label {
  color: #64748b;
  font-size: 13px;
}
.summary-value {
  color: #0f172a;
  font-size: 24px;
  line-height: 1.2;
}
.summary-sub {
  color: #94a3b8;
  font-size: 12px;
}

/* ── 表单弹窗 ── */
.patrol-form-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}
.patrol-form {
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
.form-section--last {
  margin-bottom: 4px;
}
.section-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}
.section-dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 50%;
  background: #14b8a6;
  flex: 0 0 auto;
}
.section-dot--blue {
  background: #3b82f6;
}
.section-dot--amber {
  background: #f59e0b;
}
.section-dot--green {
  background: #22c55e;
}
.section-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.section-head p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  column-gap: 16px;
}
.span-12 {
  grid-column: span 12;
}
.span-8 {
  grid-column: span 8;
}
.span-6 {
  grid-column: span 6;
}
.span-5 {
  grid-column: span 5;
}
.span-4 {
  grid-column: span 4;
}
.span-3 {
  grid-column: span 3;
}
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
.form-grid :deep(.el-select),
.form-grid :deep(.el-input-number),
.form-grid :deep(.el-input) {
  width: 100%;
}
.switch-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  color: #475569;
  font-size: 13px;
}
.checkbox-line {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}
.auth-switches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}
.auth-switch-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid #e8edf3;
  border-radius: 12px;
  background: #f8fafc;
}
.auth-switch-card--danger {
  border-color: #fee2e2;
  background: #fff7f7;
}
.auth-switch-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.auth-switch-hint {
  margin-top: 2px;
  font-size: 12px;
  color: #94a3b8;
}
.section-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.dialog-footer__hint {
  font-size: 12px;
  color: #94a3b8;
}
</style>
