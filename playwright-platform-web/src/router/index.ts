import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/scenes' },
    {
      path: '/repos',
      component: () => import('../views/repository/RepositoryListView.vue'),
      meta: { title: '代码仓库管理' },
    },
    {
      path: '/scenes',
      component: () => import('../views/scene/SceneListView.vue'),
      meta: { title: 'E2E场景管理' },
    },
    {
      path: '/scenes/:id/tasks',
      component: () => import('../views/task/TaskListView.vue'),
      meta: { title: '场景任务' },
    },
    {
      path: '/tasks',
      component: () => import('../views/task/TaskListView.vue'),
      meta: { title: '任务列表' },
    },
    {
      path: '/tasks/:id',
      component: () => import('../views/task/TaskDetailView.vue'),
      meta: { title: '任务详情' },
    },
  ],
})

export default router
