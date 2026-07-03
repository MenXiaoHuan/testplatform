<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useSpaceStore } from '../../stores/space'
import type { SpaceRole } from '../../types/space'
import { isPendingSpaceAccessRequestError, toErrorMessage } from '../../utils/error'
import { showAppToast } from '../../utils/ui-feedback'

const route = useRoute()
const spaceStore = useSpaceStore()
const submitting = ref(false)
const form = reactive({
  requestedRole: 'VIEWER' as SpaceRole,
  reason: '',
})

const roleOptions: Array<{ label: string, value: SpaceRole, description: string }> = [
  { label: '访问者', value: 'VIEWER', description: '只读查看空间内容' },
  { label: '运维', value: 'OPERATOR', description: '可执行仓库与场景操作' },
  { label: '管理员', value: 'ADMIN', description: '可审批申请并管理空间' },
]

const spaceId = computed(() => {
  const raw = typeof route.params.spaceId === 'string' ? Number(route.params.spaceId) : Number.NaN
  return Number.isFinite(raw) ? raw : null
})

async function submitRequest() {
  if (spaceId.value === null) {
    showAppToast('空间 ID 无效', 'error')
    return
  }
  if (!form.reason.trim()) {
    showAppToast('请填写申请原因', 'warning')
    return
  }

  submitting.value = true
  try {
    await spaceStore.submitAccessRequest({
      requestedRole: form.requestedRole,
      reason: form.reason.trim(),
    }, spaceId.value)
    showAppToast('申请已提交，等待管理员审批', 'success')
  } catch (error) {
    if (isPendingSpaceAccessRequestError(error)) {
      showAppToast('你已有一条待审批申请，请等待管理员处理', 'warning')
      return
    }
    showAppToast(toErrorMessage(error, '提交申请失败'), 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="space-no-access">
    <el-card shadow="never" class="space-no-access__card">
      <p class="space-no-access__eyebrow">No Access</p>
      <h1>你当前还没有空间访问权限</h1>
      <p>空间 ID：{{ spaceId ?? '-' }}。你可以提交加入申请，并说明需要的权限和原因。</p>

      <el-form label-position="top" class="space-no-access__form">
        <el-form-item label="申请权限">
          <div class="space-no-access__role-list">
            <label
              v-for="option in roleOptions"
              :key="option.value"
              class="space-no-access__role-option"
              :class="{ 'space-no-access__role-option--active': form.requestedRole === option.value }"
            >
              <input
                v-model="form.requestedRole"
                type="radio"
                :value="option.value"
              >
              <span>{{ option.label }}</span>
              <small>{{ option.description }}</small>
            </label>
          </div>
        </el-form-item>
        <el-form-item label="申请原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 6 }"
            placeholder="例如：需要处理该空间的调度故障与日志排查"
          />
        </el-form-item>
        <el-button type="primary" :loading="submitting" @click="submitRequest">提交申请</el-button>
      </el-form>
    </el-card>
  </section>
</template>

<style scoped>
.space-no-access {
  display: grid;
  place-items: center;
  min-height: calc(100vh - 56px);
}

.space-no-access__card {
  width: min(100%, 560px);
  border-radius: 24px;
}

.space-no-access__eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.space-no-access__card h1 {
  margin: 0 0 12px;
}

.space-no-access__card p {
  color: #64748b;
  line-height: 1.6;
}

.space-no-access__form {
  margin-top: 20px;
}

.space-no-access__role-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.space-no-access__role-option {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 2px 8px;
  align-items: center;
  min-height: 62px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid #dbe4ee;
  background: #ffffff;
  color: #0f172a;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease;
}

.space-no-access__role-option input {
  margin: 0;
  grid-row: 1 / span 2;
}

.space-no-access__role-option--active {
  border-color: #14b8a6;
  background: #ecfeff;
  color: #0f766e;
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.12);
}

.space-no-access__role-option span {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.space-no-access__role-option small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.space-no-access__role-option--active small {
  color: #0f766e;
}
</style>
