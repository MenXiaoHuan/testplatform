import { describe, expect, it } from 'vitest'
import {
  canAccessSpaceRoute,
  getActiveSpaceMenuIndex,
  getFlatMenuItems,
  getSpaceMenuGroups,
} from '../../src/utils/space-permissions'

const EVAL_LABELS = ['Agent 管理', 'Prompt 管理', '评测集管理', '离线任务', '在线监控']

describe('space permissions', () => {
  it('should group UI automation and model evaluation for viewer role', () => {
    const groups = getSpaceMenuGroups(7, 'VIEWER')

    expect(groups.map((group) => group.group)).toEqual(['UI 自动化', '模型评测'])
    expect(groups.flatMap((group) => group.children.map((item) => item.label))).toEqual([
      '仓库管理',
      '场景管理',
      ...EVAL_LABELS,
    ])
  })

  it('should keep platform ops hidden from operator and expose it only to admin', () => {
    const labels = getFlatMenuItems(7, 'OPERATOR').map((item) => item.label)

    expect(labels).toEqual(['仓库管理', '场景管理', ...EVAL_LABELS])
    expect(labels).not.toContain('空间审批')
  })

  it('should expose approval to admin', () => {
    const labels = getFlatMenuItems(7, 'ADMIN').map((item) => item.label)

    expect(labels).toEqual(['仓库管理', '场景管理', ...EVAL_LABELS, '空间审批'])
  })

  it('should keep task route accessible without showing it as a sidebar item', () => {
    const groups = getSpaceMenuGroups(7, 'VIEWER')

    expect(getActiveSpaceMenuIndex('/spaces/7/tasks', groups)).toBe('')
    expect(getActiveSpaceMenuIndex('/spaces/7/repos', groups)).toBe('/spaces/7/repos')
    expect(getActiveSpaceMenuIndex('/spaces/7/scenes', groups)).toBe('/spaces/7/scenes')
    expect(getActiveSpaceMenuIndex('/spaces/7/evaluation/targets', groups))
      .toBe('/spaces/7/evaluation/targets')
  })

  it('should allow admin to access approval route and reject viewer access', () => {
    expect(canAccessSpaceRoute('/spaces/7/access-requests', 'ADMIN')).toBe(true)
    expect(canAccessSpaceRoute('/spaces/7/access-requests', 'VIEWER')).toBe(false)
    expect(canAccessSpaceRoute('/spaces/7/tasks', 'VIEWER')).toBe(true)
    expect(canAccessSpaceRoute('/spaces/7/evaluation/targets', 'VIEWER')).toBe(true)
  })
})
