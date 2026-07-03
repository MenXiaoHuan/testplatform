import type { SpaceRole } from '../types/space'

export interface SpaceMenuItem {
  index: string
  label: string
}

function hasAtLeastOperatorRole(role: SpaceRole | null) {
  return role === 'OPERATOR' || role === 'ADMIN'
}

function isAdminRole(role: SpaceRole | null) {
  return role === 'ADMIN'
}

export function getSpaceMenuItems(spaceId: number, role: SpaceRole | null): SpaceMenuItem[] {
  const items: SpaceMenuItem[] = [
    { index: `/spaces/${spaceId}/repos`, label: '仓库管理' },
    { index: `/spaces/${spaceId}/scenes`, label: '场景管理' },
  ]

  if (hasAtLeastOperatorRole(role)) {
    items.push({ index: `/spaces/${spaceId}/schedule-events`, label: '调度事件' })
  }

  if (isAdminRole(role)) {
    items.push({ index: `/spaces/${spaceId}/access-requests`, label: '空间审批' })
  }

  return items
}

export function getDefaultSpaceRoute(spaceId: number, role: SpaceRole | null) {
  return getSpaceMenuItems(spaceId, role)[0]?.index ?? `/spaces/${spaceId}/repos`
}

export function getActiveSpaceMenuIndex(path: string, items: SpaceMenuItem[]) {
  if (path.includes('/repos')) {
    return items.find((item) => item.index.includes('/repos'))?.index ?? ''
  }
  if (path.includes('/scenes')) {
    return items.find((item) => item.index.includes('/scenes'))?.index ?? ''
  }
  if (path.includes('/schedule-events')) {
    return items.find((item) => item.index.includes('/schedule-events'))?.index ?? ''
  }
  if (path.includes('/access-requests')) {
    return items.find((item) => item.index.includes('/access-requests'))?.index ?? ''
  }
  return ''
}

export function canAccessSpaceRoute(path: string, role: SpaceRole | null) {
  if (path.includes('/access-requests')) {
    return isAdminRole(role)
  }
  if (path.includes('/schedule-events')) {
    return hasAtLeastOperatorRole(role)
  }
  return true
}
