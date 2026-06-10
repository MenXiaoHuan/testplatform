import { describe, expect, it } from 'vitest'
import { isPositiveId, isRequired } from './validators'

describe('validators', () => {
  it('should reject blank text for required fields', () => {
    expect(isRequired('   ')).toBe(false)
  })

  it('should accept non-empty text for required fields', () => {
    expect(isRequired('repo-name')).toBe(true)
  })

  it('should reject non-positive ids', () => {
    expect(isPositiveId(0)).toBe(false)
  })

  it('should accept positive ids', () => {
    expect(isPositiveId(1)).toBe(true)
  })
})
