import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSpaceStore } from '../stores/space'
import { canAccessSpaceRoute, getDefaultSpaceRoute } from '../utils/space-permissions'
import { showAppToast } from '../utils/ui-feedback'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../views/home/HomeView.vue'),
      meta: { title: '首页', requiresAuth: true },
    },
    {
      path: '/login',
      component: () => import('../views/auth/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/setup-nickname',
      component: () => import('../views/auth/SetupNicknameView.vue'),
      meta: { title: '设置昵称', requiresAuth: true },
    },
    {
      path: '/home',
      component: () => import('../views/home/HomeView.vue'),
      meta: { title: '空间广场', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/no-access',
      component: () => import('../views/space/SpaceNoAccessView.vue'),
      meta: { title: '申请加入空间', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/access-requests',
      component: () => import('../views/space/SpaceAccessRequestListView.vue'),
      meta: { title: '空间审批', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/users',
      component: () => import('../views/admin/UserManagementView.vue'),
      meta: { title: '用户管理', requiresAuth: true, superAdminOnly: true },
    },
    {
      path: '/spaces/:spaceId/repos',
      component: () => import('../views/repository/RepositoryListView.vue'),
      meta: { title: '代码仓库管理', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/scenes',
      component: () => import('../views/scene/SceneListView.vue'),
      meta: { title: 'E2E场景管理', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/patrol',
      component: () => import('../views/patrol/PatrolSceneListView.vue'),
      meta: { title: '巡页', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/evaluation',
      redirect: (to) => `/spaces/${to.params.spaceId}/evaluation/targets`,
    },
    {
      path: '/spaces/:spaceId/evaluation/targets',
      component: () => import('../views/evaluation/TargetListView.vue'),
      meta: { title: '目标管理', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/evaluation/datasets',
      component: () => import('../views/evaluation/DatasetListView.vue'),
      meta: { title: '评测集', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/evaluation/prompts',
      component: () => import('../views/evaluation/PromptListView.vue'),
      meta: { title: 'Prompt 托管', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/evaluation/runs',
      component: () => import('../views/evaluation/OfflineRunListView.vue'),
      meta: { title: '离线运行', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/evaluation/monitor',
      component: () => import('../views/evaluation/OnlineMonitorView.vue'),
      meta: { title: '在线监控', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/scenes/:sceneId/tasks',
      component: () => import('../views/task/TaskListView.vue'),
      meta: { title: '场景任务', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/tasks',
      component: () => import('../views/task/TaskListView.vue'),
      meta: { title: '任务列表', requiresAuth: true },
    },
    {
      path: '/spaces/:spaceId/tasks/:id',
      component: () => import('../views/task/TaskDetailView.vue'),
      meta: { title: '任务详情', requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const spaceStore = useSpaceStore()
  const isPublicRoute = to.meta.public === true

  if (!authStore.initialized) {
    try {
      await authStore.restoreSession()
    } catch {
      authStore.clearSession()
    }
  }

  if (isPublicRoute) {
    if (to.path === '/login' && authStore.isAuthenticated) {
      return '/home'
    }
    return true
  }

  if (!authStore.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath && to.fullPath !== '/home' ? { redirect: to.fullPath } : {},
    }
  }

  if (authStore.user?.needsSetup && to.path !== '/setup-nickname') {
    return '/setup-nickname'
  }

  if (!spaceStore.loaded) {
    await spaceStore.fetchSpaces()
  }

  if (!spaceStore.plazaLoaded) {
    await spaceStore.fetchPlaza()
  }

  if (to.path === '/') {
    return typeof spaceStore.currentSpaceId === 'number' ? `/spaces/${spaceStore.currentSpaceId}/repos` : '/home'
  }

  const rawSpaceId = typeof to.params.spaceId === 'string' ? Number(to.params.spaceId) : Number.NaN
  if (Number.isFinite(rawSpaceId)) {
    const matchedSpace = spaceStore.items.find((item) => item.id === rawSpaceId)
    if (!matchedSpace) {
      if (to.path !== `/spaces/${rawSpaceId}/no-access`) {
        showAppToast('你当前没有该空间的访问权限', 'error')
        return `/spaces/${rawSpaceId}/no-access`
      }
      return true
    }
    if (to.path === `/spaces/${rawSpaceId}/no-access`) {
      return `/spaces/${rawSpaceId}/repos`
    }
    spaceStore.setCurrentSpace(rawSpaceId)

    const currentRole = spaceStore.plazaItems.find((item) => item.id === rawSpaceId)?.currentRole ?? null
    if (!canAccessSpaceRoute(to.path, currentRole, authStore.isSuperAdmin)) {
      showAppToast('你当前没有该模块访问权限', 'warning')
      return getDefaultSpaceRoute(rawSpaceId, currentRole)
    }
  }

  return true
})

export default router
