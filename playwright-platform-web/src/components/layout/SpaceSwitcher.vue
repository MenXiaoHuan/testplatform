<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSpaceStore } from '../../stores/space'
import { showAppToast } from '../../utils/ui-feedback'

const router = useRouter()
const spaceStore = useSpaceStore()

const orderedSpaces = computed(() => {
  const currentSpaceId = spaceStore.currentSpaceId
  if (typeof currentSpaceId !== 'number') {
    return spaceStore.items
  }
  const current = spaceStore.items.find((item) => item.id === currentSpaceId)
  const rest = spaceStore.items.filter((item) => item.id !== currentSpaceId)
  return current ? [current, ...rest] : rest
})

const selectedSpaceId = computed({
  get: () => spaceStore.currentSpaceId ?? undefined,
  set: (value: number | string | undefined) => {
    const nextSpaceId = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(nextSpaceId)) {
      return
    }
    const matchedSpace = spaceStore.items.find((item) => item.id === nextSpaceId)
    if (!matchedSpace) {
      showAppToast('你当前没有该空间的访问权限', 'error')
      return
    }
    spaceStore.setCurrentSpace(nextSpaceId)
    void router.push(`/spaces/${nextSpaceId}/repos`)
  },
})

function goHome() {
  void router.push('/home')
}
</script>

<template>
  <section class="space-switcher">
    <div class="space-switcher__header">
      <span class="space-switcher__label">当前空间</span>
      <el-button link type="primary" @click="goHome">广场</el-button>
    </div>
    <el-select
      v-model="selectedSpaceId"
      class="space-switcher__control"
      placeholder="请选择空间"
      :disabled="spaceStore.items.length === 0"
    >
      <el-option
        v-for="item in orderedSpaces"
        :key="item.id"
        :label="item.name"
        :value="item.id"
      />
    </el-select>
  </section>
</template>

<style scoped>
.space-switcher {
  display: grid;
  gap: 10px;
}

.space-switcher__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.space-switcher__label {
  color: #64748b;
  font-size: 12px;
}

.space-switcher__control :deep(.el-select__wrapper) {
  min-height: 44px;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}
</style>
