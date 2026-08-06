import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { mockServerPlugin } from './mock/mockServer'

// https://vitejs.dev/config/
// 通过函数形式读取 .env.local 中的 VITE_USE_MOCK（process.env 不会自动加载 .env 文件）
export default defineConfig(({ mode }) => {
  // loadEnv 会合并读取 .env / .env.local / .env.[mode]
  const env = loadEnv(mode, process.cwd(), '')
  // 是否启用 Mock Server（默认 dev 模式启用，可通过 VITE_USE_MOCK=false 关闭）
  const useMock = env.VITE_USE_MOCK !== 'false'

  return {
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
      port: 5175,
      host: '0.0.0.0',
      // 代理后端 API（避免开发环境 CORS）
      // 通过环境变量 VITE_API_PROXY_TARGET 配置，默认本地后端
      // 注意：启用 Mock 时会跳过 proxy（Mock 中间件优先匹配 /api/ 请求）
      ...(useMock
        ? {}
        : {
            proxy: {
              '/api': {
                target:
                  env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
                changeOrigin: true,
              },
            },
          }),
    },
  }
})
