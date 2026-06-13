import { defineStore } from 'pinia'
import { createRepository, deleteRepository, listRepositories, updateRepository } from '../api/repository'
import { createRepositoryForm, type RepositoryForm, type RepositoryRecord } from '../types/repository'

export const useRepositoryStore = defineStore('repository', {
  state: () => ({
    items: [] as RepositoryRecord[],
    options: [] as RepositoryRecord[],
    loading: false,
    page: 1,
    size: 10,
    total: 0,
    totalPages: 0,
    optionsLoaded: false,
  }),
  actions: {
    async fetchAll(page?: number, size?: number) {
      const currentPage = page ?? this.page
      const currentSize = size ?? this.size
      this.loading = true
      try {
        const response = await listRepositories(currentPage, currentSize)
        this.items = response.items
        this.page = response.page
        this.size = response.size
        this.total = response.total
        this.totalPages = response.totalPages
      } finally {
        this.loading = false
      }
    },
    async fetchOptions() {
      const response = await listRepositories(1, 200)
      this.options = response.items
      this.optionsLoaded = true
    },
    async save(id: number | null, payload: RepositoryForm) {
      if (id === null) {
        await createRepository(payload)
        await this.fetchAll(1, this.size)
      } else {
        await updateRepository(id, payload)
        await this.fetchAll(this.page, this.size)
      }
      if (this.optionsLoaded) {
        await this.fetchOptions()
      }
    },
    async remove(id: number) {
      await deleteRepository(id)
      const nextPage = this.items.length === 1 && this.page > 1 ? this.page - 1 : this.page
      await this.fetchAll(nextPage, this.size)
      if (this.optionsLoaded) {
        await this.fetchOptions()
      }
    },
    createEmptyForm() {
      return createRepositoryForm()
    },
  },
})
