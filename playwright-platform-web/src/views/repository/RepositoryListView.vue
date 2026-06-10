<script setup lang="ts"> 
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRepositoryStore } from '../../stores/repository'
import type { RepositoryForm, RepositoryRecord } from '../../types/repository'
import { toErrorMessage } from '../../utils/error'
import { isRequired } from '../../utils/validators'

const store = useRepositoryStore()
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<RepositoryForm>(store.createEmptyForm())

const rows = computed(() => store.items)
const loading = computed(() => store.loading)

function openCreate() {
  editingId.value = null
  Object.assign(form, store.createEmptyForm())
  dialogVisible.value = true
}

function openEdit(row: RepositoryRecord) {
  editingId.value = row.id
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function submit() {
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

onMounted(() => {
  void store.fetchAll().catch((error) => {
    ElMessage.error(toErrorMessage(error, '仓库列表加载失败'))
  })
})
</script>

<template>
  <section class="page-grid">
    <div class="page-hero">
      <p class="eyebrow">Repository Control</p>
      <h1>测试仓库接入</h1>
      <p class="hero-copy">维护 Playwright 测试仓库的接入信息、执行命令和报告输出约定。</p>
      <el-button type="primary" size="large" @click="openCreate">新增仓库</el-button>
    </div>

    <el-card class="glass-card" shadow="never">
      <el-table :data="rows" :loading="loading" empty-text="暂无仓库">
        <el-table-column prop="name" label="仓库名称" min-width="180" />
        <el-table-column prop="gitUrl" label="Git 地址" min-width="260" />
        <el-table-column prop="defaultBranch" label="默认分支" width="120" />
        <el-table-column prop="packageManager" label="包管理器" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增测试仓库' : '编辑测试仓库'" width="720px">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="仓库名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="Git 地址"><el-input v-model="form.gitUrl" /></el-form-item>
        <el-form-item label="默认分支"><el-input v-model="form.defaultBranch" /></el-form-item>
        <el-form-item label="包管理器"><el-input v-model="form.packageManager" /></el-form-item>
        <el-form-item label="安装命令"><el-input v-model="form.installCommand" /></el-form-item>
        <el-form-item label="执行命令模板"><el-input v-model="form.runCommandTemplate" /></el-form-item>
        <el-form-item label="测试目录"><el-input v-model="form.testRoot" /></el-form-item>
        <el-form-item label="报告目录"><el-input v-model="form.reportRelativePath" /></el-form-item>
        <el-form-item label="Node 版本"><el-input v-model="form.nodeVersion" /></el-form-item>
        <el-form-item label="是否启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
