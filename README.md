# 企业级工单智能分流与协同处理系统

> 低代码/AI + 业务流 · Spring Boot · Flowable · 大模型语义分派 · Redis 分布式锁 · ECharts

一个面向“财务报销 / IT 报修 / 客户投诉”等复杂企业工单场景的**智能分流 + 协同处理**系统。
核心解决两个痛点：**人工分派错漏** 与 **流转状态混乱**。

---

## 一、架构总览

```
                         ┌─────────────────────────────────────────────┐
   前端 (Vue3+ECharts)   │                  ticket-web                  │
   ┌──────────┐  REST    │  TicketController / DashboardController      │
   │ 提交/列表 │─────────▶│  TicketService（状态机 + 幂等 + Redis 锁）    │
   │ 待办/仪表 │◀─────────│        │                    │               │
   └──────────┘  /api    │        ▼                    ▼               │
                         │  ticket-ai          ticket-workflow          │
                         │  AI 语义分派          Flowable 引擎           │
                         │  (向量检索 few-shot)   (BPMN/会签/动态驳回)    │
                         └─────────────────────────────────────────────┘
                               │                    │
                           Redis(锁)          H2/MySQL(业务表)
                                              Flowable 历史表
```

### 模块职责（生产级多模块）

| 模块 | 职责 |
|------|------|
| `ticket-common` | 实体、状态机枚举、DTO、异常、雪花 ID、常量 |
| `ticket-ai` | 向量存储抽象 + 内存向量库（零依赖可跑）+ 可选 Chroma + few-shot 分类器 + 种子数据 |
| `ticket-workflow` | Flowable 自动配置、BPMN 流程、会签监听器、流程服务（含动态驳回） |
| `ticket-web` | Spring Boot 入口、业务编排、幂等锁、REST、SLA 聚合、归档委托 |
| `ticket-frontend` | Vue3 + Vite + ECharts 前端（提交 / 列表 / 待办 / SLA 仪表盘） |

---

## 二、四大硬核设计落点

1. **工作流引擎深度整合（Flowable）**
   - BPMN：`提交 → 初审 → 复审(会签) → 财务打款 → 归档`，含“驳回→补充材料”回路。
   - **会签**：`复审` 为并行多实例，任一审批人驳回即整体驳回（`completionCondition` + `TaskListener`）。
   - **动态驳回**：`WorkflowService.rejectDynamic` 通过 `ChangeActivityStateBuilder` 将执行跳转到任意历史节点——比固定回路更灵活的企业级能力。

2. **AI 语义分派（极简有效、不训练模型）**
   - 历史工单向量化入库（中文 char-bigram + 余弦相似度，零外部模型即可跑；生产可切 Chroma）。
   - 新工单 → 向量 → Top-K 相似历史工单加权投票 → 分类 + 置信度 + 可解释证据。
   - 目标 **85% 准确率**即可，工程成本低、上线快、可解释强。
   - **AI 建议 + 人工覆盖**：提交时可指定分类，体现人机协同兜底。

3. **状态机 + 幂等设计**
   - `TicketStatus` 严格定义 `待接单/处理中/挂起/已完成` 及合法流转，`canTransitionTo` 守卫杜绝状态混乱。
   - **幂等键 + Redis 分布式锁**：同一请求（idempotencyKey）并发提交只建一张单；接单加锁防止“超卖式”重复处理。

4. **可视化仪表盘（SLA 监控）**
   - 基于 Flowable 历史活动实例，聚合各环节平均/最大处理时长。
   - ECharts 柱状图暴露**慢节点**（超阈值标红），用于持续优化流程。

---

## 三、本地一键运行

> 环境要求：JDK 8+、Maven 3.6+、Node 18+、Redis（已测 3.2 可用）。

### 1. 启动 Redis
```bash
redis-server                          # 或 docker run -d -p 6379:79 redis
```

### 2. 启动后端
```bash
cd ticket-system
mvn -pl ticket-web -am spring-boot:run     # 默认端口 8080
# 或整包构建： mvn clean package && java -jar ticket-web/target/ticket-web-1.0.0.jar
```
启动后访问：
- API 基准：`http://localhost:8080/api`
- H2 控制台：`http://localhost:8080/h2-console`（JDBC: `jdbc:h2:mem:ticketdb`）

### 3. 启动前端
```bash
cd ticket-frontend
npm install
npm run dev                           # 开发服务器 http://localhost:5173 （/api 已代理到 8080）
# 或生产构建： npm run build && npm run preview
```

### 4. 跑通一条工单流（演示脚本）
```bash
# 1) AI 预判（不建单）
curl -X POST http://localhost:8080/api/ai/route \
  -H 'Content-Type: application/json' \
  -d '{"title":"笔记本无法开机","description":"电池故障 电源灯不亮"}'

# 2) 提交工单（自动分派 + 启动流程）
curl -X POST http://localhost:8080/api/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"财务部报销差旅费","description":"发票金额与明细不符 需复核","priority":3,"slaHours":8}'

# 3) 查看组内待办（按 AI 分派到的候选人组，如 finance-group）
curl "http://localhost:8080/api/tickets/tasks?group=finance-group"

# 4) 接单 + 通过（用上一步返回的 taskId）
curl -X POST "http://localhost:8080/api/tickets/{id}/claim?taskId={taskId}&userId=alice"
curl -X POST "http://localhost:8080/api/tickets/{id}/complete?taskId={taskId}&userId=alice&approved=true"

# 5) 查看 SLA
curl "http://localhost:8080/api/dashboard/sla"
```

---

## 四、主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tickets` | 提交工单（幂等建单 + AI 分派 + 启动流程） |
| GET  | `/api/tickets` | 工单列表（?status= / ?category= 过滤） |
| GET  | `/api/tickets/{id}` | 工单详情（含当前流程节点） |
| POST | `/api/tickets/{id}/claim` | 接单（Redis 锁防并发） |
| POST | `/api/tickets/{id}/complete` | 审批（approved=true/false 驱动流程） |
| POST | `/api/tickets/{id}/suspend` `/resume` | 挂起 / 恢复（状态机约束） |
| POST | `/api/tickets/{id}/reject` | 动态驳回（?target=audit1/revise/pay） |
| GET  | `/api/tickets/tasks` | 待办（?group= / ?assignee=） |
| POST | `/api/ai/route` | 仅语义分派（演示/联调） |
| GET  | `/api/dashboard/sla` | SLA 各环节时长 + 慢节点 |

---

## 五、技术选型与面试讲稿要点

**为什么用 Flowable 而不是自己写状态 if-else？**
企业流程会变（加一个会签节点、改驳回策略）。Flowable 用 BPMN 把“流程”从代码里抽离，改流程不改业务代码，且自带历史、任务、权限模型。这是“懂业务流转的后端”和“纯 CRUD”的分水岭。

**为什么 AI 分派不训练模型？**
工单分类样本少、迭代快、可解释要求高。few-shot 向量检索（历史工单做参考）85% 准确率就够，且能给出“为什么分到这类”的证据；真要提升再上 Chroma/微调，接口不变。

**幂等与分布式锁解决什么？**
高并发下“重复提交建多单”“两人同时接单重复处理”。幂等键防建单重入，Redis 锁防接单竞争——本质都是把“并发副作用”变成“幂等/互斥”。

**SLA 仪表盘的业务价值？**
把“流程慢在哪”量化出来。比如发现“复审”平均 3 天，就能针对性加人或改会签策略，而不是凭感觉优化。

---

## 六、可扩展方向（简历加分项）

- 流程版本灰度：BPMN 改了，旧流程实例不受影响（Flowable 天然支持多版本共存）。
- 向量库升级：本地 `LocalVectorStore` 换 `ChromaVectorStore`（`ai.vector-store-type=chroma`），零业务改动。
- 大模型增强：在 `TicketClassifier` 前加一层 LLM 摘要/关键词抽取，提升长文本分派准确率。
- 通知集成：工单分流 / 待人工复核 / 审批通过 / 驳回(含理由) 自动触发**可插拔通知**（默认 console 日志；在 `application.yml` 配置钉钉机器人 webhook 即推送到群）。
- 多租户：按 `categoryGroup` 扩展为部门/租户维度隔离。
```
