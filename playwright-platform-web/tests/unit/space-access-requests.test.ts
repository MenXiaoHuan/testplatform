import { describe, expect, it } from 'vitest'
import { sortSpaceAccessRequests } from '../../src/utils/space-access-requests'
import type { SpaceAccessRequest } from '../../src/types/space'

function requestOf(partial: Partial<SpaceAccessRequest>): SpaceAccessRequest {
  return {
    id: partial.id ?? 1,
    spaceId: partial.spaceId ?? 7,
    applicantUserId: partial.applicantUserId ?? 2,
    applicantUsername: partial.applicantUsername ?? 'tester',
    applicantNickname: partial.applicantNickname ?? '测试用户',
    applicantAvatarUrl: partial.applicantAvatarUrl ?? null,
    requestedRole: partial.requestedRole ?? 'VIEWER',
    reason: partial.reason ?? 'test',
    status: partial.status ?? 'PENDING',
    reviewComment: partial.reviewComment ?? null,
    reviewedBy: partial.reviewedBy ?? null,
    reviewedAt: partial.reviewedAt ?? null,
    createdAt: partial.createdAt ?? '2026-07-03T10:00:00',
    updatedAt: partial.updatedAt ?? '2026-07-03T10:00:00',
  }
}

describe('space access requests sorting', () => {
  it('should put pending requests first and reviewed requests after them', () => {
    const sorted = sortSpaceAccessRequests([
      requestOf({ id: 3, status: 'APPROVED', reviewedAt: '2026-07-03T10:10:00' }),
      requestOf({ id: 1, status: 'PENDING', createdAt: '2026-07-03T10:00:00' }),
      requestOf({ id: 2, status: 'REJECTED', reviewedAt: '2026-07-03T10:20:00' }),
    ])

    expect(sorted.map((item) => item.id)).toEqual([1, 2, 3])
  })

  it('should sort reviewed requests by reviewed time descending', () => {
    const sorted = sortSpaceAccessRequests([
      requestOf({ id: 11, status: 'APPROVED', reviewedAt: '2026-07-03T10:10:00' }),
      requestOf({ id: 12, status: 'REJECTED', reviewedAt: '2026-07-03T10:30:00' }),
    ])

    expect(sorted.map((item) => item.id)).toEqual([12, 11])
  })
})
