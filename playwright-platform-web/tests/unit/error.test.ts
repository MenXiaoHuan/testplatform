import { describe, expect, it } from 'vitest'
import { toErrorMessage } from '../../src/utils/error'

describe('toErrorMessage', () => {
  it('should extract msg field from backend error envelope', () => {
    expect(toErrorMessage({
      response: {
        data: {
          code: 'CONFLICT',
          msg: '当前场景已有执行中的任务，请稍后再试',
        },
      },
    }, '执行失败')).toBe('当前场景已有执行中的任务，请稍后再试')
  })

  it('should fall back to message field when msg is absent', () => {
    expect(toErrorMessage({
      response: {
        data: {
          code: 'BAD_REQUEST',
          message: '参数错误',
        },
      },
    }, '执行失败')).toBe('参数错误')
  })
})
