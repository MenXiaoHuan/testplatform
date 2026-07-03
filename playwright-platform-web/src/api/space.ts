import { del, get, post, put } from './http'
import type {
  CreateSpacePayload,
  ReviewSpaceAccessRequestPayload,
  SpaceAccessRequest,
  SpacePlazaItem,
  SpaceSummary,
  SubmitSpaceAccessRequestPayload,
  UpdateSpacePayload,
} from '../types/space'

export const listMySpaces = async () => {
  return get<SpaceSummary[]>('/spaces')
}

export const listSpacePlaza = async () => {
  return get<SpacePlazaItem[]>('/spaces/plaza')
}

export const createSpace = async (payload: CreateSpacePayload) => {
  return post<SpaceSummary>('/spaces', payload)
}

export const updateSpace = async (spaceId: number, payload: UpdateSpacePayload) => {
  return put<SpaceSummary>(`/spaces/${spaceId}`, payload)
}

export const deleteSpace = async (spaceId: number) => {
  await del(`/spaces/${spaceId}`)
}

export const listSpaceAccessRequests = async (spaceId: number) => {
  return get<SpaceAccessRequest[]>(`/spaces/${spaceId}/access-requests`)
}

export const submitSpaceAccessRequest = async (spaceId: number, payload: SubmitSpaceAccessRequestPayload) => {
  await post<void>(`/spaces/${spaceId}/access-requests`, payload)
}

export const approveSpaceAccessRequest = async (
  spaceId: number,
  requestId: number,
  payload: ReviewSpaceAccessRequestPayload,
) => {
  await post<void>(`/spaces/${spaceId}/access-requests/${requestId}/approve`, payload)
}

export const rejectSpaceAccessRequest = async (
  spaceId: number,
  requestId: number,
  payload: ReviewSpaceAccessRequestPayload,
) => {
  await post<void>(`/spaces/${spaceId}/access-requests/${requestId}/reject`, payload)
}
