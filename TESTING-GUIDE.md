# 企业级工单智能分流系统 · 测试指南（带示例）

> 系统当前已在线：后端 `http://localhost:8080`、前端 `http://localhost:5173`、Redis `6379`。
> 三种测试方法：A 浏览器 UI（最直观）、B API/curl（逐节点验证）、C 自动化脚本（一键回归）。

---

## 0. 启动系统
- 双击桌面「工单系统」快捷方式，或运行 `D:\fenliu\start-ticket-system.bat`
- 自动开三个窗口：Redis → 后端(8080) → 前端(5173)，并打开浏览器到 5173
- 自检在线：`curl http://localhost:8080/api/tickets` 返回 JSON（code=0）即正常

---

## 1. 三类示例工单（覆盖全部 AI 路由）
| 分类 | 标题示例 | 描述示例 | 命中候选人组 |
|---|---|---|---|
| 硬件类 | 笔记本无法开机，疑似电池故障 | 今早开机无反应，插电也无指示灯，疑似电池或电源模块损坏，需更换硬件 | `hardware-group` |
| 财务类 | 差旅报销申请打款 | 上月出差北京，机票+酒店共 3200 元，附发票，申请付款 | `finance-group` |
| 权限类 | 申请开通生产库只读账号 | 数据分析需查询生产库 sales 表，申请只读权限 | `permission-group` |

> API 返回的 `category` 是枚举名（`HARDWARE`/`FINANCE`/`PERMISSION`），UI 显示中文「硬件类/财务类/权限类」。

---

## 2. 完整流程节点（一图看懂）
```
提交 ─► 初审 [第1步 · categoryGroup]
            │
            ├─(驳回)─► 补充材料 [categoryGroup] ─► 回到初审
            │
            └─(通过)─► 复审会签 [第2步 · reviewerA + reviewerB 并行]
                              │
                              ├─(任一驳回)─► 补充材料 ─► 回到初审
                              │
                              └─(都通过)─► 财务打款 [第3步 · finance-group]
                                            │
                                            └─► 归档(自动服务任务) ─► 结束(COMPLETED)
```
关键角色：
- **初审 / 补充材料**：按分类组，示例 `alice` 查 `hardware-group`
- **复审会签**：`reviewerA` + `reviewerB` 并行，**两人都通过**才进财务；任一驳回整体回补充材料
- **财务打款**：`bob` 查 `finance-group`

---

## 3. 方法 A：浏览器 UI 测试（推荐先跑，新版 UI 已优化）

### 新版 UI 改进点
- ✅ **预设角色按钮**：一键切换 alice/reviewerA/reviewerB/bob（不用手输用户名）
- ✅ **步骤提示**：每条待办显示「第X步」+ 该步说明（如"会签需两人都通过"）
- ✅ **「一键接单并通过」按钮**：两步合一，不用先接单再单独通过
- ✅ **操作后自动刷新视图**：不管你用哪种查询方式，操作完自动同类型刷新

### 以「示例1 硬件类」为例，完整走一遍

**第 1 步：提交工单**
1. 切到「提交工单」tab
2. 填标题：`笔记本无法开机，疑似电池故障`
3. 填描述：`今早开机无反应，插电也无指示灯，疑似电池或电源模块损坏`
4. 点「AI 预判」→ 看到 **分类=硬件类**
5. 点「提交工单」

**第 2 步：初审（alice）**
1. 切到「待办处理」tab
2. 点击预设按钮 **「初审(alice)」**（自动切用户=alice + 组=hardware-group）
3. 自动查询组内待办 → 看到「✍️ 初审」任务卡片（显示「第1步」）
4. 点 **「一键接单并通过」**（绿色按钮，一步完成接单+通过）

> ⚠️ 如果看不到任务？点「📊 全部待办」查看所有未完成任务。如果初审已完成但复审没出来，刷新页面试试。

**第 3 步：复审会签（reviewerA + reviewerB，必须都做！）**
> 🔑 这是之前卡住你的地方！复审是**并行会签**，需要两个审批人都操作。

1. 点击预设按钮 **「复审A」**（自动切用户=reviewerA + 查询我的待办）
2. 看到「🔍 复审」任务卡片（显示 **第2步 · 会签（需 reviewerA + reviewerB 都通过才进财务）**）
3. 点 **「一键接单并通过」**

4. 点击预设按钮 **「复审B」**（自动切用户=reviewerB + 查询我的待办）
5. 同样看到「复审」任务卡片
6. 点 **「一键接单并通过」**

> ❗ 必须两个人都点了「通过」，流程才会进入下一步财务打款。只做一个人，流程永远卡在复审！

**第 4 步：财务打款（bob）**
> 只有复审两人都完成后，这一步才会有任务出现！

1. 点击预设按钮 **「财务(bob)」**（自动切用户=bob + 组=finance-group）
2. 看到「💰 财务打款」任务卡片（显示「第3步」）
3. 点 **「接单」** → 再点 **「通过」**

**第 5 步：验证完成**
1. 切到「工单列表」tab → 点「刷新」
2. 该工单状态变 **COMPLETED**，当前节点为空

### 其他测试场景

**测驳回分支**：再提交一个工单，初审时点「驳回」（不点通过）→ 列表里该工单当前节点变「补充材料」。

**测动态驳回**：对任意 PROCESSING 工单，在列表选「动态驳回至 → 初审 / 补充材料 / 财务打款」→「执行」。

**测删除**：列表「删除」→ 二次确认 → 工单消失（流程实例与历史一并清除）。

**SLA 仪表盘**：走完几个工单后看各节点平均/最大时长，慢节点标红。

---

## 4. 方法 B：API / curl 测试（逐节点验证）
以下命令基于真实环境实跑，返回要点已标注。

**① 建单（硬件类）**
```bash
curl -X POST http://localhost:8080/api/tickets -H "Content-Type: application/json" \
  -d '{"title":"笔记本无法开机，疑似电池故障","description":"今早开机无反应，插电也无指示灯，疑似电池或电源模块损坏，需更换硬件","priority":3,"slaHours":24}'
```
返回要点：`code=0`，`data.category=HARDWARE`，`status=PROCESSING`，`currentNode=初审`。

**② 查硬件组待办**
```bash
curl "http://localhost:8080/api/tickets/tasks?group=hardware-group"
```
返回：含 `nodeName=初审`、`taskId`、`assignee=null` 的任务。

**③ 接单 + 初审通过**
```bash
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/claim?taskId={taskId}&userId=alice"
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/complete?taskId={taskId}&userId=alice&approved=true"
```

**④ 查全部待办 → 应出现两个复审任务**
```bash
curl "http://localhost:8080/api/tickets/tasks"
```
出现两个 `nodeName=复审`（分别属于 reviewerA / reviewerB）。
⚠️ **必须两个都 complete(approved=true)** 才能继续。

**⑤ 复审两人都通过（顺序可换）**
```bash
# reviewerA 先来
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/claim?taskId={revA_taskId}&userId=reviewerA"
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/complete?taskId={revA_taskId}&userId=reviewerA&approved=true"
# reviewerB 后来
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/claim?taskId={revB_taskId}&userId=reviewerB"
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/complete?taskId={revB_taskId}&userId=reviewerB&approved=true"
```

**⑥ 财务打款（复审全过后才出现！）**
```bash
curl "http://localhost:8080/api/tickets/tasks?group=finance-group"   # 此时才能查到 pay 任务
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/claim?taskId={pay_taskId}&userId=bob"
curl -X POST "http://localhost:8080/api/tickets/{工单ID}/complete?taskId={pay_taskId}&userId=bob&approved=true"
```

**⑦ 查最终状态**
```bash
curl "http://localhost:8080/api/tickets/{工单ID}"
```
→ `status=COMPLETED`，`currentNode=null`。

**其他接口速查**
- 列表过滤：`GET /api/tickets?status=PROCESSING&category=HARDWARE`
- 挂起/恢复：`POST /api/tickets/{id}/suspend` 、`POST /api/tickets/{id}/resume`
- 动态驳回：`POST /api/tickets/{id}/reject?target=audit1|revise|pay`
- 删除：`DELETE /api/tickets/{id}`
- AI 预判（不建单）：`POST /api/ai/route` body `{"title":"...","description":"..."}`
- SLA 数据：`GET /api/dashboard/sla`

---

## 5. 方法 C：自动化回归（一键 57 项）
```bash
cd D:\fenliu
python _selftest.py
```
覆盖：三类工单 happy path、初审驳回、复审驳回、二次复审死循环、动态驳回三目标、并发锁、currentNode 一致性、跨工单防护。**全部 PASS = 系统健康**。

---

## 6. 自检清单（验证系统是否真的"聪明"）
- [ ] AI 分派：硬件/财务/权限三类各命中对应候选人组
- [ ] 会签：必须 reviewerA 与 reviewerB 都通过才进财务（**只做一人会卡住**）
- [ ] 任一驳回：复审任一人驳回 → 整体回「补充材料」
- [ ] 动态驳回：可跳回 初审 / 补充材料 / 财务打款 任一节点
- [ ] 持久化：关掉系统再开，历史工单仍在（`D:\fenliu\data` 文件库）
- [ ] 并发安全：同一任务被两人同时接单，Redis 锁挡住重复处理
