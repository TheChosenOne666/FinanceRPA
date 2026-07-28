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
    // 通过环境变量 VITE_API_PROXY_TARGET 配置，默认 Docker 网络服务名
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://finance-backend:8080',
        changeOrigin: true,
      },
    },
  },
})
