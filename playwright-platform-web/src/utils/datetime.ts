/** 全平台统一使用北京时间（UTC+8）展示与按天筛选。 */
const BEIJING_OFFSET_MS = 8 * 60 * 60 * 1000

/** 后端 MySQL LocalDateTime 以 UTC 存储但不带时区，需按 UTC 解析后再转北京时间。 */
function parseTime(value?: string | number | Date | null): Date | null {
  if (value == null || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'number') return new Date(value)
  const text = String(value).trim()
  if (!text) return null
  // 2026-10-31T05:35:37 / 2026-10-31 05:35:37 这类无时区字符串按 UTC 处理
  const withoutZone = /^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?(\.\d+)?)?$/.test(text)
  const date = new Date(withoutZone ? `${text.replace(' ', 'T')}Z` : text)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/** 格式化为北京时间：YYYY-MM-DD HH:mm:ss。 */
export function formatBeijingDateTime(
  value?: string | number | Date | null,
  fallback = '—',
): string {
  const date = parseTime(value)
  if (!date) return fallback
  const beijing = new Date(date.getTime() + BEIJING_OFFSET_MS)
  return `${beijing.getUTCFullYear()}-${pad(beijing.getUTCMonth() + 1)}-${pad(beijing.getUTCDate())} `
    + `${pad(beijing.getUTCHours())}:${pad(beijing.getUTCMinutes())}:${pad(beijing.getUTCSeconds())}`
}

/** 格式化为北京时间 H:mm 或 HH:mm:ss。 */
export function formatBeijingClock(
  value?: string | number | Date | null,
  withSeconds = false,
  fallback = '—',
): string {
  const date = parseTime(value)
  if (!date) return fallback
  const beijing = new Date(date.getTime() + BEIJING_OFFSET_MS)
  const hm = `${pad(beijing.getUTCHours())}:${pad(beijing.getUTCMinutes())}`
  return withSeconds ? `${hm}:${pad(beijing.getUTCSeconds())}` : hm
}

/** 选中日期按北京时间当天 00:00:00 换算为 UTC 时间点（与浏览器本地时区无关）。 */
export function toBeijingDayStart(value: Date): Date {
  return new Date(Date.UTC(value.getFullYear(), value.getMonth(), value.getDate()) - BEIJING_OFFSET_MS)
}

/** 选中日期按北京时间当天 23:59:59.999 换算为 UTC 时间点。 */
export function toBeijingDayEnd(value: Date): Date {
  return new Date(Date.UTC(value.getFullYear(), value.getMonth(), value.getDate() + 1) - BEIJING_OFFSET_MS - 1)
}
