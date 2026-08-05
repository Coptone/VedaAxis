import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    base: env.VEDAAXIS_WEB_BASE || '/',
    plugins: [vue()],
    server: {
      port: 5173,
      strictPort: true,
    },
    test: {
      environment: 'jsdom',
    },
  }
})
