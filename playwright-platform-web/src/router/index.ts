import { createRouter, createWebHistory } from 'vue-router'
import RepositoryListView from '../views/repository/RepositoryListView.vue'
import SceneListView from '../views/scene/SceneListView.vue'
import TaskListView from '../views/task/TaskListView.vue'
import TaskDetailView from '../views/task/TaskDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/repos' },
    { path: '/repos', component: RepositoryListView, meta: { title: '测试仓库' } },
    { path: '/scenes', component: SceneListView, meta: { title: '执行场景' } },
    { path: '/tasks', component: TaskListView, meta: { title: '任务列表' } },
    { path: '/tasks/:id', component: TaskDetailView, meta: { title: '任务详情' } },
  ],
})

export default router
