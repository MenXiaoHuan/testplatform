import { defineStore } from 'pinia'
import { createRepository, deleteRepository, listRepositories, updateRepository } from '../api/repository'
import { createRepositoryForm, type RepositoryForm, type RepositoryRecord } from '../types/repository'

export const useRepositoryStore = defineStore('repository', {
  state: () => ({
    items: [] as RepositoryRecord[],
    loading: false,
  }),
  actions: {
    async fetchAll() {
      this.loading = true
      try {
        this.items = await listRepositories()
      } finally {
        this.loading = false
      }
    },
    async save(id: number | null, payload: RepositoryForm) {
      if (id === null) {
        await createRepository(payload)
      } else {
        await updateRepository(id, payload)
      }
      await this.fetchAll()
    },
    async remove(id: number) {
      await deleteRepository(id)
      await this.fetchAll()
    },
    createEmptyForm() {
      return createRepositoryForm()
    },
  },
})
