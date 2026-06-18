import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const apiProxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: false,
      resolvers: [
        ElementPlusResolver({
          importStyle: 'css',
        }),
      ],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('element-plus')) {
            return 'element-plus'
          }
          if (id.includes('vue-router') || id.includes('pinia') || id.includes('/vue/')) {
            return 'vue-core'
          }
          return 'vendor'
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/unit/**/*.{test,spec}.ts'],
    css: true,
    server: {
      deps: {
        inline: ['element-plus'],
      },
    },
  },
})
