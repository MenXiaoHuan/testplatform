const DEFAULT_STAGE_LOG_FALLBACK = '日志暂时无法加载，请使用下载日志查看完整内容。'

export async function fetchStageLogContent(
  url: string,
  fallbackText?: string | null,
  fetchImpl: typeof fetch = fetch,
  timeoutMs = 5000,
) {
  const controller = new AbortController()
  const timeoutHandle = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetchImpl(url, { signal: controller.signal })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const text = (await response.text()).replace(/\r\n/g, '\n')
    return text || fallbackText || DEFAULT_STAGE_LOG_FALLBACK
  } catch {
    return fallbackText || DEFAULT_STAGE_LOG_FALLBACK
  } finally {
    window.clearTimeout(timeoutHandle)
  }
}
