import type { SpaceRole } from '../types/space'

export interface SpaceMenuItem {
  index: string
  label: string
}

export interface SpaceMenuGroup {
  group: string
  children: SpaceMenuItem[]
}

function isAdminRole(role: SpaceRole | null) {
  return role === 'ADMIN'
}

/**
 * 按一级分组返回当前空间的侧边栏菜单。
 *
 * 分组结构：
 *   UI 自动化  → 仓库管理 / 场景管理 / 巡页
 *   模型评测   → Agent 管理 / Prompt 管理 / 评测集管理 / 离线任务 / 在线监控
 *   平台运维   → 空间审批(Admin) / 用户管理(超管)
 */
export function getSpaceMenuGroups(
  spaceId: number,
  role: SpaceRole | null,
  superAdmin = false,
): SpaceMenuGroup[] {
  const groups: SpaceMenuGroup[] = [
    {
      group: 'UI 自动化',
      children: [
        { index: `/spaces/${spaceId}/repos`, label: '仓库管理' },
        { index: `/spaces/${spaceId}/scenes`, label: '场景管理' },
        { index: `/spaces/${spaceId}/patrol`, label: '巡页' },
      ],
    },
    {
      group: '模型评测',
      children: [
        { index: `/spaces/${spaceId}/evaluation/targets`,  label: 'Agent 管理' },
        { index: `/spaces/${spaceId}/evaluation/prompts`,  label: 'Prompt 管理' },
        { index: `/spaces/${spaceId}/evaluation/datasets`, label: '评测集管理' },
        { index: `/spaces/${spaceId}/evaluation/runs`,     label: '离线任务' },
        { index: `/spaces/${spaceId}/evaluation/monitor`,  label: '在线监控' },
      ],
    },
  ]

  // 平台运维分组 — 按角色追加子项
  const opsChildren: SpaceMenuItem[] = []
  if (isAdminRole(role)) {
    opsChildren.push({ index: `/spaces/${spaceId}/access-requests`, label: '空间审批' })
  }
  if (superAdmin) {
    opsChildren.push({ index: `/spaces/${spaceId}/users`, label: '用户管理' })
  }
  if (opsChildren.length > 0) {
    groups.push({
      group: '平台运维',
      children: opsChildren,
    })
  }

  return groups
}

/** 扁平化菜单（用于无空间时的广场页）。 */
export function getFlatMenuItems(spaceId: number, role: SpaceRole | null): SpaceMenuItem[] {
  return getSpaceMenuGroups(spaceId, role).flatMap((g) => g.children)
}

export function getDefaultSpaceRoute(spaceId: number, role: SpaceRole | null) {
  const groups = getSpaceMenuGroups(spaceId, role)
  const firstChild = groups[0]?.children[0]
  return firstChild?.index ?? `/spaces/${spaceId}/repos`
}

/** 根据当前 URL path 找到高亮用的菜单 index（优先精确匹配 evaluation 子路由）。 */
export function getActiveSpaceMenuIndex(path: string, groups: SpaceMenuGroup[]): string {
  // evaluation 子路由精确匹配优先
  const evalSubPaths = [
    '/evaluation/targets',
    '/evaluation/datasets',
    '/evaluation/prompts',
    '/evaluation/runs',
    '/evaluation/monitor',
  ]
  for (const sub of evalSubPaths) {
    if (path.includes(sub)) return groups.find((g) => g.group === '模型评测')?.children.find((c) => c.index.includes(sub.split('/').pop() ?? ''))?.index ?? path
  }
  if (path.includes('/evaluation')) return groups.find((g) => g.group === '模型评测')?.children[0]?.index ?? path
  if (path.includes('/repos'))     return groups.find((g) => g.group === 'UI 自动化')?.children.find((c) => c.index.includes('/repos'))?.index ?? ''
  if (path.includes('/scenes'))    return groups.find((g) => g.group === 'UI 自动化')?.children.find((c) => c.index.includes('/scenes'))?.index ?? ''
  if (path.includes('/patrol'))    return groups.find((g) => g.group === 'UI 自动化')?.children.find((c) => c.index.includes('/patrol'))?.index ?? ''
  if (path.includes('/access-requests')) return groups.find((g) => g.group === '平台运维')?.children.find((c) => c.index.includes('/access'))?.index ?? ''
  if (path.includes('/users'))          return groups.find((g) => g.group === '平台运维')?.children.find((c) => c.index.includes('/users'))?.index ?? ''
  return ''
}

/** 根据 URL path 找到所属分组（用于展开 sub-menu）。 */
export function getOpenMenuGroup(path: string, groups: SpaceMenuGroup[]): string {
  for (const g of groups) {
    if (g.children.some((c) => path.includes(c.index.replace(/\/$/, '').split('/').pop() ?? ''))) {
      return g.group
    }
  }
  return ''
}

export function canAccessSpaceRoute(path: string, role: SpaceRole | null, superAdmin = false) {
  if (path.includes('/access-requests')) return isAdminRole(role)
  if (path.includes('/users')) return superAdmin
  return true
}
