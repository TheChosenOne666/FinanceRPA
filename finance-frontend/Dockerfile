# finance-frontend/Dockerfile
# 多阶段构建：Node 构建 → Nginx 静态资源

# ========== Stage 1: 构建 ==========
FROM node:20-alpine AS builder

WORKDIR /build

# 先复制依赖文件，利用 Docker 缓存
COPY package.json package-lock.json ./
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# ========== Stage 2: 运行 ==========
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 替换默认配置（SPA history 路由 fallback）
RUN echo 'server { \
    listen 80; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html; \
        try_files $uri $uri/ /index.html; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
