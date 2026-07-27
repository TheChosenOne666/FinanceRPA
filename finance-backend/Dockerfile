# finance-backend/Dockerfile
# 多阶段构建：Maven 编译 → JRE 运行

# ========== Stage 1: 构建 ==========
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先复制 pom.xml，利用 Docker 缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# ========== Stage 2: 运行 ==========
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 时区
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 复制构建产物
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
