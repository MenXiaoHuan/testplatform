import { defineStore } from 'pinia'
import { createScene, deleteScene, getScene, listScenes, updateScene } from '../api/scene'
import { createSceneForm, type SceneDetail, type SceneForm, type SceneRecord } from '../types/scene'

function normalizeSceneRecord(record: SceneRecord) {
  return {
    ...record,
    description: record.description ?? '',
    cronExpression: record.cronExpression ?? '',
    lastTaskStatus: record.lastTaskStatus ?? null,
    lastRunAt: record.lastRunAt ?? null,
    environmentVariableCount: record.environmentVariableCount ?? 0,
  }
}

function toSceneForm(detail: SceneDetail): SceneForm {
  return {
    ...createSceneForm(),
    ...detail,
    branch: detail.branch ?? 'main',
    description: detail.description ?? '',
    testSelectorType: detail.testSelectorType ?? 'file',
    testSelectorValue: detail.testSelectorValue ?? detail.matchValue ?? '',
    matchValue: detail.matchValue ?? detail.testSelectorValue ?? '',
    projectName: detail.projectName ?? 'chromium',
    browser: detail.browser ?? 'chromium',
    envJson: detail.envJson ?? '',
    runCommand: detail.runCommand ?? 'node ./scripts/run-e2e.cjs',
    scheduleEnabled: detail.scheduleEnabled ?? false,
    cronExpression: detail.cronExpression ?? '',
  }
}

export const useSceneStore = defineStore('scene', {
  state: () => ({
    items: [] as SceneRecord[],
    loading: false,
    page: 1,
    size: 10,
    total: 0,
    totalPages: 0,
  }),
  actions: {
    async fetchAll(page?: number, size?: number) {
      const currentPage = page ?? this.page
      const currentSize = size ?? this.size
      this.loading = true
      try {
        const response = await listScenes(currentPage, currentSize)
        this.items = response.items.map(normalizeSceneRecord)
        this.page = response.page
        this.size = response.size
        this.total = response.total
        this.totalPages = response.totalPages
      } finally {
        this.loading = false
      }
    },
    async fetchOne(id: number) {
      const detail = await getScene(id)
      return toSceneForm(detail)
    },
    async save(id: number | null, payload: SceneForm) {
      if (id === null) {
        await createScene(payload)
        await this.fetchAll(1, this.size)
      } else {
        await updateScene(id, payload)
        await this.fetchAll(this.page, this.size)
      }
    },
    async remove(id: number) {
      await deleteScene(id)
      const nextPage = this.items.length === 1 && this.page > 1 ? this.page - 1 : this.page
      await this.fetchAll(nextPage, this.size)
    },
    createEmptyForm() {
      return createSceneForm()
    },
  },
})
