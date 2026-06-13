import { defineStore } from 'pinia'
import { fetchSceneTasks, getTask, getTaskReport, getTaskReportSummary, listArtifacts, listTaskCases, listTasks, runScene } from '../api/task'
import type { CaseResultRecord, TaskReportSummary } from '../types/report'
import type { ArtifactRecord, TaskRecord, TaskReport } from '../types/task'

export const useTaskStore = defineStore('task', {
  state: () => ({
    items: [] as TaskRecord[],
    loading: false,
    page: 1,
    size: 10,
    total: 0,
    totalPages: 0,
    current: null as TaskRecord | null,
    report: null as TaskReport | null,
    reportSummary: null as TaskReportSummary | null,
    caseResults: [] as CaseResultRecord[],
    artifacts: [] as ArtifactRecord[],
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
      const [task, report, artifacts] = await Promise.all([
        getTask(taskId),
        getTaskReport(taskId),
        listArtifacts(taskId),
        ])
      this.current = task
      this.report = report
      this.artifacts = artifacts
    },
    async fetchReportSummary(taskId: number) {
      const summary = await getTaskReportSummary(taskId)
      this.reportSummary = summary
      return summary
    },
    async fetchTaskDetailPage(taskId: number) {
      const [taskResult, summaryResult, caseResult] = await Promise.allSettled([
        getTask(taskId),
        getTaskReportSummary(taskId),
        listTaskCases(taskId),
      ])

      if (taskResult.status === 'rejected') {
        throw taskResult.reason
      }

      this.current = taskResult.value
      this.report = {
        taskId,
        reportUrl: taskResult.value.reportUrl ?? null,
      }

      if (summaryResult.status === 'fulfilled') {
        this.reportSummary = summaryResult.value
        this.artifacts = summaryResult.value.artifacts
        this.report = {
          taskId,
          reportUrl: summaryResult.value.reportUrl,
        }
      } else {
        this.reportSummary = null
        this.artifacts = []
      }

      if (caseResult.status === 'fulfilled') {
        this.caseResults = caseResult.value
      } else {
        this.caseResults = []
      }
    },
  },
})
