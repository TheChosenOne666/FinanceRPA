/**
 * 应用根组件
 *
 * 职责：挂载路由配置（M1.3 接入 RouterProvider）
 * M4 前端开发阶段将按 UI 设计图实现完整布局与路由
 *
 * @author <a href="https://github.com/TheChosenOne666">小楼</a>
 * @from <a href="https://github.com/TheChosenOne666">TheChosenOne666</a>
 */

import { RouterProvider } from 'react-router-dom'
import router from '@/router'

function App() {
  return <RouterProvider router={router} />
}

export default App
