<script setup lang="ts"> 
import { ElMessage } from 'element-plus'
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useTaskStore } from '../../stores/task'
import { formatArtifactType, formatFileSize, getArtifactFileName } from '../../utils/artifact'
import { toErrorMessage } from '../../utils/error'

const route = useRoute()
const store = useTaskStore()

const task = computed(() => store.current)
const report = computed(() => store.report)
const artifacts = computed(() => store.artifacts)

async function loadDetail() {
  const taskId = Number(route.params.id)
  if (!Number.isFinite(taskId)) {
    ElMessage.error('任务 ID 无效')
    return
  }
  try {
    await store.fetchDetail(taskId)
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '任务详情加载失败'))
  }
}

function openReport() {
  if (report.value?.reportUrl) {
    window.open(report.value.reportUrl, '_blank', 'noopener')
  }
}

function openArtifact(url?: string | null) {
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

function downloadArtifact(url?: string | null, objectKey?: string) {
  if (!url) {
    return
  }

  const link = document.createElement('a')
  link.href = url
  link.download = getArtifactFileName(objectKey ?? '')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(() => {
  void loadDetail()
})

watch(() => route.params.id, () => {
  void loadDetail()
})
</script>

<template>
  <section class="page-grid">
    <div class="page-hero">
      <p class="eyebrow">Task Snapshot</p>
      <h1>任务详情</h1>
      <p class="hero-copy">查看任务基础信息、报告入口和归档附件，后续这里还可以继续扩展用例结果摘要。</p>
    </div>

    <div class="detail-grid">
      <el-card class="glass-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>基础信息</span>
            <el-button type="primary" plain :disabled="!report?.reportUrl" @click="openReport">打开报告</el-button>
          </div>
        </template>
        <div class="detail-list" v-if="task">
          <div><span>任务 ID</span><strong>{{ task.id }}</strong></div>
          <div><span>状态</span><strong>{{ task.status }}</strong></div>
          <div><span>分支</span><strong>{{ task.branch }}</strong></div>
          <div><span>触发方式</span><strong>{{ task.triggerType }}</strong></div>
          <div><span>耗时</span><strong>{{ task.durationMs ?? '-' }}</strong></div>
          <div><span>执行器</span><strong>{{ task.runnerName ?? '-' }}</strong></div>
          <div><span>附件数量</span><strong>{{ task.artifactCount ?? artifacts.length }}</strong></div>
          <div><span>附件状态</span><strong>{{ task.hasArtifacts ? '已有附件' : '暂无附件' }}</strong></div>
          <div><span>报告状态</span><strong>{{ task.reportReady ? '已就绪' : '未就绪' }}</strong></div>
          <div><span>报告地址</span><strong>{{ report?.reportUrl ?? '暂无' }}</strong></div>
          <div><span>日志地址</span><strong>{{ task.logUrl ?? '暂无' }}</strong></div>
        </div>
        <el-empty v-else description="暂无任务详情" />
      </el-card>

      <el-card class="glass-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>附件列表</span>
            <span class="muted">{{ artifacts.length }} 个</span>
          </div>
        </template>
        <el-table :data="artifacts" empty-text="任务未产出附件，或附件仍在归档中">
          <el-table-column label="类型" width="140">
            <template #default="{ row }">
              {{ formatArtifactType(row.artifactType) }}
            </template>
          </el-table-column>
          <el-table-column label="文件名" min-width="180">
            <template #default="{ row }">
              {{ getArtifactFileName(row.objectKey) }}
            </template>
          </el-table-column>
          <el-table-column label="大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column prop="objectKey" label="对象路径" min-width="260" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!row.url" @click="openArtifact(row.url)">打开</el-button>
              <el-button link type="success" :disabled="!row.url" @click="downloadArtifact(row.url, row.objectKey)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </section>
</template>
