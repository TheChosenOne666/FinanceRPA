import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
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
        // 代理后端 API（避免开发环境 CORS）
        // 路径约定（对齐 system-design.md 4.3 节）：
        // - Python AI 服务路径已含 /api/v1/ai 前缀，直接转发
        // - Java 后端 controller 实际无 /api/v1 前缀（M1.1/M1.2 落地偏差），通过 rewrite 去除
        // - 生产环境由 nginx 配置 rewrite 实现同样效果
        proxy: {
            '/api/v1/ai': {
                target: 'http://localhost:8000',
                changeOrigin: true,
            },
            '/api/v1': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                rewrite: function (path) { return path.replace(/^\/api\/v1/, ''); },
            },
            '/actuator': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
