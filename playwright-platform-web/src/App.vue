<script setup lang="ts"> 
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { index: '/repos', label: '测试仓库' },
  { index: '/scenes', label: '执行场景' },
  { index: '/tasks', label: '任务列表' },
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/tasks/')) {
    return '/tasks'
  }
  return route.path
})

function handleSelect(index: string) {
  void router.push(index)
}
</script>

<template>
  <el-config-provider>
    <el-container class="shell">
      <el-aside width="264px" class="shell-aside">
        <div class="brand">
          <p>Playwright Platform</p>
          <h2>测试平台 MVP</h2>
          <span>集中管理仓库、场景与执行任务</span>
        </div>
        <el-menu :default-active="activeMenu" class="nav-menu" @select="handleSelect">
          <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">{{ item.label }}</el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="shell-header">
          <div>
            <p class="eyebrow">Execution Console</p>
            <h3>{{ String(route.meta.title ?? '测试平台') }}</h3>
          </div>
          <div class="header-badge">MVP · Vue 3 + Element Plus</div>
        </el-header>
        <el-main class="shell-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>
