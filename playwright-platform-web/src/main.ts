import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { useSpaceStore } from './stores/space'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import './style.css'

const pinia = createPinia()
setActivePinia(pinia)

if (typeof window !== 'undefined') {
  window.addEventListener('platform:unauthorized', () => {
    const authStore = useAuthStore()
    const spaceStore = useSpaceStore()
    authStore.clearSession()
    spaceStore.clearState()
    if (router.currentRoute.value.path !== '/login') {
      void router.push('/login')
    }
  })
}

createApp(App).use(pinia).use(router).mount('#app')
