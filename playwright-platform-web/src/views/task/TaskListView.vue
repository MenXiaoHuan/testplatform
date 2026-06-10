<script setup lang="ts"> 
import { ElMessage } from 'element-plus'
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTaskStore } from '../../stores/task'
import type { TaskRecord } from '../../types/task'
import { toErrorMessage } from '../../utils/error'

const router = useRouter()
const store = useTaskStore()
const rows = computed(() => store.items)
const loading = computed(() => store.loading)

function statusType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function openDetail(row: TaskRecord) {
  void router.push(`/tasks/${row.id}`)
}

function openReport(row: TaskRecord) {
  if (row.reportUrl) {
    window.open(row.reportUrl, '_blank', 'noopener')
  }
}

onMounted(() => {
  void store.fetchAll().catch((error) => {
    ElMessage.error(toErrorMessage(error, '任务列表加载失败'))
  })
})
</script>

<template>
  <section class="page-grid">
    <div class="page-hero">
      <p class="eyebrow">Task Timeline</p>
      <h1>任务记录与状态</h1>
      <p class="hero-copy">集中查看所有执行任务的状态、分支、时长和报告入口，作为后续结果分析的入口。</p>
    </div>

    <el-card class="glass-card" shadow="never">
      <el-table :data="rows" :loading="loading" empty-text="暂无任务">
        <el-table-column prop="id" label="任务 ID" width="100" />
        <el-table-column prop="branch" label="分支" width="140" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发方式" width="120" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="140" />
        <el-table-column prop="runnerName" label="执行器" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="success" :disabled="!row.reportUrl" @click="openReport(row)">报告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
