import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 8081,
    host: '0.0.0.0',
    // 代理后端 API（避免开发环境 CORS）
    // Docker 环境：使用 Docker 网络服务名
    // 本地开发：将下方 target 改为 http://localhost:8080
    proxy: {
      '/api': {
        target: 'http://finance-backend:8080',
        changeOrigin: true,
      },
    },
  },
})
