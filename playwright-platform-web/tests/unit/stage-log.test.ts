import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchStageLogContent } from '../../src/utils/stage-log'

describe('fetchStageLogContent', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should return normalized text when fetch succeeds', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => 'line1\r\nline2',
    })

    await expect(fetchStageLogContent('http://localhost:10000/log', 'preview', fetchMock as typeof fetch, 100))
      .resolves
      .toBe('line1\nline2')
  })

  it('should fall back to preview text when fetch times out', async () => {
    const fetchMock = vi.fn((_url: string, init?: RequestInit) => new Promise((_, reject) => {
      init?.signal?.addEventListener('abort', () => {
        const abortError = Object.assign(new Error('aborted'), { name: 'AbortError' })
        reject(abortError)
      })
    }))

    const contentPromise = fetchStageLogContent(
      'http://localhost:10000/log',
      'preview line',
      fetchMock as typeof fetch,
      100,
    )

    await vi.advanceTimersByTimeAsync(100)

    await expect(contentPromise).resolves.toBe('preview line')
  })
})
