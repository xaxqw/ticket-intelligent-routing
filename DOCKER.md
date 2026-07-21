# 部署指南（Docker 一键起）

本系统提供容器化一键部署：后端（Spring Boot + Flowable + Redisson）与前端（Vite 构建产物）打进同一个 jar，由 **8080 端口同源托管**；MySQL 存业务 / 流程数据，Redis 提供分布式锁。

## 快速开始

```bash
# 在仓库根目录执行
docker compose up -d --build
```

- 前端 + 接口：http://localhost:8080/
- Swagger 文档：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

## 服务与端口

| 服务   | 镜像            | 端口  | 说明                                                |
|--------|-----------------|-------|-----------------------------------------------------|
| app    | 本地构建        | 8080  | 单 jar 同源托管前端页面与 REST API                  |
| mysql  | mysql:8.0       | 3306  | 业务库 + Flowable 流程库（启动自动建表）            |
| redis  | redis:7-alpine  | 6379  | Redisson 分布式锁（幂等建单 / 接单互斥）            |

## 环境变量（compose 已配，可覆盖）

| 变量                          | 默认                                            | 说明              |
|-------------------------------|-------------------------------------------------|-------------------|
| SPRING_PROFILES_ACTIVE        | mysql                                           | 启用 MySQL profile |
| SPRING_DATASOURCE_URL         | jdbc:mysql://mysql:3306/ticketdb?...            | 数据源 JDBC URL   |
| SPRING_DATASOURCE_USERNAME    | ticket                                          | 数据库用户        |
| SPRING_DATASOURCE_PASSWORD    | ticket123                                       | 数据库密码        |
| SPRING_REDIS_HOST             | redis                                           | Redis 主机        |
| SPRING_REDIS_PORT             | 6379                                            | Redis 端口        |

## 数据持久化

MySQL 数据落在命名卷 `mysql-data`，容器重建不丢数据。

## 健康检查与启动顺序

- MySQL / Redis 配置了 `healthcheck`，`app` 通过 `depends_on: condition: service_healthy` 等两者就绪后才启动，避免启动竞态。
- `app` 设置 `restart: on-failure` 自动重启。

## 本地开发模式（不使用 Docker）

```bash
# 终端 1：后端（默认 H2 文件库 + 本机已运行的 Redis）
mvn package && java -jar ticket-web/target/ticket-web-1.0.0.jar
# 终端 2：前端
cd ticket-frontend && npm install && npm run dev   # http://localhost:5173
```

## 修改前端后重新部署

前端改动需重新构建并拷入后端静态资源，再打 jar（或重新 `docker compose up -d --build`）：

```bash
cd ticket-frontend && npm run build
mkdir -p ticket-web/src/main/resources/static && cp -r dist/* ticket-web/src/main/resources/static/
mvn package -DskipTests
```
