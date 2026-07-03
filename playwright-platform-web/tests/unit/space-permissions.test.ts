import { describe, expect, it } from 'vitest'
import { canAccessSpaceRoute, getActiveSpaceMenuIndex, getSpaceMenuItems } from '../../src/utils/space-permissions'

describe('space permissions', () => {
  it('should expose fewer shell modules to viewer role', () => {
    const items = getSpaceMenuItems(7, 'VIEWER')

    expect(items.map((item) => item.label)).toEqual([
      '仓库管理',
      '场景管理',
    ])
  })

  it('should expose schedule events to operator but keep access requests hidden', () => {
    const items = getSpaceMenuItems(7, 'OPERATOR')

    expect(items.map((item) => item.label)).toEqual([
      '仓库管理',
      '场景管理',
      '调度事件',
    ])
  })

  it('should keep task route accessible without showing it as a sidebar item', () => {
    const items = getSpaceMenuItems(7, 'VIEWER')

    expect(getActiveSpaceMenuIndex('/spaces/7/tasks', items)).toBe('')
    expect(getActiveSpaceMenuIndex('/spaces/7/repos', items)).toBe('/spaces/7/repos')
    expect(getActiveSpaceMenuIndex('/spaces/7/scenes', items)).toBe('/spaces/7/scenes')
  })

  it('should allow admin to access approval route and reject viewer access', () => {
    expect(canAccessSpaceRoute('/spaces/7/access-requests', 'ADMIN')).toBe(true)
    expect(canAccessSpaceRoute('/spaces/7/access-requests', 'VIEWER')).toBe(false)
    expect(canAccessSpaceRoute('/spaces/7/schedule-events', 'VIEWER')).toBe(false)
    expect(canAccessSpaceRoute('/spaces/7/tasks', 'VIEWER')).toBe(true)
  })
})
