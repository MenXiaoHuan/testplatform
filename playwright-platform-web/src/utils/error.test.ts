import { describe, expect, it } from 'vitest'
import { toErrorMessage } from './error'

describe('toErrorMessage', () => {
  it('should fallback to a default message for unknown errors', () => {
    expect(toErrorMessage(null, '加载失败')).toBe('加载失败')
  })

  it('should prefer the native error message when available', () => {
    expect(toErrorMessage(new Error('请求超时'), '加载失败')).toBe('请求超时')
  })
})
