<script setup lang="ts">
interface PaginationState {
  page: number
  size: number
  total: number
}

const props = defineProps<{
  pagination?: PaginationState | null
}>()

const emit = defineEmits<{
  (event: 'page-change', value: number): void
  (event: 'size-change', value: number): void
}>()

function handleCurrentChange(value: number) {
  emit('page-change', value)
}

function handleSizeChange(value: number) {
  emit('size-change', value)
}
</script>

<template>
  <section class="page-grid">
    <div class="glass-card content-panel scene-panel">
      <div class="content-panel__header">
        <div class="content-panel__header-left content-panel__header-side content-panel__header-side--left">
          <slot name="header-left" />
        </div>
        <div class="content-panel__header-right content-panel__header-side content-panel__header-side--right">
          <slot name="header-right" />
        </div>
      </div>
      <div class="content-panel__body">
        <slot />
      </div>
      <div v-if="props.pagination" class="content-panel__footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="props.pagination.page"
          :page-size="props.pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="props.pagination.total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.content-panel__header {
  justify-content: space-between;
}

.content-panel__header-side {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1 1 0;
}

.content-panel__header-side--left {
  justify-content: flex-start;
}

.content-panel__header-side--right {
  justify-content: flex-end;
}

.content-panel__footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--app-border);
}
</style>
