<script setup lang="ts"> 
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menuItems = [
  { index: '/repos', label: '代码仓库管理' },
  { index: '/scenes', label: 'E2E场景管理' },
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/scenes/')) {
    return '/scenes'
  }
  if (route.path.startsWith('/tasks/')) {
    return ''
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
        <el-menu :default-active="activeMenu" class="nav-menu" @select="handleSelect">
          <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">{{ item.label }}</el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-main class="shell-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>
