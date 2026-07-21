# -*- coding: utf-8 -*-
"""
工单系统可视化走查脚本（覆盖三类工单 + 关键分支）
运行:  cd D:\fenliu && python test-walkthrough.py
前置:  系统已启动（后端 8080 / 前端 5173 / Redis 6379）
"""
import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
PASS, FAIL = 0, 0

def call(method, path, data=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"__error": e.code, "body": e.read().decode()}

def ok(label, cond, extra=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  ✅ {label} {extra}")
    else:
        FAIL += 1
        print(f"  ❌ {label} {extra}")

def create(title, desc, cat=None, pri=2):
    payload = {"title": title, "description": desc, "priority": pri, "slaHours": 24}
    if cat:
        payload["category"] = cat
    r = call("POST", "/tickets", payload)
    assert r.get("code") == 0, f"建单失败: {r}"
    return r["data"]

def tasks_by(group="", assignee=""):
    r = call("GET", "/tickets/tasks", params={"group": group, "assignee": assignee})
    return r.get("data") or []

def find(tasks, tid):
    return [t for t in tasks if t.get("ticketId") == tid]

def claim(tid, task_id, user):
    return call("POST", f"/tickets/{tid}/claim", params={"taskId": task_id, "userId": user})

def complete(tid, task_id, user, approved):
    return call("POST", f"/tickets/{tid}/complete", params={"taskId": task_id, "userId": user, "approved": approved})

def get(tid):
    return call("GET", f"/tickets/{tid}").get("data", {})

def delete(tid):
    return call("DELETE", f"/tickets/{tid}")

# ============ 场景1: 硬件类 happy path ============
print("\n=== 场景1: 硬件类工单 完整流程 ===")
t = create("笔记本无法开机，疑似电池故障", "今早开机无反应，插电也无指示灯，疑似电池或电源模块损坏", pri=3)
tid = t["id"]
ok("AI 分派=硬件类", t["category"] == "HARDWARE", f"({t['category']})")
ok("初始节点=初审", t["currentNode"] == "初审", f"({t['currentNode']})")

# 初审 alice
ts = find(tasks_by(group="hardware-group"), tid)
ok("硬件组有初审任务", len(ts) == 1)
claim(tid, ts[0]["taskId"], "alice"); complete(tid, ts[0]["taskId"], "alice", True)

# 复审会签 reviewerA + reviewerB
ts = find(tasks_by(assignee="reviewerA"), tid)
ok("reviewerA 有复审任务", len(ts) == 1, f"(共{len(ts)}个)")
claim(tid, ts[0]["taskId"], "reviewerA"); complete(tid, ts[0]["taskId"], "reviewerA", True)
ts = find(tasks_by(assignee="reviewerB"), tid)
ok("reviewerB 有复审任务", len(ts) == 1)
claim(tid, ts[0]["taskId"], "reviewerB"); complete(tid, ts[0]["taskId"], "reviewerB", True)

# 财务打款 bob
ts = find(tasks_by(group="finance-group"), tid)
ok("财务组有打款任务", len(ts) == 1, f"(节点={ts[0]['nodeName']})")
claim(tid, ts[0]["taskId"], "bob"); complete(tid, ts[0]["taskId"], "bob", True)

t = get(tid)
ok("最终状态=COMPLETED", t["status"] == "COMPLETED", f"({t['status']})")
delete(tid)
ok("删除工单成功", call("GET", f"/tickets/{tid}").get("__error") == 400)

# ============ 场景2: 财务类 happy path ============
print("\n=== 场景2: 财务类工单 完整流程 ===")
t = create("差旅报销申请打款", "上月出差北京，机票+酒店共 3200 元，附发票，申请付款", pri=2)
tid = t["id"]
ok("AI 分派=财务类", t["category"] == "FINANCE", f"({t['category']})")
ts = find(tasks_by(group="finance-group"), tid)  # 财务类初审组也是 finance-group
ok("财务组有初审任务", len(ts) == 1)
claim(tid, ts[0]["taskId"], "alice"); complete(tid, ts[0]["taskId"], "alice", True)
ts = find(tasks_by(assignee="reviewerA"), tid)
claim(tid, ts[0]["taskId"], "reviewerA"); complete(tid, ts[0]["taskId"], "reviewerA", True)
ts = find(tasks_by(assignee="reviewerB"), tid)
claim(tid, ts[0]["taskId"], "reviewerB"); complete(tid, ts[0]["taskId"], "reviewerB", True)
ts = find(tasks_by(group="finance-group"), tid)
ok("财务打款任务出现", len(ts) == 1)
claim(tid, ts[0]["taskId"], "bob"); complete(tid, ts[0]["taskId"], "bob", True)
ok("最终状态=COMPLETED", get(tid)["status"] == "COMPLETED")
delete(tid)

# ============ 场景3: 权限类 happy path ============
print("\n=== 场景3: 权限类工单 完整流程 ===")
t = create("申请开通生产库只读账号", "数据分析需查询生产库 sales 表，申请只读权限", pri=2)
tid = t["id"]
ok("AI 分派=权限类", t["category"] == "PERMISSION", f"({t['category']})")
ts = find(tasks_by(group="permission-group"), tid)
ok("权限组有初审任务", len(ts) == 1)
claim(tid, ts[0]["taskId"], "alice"); complete(tid, ts[0]["taskId"], "alice", True)
ts = find(tasks_by(assignee="reviewerA"), tid)
claim(tid, ts[0]["taskId"], "reviewerA"); complete(tid, ts[0]["taskId"], "reviewerA", True)
ts = find(tasks_by(assignee="reviewerB"), tid)
claim(tid, ts[0]["taskId"], "reviewerB"); complete(tid, ts[0]["taskId"], "reviewerB", True)
ts = find(tasks_by(group="finance-group"), tid)
claim(tid, ts[0]["taskId"], "bob"); complete(tid, ts[0]["taskId"], "bob", True)
ok("最终状态=COMPLETED", get(tid)["status"] == "COMPLETED")
delete(tid)

# ============ 场景4: 初审驳回 -> 补充材料 ============
print("\n=== 场景4: 初审驳回分支 ===")
t = create("测试驳回", "测试初审驳回回到补充材料", cat="硬件类", pri=1)
tid = t["id"]
ts = find(tasks_by(group="hardware-group"), tid)
claim(tid, ts[0]["taskId"], "alice"); complete(tid, ts[0]["taskId"], "alice", False)  # 驳回
ok("驳回后节点=补充材料", get(tid)["currentNode"] == "补充材料")
delete(tid)

# ============ 场景5: 动态驳回 ============
print("\n=== 场景5: 动态驳回(运行中发现某步错了，跳回初审) ===")
t = create("测试动态驳回", "走到财务后动态驳回回初审", cat="硬件类", pri=2)
tid = t["id"]
ts = find(tasks_by(group="hardware-group"), tid)
claim(tid, ts[0]["taskId"], "alice"); complete(tid, ts[0]["taskId"], "alice", True)
ts = find(tasks_by(assignee="reviewerA"), tid)
claim(tid, ts[0]["taskId"], "reviewerA"); complete(tid, ts[0]["taskId"], "reviewerA", True)
ts = find(tasks_by(assignee="reviewerB"), tid)
claim(tid, ts[0]["taskId"], "reviewerB"); complete(tid, ts[0]["taskId"], "reviewerB", True)
ts = find(tasks_by(group="finance-group"), tid)
ok("已到财务打款节点", get(tid)["currentNode"] == "财务打款")
claim(tid, ts[0]["taskId"], "bob"); complete(tid, ts[0]["taskId"], "bob", True)
ok("财务打款后=COMPLETED", get(tid)["status"] == "COMPLETED")
r = call("POST", f"/tickets/{tid}/reject", params={"target": "audit1"})
ok("动态驳回到初审=成功", r.get("code") == 0)
ok("节点跳回初审", get(tid)["currentNode"] == "初审")
delete(tid)

# ============ 汇总 ============
print(f"\n{'='*50}")
print(f"测试结果: ✅ {PASS} 通过  ❌ {FAIL} 失败")
print(f"{'='*50}")
if FAIL == 0:
    print("🎉 全部通过！系统功能正常。")
else:
    print("⚠️  有失败项，请检查后端日志 backend.log")
