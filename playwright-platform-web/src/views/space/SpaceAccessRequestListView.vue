<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import ListPageShell from '../../components/list/ListPageShell.vue'
import defaultAvatarUrl from '../../assets/default-avatar.svg'
import { useSpaceStore } from '../../stores/space'
import type { SpaceAccessRequest } from '../../types/space'
import { sortSpaceAccessRequests } from '../../utils/space-access-requests'
import { toErrorMessage } from '../../utils/error'
import { confirmDangerAction, showAppToast } from '../../utils/ui-feedback'

const route = useRoute()
const spaceStore = useSpaceStore()
const sortedAccessRequests = computed(() => sortSpaceAccessRequests(spaceStore.accessRequests))

const spaceId = computed(() => {
  const raw = typeof route.params.spaceId === 'string' ? Number(route.params.spaceId) : Number.NaN
  return Number.isFinite(raw) ? raw : null
})

async function loadRequests() {
  if (spaceId.value === null) {
    return
  }
  try {
    await spaceStore.fetchAccessRequests(spaceId.value)
  } catch (error) {
    showAppToast(toErrorMessage(error, '审批列表加载失败'), 'error')
  }
}

async function review(requestId: number, action: 'approve' | 'reject') {
  if (spaceId.value === null) {
    return
  }
  const confirmed = await confirmDangerAction({
    title: action === 'approve' ? '同意申请' : '拒绝申请',
    message: `确认${action === 'approve' ? '同意' : '拒绝'}该入空间申请吗？`,
    confirmButtonText: action === 'approve' ? '同意' : '拒绝',
  })
  if (!confirmed) {
    return
  }
  try {
    if (action === 'approve') {
      await spaceStore.approveAccessRequest(requestId, { reviewComment: '已同意' }, spaceId.value)
    } else {
      await spaceStore.rejectAccessRequest(requestId, { reviewComment: '已拒绝' }, spaceId.value)
    }
    showAppToast(`申请已${action === 'approve' ? '同意' : '拒绝'}`, 'success')
  } catch (error) {
    showAppToast(toErrorMessage(error, '审批操作失败'), 'error')
  }
}

function resolveApplicantDisplayName(item: SpaceAccessRequest) {
  return item.applicantNickname?.trim() || item.applicantUsername || '未命名用户'
}

function resolveApplicantAvatar(item: SpaceAccessRequest) {
  return item.applicantAvatarUrl || defaultAvatarUrl
}

function roleLabel(role: string) {
  return role === 'ADMIN' ? '管理员' : role === 'OPERATOR' ? '运维' : '访问者'
}

function statusLabel(status: string) {
  return status === 'APPROVED' ? '已同意' : status === 'REJECTED' ? '已拒绝' : '待审批'
}

function statusTagType(status: string) {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning'
}

function applicantBio(item: SpaceAccessRequest) {
  return `申请${roleLabel(item.requestedRole)}权限，当前状态${statusLabel(item.status)}。`
}

onMounted(() => {
  void loadRequests()
})
</script>

<template>
  <ListPageShell :pagination="null">
    <el-table class="list-table" :data="sortedAccessRequests" empty-text="当前没有待处理申请">
      <el-table-column prop="id" label="申请ID" width="100" />
      <el-table-column label="申请人" min-width="220">
        <template #default="{ row }">
          <el-popover placement="right" :width="280" trigger="hover">
            <template #reference>
              <div class="request-applicant">
                <img
                  class="request-applicant__avatar"
                  :src="resolveApplicantAvatar(row)"
                  :alt="`${resolveApplicantDisplayName(row)} 头像`"
                >
                <div class="request-applicant__meta">
                  <strong>{{ resolveApplicantDisplayName(row) }}</strong>
                  <span>{{ row.applicantUsername }}</span>
                </div>
              </div>
            </template>
            <div class="request-applicant-card">
              <img
                class="request-applicant-card__avatar"
                :src="resolveApplicantAvatar(row)"
                :alt="`${resolveApplicantDisplayName(row)} 头像`"
              >
              <div class="request-applicant-card__meta">
                <strong>{{ resolveApplicantDisplayName(row) }}</strong>
                <span>{{ row.applicantUsername }}</span>
                <p>{{ applicantBio(row) }}</p>
              </div>
            </div>
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column label="申请权限" width="140">
        <template #default="{ row }">
          <span>{{ roleLabel(row.requestedRole) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="申请原因" min-width="280" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="light" round>{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <div v-if="row.status === 'PENDING'" class="space-requests__actions">
            <el-button
              link
              type="primary"
              @click="review(row.id, 'approve')"
            >
              同意
            </el-button>
            <el-button
              link
              type="danger"
              @click="review(row.id, 'reject')"
            >
              拒绝
            </el-button>
          </div>
          <span v-else class="space-requests__reviewed-hint">已审批</span>
        </template>
      </el-table-column>
    </el-table>
  </ListPageShell>
</template>

<style scoped>
.space-requests__actions {
  display: flex;
  gap: 8px;
}

.space-requests__reviewed-hint {
  color: #94a3b8;
  font-size: 12px;
}

.request-applicant {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.request-applicant__avatar,
.request-applicant-card__avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: cover;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.request-applicant__meta,
.request-applicant-card__meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.request-applicant__meta strong,
.request-applicant-card__meta strong {
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.request-applicant__meta span,
.request-applicant-card__meta span {
  color: #64748b;
  font-size: 12px;
}

.request-applicant-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.request-applicant-card__meta p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}
</style>
