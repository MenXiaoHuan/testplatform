import { createRouter, createWebHistory } from 'vue-router'
import RepositoryListView from '../views/repository/RepositoryListView.vue'
import SceneListView from '../views/scene/SceneListView.vue'
import TaskListView from '../views/task/TaskListView.vue'
import TaskDetailView from '../views/task/TaskDetailView.vue'
import TaskReportView from '../views/task/TaskReportView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/scenes' },
    { path: '/repos', component: RepositoryListView, meta: { title: '代码仓库管理' } },
    { path: '/scenes', component: SceneListView, meta: { title: 'E2E场景管理' } },
    { path: '/scenes/:id/tasks', component: TaskListView, meta: { title: '场景任务' } },
    { path: '/tasks', component: TaskListView, meta: { title: '任务列表' } },
    { path: '/tasks/:id', component: TaskDetailView, meta: { title: '任务详情' } },
    { path: '/tasks/:id/report', component: TaskReportView, meta: { title: '任务报告' } },
  ],
})

export default router
