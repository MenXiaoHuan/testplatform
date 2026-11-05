<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM'

const props = defineProps<{ modelValue?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const WEEKDAYS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

const frequency = ref<Frequency>('DAILY')
const timeValue = ref('02:30')
const weekdays = ref<number[]>([1])
const monthDay = ref(1)
const customCron = ref('')
const syncing = ref(false)

const summary = computed(() => {
  if (frequency.value === 'DAILY') return `每天 ${timeValue.value} 自动执行`
  if (frequency.value === 'WEEKLY') {
    const names = WEEKDAYS.filter((item) => weekdays.value.includes(item.value))
      .map((item) => item.label)
      .join('、')
    return `每${names || '周一'} ${timeValue.value} 自动执行`
  }
  if (frequency.value === 'MONTHLY') return `每月 ${monthDay.value} 日 ${timeValue.value} 自动执行`
  return '按自定义 Cron 表达式执行'
})

function buildCron(target: Frequency = frequency.value) {
  const [hour, minute] = timeValue.value.split(':').map(Number)
  if (target === 'DAILY') return `0 ${minute} ${hour} * * *`
  if (target === 'WEEKLY') {
    const days = weekdays.value.length ? [...weekdays.value].sort((a, b) => a - b) : [1]
    return `0 ${minute} ${hour} * * ${days.join(',')}`
  }
  if (target === 'MONTHLY') return `0 ${minute} ${hour} ${monthDay.value} * *`
  return customCron.value.trim()
}

function parseCron(raw?: string | null) {
  const cron = (raw ?? '').trim()
  if (!cron) {
    frequency.value = 'DAILY'
    timeValue.value = '02:30'
    weekdays.value = [1]
    monthDay.value = 1
    customCron.value = ''
    return
  }
  const parts = cron.split(/\s+/)
  if (parts.length === 6) {
    const [, minute, hour, day, month, weekday] = parts
    if (/^\d{1,2}$/.test(minute) && /^\d{1,2}$/.test(hour)) {
      const time = `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`
      if (day === '*' && month === '*' && weekday === '*') {
        frequency.value = 'DAILY'
        timeValue.value = time
        return
      }
      if (day === '*' && month === '*' && /^[1-7](,[1-7])*$/.test(weekday)) {
        frequency.value = 'WEEKLY'
        timeValue.value = time
        weekdays.value = weekday.split(',').map(Number)
        return
      }
      if (month === '*' && weekday === '*' && /^\d{1,2}$/.test(day) && Number(day) >= 1 && Number(day) <= 31) {
        frequency.value = 'MONTHLY'
        timeValue.value = time
        monthDay.value = Number(day)
        return
      }
    }
  }
  frequency.value = 'CUSTOM'
  customCron.value = cron
}

function emitIfChanged() {
  const next = buildCron()
  if (next && next !== (props.modelValue ?? '')) {
    emit('update:modelValue', next)
  }
}

watch([frequency, timeValue, weekdays, monthDay, customCron], () => {
  if (syncing.value) return
  emitIfChanged()
}, { deep: true })

watch(frequency, (value, oldValue) => {
  if (value === 'CUSTOM' && !customCron.value.trim()) {
    customCron.value = buildCron(oldValue)
  }
})

watch(weekdays, (value) => {
  if (!value.length) {
    weekdays.value = [1]
  }
})

watch(() => props.modelValue, (value) => {
  if (syncing.value) return
  syncing.value = true
  parseCron(value)
  void nextTick(() => {
    syncing.value = false
  })
})

onMounted(() => {
  syncing.value = true
  parseCron(props.modelValue)
  void nextTick(() => {
    syncing.value = false
    emitIfChanged()
  })
})
</script>

<template>
  <div class="schedule-picker">
    <div class="schedule-row">
      <el-select v-model="frequency" class="schedule-frequency">
        <el-option label="每天" value="DAILY" />
        <el-option label="每周" value="WEEKLY" />
        <el-option label="每月" value="MONTHLY" />
        <el-option label="自定义" value="CUSTOM" />
      </el-select>
      <el-time-select
        v-if="frequency !== 'CUSTOM'"
        v-model="timeValue"
        class="schedule-time"
        start="00:00"
        step="00:15"
        end="23:45"
        placeholder="选择时间"
      />
      <el-select v-if="frequency === 'MONTHLY'" v-model="monthDay" class="schedule-day">
        <el-option v-for="day in 31" :key="day" :label="`${day} 日`" :value="day" />
      </el-select>
    </div>
    <div v-if="frequency === 'WEEKLY'" class="schedule-weekdays">
      <el-checkbox-group v-model="weekdays">
        <el-checkbox-button
          v-for="item in WEEKDAYS"
          :key="item.value"
          :value="item.value"
          :label="item.label"
        />
      </el-checkbox-group>
    </div>
    <el-input
      v-if="frequency === 'CUSTOM'"
      v-model="customCron"
      placeholder="0 30 2 * * *（秒 分 时 日 月 周）"
    />
    <p class="schedule-hint">{{ summary }}</p>
  </div>
</template>

<style scoped>
.schedule-picker {
  width: 100%;
}
.schedule-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.schedule-frequency {
  width: 130px;
}
.schedule-time {
  width: 150px;
}
.schedule-day {
  width: 110px;
}
.schedule-weekdays {
  margin-top: 10px;
}
.schedule-weekdays :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.schedule-weekdays :deep(.el-checkbox-button__inner) {
  border-radius: 6px;
  border-left: 1px solid var(--el-border-color);
}
.schedule-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
