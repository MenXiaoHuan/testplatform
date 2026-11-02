<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** hover 时展示的完整内容 */
  content?: string | number | null
  /** 单元格内展示的文本，默认与 content 一致，用于超长文本的预览态 */
  display?: string
  mono?: boolean
}>()

const rawContent = computed(() => {
  const value = props.content
  return value === null || value === undefined ? '' : String(value).trim()
})

const tooltipText = computed(() => rawContent.value || '—')
const cellText = computed(() => {
  const preview = props.display?.trim()
  return preview ? preview : tooltipText.value
})
const tooltipDisabled = computed(() => rawContent.value === '')
</script>

<template>
  <el-tooltip
    :content="tooltipText"
    placement="top"
    effect="dark"
    :show-after="300"
    :disabled="tooltipDisabled"
    :popper-options="{ strategy: 'fixed' }"
  >
    <span class="cell-tooltip" :class="{ 'cell-tooltip--mono': mono }">{{ cellText }}</span>
  </el-tooltip>
</template>
