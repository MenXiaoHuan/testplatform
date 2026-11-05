const WEEKDAY_NAMES: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}

/** 将常见 Cron 表达式转换为可读的定时描述，无法识别的表达式原样返回。 */
export function describeCron(cron?: string | null): string {
  const raw = (cron ?? '').trim()
  if (!raw) return ''
  const parts = raw.split(/\s+/)
  if (parts.length !== 6) return raw
  const [, minute, hour, day, month, weekday] = parts
  if (!/^\d{1,2}$/.test(minute) || !/^\d{1,2}$/.test(hour)) return raw
  const time = `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`
  if (day === '*' && month === '*' && weekday === '*') {
    return `每天 ${time}`
  }
  if (day === '*' && month === '*' && /^[1-7](,[1-7])*$/.test(weekday)) {
    const names = weekday.split(',').map((item) => WEEKDAY_NAMES[Number(item)]).filter(Boolean).join('、')
    return `每${names} ${time}`
  }
  if (month === '*' && weekday === '*' && /^\d{1,2}$/.test(day)) {
    const value = Number(day)
    if (value >= 1 && value <= 31) return `每月 ${value} 日 ${time}`
  }
  return raw
}
