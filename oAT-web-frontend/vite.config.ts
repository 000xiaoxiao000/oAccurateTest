import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

const backendTarget = process.env.OAT_BACKEND_TARGET || 'http://localhost:8899'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('/vue-router/') || id.includes('/pinia/')) {
            return 'router-pinia'
          }
          return 'vendor'
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': backendTarget,
      '/p': backendTarget,
      '/css': backendTarget,
      '/js': backendTarget,
      '/images': backendTarget,
      '/share': backendTarget,
      '/user': backendTarget,
      '/r': backendTarget,
    },
  },
})
