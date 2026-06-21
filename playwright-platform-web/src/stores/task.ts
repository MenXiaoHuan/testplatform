import { defineStore } from 'pinia'
import { cancelTask, fetchSceneTasks, getTask, listArtifacts, listTaskCases, listTaskLogs, listTasks, runScene } from '../api/task'
import type { CaseResultRecord } from '../types/report'
import type { ArtifactRecord, TaskRecord, TaskStageLogRecord } from '../types/task'

/**
 * Central Pinia store for task lists and task detail read models.
 *
 * The store keeps list pagination separate from the current detail view and
 * tolerates partial detail failures so artifacts, cases, or logs can fail
 * independently without hiding the task itself.
 */
export const useTaskStore = defineStore('task', {
  state: () => ({
    items: [] as TaskRecord[],
    loading: false,
    page: 1,
    size: 10,
    total: 0,
    totalPages: 0,
    current: null as TaskRecord | null,
    caseResults: [] as CaseResultRecord[],
    artifacts: [] as ArtifactRecord[],
    stageLogs: [] as TaskStageLogRecord[],
  }),
  actions: {
    async fetchAll(page?: number, size?: number) {
      const currentPage = page ?? this.page
      const currentSize = size ?? this.size
      this.loading = true
      try {
        const response = await listTasks(currentPage, currentSize)
        this.items = response.items
        this.page = response.page
        this.size = response.size
        this.total = response.total
        this.totalPages = response.totalPages
      } finally {
        this.loading = false
      }
    },
    async fetchByScene(sceneId: number, page?: number, size?: number) {
      const currentPage = page ?? this.page
      const currentSize = size ?? this.size
      this.loading = true
      try {
        const response = await fetchSceneTasks(sceneId, currentPage, currentSize)
        this.items = response.items
        this.page = response.page
        this.size = response.size
        this.total = response.total
        this.totalPages = response.totalPages
      } finally {
        this.loading = false
      }
    },
    async refreshList(sceneId?: number | null) {
      const response = typeof sceneId === 'number'
        ? await fetchSceneTasks(sceneId, this.page, this.size)
        : await listTasks(this.page, this.size)
      this.items = response.items
      this.page = response.page
      this.size = response.size
      this.total = response.total
      this.totalPages = response.totalPages
    },
    async executeScene(sceneId: number, _refreshSceneId?: number | null) {
      const createdTask = await runScene(sceneId)
      this.page = 1
      this.total += 1
      this.totalPages = Math.ceil(this.total / this.size)
      this.items = [
        createdTask,
        ...this.items.filter((item) => item.id !== createdTask.id),
      ].slice(0, this.size)
      if (this.totalPages === 0) {
        this.totalPages = 1
      }
      return createdTask
    },
    setPage(page: number) {
      this.page = page
    },
    setSize(size: number) {
      this.size = size
      this.page = 1
    },
    resetPagination() {
      this.page = 1
      this.size = 10
      this.total = 0
      this.totalPages = 0
    },
    async fetchDetail(taskId: number) {
      const [task, artifacts, stageLogs] = await Promise.all([
        getTask(taskId),
        listArtifacts(taskId),
        listTaskLogs(taskId),
      ])
      this.current = task
      this.artifacts = artifacts
      this.stageLogs = stageLogs
    },
    async fetchTaskDetailPage(taskId: number) {
      const [taskResult, artifactResult, caseResult, stageLogResult] = await Promise.allSettled([
        getTask(taskId),
        listArtifacts(taskId),
        listTaskCases(taskId),
        listTaskLogs(taskId),
      ])

      if (taskResult.status === 'rejected') {
        throw taskResult.reason
      }

      this.current = taskResult.value
      if (artifactResult.status === 'fulfilled') {
        this.artifacts = artifactResult.value
      } else {
        this.artifacts = []
      }

      if (caseResult.status === 'fulfilled') {
        this.caseResults = caseResult.value
      } else {
        this.caseResults = []
      }

      if (stageLogResult.status === 'fulfilled') {
        this.stageLogs = stageLogResult.value
      } else {
        this.stageLogs = []
      }
    },
    async cancelCurrentTask(taskId: number) {
      await cancelTask(taskId)
      this.current = await getTask(taskId)
    },
  },
})
