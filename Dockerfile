# syntax=docker/dockerfile:1
# ============================================================================
# 多阶段构建：前端(Vite) -> 后端(Maven) -> 运行(eclipse-temurin:8-jre)
# 最终镜像为「单 jar 同源托管」：后端 8080 同时提供 API 与打包好的前端页面。
# ============================================================================

# ---------- 1) 前端构建阶段 ----------
FROM node:18-alpine AS frontend
WORKDIR /frontend
# 仅复制源码（node_modules / dist 由 .dockerignore 排除，容器内重新安装构建，保证跨平台二进制正确）
COPY ticket-frontend/package.json ./
RUN npm install
COPY ticket-frontend/ ./
RUN npm run build

# ---------- 2) 后端构建阶段 ----------
FROM maven:3.9-eclipse-temurin-8 AS backend
WORKDIR /app
COPY . .
# 把前端构建产物放入后端静态资源目录，单 jar 同源托管
RUN mkdir -p ticket-web/src/main/resources/static \
    && cp -r /frontend/dist/* ticket-web/src/main/resources/static/
RUN mvn -B -q package -DskipTests

# ---------- 3) 运行阶段 ----------
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=backend /app/ticket-web/target/ticket-web-1.0.0.jar /app/app.jar
# 容器默认走 MySQL profile（compose 会注入数据源 / Redis 环境变量）
ENV SPRING_PROFILES_ACTIVE=mysql \
    JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
