<script setup lang="ts"> 
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRepositoryStore } from '../../stores/repository'
import { useSceneStore } from '../../stores/scene'
import { useTaskStore } from '../../stores/task'
import type { SceneForm, SceneRecord } from '../../types/scene'
import { toErrorMessage } from '../../utils/error'
import { isPositiveId, isRequired } from '../../utils/validators'

const repositoryStore = useRepositoryStore()
const sceneStore = useSceneStore()
const taskStore = useTaskStore()
const dialogVisible = ref(false)
const saving = ref(false)
const runningId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const form = reactive<SceneForm>(sceneStore.createEmptyForm())

const rows = computed(() => sceneStore.items)
const repositories = computed(() => repositoryStore.items)
const loading = computed(() => sceneStore.loading)

function openCreate() {
  editingId.value = null
  Object.assign(form, sceneStore.createEmptyForm())
  form.repoId = repositories.value[0]?.id ?? 0
  dialogVisible.value = true
}

function openEdit(row: SceneRecord) {
  editingId.value = row.id
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submit() {
  if (!isPositiveId(form.repoId) || !isRequired(form.name) || !isRequired(form.branch) || !isRequired(form.testSelectorValue) || !isRequired(form.runCommand)) {
    ElMessage.warning('请完善所属仓库、场景名称、分支、筛选值和执行命令')
    return
  }

  saving.value = true
  try {
    await sceneStore.save(editingId.value, { ...form })
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

async function run(row: SceneRecord) {
  runningId.value = row.id
  try {
    await taskStore.executeScene(row.id)
    ElMessage.success('任务已触发')
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景执行失败'))
  } finally {
    runningId.value = null
  }
}

onMounted(async () => {
  try {
    await Promise.all([repositoryStore.fetchAll(), sceneStore.fetchAll()])
    if (!form.repoId) {
      form.repoId = repositories.value[0]?.id ?? 0
    }
  } catch (error) {
    ElMessage.error(toErrorMessage(error, '场景列表加载失败'))
  }
})
</script>

<template>
  <section class="page-grid">
    <div class="page-hero">
      <p class="eyebrow">Scene Studio</p>
      <h1>执行场景配置</h1>
      <p class="hero-copy">将仓库、分支、过滤规则和执行命令抽象成可复用的场景，统一触发任务。</p>
      <el-button type="primary" size="large" @click="openCreate">新增场景</el-button>
    </div>

    <el-card class="glass-card" shadow="never">
      <el-table :data="rows" :loading="loading" empty-text="暂无场景">
        <el-table-column prop="name" label="场景名称" min-width="180" />
        <el-table-column prop="branch" label="分支" width="120" />
        <el-table-column prop="testSelectorType" label="选择方式" width="120" />
        <el-table-column prop="testSelectorValue" label="筛选值" min-width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :loading="runningId === row.id" @click="run(row)">执行</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增执行场景' : '编辑执行场景'" width="720px">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="所属仓库">
          <el-select v-model="form.repoId" placeholder="请选择仓库">
            <el-option v-for="item in repositories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分支"><el-input v-model="form.branch" /></el-form-item>
        <el-form-item label="选择方式">
          <el-select v-model="form.testSelectorType">
            <el-option label="文件" value="file" />
            <el-option label="目录" value="directory" />
            <el-option label="grep" value="grep" />
          </el-select>
        </el-form-item>
        <el-form-item label="筛选值"><el-input v-model="form.testSelectorValue" /></el-form-item>
        <el-form-item label="Playwright Project"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="浏览器"><el-input v-model="form.browser" /></el-form-item>
        <el-form-item label="环境参数 JSON"><el-input v-model="form.envJson" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="执行命令"><el-input v-model="form.runCommand" /></el-form-item>
        <el-form-item label="是否启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
