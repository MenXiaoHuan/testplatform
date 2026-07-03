export interface SpaceSummary {
  id: number
  name: string
  description: string
}

export type SpaceRole = 'VIEWER' | 'OPERATOR' | 'ADMIN'

export interface SpacePlazaItem {
  id: number
  name: string
  description: string
  ownerUserId: number
  ownerUsername: string
  ownerNickname: string
  ownerAvatarUrl: string | null
  accessible: boolean
  manageable: boolean
  currentRole: SpaceRole | null
  pendingRequestedRole: SpaceRole | null
}

export interface CreateSpacePayload {
  name: string
  description?: string
}

export interface UpdateSpacePayload {
  name: string
  description?: string
}

export interface SpaceAccessRequest {
  id: number
  spaceId: number
  applicantUserId: number
  applicantUsername: string
  applicantNickname: string
  applicantAvatarUrl: string | null
  requestedRole: string
  reason: string
  status: string
  reviewComment: string | null
  reviewedBy: number | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface SubmitSpaceAccessRequestPayload {
  requestedRole: string
  reason: string
}

export interface ReviewSpaceAccessRequestPayload {
  reviewComment?: string
}
