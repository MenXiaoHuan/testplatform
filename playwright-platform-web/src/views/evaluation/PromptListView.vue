<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { get, post, put, del } from '../../api/http'
import CellTooltip from '../../components/list/CellTooltip.vue'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import { formatBeijingDateTime } from '../../utils/datetime'

const route = useRoute()
const spaceId = computed(() => Number(route.params.spaceId))

interface PromptKeySummary {
  promptKey: string
  name: string
  description?: string
  versionCount: number
  activeVersion?: string
  updatedAt?: string
}

interface MessageRow {
  role: 'system' | 'user' | 'assistant'
  content: string
}

const TYPE_OPTIONS = [
  { value: 'SYSTEM', label: '系统提示词' },
  { value: 'JUDGING', label: '评审提示词' },
  { value: 'EXTRACTION', label: '抽取提示词' },
  { value: 'CUSTOM', label: '自定义' },
]

const keys = ref<PromptKeySummary[]>([])
const versions = ref<any[]>([])
const selectedKey = ref('')
const loadingKeys = ref(false)
const loadingVersions = ref(false)

const editorVisible = ref(false)
const editorMode = ref<'create' | 'new-version' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)

const viewerVisible = ref(false)
const viewerVersion = ref<any>(null)

const form = reactive({
  promptKey: '',
  name: '',
  description: '',
  promptType: 'SYSTEM',
  versionLabel: 'v1',
  changeLog: '',
  messages: [{ role: 'system', content: '' }] as MessageRow[],
  variables: [] as string[],
})

const selectedSummary = computed(() => keys.value.find((item) => item.promptKey === selectedKey.value))
const pullUrl = computed(
  () => `/api/evaluation/prompts/active/${selectedKey.value || '{promptKey}'}?spaceId=${spaceId.value}`,
)
const editorTitle = computed(() => {
  if (editorMode.value === 'create') return '新建提示词'
  if (editorMode.value === 'new-version') return `新建版本 · ${form.name || form.promptKey}`
  return `编辑草稿 · ${form.name || form.promptKey}`
})

function statusTag(status?: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DEPRECATED') return 'info'
  return 'warning'
}

function statusLabel(status?: string) {
  if (status === 'ACTIVE') return '已发布'
  if (status === 'DEPRECATED') return '已废弃'
  return '草稿'
}

function typeLabel(type?: string) {
  return TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type ?? '-'
}

/** 后端时间统一按北京时间（UTC+8）展示。 */
function formatDateTime(value?: string | null) {
  return formatBeijingDateTime(value, '-')
}

/** 内容可能被省略号截断时，才启用悬浮提示。 */
function needsTooltip(value?: string | null, limit = 18) {
  return (value?.length ?? 0) > limit
}

function safeParseList(raw: any): any[] {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = JSON.parse(String(raw))
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function safeParseMessages(raw: any): MessageRow[] {
  const list = safeParseList(raw)
  const messages = list
    .filter((item) => item && typeof item === 'object')
    .map((item) => ({
      role: (['system', 'user', 'assistant'].includes(item.role) ? item.role : 'user') as MessageRow['role'],
      content: String(item.content ?? ''),
    }))
  return messages.length > 0 ? messages : [{ role: 'system', content: '' }]
}

async function loadKeys() {
  if (!Number.isFinite(spaceId.value)) {
    loadingKeys.value = false
    showAppToast('空间 ID 无效，请刷新页面', 'error')
    return
  }
  loadingKeys.value = true
  try {
    const list = await get<PromptKeySummary[]>('/evaluation/prompts/keys', { params: { spaceId: spaceId.value } })
    keys.value = list ?? []
    if (!selectedKey.value && keys.value.length > 0) {
      await selectKey(keys.value[0].promptKey)
    } else if (selectedKey.value && !keys.value.some((item) => item.promptKey === selectedKey.value)) {
      selectedKey.value = ''
      versions.value = []
      if (keys.value.length > 0) {
        await selectKey(keys.value[0].promptKey)
      }
    }
  } catch (e: any) {
    showAppToast(`加载提示词列表失败: ${toErrorMessage(e, '请稍后重试')}`, 'error')
  } finally {
    loadingKeys.value = false
  }
}

async function selectKey(key: string) {
  selectedKey.value = key
  await loadVersions()
}

async function loadVersions() {
  if (!selectedKey.value) {
    versions.value = []
    return
  }
  loadingVersions.value = true
  try {
    const list = await get<any[]>('/evaluation/prompts/list', {
      params: { promptKey: selectedKey.value, spaceId: spaceId.value },
    })
    versions.value = list ?? []
  } catch (e: any) {
    versions.value = []
    showAppToast(`加载版本链失败: ${toErrorMessage(e, '请稍后重试')}`, 'error')
  } finally {
    loadingVersions.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    promptKey: '',
    name: '',
    description: '',
    promptType: 'SYSTEM',
    versionLabel: 'v1',
    changeLog: '',
    messages: [{ role: 'system', content: '' }],
    variables: [],
  })
}

function openCreate() {
  resetForm()
  editorMode.value = 'create'
  editingId.value = null
  editorVisible.value = true
}

function nextVersionLabel(): string {
  const numbers = versions.value
    .map((item) => /^v(\d+)$/.exec(String(item.versionLabel ?? ''))?.[1])
    .filter((value): value is string => Boolean(value))
    .map(Number)
  const next = numbers.length > 0 ? Math.max(...numbers) + 1 : 1
  return `v${next}`
}

function openNewVersion() {
  const summary = selectedSummary.value
  if (!summary) return
  // 以最新版本内容为底稿
  const base = versions.value[0]
  Object.assign(form, {
    promptKey: summary.promptKey,
    name: base?.name ?? summary.name,
    description: base?.description ?? summary.description ?? '',
    promptType: base?.promptType ?? 'SYSTEM',
    versionLabel: nextVersionLabel(),
    changeLog: '',
    messages: safeParseMessages(base?.template),
    variables: safeParseList(base?.variables).map(String),
  })
  editorMode.value = 'new-version'
  editingId.value = null
  editorVisible.value = true
}

function openEdit(version: any) {
  Object.assign(form, {
    promptKey: version.promptKey,
    name: version.name ?? '',
    description: version.description ?? '',
    promptType: version.promptType ?? 'SYSTEM',
    versionLabel: version.versionLabel ?? '',
    changeLog: version.changeLog ?? '',
    messages: safeParseMessages(version.template),
    variables: safeParseList(version.variables).map(String),
  })
  editorMode.value = 'edit'
  editingId.value = Number(version.id)
  editorVisible.value = true
}

function openViewer(version: any) {
  viewerVersion.value = {
    ...version,
    parsedMessages: safeParseMessages(version.template),
    parsedVariables: safeParseList(version.variables).map(String),
  }
  viewerVisible.value = true
}

function addMessage(role: MessageRow['role']) {
  form.messages.push({ role, content: '' })
}

function removeMessage(index: number) {
  if (form.messages.length <= 1) {
    showAppToast('至少保留一条消息', 'warning')
    return
  }
  form.messages.splice(index, 1)
}

function autoExtractVariables() {
  const found = new Set<string>()
  const pattern = /\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g
  for (const message of form.messages) {
    let match: RegExpExecArray | null
    while ((match = pattern.exec(message.content)) !== null) {
      found.add(match[1])
    }
  }
  form.variables = Array.from(found)
  showAppToast(found.size > 0 ? `已提取 ${found.size} 个变量` : '没有发现 {{变量}} 占位符', found.size > 0 ? 'success' : 'info')
}

async function submit() {
  if (!form.name.trim()) {
    showAppToast('请填写提示词名称', 'warning')
    return
  }
  if (editorMode.value !== 'edit' && !form.versionLabel.trim()) {
    showAppToast('请填写版本号，如 v1', 'warning')
    return
  }
  const messages = form.messages
    .map((item) => ({ role: item.role, content: item.content.trim() }))
    .filter((item) => item.content.length > 0)
  if (messages.length === 0) {
    showAppToast('至少填写一条消息内容', 'warning')
    return
  }

  saving.value = true
  try {
    const template = JSON.stringify(messages)
    const variables = JSON.stringify(form.variables)
    if (editorMode.value === 'edit' && editingId.value) {
      await put(`/evaluation/prompts/versions/${editingId.value}`, {
        name: form.name.trim(),
        description: form.description.trim(),
        template,
        variables,
        changeLog: form.changeLog.trim(),
      }, { params: { spaceId: spaceId.value } })
      showAppToast('草稿已保存', 'success')
      await loadVersions()
    } else {
      const payload: Record<string, any> = {
        spaceId: spaceId.value,
        name: form.name.trim(),
        description: form.description.trim(),
        promptType: form.promptType,
        template,
        variables,
        versionLabel: form.versionLabel.trim(),
        changeLog: form.changeLog.trim(),
        createdBy: 'console',
      }
      // 新建提示词不传 promptKey，由后端生成；新建版本沿用当前提示词的标识
      if (editorMode.value === 'new-version') payload.promptKey = form.promptKey.trim()
      const created = await post<any>('/evaluation/prompts', payload)
      showAppToast(editorMode.value === 'create' ? '提示词已创建（草稿）' : '新版本已创建（草稿）', 'success')
      const key = created?.promptKey ?? form.promptKey.trim()
      await loadKeys()
      if (key) await selectKey(key)
    }
    editorVisible.value = false
  } catch (e: any) {
    showAppToast(`保存失败: ${toErrorMessage(e, '请检查表单内容')}`, 'error')
  } finally {
    saving.value = false
  }
}

async function changeVersionStatus(version: any, status: 'DRAFT' | 'ACTIVE' | 'DEPRECATED') {
  const label = statusLabel(status)
  try {
    await put(`/evaluation/prompts/${version.id}/status`, undefined, {
      params: { spaceId: spaceId.value, status },
    })
    showAppToast(`状态已改为「${label}」`, 'success')
    await loadKeys()
    await loadVersions()
  } catch (e: any) {
    showAppToast(`状态变更失败: ${toErrorMessage(e, '请稍后重试')}`, 'error')
  }
}

async function removeVersion(version: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除版本「${version.versionLabel}」（${statusLabel(version.status)}）？`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await del(`/evaluation/prompts/versions/${version.id}`, { params: { spaceId: spaceId.value } })
    showAppToast('版本已删除', 'success')
    await loadKeys()
    await loadVersions()
  } catch (e: any) {
    showAppToast(`删除失败: ${toErrorMessage(e, '请稍后重试')}`, 'error')
  }
}

async function removePromptGroup(item: PromptKeySummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除提示词「${item.name || item.promptKey}」及其全部 ${item.versionCount} 个版本？删除后 Pull API 将立即失效。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await del(`/evaluation/prompts/keys/${encodeURIComponent(item.promptKey)}`, {
      params: { spaceId: spaceId.value },
    })
    showAppToast('提示词已删除', 'success')
    await loadKeys()
  } catch (e: any) {
    showAppToast(`删除失败: ${toErrorMessage(e, '请稍后重试')}`, 'error')
  }
}

async function copyPullUrl() {
  try {
    await navigator.clipboard.writeText(pullUrl.value)
    showAppToast('Pull 地址已复制', 'success')
  } catch {
    showAppToast(pullUrl.value, 'info')
  }
}

onMounted(loadKeys)
</script>

<template>
  <div class="page">
    <div class="prompt-layout">
      <!-- 左：提示词列表 -->
      <aside class="key-panel">
        <div class="panel-head">
          <span class="panel-title">提示词（{{ keys.length }}）</span>
          <el-button size="small" type="primary" @click="openCreate">+ 新建</el-button>
        </div>

        <div v-if="loadingKeys" class="mini-empty">加载中…</div>
        <div v-else-if="keys.length === 0" class="mini-empty">
          还没有提示词<br />
          点右上角「新建」创建第一个
        </div>
        <div v-else class="key-list">
          <div
            v-for="item in keys"
            :key="item.promptKey"
            class="key-item"
            :class="{ 'key-item--active': selectedKey === item.promptKey }"
            @click="selectKey(item.promptKey)"
          >
            <el-button
              class="key-delete"
              link
              type="danger"
              title="删除提示词"
              @click.stop="removePromptGroup(item)"
            >×</el-button>
            <el-tooltip
              :content="item.name || item.promptKey"
              placement="top"
              :show-after="300"
              :disabled="!needsTooltip(item.name || item.promptKey, 12)"
            >
              <div class="key-item__name">{{ item.name || item.promptKey }}</div>
            </el-tooltip>
            <el-tooltip
              :content="item.promptKey"
              placement="top"
              :show-after="300"
              :disabled="!needsTooltip(item.promptKey, 22)"
            >
              <div class="key-item__code">{{ item.promptKey }}</div>
            </el-tooltip>
            <div class="key-item__meta">
              <el-tag v-if="item.activeVersion" type="success" size="small" effect="plain">
                ACTIVE {{ item.activeVersion }}
              </el-tag>
              <span class="muted">{{ item.versionCount }} 个版本</span>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右：版本链 -->
      <section class="version-panel">
        <div v-if="!selectedKey" class="big-empty">
          <div class="big-empty__icon">📝</div>
          <p class="big-empty__title">选择左侧提示词查看版本链</p>
          <p class="big-empty__desc">或者新建一个提示词，平台负责版本管理与对外提供拉取接口</p>
          <el-button type="primary" @click="openCreate">+ 新建提示词</el-button>
        </div>

        <template v-else>
          <div class="version-head">
            <div>
              <el-tooltip
                :content="selectedSummary?.name || selectedKey"
                placement="top"
                :show-after="300"
                :disabled="!needsTooltip(selectedSummary?.name || selectedKey, 22)"
              >
                <div class="version-head__name">{{ selectedSummary?.name || selectedKey }}</div>
              </el-tooltip>
              <el-tooltip
                :content="selectedKey"
                placement="top"
                :show-after="300"
                :disabled="!needsTooltip(selectedKey, 30)"
              >
                <div class="version-head__key">{{ selectedKey }}</div>
              </el-tooltip>
            </div>
            <div class="version-head__actions">
              <el-button size="small" @click="loadVersions">刷新</el-button>
              <el-button size="small" type="primary" @click="openNewVersion">+ 新建版本</el-button>
            </div>
          </div>

          <div v-if="loadingVersions" class="mini-empty">加载中…</div>
          <el-table v-else :data="versions" stripe class="version-table">
            <el-table-column label="版本" width="100">
              <template #default="{ row }">
                <CellTooltip :content="row.versionLabel" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <span class="status-cell">
                  <el-tag :type="statusTag(row.status)" size="small" class="status-tag">{{ statusLabel(row.status) }}</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <CellTooltip :content="typeLabel(row.promptType)" />
              </template>
            </el-table-column>
            <el-table-column label="变更说明" min-width="130">
              <template #default="{ row }">
                <CellTooltip :content="row.changeLog || '-'" />
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">
                <CellTooltip :content="formatDateTime(row.createdAt)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link size="small" @click="openViewer(row)">查看</el-button>
                  <el-button v-if="row.status === 'DRAFT'" link size="small" @click="openEdit(row)">编辑</el-button>
                  <el-dropdown trigger="click" @command="changeVersionStatus(row, $event)">
                    <el-button link size="small">状态 ▾</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="row.status !== 'DRAFT'" command="DRAFT">设为草稿</el-dropdown-item>
                        <el-dropdown-item v-if="row.status !== 'ACTIVE'" command="ACTIVE">发布</el-dropdown-item>
                        <el-dropdown-item v-if="row.status !== 'DEPRECATED'" command="DEPRECATED">废弃</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button link size="small" type="danger" @click="removeVersion(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pull-card">
            <div class="pull-card__head">
              <span>外部调用（Pull API）</span>
              <el-button link size="small" @click="copyPullUrl">复制地址</el-button>
            </div>
            <code class="pull-card__url">{{ pullUrl }}</code>
            <p class="pull-card__hint">
              外部系统按 promptKey 拉取当前 ACTIVE 版本；返回 template 的 JSON 消息数组。
              支持 <code v-pre>If-None-Match</code> 304 协商缓存。
            </p>
          </div>
        </template>
      </section>
    </div>

    <!-- ============ 编辑器 ============ -->
    <el-dialog
      v-model="editorVisible"
      :title="editorTitle"
      width="900px"
      top="4vh"
      class="app-form-dialog"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="form" label-position="top" class="app-form">
        <section class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot" />
            <div>
              <h4>基本信息</h4>
              <p>名称、类型与版本信息</p>
            </div>
            <code v-if="editorMode !== 'create'" class="prompt-key">{{ form.promptKey }}</code>
          </div>
          <div class="app-form-grid">
            <el-form-item label="名称" required class="app-span-12">
              <el-input v-model="form.name" placeholder="例如：客服系统提示词" maxlength="256" />
            </el-form-item>
            <el-form-item label="描述" class="app-span-12">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                maxlength="1024"
                placeholder="这个提示词用于什么场景"
              />
            </el-form-item>
            <el-form-item label="类型" class="app-span-6">
              <el-select v-if="editorMode !== 'edit'" v-model="form.promptType" style="width: 100%">
                <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-tag v-else type="info" size="small" effect="plain">{{ typeLabel(form.promptType) }}</el-tag>
            </el-form-item>
            <el-form-item v-if="editorMode !== 'edit'" label="版本号" required class="app-span-6">
              <el-input v-model="form.versionLabel" placeholder="v1" />
              <div class="field-hint">同一提示词下版本号唯一。</div>
            </el-form-item>
            <el-form-item v-if="editorMode !== 'create'" label="变更说明" class="app-span-12">
              <el-input v-model="form.changeLog" placeholder="本次改了什么" maxlength="1024" />
            </el-form-item>
          </div>
          <div v-if="editorMode === 'create'" class="auto-key-hint">
            保存后由平台自动生成唯一标识，用于 Pull API 拉取。
          </div>
        </section>

        <section class="app-form-section">
          <div class="app-section-head">
            <span class="app-section-dot app-section-dot--blue" />
            <div>
              <h4>提示词内容</h4>
              <p>按消息顺序组装，可使用变量占位符</p>
            </div>
          </div>
          <div class="message-editor">
            <div v-for="(message, index) in form.messages" :key="index" class="message-card">
              <div class="message-card__head">
                <el-select v-model="message.role" size="small" style="width: 120px">
                  <el-option label="system" value="system" />
                  <el-option label="user" value="user" />
                  <el-option label="assistant" value="assistant" />
                </el-select>
                <el-button link type="danger" @click="removeMessage(index)">删除</el-button>
              </div>
              <el-input
                v-model="message.content"
                type="textarea"
                :autosize="{ minRows: 4, maxRows: 12 }"
                placeholder="消息内容，可使用变量占位符"
              />
            </div>
            <div class="message-actions">
              <el-button link type="primary" @click="addMessage('system')">+ system</el-button>
              <el-button link type="primary" @click="addMessage('user')">+ user</el-button>
              <el-button link type="primary" @click="addMessage('assistant')">+ assistant</el-button>
            </div>
          </div>
        </section>

        <section class="app-form-section app-form-section--last">
          <div class="app-section-head">
            <span class="app-section-dot app-section-dot--amber" />
            <div>
              <h4>变量</h4>
              <p>外部调用时需要填入的变量</p>
            </div>
            <el-button class="app-section-action" link type="primary" @click="autoExtractVariables">从内容自动提取</el-button>
          </div>
          <div class="app-form-grid">
            <div class="app-span-12">
              <el-select
                v-model="form.variables"
                multiple
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                placeholder="外部调用时填入的变量，如 context / user_input"
              />
              <div class="field-hint">
                在消息内容里写 <code v-pre>{{context}}</code> 这样的占位符，外部服务组装时替换。
              </div>
            </div>
          </div>
        </section>
      </el-form>

      <template #footer>
        <div class="app-form-footer">
          <span class="app-form-hint">保存后为草稿状态，发布后才可通过 Pull API 获取</span>
          <div>
            <el-button @click="editorVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit">保存草稿</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- ============ 查看器 ============ -->
    <el-dialog v-model="viewerVisible" :title="`${viewerVersion?.promptKey} · ${viewerVersion?.versionLabel}`" width="720px">
      <div class="viewer-meta">
        <el-tag :type="statusTag(viewerVersion?.status)" size="small">{{ statusLabel(viewerVersion?.status) }}</el-tag>
        <span class="muted">{{ typeLabel(viewerVersion?.promptType) }}</span>
        <span class="muted">创建人：{{ viewerVersion?.createdBy }}</span>
      </div>
      <div v-if="viewerVersion?.parsedVariables?.length" class="viewer-vars">
        <span class="muted">变量：</span>
        <el-tag v-for="item in viewerVersion.parsedVariables" :key="item" size="small" effect="plain">{{ item }}</el-tag>
      </div>
      <div class="viewer-messages">
        <div v-for="(message, index) in viewerVersion?.parsedMessages" :key="index" class="viewer-message">
          <div class="viewer-message__role">{{ message.role }}</div>
          <pre class="viewer-message__content">{{ message.content }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="viewerVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1280px; }

.prompt-layout { display: flex; gap: 16px; align-items: flex-start; }
.key-panel { width: 300px; flex-shrink: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.panel-title { font-weight: 600; color: #0f172a; font-size: 14px; }
.key-list { display: flex; flex-direction: column; gap: 8px; max-height: 620px; overflow: auto; }
.key-item {
  position: relative; border: 1px solid #e2e8f0; border-radius: 10px;
  padding: 10px 34px 10px 12px; cursor: pointer; transition: all 0.15s;
}
.key-item:hover { border-color: #99f6e4; background: #f0fdfa; }
.key-item--active { border-color: #0f766e; background: #ecfdf5; }
.key-delete {
  position: absolute; top: 4px; right: 4px; z-index: 1;
  padding: 0 4px; font-size: 18px; line-height: 1;
  opacity: 0; transition: opacity 0.15s;
}
.key-item:hover .key-delete,
.key-item:focus-within .key-delete { opacity: 1; }
.key-item__name {
  font-weight: 600; color: #0f172a; font-size: 13.5px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.key-item__code {
  margin-top: 2px; color: #64748b;
  font-family: ui-monospace, Menlo, monospace; font-size: 12px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.key-item__meta { display: flex; align-items: center; gap: 8px; margin-top: 6px; }

.version-panel { flex: 1; min-width: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; }
.version-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.version-head__name {
  max-width: 420px; font-size: 16px; font-weight: 600; color: #0f172a;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.version-head__key {
  max-width: 420px; margin-top: 2px; color: #64748b;
  font-family: ui-monospace, Menlo, monospace; font-size: 12px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.version-head__actions { display: flex; gap: 8px; }

.version-table :deep(.cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-actions { display: inline-flex; align-items: center; gap: 12px; white-space: nowrap; }
.table-actions :deep(.el-button + .el-button) { margin-left: 0; }

.status-cell { display: inline-flex; align-items: center; line-height: 1; white-space: nowrap; }
.status-tag { white-space: nowrap; }
.status-cell::before,
.status-cell::after { content: none !important; }

.pull-card { margin-top: 16px; border: 1px dashed #cbd5e1; border-radius: 10px; padding: 12px 14px; background: #f8fafc; }
.pull-card__head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #334155; font-size: 13px; }
.pull-card__url { display: block; margin-top: 8px; font-size: 12.5px; color: #0f766e; word-break: break-all; }
.pull-card__hint { margin: 6px 0 0; font-size: 12px; color: #94a3b8; line-height: 1.7; }
.pull-card__hint code { background: #e2e8f0; padding: 0 4px; border-radius: 4px; }

.big-empty { padding: 90px 20px; text-align: center; }
.big-empty__icon { font-size: 46px; margin-bottom: 8px; }
.big-empty__title { font-size: 16px; font-weight: 600; color: #334155; margin: 0 0 4px; }
.big-empty__desc { color: #64748b; font-size: 13px; margin: 0 0 16px; }
.mini-empty { padding: 30px 12px; text-align: center; color: #94a3b8; font-size: 13px; line-height: 1.8; }
.muted { color: #94a3b8; font-size: 12px; }

.prompt-key {
  margin-left: auto;
  padding: 2px 10px; border: 1px solid #99f6e4; border-radius: 999px;
  background: #ecfdf5; color: #0f766e; font-family: ui-monospace, Menlo, monospace;
  font-size: 12px; font-weight: 500;
}
.auto-key-hint { margin-top: 2px; font-size: 12px; color: #64748b; line-height: 1.6; }

.field-hint { font-size: 12px; color: #94a3b8; line-height: 1.7; margin-top: 2px; }
.field-hint code { background: #f1f5f9; color: #0f766e; padding: 0 4px; border-radius: 4px; }

.message-editor { width: 100%; display: flex; flex-direction: column; gap: 10px; }
.message-card { padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
.message-card__head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.message-actions { display: flex; gap: 12px; }

.viewer-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.viewer-vars { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.viewer-messages { display: flex; flex-direction: column; gap: 12px; max-height: 420px; overflow: auto; }
.viewer-message { border: 1px solid #e2e8f0; border-radius: 10px; overflow: hidden; }
.viewer-message__role { background: #f8fafc; padding: 6px 12px; font-size: 12px; color: #0f766e; font-weight: 600; }
.viewer-message__content { margin: 0; padding: 12px; font-size: 12.5px; color: #334155; white-space: pre-wrap; word-break: break-word; font-family: inherit; }
</style>
