import { defineStore } from 'pinia'
import {
  approveSpaceAccessRequest,
  deleteSpace,
  createSpace,
  listSpacePlaza,
  listMySpaces,
  listSpaceAccessRequests,
  rejectSpaceAccessRequest,
  submitSpaceAccessRequest,
  updateSpace,
} from '../api/space'
import { useAuthStore } from './auth'
import type {
  CreateSpacePayload,
  ReviewSpaceAccessRequestPayload,
  SpaceAccessRequest,
  SpacePlazaItem,
  SpaceSummary,
  SubmitSpaceAccessRequestPayload,
  UpdateSpacePayload,
} from '../types/space'

const LAST_SPACE_ID_STORAGE_KEY = 'platform:last-space-id'

function readPersistedSpaceId() {
  if (typeof window === 'undefined') {
    return null
  }
  const raw = window.localStorage.getItem(LAST_SPACE_ID_STORAGE_KEY)
  const parsed = raw === null ? Number.NaN : Number(raw)
  return Number.isFinite(parsed) ? parsed : null
}

function persistSpaceId(spaceId: number | null) {
  if (typeof window === 'undefined') {
    return
  }
  if (typeof spaceId === 'number') {
    window.localStorage.setItem(LAST_SPACE_ID_STORAGE_KEY, String(spaceId))
    return
  }
  window.localStorage.removeItem(LAST_SPACE_ID_STORAGE_KEY)
}

function selectSpaceId(candidates: SpaceSummary[], preferredIds: Array<number | null | undefined>) {
  for (const preferredId of preferredIds) {
    if (typeof preferredId !== 'number') {
      continue
    }
    const matched = candidates.find((item) => item.id === preferredId)
    if (matched) {
      return matched.id
    }
  }
  return null
}

function sortPlazaItems(items: SpacePlazaItem[], currentUserId: number | null) {
  return [...items].sort((left, right) => {
    const leftPriority = left.ownerUserId === currentUserId ? 0 : left.accessible ? 1 : 2
    const rightPriority = right.ownerUserId === currentUserId ? 0 : right.accessible ? 1 : 2
    if (leftPriority !== rightPriority) {
      return leftPriority - rightPriority
    }
    return left.id - right.id
  })
}

export const useSpaceStore = defineStore('space', {
  state: () => ({
    items: [] as SpaceSummary[],
    plazaItems: [] as SpacePlazaItem[],
    currentSpaceId: null as number | null,
    loading: false,
    loaded: false,
    plazaLoaded: false,
    accessRequests: [] as SpaceAccessRequest[],
  }),
  getters: {
    currentSpace: (state) => state.items.find((item) => item.id === state.currentSpaceId) ?? null,
    currentSpacePlazaItem: (state) => state.plazaItems.find((item) => item.id === state.currentSpaceId) ?? null,
    currentSpaceRole(): SpacePlazaItem['currentRole'] {
      return this.currentSpacePlazaItem?.currentRole ?? null
    },
    hasSpaces: (state) => state.items.length > 0,
  },
  actions: {
    async fetchSpaces(preferredSpaceId?: number | null) {
      this.loading = true
      try {
        const spaces = (await listMySpaces()) ?? []
        this.items = spaces
        const authStore = useAuthStore()
        this.currentSpaceId = selectSpaceId(spaces, [
          preferredSpaceId,
          this.currentSpaceId,
          readPersistedSpaceId(),
          authStore.user?.lastSpaceId,
        ])
        persistSpaceId(this.currentSpaceId)
        this.loaded = true
        return this.items
      } finally {
        this.loading = false
      }
    },
    async fetchPlaza() {
      const authStore = useAuthStore()
      const items = (await listSpacePlaza()) ?? []
      this.plazaItems = sortPlazaItems(items, authStore.user?.id ?? null)
      this.plazaLoaded = true
      return this.plazaItems
    },
    syncCurrentUserIdentity() {
      const authStore = useAuthStore()
      const currentUser = authStore.user
      if (!currentUser) {
        return this.plazaItems
      }

      this.plazaItems = sortPlazaItems(
        this.plazaItems.map((item) => {
          if (item.ownerUserId !== currentUser.id) {
            return item
          }
          return {
            ...item,
            ownerUsername: currentUser.username,
            ownerNickname: currentUser.nickname,
            ownerAvatarUrl: currentUser.avatarUrl,
          }
        }),
        currentUser.id,
      )
      return this.plazaItems
    },
    setCurrentSpace(spaceId: number | null) {
      this.currentSpaceId = typeof spaceId === 'number' ? spaceId : null
      persistSpaceId(this.currentSpaceId)
    },
    async createNewSpace(payload: CreateSpacePayload) {
      const created = await createSpace(payload)
      const authStore = useAuthStore()
      this.items = [...this.items, created]
      this.plazaItems = sortPlazaItems([
        ...this.plazaItems,
        {
          ...created,
          ownerUserId: authStore.user?.id ?? 0,
          ownerUsername: authStore.user?.username ?? '',
          ownerNickname: authStore.user?.nickname ?? authStore.user?.username ?? '未命名用户',
          ownerAvatarUrl: authStore.user?.avatarUrl ?? null,
          accessible: true,
          manageable: true,
          currentRole: 'ADMIN',
          pendingRequestedRole: null,
        },
      ], authStore.user?.id ?? null)
      this.currentSpaceId = created.id
      persistSpaceId(this.currentSpaceId)
      this.loaded = true
      this.plazaLoaded = true
      return created
    },
    async updateExistingSpace(spaceId: number, payload: UpdateSpacePayload) {
      const updated = await updateSpace(spaceId, payload)
      this.items = this.items.map((item) => (item.id === spaceId ? updated : item))
      this.plazaItems = this.plazaItems.map((item) => (item.id === spaceId ? { ...item, ...updated } : item))
      if (this.currentSpaceId === spaceId) {
        this.currentSpaceId = updated.id
      }
      return updated
    },
    async removeSpace(spaceId: number) {
      await deleteSpace(spaceId)
      this.items = this.items.filter((item) => item.id !== spaceId)
      this.plazaItems = this.plazaItems.filter((item) => item.id !== spaceId)
      if (this.currentSpaceId === spaceId) {
        this.currentSpaceId = null
        persistSpaceId(null)
      }
    },
    async fetchAccessRequests(spaceId?: number | null) {
      const currentSpaceId = spaceId ?? this.currentSpaceId
      if (typeof currentSpaceId !== 'number') {
        this.accessRequests = []
        return []
      }

      this.accessRequests = await listSpaceAccessRequests(currentSpaceId)
      return this.accessRequests
    },
    async submitAccessRequest(payload: SubmitSpaceAccessRequestPayload, spaceId?: number | null) {
      const currentSpaceId = spaceId ?? this.currentSpaceId
      if (typeof currentSpaceId !== 'number') {
        throw new Error('spaceId is required')
      }

      await submitSpaceAccessRequest(currentSpaceId, payload)
      return undefined
    },
    async approveAccessRequest(requestId: number, payload: ReviewSpaceAccessRequestPayload, spaceId?: number | null) {
      const currentSpaceId = spaceId ?? this.currentSpaceId
      if (typeof currentSpaceId !== 'number') {
        throw new Error('spaceId is required')
      }

      await approveSpaceAccessRequest(currentSpaceId, requestId, payload)
      return this.fetchAccessRequests(currentSpaceId)
    },
    async rejectAccessRequest(requestId: number, payload: ReviewSpaceAccessRequestPayload, spaceId?: number | null) {
      const currentSpaceId = spaceId ?? this.currentSpaceId
      if (typeof currentSpaceId !== 'number') {
        throw new Error('spaceId is required')
      }

      await rejectSpaceAccessRequest(currentSpaceId, requestId, payload)
      return this.fetchAccessRequests(currentSpaceId)
    },
    clearState() {
      this.items = []
      this.plazaItems = []
      this.currentSpaceId = null
      persistSpaceId(null)
      this.loading = false
      this.loaded = false
      this.plazaLoaded = false
      this.accessRequests = []
    },
  },
})

export function requireCurrentSpaceId() {
  const spaceId = useSpaceStore().currentSpaceId
  if (typeof spaceId !== 'number') {
    throw new Error('当前未选择空间')
  }
  return spaceId
}
