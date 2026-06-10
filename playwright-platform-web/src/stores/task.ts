import { defineStore } from 'pinia'
import { getTask, getTaskReport, listArtifacts, listTasks, runScene } from '../api/task'
import type { ArtifactRecord, TaskRecord, TaskReport } from '../types/task'

export const useTaskStore = defineStore('task', {
  state: () => ({
    items: [] as TaskRecord[],
    loading: false,
    current: null as TaskRecord | null,
    report: null as TaskReport | null,
    artifacts: [] as ArtifactRecord[],
  }),
  actions: {
    async fetchAll() {
      this.loading = true
      try {
        this.items = await listTasks()
      } finally {
        this.loading = false
      }
    },
    async executeScene(sceneId: number) {
      await runScene(sceneId)
      await this.fetchAll()
    },
    async fetchDetail(taskId: number) {
      const [task, report, artifacts] = await Promise.all([
        getTask(taskId),
        getTaskReport(taskId),
        listArtifacts(taskId),
      ])
      this.current = task
      this.report = report
      this.artifacts = artifacts
    },
  },
})
