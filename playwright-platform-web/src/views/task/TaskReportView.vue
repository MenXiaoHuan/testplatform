<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTaskStore } from '../../stores/task'
import { toErrorMessage } from '../../utils/error'

const route = useRoute()
const router = useRouter()
const store = useTaskStore()

const summary = computed(() => store.reportSummary)
const task = computed(() => summary.value?.task ?? null)
const artifacts = computed(() => summary.value?.artifacts ?? [])
const caseResults = computed(() => summary.value?.caseResults ?? [])
const activeStatus = ref<'ALL' | 'FAILED' | 'PASSED' | 'SKIPPED'>('ALL')
const failedCases = computed(() => caseResults.value.filter((item) => item.status === 'FAILED'))
const filteredCaseResults = computed(() => {
  if (activeStatus.value === 'ALL') {
    return caseResults.value
  }
  return caseResults.value.filter((item) => item.status === activeStatus.value)
})
const projectStats = computed(() => {
  const stats = new Map<string, number>()
  caseResults.value.forEach((item) => {
    const key = item.projectName || 'unknown'
    stats.set(key, (stats.get(key) ?? 0) + 1)
  })
  return Array.from(stats.entries()).map(([projectName, total]) => ({ projectName, total }))
})

function caseQuickArtifacts(index: number) {
  return failedCases.value[index]?.artifacts ?? []
}

async function loadSummary() {
  const taskId = Number(route.params.id)
  if (!Number.isFinite(taskId)) {
    ElMessage.error('任务 ID 无效')
    return
  }

  try {
    await store.fetchReportSummary(taskId)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '任务报告加载失败'))
  }
}

onMounted(() => {
  void loadSummary()
})

watch(() => route.params.id, () => {
  void loadSummary()
})

function openReport() {
  if (summary.value?.reportUrl) {
    window.open(summary.value.reportUrl, '_blank', 'noopener')
  }
}

function downloadFirstArtifact() {
  const firstArtifact = artifacts.value[0]
  if (firstArtifact?.url) {
    window.open(firstArtifact.url, '_blank', 'noopener')
  }
}

function openArtifact(url?: string | null) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function downloadArtifact(url?: string | null) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function backToDetail() {
  const taskId = Number(route.params.id)
  if (!Number.isFinite(taskId)) {
    ElMessage.error('任务 ID 无效')
    return
  }
  void router.push(`/tasks/${taskId}`)
}
</script>

<template>
  <section class="page-grid">
    <div class="page-hero">
      <p class="eyebrow">Task Report</p>
      <h1>任务报告</h1>
      <p class="hero-copy">聚焦原始报告链接、附件归档和失败分析，作为任务结果的补充查看页。</p>
    </div>

    <div v-if="task" class="detail-grid">
      <div class="detail-summary-grid">
        <section class="summary-panel">
          <div class="summary-panel__header">
            <div>
              <p class="eyebrow">Snapshot</p>
              <h2>执行快照</h2>
            </div>
            <div class="card-header">
              <el-button type="primary" plain :disabled="!summary?.reportUrl" @click="openReport">打开报告</el-button>
              <el-button plain :disabled="artifacts.length === 0" @click="downloadFirstArtifact">下载附件</el-button>
              <el-button plain @click="backToDetail">返回任务详情</el-button>
            </div>
          </div>
          <div class="detail-list">
            <div><span>任务 ID</span><strong>{{ task.id }}</strong></div>
            <div><span>状态</span><strong>{{ task.status }}</strong></div>
            <div><span>浏览器</span><strong>{{ task.resolvedBrowser ?? '-' }}</strong></div>
            <div><span>用例路径</span><strong>{{ task.resolvedMatchValue ?? '测试目录下全部用例' }}</strong></div>
            <div><span>执行命令</span><strong>{{ task.resolvedRunCommand ?? '-' }}</strong></div>
          </div>
        </section>

        <section class="summary-panel">
          <div class="summary-panel__header">
            <div>
              <p class="eyebrow">Outputs</p>
              <h2>报告信息</h2>
            </div>
          </div>
          <div class="detail-list">
            <div><span>报告状态</span><strong>{{ summary?.reportReady ? '已就绪' : '未就绪' }}</strong></div>
            <div><span>报告地址</span><strong>{{ summary?.reportUrl ?? '暂无' }}</strong></div>
            <div><span>附件数量</span><strong>{{ summary?.artifactCount ?? 0 }}</strong></div>
            <div><span>用例统计</span><strong>{{ summary?.caseSummary.passed ?? 0 }} / {{ summary?.caseSummary.failed ?? 0 }} / {{ summary?.caseSummary.skipped ?? 0 }}</strong></div>
          </div>
        </section>
      </div>

      <el-card class="glass-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>附件列表</span>
            <span class="muted">{{ artifacts.length }} 个</span>
          </div>
        </template>
        <el-table :data="artifacts" empty-text="暂无归档附件">
          <el-table-column prop="artifactType" label="类型" width="160" />
          <el-table-column prop="objectKey" label="对象路径" min-width="260" />
          <el-table-column prop="url" label="访问地址" min-width="260" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openArtifact(row.url)">打开</el-button>
              <el-button link type="success" @click="downloadArtifact(row.url)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="failedCases.length > 0" class="glass-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>失败用例</span>
            <span class="muted">{{ failedCases.length }} 条</span>
          </div>
        </template>
        <div class="detail-list">
          <div v-for="(item, index) in failedCases" :key="item.id">
            <span>{{ item.projectName ?? '-' }}</span>
            <strong>{{ item.fullName }}</strong>
            <div class="card-header">
              <el-button
                v-for="artifact in caseQuickArtifacts(index)"
                :key="`${item.id}-${artifact.artifactType}`"
                link
                type="danger"
                @click="openArtifact(artifact.url)"
              >
                {{ artifact.label }}
              </el-button>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="glass-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>Project 统计</span>
            <span class="muted">{{ projectStats.length }} 个</span>
          </div>
        </template>
        <div class="detail-list">
          <div v-for="item in projectStats" :key="item.projectName">
            <span>{{ item.projectName }}</span>
            <strong>{{ item.total }}</strong>
          </div>
        </div>
      </el-card>

      <el-card class="glass-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>用例结果</span>
            <span class="muted">{{ summary?.caseSummary.total ?? 0 }} 条</span>
          </div>
        </template>
        <div class="card-header">
          <el-button plain @click="activeStatus = 'ALL'">全部</el-button>
          <el-button plain @click="activeStatus = 'FAILED'">FAILED</el-button>
          <el-button plain @click="activeStatus = 'PASSED'">PASSED</el-button>
          <el-button plain @click="activeStatus = 'SKIPPED'">SKIPPED</el-button>
        </div>
        <p class="muted">当前筛选：{{ activeStatus }}</p>
        <el-table :data="filteredCaseResults" empty-text="暂无用例结果">
          <el-table-column prop="fullName" label="用例名称" min-width="260" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="projectName" label="Project" width="140" />
          <el-table-column prop="durationMs" label="耗时(ms)" width="120" />
          <el-table-column prop="artifactCount" label="附件数" width="100" />
        </el-table>
      </el-card>
    </div>

    <el-card v-else class="glass-card" shadow="never">
      <div class="muted">暂无任务报告</div>
    </el-card>
  </section>
</template>
