import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { mockServerPlugin } from './mock/mockServer'

// 是否启用 Mock Server（默认 dev 模式启用，可通过 VITE_USE_MOCK=false 关闭）
const useMock = process.env.VITE_USE_MOCK !== 'false'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Mock Server：仅 dev 模式启用，本地测试 SSE 流无需启动 Java/Python 后端
    // 覆盖 /api/auth/* + /api/tasks/* + /api/ai/* 端点
    useMock && mockServerPlugin(),
  ].filter(Boolean),
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
    // 注意：启用 Mock 时会跳过 proxy（Mock 中间件优先匹配 /api/ 请求）
    ...(useMock
      ? {}
      : {
          proxy: {
            '/api': {
              target:
                process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
              changeOrigin: true,
            },
          },
        }),
  },
})
