import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App.tsx'
import './index.css'

/**
 * 全局 React Query 客户端
 *
 * 配置说明：
 * - refetchOnWindowFocus: 关闭（避免切回浏览器标签时频繁刷新）
 * - retry: 失败重试 1 次（默认 3 次过多）
 * - staleTime: 5s（避免列表短时间内重复请求）
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 5_000,
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>,
)
