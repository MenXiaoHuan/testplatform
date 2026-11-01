<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { get, post, put, del } from '../../api/http'
import { toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'
import { formatBeijingDateTime } from '../../utils/datetime'

const route = useRoute()
const spaceId = computed(() => Number(route.params.spaceId))

interface Dataset {
  id: number
  spaceId: number
  name: string
  version?: string
  description?: string
  scenarioTags?: string
  recommendedTargetTypes?: string
  recommendedEvaluators?: string
  externalSource?: string
  builtinKey?: string
  isBuiltin?: boolean
  syncStatus?: 'IDLE' | 'SYNCING' | 'READY' | 'ERROR'
  lastSyncedAt?: string
  syncError?: string
  totalSamples?: number
  annotatedSamples?: number
  createdAt?: string
}

interface Sample {
  id: number
  datasetId: number
  sampleIndex: number
  inputText: string
  goldenAnswer?: string
  keywords?: string
  referenceContext?: string
  metadata?: string
  scenarioTags?: string
}

const items = ref<Dataset[]>([])
const selectedId = ref<number | null>(null)
const dataset = ref<Dataset | null>(null)
const samples = ref<Sample[]>([])
const sampleTotal = ref(0)
const samplePage = ref(1)
const sampleSize = ref(20)
const loadingList = ref(false)
const loadingDetail = ref(false)
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const saving = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importDatasetId = ref<number | null>(null)
const importDatasetName = ref('')

const sampleDialogVisible = ref(false)
const sampleSaving = ref(false)
const editingSampleId = ref<number | null>(null)
const editingSample = ref<Sample | null>(null)
const sampleViewerVisible = ref(false)
const viewingSample = ref<Sample | null>(null)
const sampleForm = reactive({
  inputText: '',
  goldenAnswer: '',
  keywords: [] as string[],
  referenceContext: '',
})

const form = reactive({
  name: '',
  version: 'v1',
  description: '',
  scenarioTags: [] as string[],
  recommendedTargetTypes: ['LLM_API', 'HTTP_AGENT'],
})

const selectedSummary = computed(() => items.value.find((item) => item.id === selectedId.value) ?? dataset.value)
const scenarioTags = computed(() => parseTags(dataset.value?.scenarioTags))
const recommendedTargets = computed(() => parseTags(dataset.value?.recommendedTargetTypes))

function parseTags(raw?: string): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

function keywordsOf(raw?: string): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

function targetLabel(value: string) {
  if (value === 'LLM_API') return 'LLM 接口'
  if (value === 'HTTP_AGENT') return '外部 Agent'
  if (value === 'BUILTIN_AGENT') return '内置 Agent'
  return value
}

async function loadList() {
  if (!Number.isFinite(spaceId.value)) {
    showAppToast('空间 ID 无效，请刷新页面', 'error')
    return
  }
  loadingList.value = true
  try {
    items.value = await get<Dataset[]>('/evaluation/datasets', { params: { spaceId: spaceId.value } }) ?? []
    if (!items.value.length) {
      selectedId.value = null
      dataset.value = null
      samples.value = []
      sampleTotal.value = 0
      return
    }
    if (!selectedId.value || !items.value.some((item) => item.id === selectedId.value)) {
      await selectDataset(items.value[0].id)
    }
  } catch (e: any) {
    showAppToast(`加载失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    loadingList.value = false
  }
}

async function selectDataset(id: number) {
  if (selectedId.value === id && dataset.value) return
  selectedId.value = id
  samplePage.value = 1
  await Promise.all([loadDataset(id), loadSamples(id)])
}

async function loadDataset(id = selectedId.value) {
  if (!id) return
  try {
    dataset.value = await get<Dataset>(`/evaluation/datasets/${id}`, {
      params: { spaceId: spaceId.value },
    })
    startSyncPollingIfNeeded()
  } catch (e: any) {
    dataset.value = null
    showAppToast(`加载评测集详情失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

async function loadSamples(id = selectedId.value) {
  if (!id) return
  loadingDetail.value = true
  try {
    const result = await get<{ items: Sample[]; total: number }>(`/evaluation/datasets/${id}/samples`, {
      params: { spaceId: spaceId.value, page: samplePage.value, size: sampleSize.value },
    })
    samples.value = result?.items ?? []
    sampleTotal.value = result?.total ?? 0
    if (!samples.value.length && samplePage.value > 1) {
      samplePage.value -= 1
      await loadSamples(id)
    }
  } catch (e: any) {
    samples.value = []
    sampleTotal.value = 0
    showAppToast(`加载样本失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    loadingDetail.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    name: '',
    version: 'v1',
    description: '',
    scenarioTags: [],
    recommendedTargetTypes: ['LLM_API', 'HTTP_AGENT'],
  })
  dialogVisible.value = true
}

async function submitCreate() {
  if (!form.name.trim()) {
    showAppToast('请填写评测集名称', 'warning')
    return
  }
  saving.value = true
  try {
    const created = await post<Dataset>('/evaluation/datasets', {
      spaceId: spaceId.value,
      name: form.name.trim(),
      version: form.version.trim() || 'v1',
      description: form.description.trim(),
      scenarioTags: JSON.stringify(form.scenarioTags),
      recommendedTargetTypes: JSON.stringify(form.recommendedTargetTypes),
    })
    dialogVisible.value = false
    showAppToast('评测集已创建', 'success')
    await loadList()
    if (created?.id) await selectDataset(created.id)
  } catch (e: any) {
    showAppToast(`创建失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    saving.value = false
  }
}

async function removeDataset(item: Dataset) {
  if (item.isBuiltin) return
  try {
    await ElMessageBox.confirm(`确定删除评测集「${item.name}」及其全部样本？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await del(`/evaluation/datasets/${item.id}`, { params: { spaceId: spaceId.value } })
    showAppToast('评测集已删除', 'success')
    if (selectedId.value === item.id) {
      selectedId.value = null
      dataset.value = null
      samples.value = []
    }
    await loadList()
  } catch (e: any) {
    showAppToast(`删除失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

function openImport(item: Dataset) {
  importDatasetId.value = item.id
  importDatasetName.value = item.name
  importFile.value = null
  importDialogVisible.value = true
}

function handleImportFileChange(file: any) {
  importFile.value = file?.raw ?? null
}

function formatFileSize(size?: number) {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function importJsonl(datasetId: number) {
  if (!importFile.value) {
    showAppToast('请先选择 JSONL 文件', 'warning')
    return
  }
  const fd = new FormData()
  fd.append('file', importFile.value)
  importing.value = true
  try {
    await post(`/evaluation/datasets/${datasetId}/import-jsonl`, fd, {
      params: { spaceId: spaceId.value },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    showAppToast('JSONL 导入完成', 'success')
    importDialogVisible.value = false
    importFile.value = null
    await loadList()
    await loadDataset(datasetId)
    await loadSamples(datasetId)
  } catch (e: any) {
    showAppToast(`导入失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    importing.value = false
  }
}

function openCreateSample() {
  if (!selectedId.value) return
  editingSampleId.value = null
  editingSample.value = null
  Object.assign(sampleForm, { inputText: '', goldenAnswer: '', keywords: [], referenceContext: '' })
  sampleDialogVisible.value = true
}

function openEditSample(sample: Sample) {
  editingSampleId.value = sample.id
  editingSample.value = sample
  Object.assign(sampleForm, {
    inputText: sample.inputText ?? '',
    goldenAnswer: sample.goldenAnswer ?? '',
    keywords: keywordsOf(sample.keywords),
    referenceContext: sample.referenceContext ?? '',
  })
  sampleDialogVisible.value = true
}

function openSampleViewer(sample: Sample) {
  viewingSample.value = sample
  sampleViewerVisible.value = true
}

let syncTimer: ReturnType<typeof setTimeout> | null = null

function stopSyncPolling() {
  if (syncTimer) {
    clearTimeout(syncTimer)
    syncTimer = null
  }
}

/** 内置评测集由后端自动同步官方全集，前端仅在同步进行中轮询进度。 */
function startSyncPollingIfNeeded() {
  if (dataset.value?.isBuiltin && dataset.value.syncStatus === 'SYNCING') {
    if (!syncTimer) syncTimer = setTimeout(pollSync, 3000)
    return
  }
  stopSyncPolling()
}

function formatDateTime(value?: string | null) {
  return formatBeijingDateTime(value)
}

async function pollSync() {
  if (!selectedId.value) return
  try {
    const current = await get<Dataset>(`/evaluation/datasets/${selectedId.value}`, {
      params: { spaceId: spaceId.value },
    })
    dataset.value = current
    if (current.syncStatus === 'SYNCING') {
      syncTimer = setTimeout(pollSync, 4000)
      return
    }
    stopSyncPolling()
    if (current.syncStatus === 'READY') {
      samplePage.value = 1
      await loadDataset(selectedId.value)
      await loadSamples(selectedId.value)
      await loadList()
      showAppToast('官方全集已自动同步完成', 'success')
    } else if (current.syncStatus === 'ERROR') {
      showAppToast(`官方全集自动同步失败: ${current.syncError || '请检查网络与数据源'}`, 'error')
    }
  } catch {
    syncTimer = setTimeout(pollSync, 5000)
  }
}

onBeforeUnmount(stopSyncPolling)

async function submitSample() {
  if (!selectedId.value) return
  if (!sampleForm.inputText.trim()) {
    showAppToast('用例输入不能为空', 'warning')
    return
  }
  sampleSaving.value = true
  try {
    const payload = {
      inputText: sampleForm.inputText,
      goldenAnswer: sampleForm.goldenAnswer || null,
      keywords: sampleForm.keywords.length ? JSON.stringify(sampleForm.keywords) : null,
      referenceContext: sampleForm.referenceContext || null,
      metadata: editingSample.value?.metadata ?? null,
      scenarioTags: editingSample.value?.scenarioTags ?? dataset.value?.scenarioTags ?? null,
    }
    if (editingSampleId.value) {
      await put(`/evaluation/datasets/${selectedId.value}/samples/${editingSampleId.value}`, payload, {
        params: { spaceId: spaceId.value },
      })
      showAppToast('用例已更新', 'success')
    } else {
      await post(`/evaluation/datasets/${selectedId.value}/samples`, payload, {
        params: { spaceId: spaceId.value },
      })
      showAppToast('用例已新增', 'success')
    }
    sampleDialogVisible.value = false
    await loadDataset(selectedId.value)
    await loadSamples(selectedId.value)
  } catch (e: any) {
    showAppToast(`保存用例失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  } finally {
    sampleSaving.value = false
  }
}

async function removeSample(sample: Sample) {
  if (!selectedId.value) return
  try {
    await ElMessageBox.confirm(`确定删除用例 #${sample.sampleIndex + 1}？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await del(`/evaluation/datasets/${selectedId.value}/samples/${sample.id}`, {
      params: { spaceId: spaceId.value },
    })
    showAppToast('用例已删除', 'success')
    await loadDataset(selectedId.value)
    await loadSamples(selectedId.value)
  } catch (e: any) {
    showAppToast(`删除用例失败: ${toErrorMessage(e, '操作失败，请稍后重试')}`, 'error')
  }
}

function handleSamplePage(value: number) {
  samplePage.value = value
  loadSamples()
}

function handleSampleSize(value: number) {
  sampleSize.value = value
  samplePage.value = 1
  loadSamples()
}

function preview(value?: string | null, limit = 80) {
  if (!value) return '—'
  const text = String(value).replace(/\s+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="dataset-layout">
      <aside class="dataset-panel">
        <div class="panel-head">
          <span class="panel-title">评测集（{{ items.length }}）</span>
          <el-button size="small" type="primary" @click="openCreate">+ 新建</el-button>
        </div>

        <div v-if="loadingList" class="mini-empty">加载中…</div>
        <div v-else-if="items.length === 0" class="mini-empty">
          还没有评测集<br />
          点右上角「新建」创建第一个
        </div>
        <div v-else class="dataset-list">
          <div
            v-for="item in items"
            :key="item.id"
            class="dataset-item"
            :class="{ 'dataset-item--active': selectedId === item.id }"
            @click="selectDataset(item.id)"
          >
            <el-button
              v-if="!item.isBuiltin"
              class="dataset-delete"
              link
              type="danger"
              title="删除评测集"
              @click.stop="removeDataset(item)"
            >×</el-button>
            <div class="dataset-item__name">{{ item.name }}</div>
            <div class="dataset-item__meta">
              <el-tag v-if="item.isBuiltin" size="small" type="success" effect="plain">内置</el-tag>
              <span class="muted">{{ item.version || 'v1' }} · {{ item.totalSamples ?? 0 }} 条</span>
            </div>
          </div>
        </div>
      </aside>

      <section class="dataset-detail">
        <div v-if="!selectedId" class="big-empty">
          <div class="big-empty__title">选择左侧评测集查看样本</div>
          <div class="big-empty__desc">每个空间会自动内置主流公开评测集，也可以新建自己的评测集。</div>
          <el-button type="primary" @click="openCreate">+ 新建评测集</el-button>
        </div>

        <template v-else>
          <div class="detail-head">
            <div class="detail-title-wrap">
              <div class="detail-title">{{ selectedSummary?.name || dataset?.name || '评测集' }}</div>
              <div class="detail-meta">
                <el-tag size="small" type="info" effect="plain">{{ dataset?.version || 'v1' }}</el-tag>
                <el-tag v-if="dataset?.isBuiltin" size="small" type="success" effect="plain">内置</el-tag>
                <span class="muted">{{ dataset?.totalSamples ?? 0 }} 条样本</span>
              </div>
            </div>
            <div class="detail-actions">
              <template v-if="dataset && !dataset.isBuiltin">
                <el-button size="small" type="primary" plain @click="openCreateSample">+ 新增用例</el-button>
                <el-button size="small" type="primary" plain @click="openImport(dataset)">导入 JSONL</el-button>
              </template>
            </div>
          </div>

          <p v-if="dataset?.description" class="detail-desc">{{ dataset.description }}</p>
          <div v-if="dataset?.isBuiltin" class="builtin-hint">
            内置评测集由平台自动同步维护官方全集，无需手动操作，不支持新增、编辑、删除用例或导入 JSONL。
            <span v-if="dataset?.syncStatus === 'SYNCING'" class="sync-note sync-note--active">
              正在自动同步官方全集，样本稍后自动更新…
            </span>
            <span v-else-if="dataset?.syncStatus === 'ERROR'" class="sync-note sync-note--error">
              上次自动同步失败：{{ dataset?.syncError || '网络异常，平台会在下次启动时自动重试' }}
            </span>
            <span v-else-if="dataset?.lastSyncedAt" class="sync-note">
              官方全集已同步 · {{ formatDateTime(dataset?.lastSyncedAt) }}
            </span>
            <span v-else class="sync-note">官方全集将在后台自动同步</span>
          </div>

          <div class="detail-tags">
            <el-tag v-for="tag in scenarioTags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            <span v-if="recommendedTargets.length" class="recommend">
              推荐：{{ recommendedTargets.map(targetLabel).join(' / ') }}
            </span>
          </div>

          <div v-if="dataset?.externalSource" class="source-line">
            官方数据源：
            <a :href="dataset.externalSource" target="_blank" rel="noreferrer">{{ dataset.externalSource }}</a>
          </div>

          <el-table
            v-loading="loadingDetail"
            :data="samples"
            stripe
            class="sample-table"
            empty-text="暂无样本，可导入 JSONL"
          >
            <el-table-column label="ID" width="80">
              <template #default="{ row }">{{ row.sampleIndex + 1 }}</template>
            </el-table-column>
            <el-table-column label="输入" min-width="180">
              <template #default="{ row }">
                <el-tooltip :content="row.inputText || '—'" placement="top" effect="dark" :show-after="300">
                  <span class="cell-tooltip">{{ preview(row.inputText, 100) }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="参考答案" min-width="120">
              <template #default="{ row }">
                <el-tooltip :content="row.goldenAnswer || '—'" placement="top" effect="dark" :show-after="300">
                  <span class="cell-tooltip">{{ preview(row.goldenAnswer, 60) }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="关键词" min-width="150">
              <template #default="{ row }">
                <el-tooltip :content="keywordsOf(row.keywords).join('、') || '—'" placement="top" effect="dark" :show-after="300">
                  <span class="cell-tooltip">{{ preview(keywordsOf(row.keywords).join('、'), 24) }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="参考上下文" min-width="130">
              <template #default="{ row }">
                <el-tooltip :content="row.referenceContext || '—'" placement="top" effect="dark" :show-after="300">
                  <span class="cell-tooltip">{{ preview(row.referenceContext, 50) }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <div class="sample-actions">
                  <el-button link type="primary" size="small" @click="openSampleViewer(row)">查看</el-button>
                  <el-button
                    v-if="dataset && !dataset.isBuiltin"
                    link
                    size="small"
                    @click="openEditSample(row)"
                  >编辑</el-button>
                  <el-button
                    v-if="dataset && !dataset.isBuiltin"
                    link
                    type="danger"
                    size="small"
                    @click="removeSample(row)"
                  >删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="samplePage"
              :page-size="sampleSize"
              :total="sampleTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next"
              @current-change="handleSamplePage"
              @size-change="handleSampleSize"
            />
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" title="新建评测集" width="560px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：金融风控-反欺诈" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" placeholder="v1" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="说明这个评测集测什么、适用场景" />
        </el-form-item>
        <el-form-item label="场景标签">
          <el-select
            v-model="form.scenarioTags"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入后回车添加，如 数学 / 代码 / 中文"
          />
        </el-form-item>
        <el-form-item label="推荐目标类型">
          <el-select v-model="form.recommendedTargetTypes" multiple style="width: 100%">
            <el-option label="LLM 接口" value="LLM_API" />
            <el-option label="外部 Agent" value="HTTP_AGENT" />
            <el-option label="内置 Agent" value="BUILTIN_AGENT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sampleDialogVisible"
      :title="editingSampleId ? '编辑用例' : '新增用例'"
      width="680px"
      destroy-on-close
    >
      <el-form :model="sampleForm" label-position="top">
        <el-form-item label="输入" required>
          <el-input
            v-model="sampleForm.inputText"
            type="textarea"
            :rows="6"
            placeholder="用户输入 / 任务描述 / Prompt 变量内容"
          />
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input
            v-model="sampleForm.goldenAnswer"
            type="textarea"
            :rows="3"
            placeholder="期望输出、标准答案或参考代码；可留空"
          />
        </el-form-item>
        <el-form-item label="判定关键词（可选）">
          <el-select
            v-model="sampleForm.keywords"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入后回车；关键词匹配评估器会优先使用这里的关键词"
          />
        </el-form-item>
        <el-form-item label="参考上下文">
          <el-input
            v-model="sampleForm.referenceContext"
            type="textarea"
            :rows="2"
            placeholder="可供 Judge 参考的上下文、文档片段或评分说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sampleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sampleSaving" @click="submitSample">
          {{ editingSampleId ? '保存修改' : '新增用例' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sampleViewerVisible"
      :title="`用例 #${(viewingSample?.sampleIndex ?? 0) + 1}`"
      width="720px"
      destroy-on-close
    >
      <div class="viewer-block">
        <label class="viewer-label">输入</label>
        <pre class="viewer-pre">{{ viewingSample?.inputText || '—' }}</pre>
      </div>
      <div class="viewer-block">
        <label class="viewer-label">参考答案</label>
        <pre class="viewer-pre">{{ viewingSample?.goldenAnswer || '—' }}</pre>
      </div>
      <div class="viewer-block">
        <label class="viewer-label">判定关键词</label>
        <pre class="viewer-pre">{{ keywordsOf(viewingSample?.keywords).join('、') || '—' }}</pre>
      </div>
      <div class="viewer-block">
        <label class="viewer-label">参考上下文</label>
        <pre class="viewer-pre">{{ viewingSample?.referenceContext || '—' }}</pre>
      </div>
      <template #footer>
        <el-button @click="sampleViewerVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      :title="`导入 JSONL → ${importDatasetName}`"
      width="560px"
      destroy-on-close
    >
      <div class="import-body">
        <div class="import-file">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".jsonl,.json"
            :on-change="handleImportFileChange"
          >
            <el-button type="primary" plain>选择 .jsonl 文件</el-button>
          </el-upload>
          <div class="import-file-meta">
            <span v-if="importFile" class="import-file-name" :title="importFile.name">{{ importFile.name }}</span>
            <span v-else class="import-file-empty">未选择文件，支持 .jsonl / .json</span>
            <span v-if="importFile" class="import-file-size">{{ formatFileSize(importFile.size) }}</span>
          </div>
        </div>

        <div class="import-format">
          <div class="import-format-title">文件格式：每行一个 JSON 对象</div>
          <pre class="import-format-example">{"input":"问题或指令","golden_answer":"参考答案","keywords":["判定关键词"],"reference_context":"背景资料"}</pre>
          <div class="import-format-tip">keywords、reference_context 可省略；keywords 为字符串数组，关键词匹配评估器会优先使用。</div>
        </div>

        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="导入会覆盖该评测集现有样本，请确认文件内容后再继续。"
        />
      </div>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!importFile || !importDatasetId"
          @click="importDatasetId && importJsonl(importDatasetId)"
        >开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1280px; }

.dataset-layout { display: flex; gap: 16px; align-items: flex-start; }
.dataset-panel { width: 300px; flex-shrink: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.panel-title { font-weight: 600; color: #0f172a; font-size: 14px; }
.dataset-list { display: flex; flex-direction: column; gap: 8px; max-height: 620px; overflow: auto; }
.dataset-item {
  position: relative; border: 1px solid #e2e8f0; border-radius: 10px;
  padding: 10px 34px 10px 12px; cursor: pointer; transition: all 0.15s;
}
.dataset-item:hover { border-color: #99f6e4; background: #f0fdfa; }
.dataset-item--active { border-color: #0f766e; background: #ecfdf5; }
.dataset-delete {
  position: absolute; top: 4px; right: 4px; z-index: 1;
  padding: 0 4px; font-size: 18px; line-height: 1; opacity: 0; transition: opacity 0.15s;
}
.dataset-item:hover .dataset-delete,
.dataset-item:focus-within .dataset-delete { opacity: 1; }
.dataset-item__name {
  font-weight: 600; color: #0f172a; font-size: 13.5px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.dataset-item__meta { display: flex; align-items: center; gap: 8px; margin-top: 6px; }

.dataset-detail { flex: 1; min-width: 0; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; }
.detail-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.detail-title-wrap { min-width: 0; }
.detail-title {
  font-size: 16px; font-weight: 600; color: #0f172a;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.detail-meta { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.detail-actions { display: flex; gap: 8px; flex-shrink: 0; }
.detail-desc { margin: 12px 0 0; color: #475569; font-size: 13px; line-height: 1.7; }
.detail-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.recommend { color: #94a3b8; font-size: 12px; }
.source-line { margin-top: 8px; color: #94a3b8; font-size: 12px; word-break: break-all; }
.source-line a { color: #0f766e; text-decoration: none; }
.source-line a:hover { text-decoration: underline; }

.sample-table { margin-top: 14px; }
.sample-table :deep(.cell) { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cell-tooltip { display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.sample-actions { display: inline-flex; align-items: center; gap: 10px; white-space: nowrap; }
.sample-actions :deep(.el-button + .el-button) { margin-left: 0; }
.pager { display: flex; justify-content: flex-end; padding-top: 16px; }

.builtin-hint { margin-top: 8px; color: #b45309; font-size: 12px; line-height: 1.6; }
.sync-note { display: inline-block; margin-left: 6px; color: #64748b; }
.sync-note--active { color: #0f766e; }
.sync-note--error { color: #dc2626; }
.viewer-block + .viewer-block { margin-top: 14px; }
.viewer-label { display: block; margin-bottom: 5px; font-size: 12px; color: #64748b; }
.viewer-pre {
  margin: 0; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px;
  background: #f8fafc; color: #334155; font-size: 12.5px; line-height: 1.7;
  white-space: pre-wrap; word-break: break-word; max-height: 220px; overflow: auto;
}

.big-empty { padding: 90px 20px; text-align: center; }
.big-empty__title { font-size: 16px; font-weight: 600; color: #334155; margin-bottom: 6px; }
.big-empty__desc { color: #64748b; font-size: 13px; margin-bottom: 16px; }
.mini-empty { padding: 30px 12px; text-align: center; color: #94a3b8; font-size: 13px; line-height: 1.8; }
.muted { color: #94a3b8; font-size: 12px; }
.import-body { display: flex; flex-direction: column; gap: 14px; }

.import-file {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px;
  border: 1px dashed #cbd5e1; border-radius: 10px;
  background: #f8fafc;
}

.import-file-meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.import-file-name {
  font-size: 13px; color: #0f172a; font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.import-file-empty { font-size: 13px; color: #94a3b8; }
.import-file-size { font-size: 12px; color: #64748b; }

.import-format {
  padding: 12px 14px;
  border: 1px solid #e2e8f0; border-radius: 10px;
  background: #fff;
}
.import-format-title { font-size: 13px; font-weight: 600; color: #0f172a; margin-bottom: 8px; }
.import-format-example {
  margin: 0; padding: 10px 12px;
  border-radius: 8px; background: #0f172a; color: #e2e8f0;
  font-size: 12px; line-height: 1.6; white-space: pre; overflow-x: auto;
}
.import-format-tip { margin-top: 8px; font-size: 12px; color: #64748b; line-height: 1.6; }
</style>
