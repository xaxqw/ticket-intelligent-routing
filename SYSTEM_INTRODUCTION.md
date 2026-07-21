# 企业级工单智能分流与协同处理系统 —— 完整介绍

> 本文从「AI / 大模型」与「工程开发」两个视角完整介绍该系统，可作为技术设计与实现参考。

## 0. 一句话定位

一个把“自然语言工单”自动判类、智能路由到正确处理组、并按业务规则走 Flowable 工作流协同处理的系统。其 AI 部分不是关键词匹配的“假 AI”，而是**带语义向量、置信度校准、人工复核闭环与主动学习数据飞轮**的少样本检索分类器。

## 1. 系统解决什么问题

- 企业工单进来后，人工分派慢、易错、口径不一致。
- 不同工单需要的审批链路不同（如：并非所有工单都需要财务打款）。
- 传统规则引擎难覆盖长尾语义；纯大模型又贵、不可控、不可解释。
- 本系统在**成本、可控性、准确率**之间取平衡：轻量语义检索分类 + 可解释路由 + 工作流编排。

## 2. 整体架构（见架构图）

| 层 | 组件 | 职责 |
|---|---|---|
| 表现层 | Vue3 + Vite SPA | Tab 切换、AiExplain 解释条、AI 复核 |
| 接入层 | ticket-web（Spring Boot 2.7） | REST API、ApiResponse 统一包装、幂等/鉴权 |
| AI 引擎 | ticket-ai | 语义向量化、检索投票分类、置信度校准、HIL |
| 流程引擎 | Flowable 6.8 | 条件路由 gw3、会签/驳回、SLA 时效 |
| 基础设施 | Redisson/Redis + H2/MySQL | 分布式锁幂等、持久化 |
| 数据飞轮 | 主动学习 | uncertainty-pool → label → learn 回流向量库 |

## 3. AI / 大模型视角（核心亮点）

### 3.1 为什么不是“关键词匹配”

早期版本是字符 bigram 词频，属于词法层面，对同义/近义/口语化无能为力，且没有量化指标、置信度算出来也没用。P0 起重构为**语义向量 + 检索投票**，并补上评测与人工复核闭环。

### 3.2 语义向量化（可插拔，关键设计）

核心抽象：`LocalVectorStore` 的向量化器是 `Function<String, Map<String,Double>>`，分类器与具体向量化方式彻底解耦。

- **默认 `TfIdfVectorizer`**：用种子语料离线算 IDF（`ln((N+1)/(df+1))+1`）+ L2 归一化，零外部依赖、启动即用。
- **可升级 `RemoteEmbeddingVectorizer`**：调用 OpenAI 兼容 `/v1/embeddings`（兼容 OpenAI / Azure / 阿里云百炼 / 自建）取**稠密语义向量**，L2 归一化后以 `Map<维度下标, 分量>` 注入检索；按文本 LRU 缓存。
- `AiAutoConfiguration` 按 `ai.embedding.enabled + apiKey` 自动切换；`GET /api/ai/vectorizer` 实时展示当前向量化器身份（`local-tfidf` / `remote-embedding[...]`），可灰度、可监控。
- 可插拔性有单测证明：`PluggableEmbeddingTest` 用桩稠密向量器喂 `TicketClassifier + LocalVectorStore` 断言分类正确。

### 3.3 少样本检索投票分类器（few-shot retrieval voting）

- 向量库存种子历史工单（每类若干标注样本）的向量。
- 新工单向量化后，与库内所有样本做余弦相似度，按“相似样本所属类别”做加权投票，得 `bestCategory` 与 `voteShare`。
- 取 Top-K 相似样本作为“证据”，前端 `AiExplain` 展示，满足可解释性。

### 3.4 置信度校准（防止假阳性）

- 原始 `confidence = voteShare` 在弱信号下会虚高（如“帮忙处理一下这个事”被判 1.00）。
- 校准：`confidence = voteShare × min(1, topSim / 0.35)`，用“最高相似度”给投票占比打折，弱语义信号自然降权。

### 3.5 人工复核闭环 HIL（Human-in-the-Loop）

- `reviewThreshold = 0.55`：置信度低于阈值 → `aiReviewRequired=true`、工单置 `PENDING`、**不自动启动流程**，进“AI 复核”队列。
- 人工在“AI 复核”Tab 确认或改判 → `classifier.learn(text, category)` 在线学习（样本回流向量库）→ 启动对应 Flowable 流程。
- 实测：评测集中 11 次低置信触发，拦截全部 3/3 例误分流。

### 3.6 在线学习 + 主动学习（数据飞轮）

- **在线学习**：每一次人工确认/改判都 `learn()` 回流，越用越准。
- **主动学习**：`GET /api/ai/uncertainty-pool` 返回当前 `aiReviewRequired` 待复核工单（模型最该让人标的样本）；`POST /api/ai/label` 批量接收 `{text, category}` 标注并 `learn()` 回流。
- 闭环演示（`_active_learn.py`，54 条标注集）：把低置信样本按金标准回流 → 复评准确率提升、HIL 触发降为 0，且对评测集外相似句（投影仪/麦克风/摄像头 + 会议）具备泛化。

### 3.7 评测体系（量化指标）

- `ai-eval.json`：54 条人工标注样本（3 类 × 18，含同义/歧义用例）。
- `_eval_ai.py`：一键输出 accuracy / 每类 P-R-F1 / macro-F1 / 混淆矩阵 / 置信度分布 / HIL 拦截统计。
- 实测（严格留出评测集口径）：运行语料为 **36 条独立种子**（18 条原始种子 + 18 条新增扩充），与 54 条标注集**零文本重叠**（无数据泄漏）。以该独立语料对 54 条标注集分类，准确率 **98.1%（53/54）**、HIL 触发 7/54；分三类命中 HARDWARE 19/19、FINANCE 17/18、PERMISSION 17/17。边界句（投影仪/摄像头/麦克风等）全部高置信正确；在 10 条未见措辞的泛化句上 **90%** 正确，全部不确定工单由 HIL 兜底（**自动误分流率 0%**）。

### 3.8 可解释性

前端 `AiExplain.vue`：分类标签 + 置信度进度条（绿/橙/红）+ 复核徽标 + Top-K 证据行，让"AI 为什么这么分"可见。

## 4. 工程开发视角

### 4.1 技术栈

- 后端：Java 8 + Spring Boot 2.7.18 + Flowable 6.8（工作流）+ Redisson（Redis 分布式锁）
- 前端：Vue3 + Vite（无 vue-router，Tab 切换）
- 数据：H2（演示）/ MySQL 8（生产 profile）
- 文档：springdoc OpenAPI
- 部署：Docker + Docker Compose

### 4.2 多模块 Maven 结构（职责清晰）

- `ticket-common`：实体、DTO（RouteResult / TicketVO / ApiResponse）、Repository 接口
- `ticket-ai`：向量化、向量库、分类器、配置（纯 AI 逻辑，可单测）
- `ticket-workflow`：Flowable BPMN 流程定义与监听器
- `ticket-web`：Spring Boot 主应用、Controller、Service、配置
- `ticket-frontend`：Vue3 前端

### 4.3 工作流协同（Flowable）

- 流程：提交 → 初审 → 复审（会签）→ 条件网关 `gw3` → 仅 FINANCE 类走“财务打款”节点 → 结束。
- **条件性路由**：AI 分类决定流程是否含财务打款节点，呼应“智能分流”定位（不是前端隐藏，是引擎按分类分支）。
- **会签/驳回**：`ReviewCompleteListener` 在有人驳回时置 `rejected=true` 提前结束会签。
- **SLA 时效**：工单记录各节点时间，具备扩展超时告警的基础。

### 4.4 分布式与并发

`RedisDistributedLock`（Redisson）做幂等/并发控制，避免重复建单 / 重复 Claim。

### 4.5 API 与前后端协作

- 统一 `ApiResponse<T>` 包装；`/api/ai/route`（仅分类不建单）、`/api/tickets`（建单/列表）、`/api/tasks`（待办）、`/api/ai/uncertainty-pool`、`/api/ai/label`、`/api/ai/vectorizer`。
- **单 jar 同源托管**：前端 `dist` 拷入后端 `static`，8080 同时出 API + UI，无需独立静态服务器。

### 4.6 工程化（P1）

- `application-mysql.yml`：生产 profile（MySQL8 方言 + 环境变量注入 + `globally_quoted_identifiers` 防保留字）。
- `Dockerfile`：多阶段（node 构建前端 → maven 打包 → eclipse-temurin:8-jre 运行），默认 mysql profile。
- `docker-compose.yml`：mysql:8.0 + redis:7-alpine + app，含 healthcheck + `depends_on: service_healthy`。
- springdoc：`OpenApiConfig` + `application.yml` 配置，`/swagger-ui.html` 可交互调试。

## 5. 量化成果

- 自研 TF-IDF 语义向量 + 少样本检索投票分类器；构建 54 条**严格留出的**标注评测集，运行语料为 **36 条独立种子**（与评测集 0 文本重叠、无数据泄漏），独立评测准确率 **98.1%（53/54）**、泛化 90% 且 HIL 自动误派率 0%。
- 置信度阈值触发人工复核闭环（HIL）拦截全部误分流，并支持人工纠偏在线回流向量库（主动学习数据飞轮）。
- 分类器与向量化器以 `Function<String,Map<String,Double>>` 接口解耦，零改动即可从本地 TF-IDF 切换为 BGE/OpenAI 稠密语义 embedding（OpenAI 兼容 /v1/embeddings，L2 归一化 + LRU 缓存），附单测证明可插拔性。
- 容器化一键部署：Docker Compose 编排 MySQL + Redis + 应用，健康检查依赖启动；springdoc 自动接口文档；前端产物打进单 jar 同源托管。

## 6. 已知边界 / 可扩展方向（诚实交代）

- **通知集成（钉钉/邮件 connector）**：已实现**可插拔 NotificationService**——默认 console 落日志（零依赖、离线可演示），钉钉群机器人 webhook 可配置即启用（支持加签），邮件/飞书按同一接口扩展即可。
- **真实 embedding 默认关闭**（需配置 apiKey），默认走本地 TF-IDF（零成本、可演示）。
- **向量库为内存态**，生产可接 Chroma/PGVector 做持久化与规模化（接口已留 `vectorStoreType`）。
