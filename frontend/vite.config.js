import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 15173,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        timeout: 120000,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // 禁用 SSE 响应的缓冲
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              proxyRes.headers['x-accel-buffering'] = 'no'
              delete proxyRes.headers['content-length']
              delete proxyRes.headers['cache-control']
            }
          })
        }
      },
      '/actuator': {
        target: 'http://localhost:9090',
        changeOrigin: true
      },
      '/skin-api': {
        target: 'https://pvp.qq.com',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/skin-api/, ''),
      }
    }
  }
})
