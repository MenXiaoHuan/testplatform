import type { SpaceAccessRequest } from '../types/space'

function reviewTimestamp(item: SpaceAccessRequest) {
  const reviewedAt = item.reviewedAt ? Date.parse(item.reviewedAt) : Number.NaN
  if (Number.isFinite(reviewedAt)) {
    return reviewedAt
  }
  const updatedAt = item.updatedAt ? Date.parse(item.updatedAt) : Number.NaN
  if (Number.isFinite(updatedAt)) {
    return updatedAt
  }
  const createdAt = item.createdAt ? Date.parse(item.createdAt) : Number.NaN
  return Number.isFinite(createdAt) ? createdAt : 0
}

export function sortSpaceAccessRequests(items: SpaceAccessRequest[]) {
  return [...items].sort((left, right) => {
    const leftPending = left.status === 'PENDING'
    const rightPending = right.status === 'PENDING'
    if (leftPending !== rightPending) {
      return leftPending ? -1 : 1
    }
    if (leftPending && rightPending) {
      return left.id - right.id
    }
    return reviewTimestamp(right) - reviewTimestamp(left)
  })
}
