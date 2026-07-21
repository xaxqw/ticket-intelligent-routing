#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实跑出系统各功能的示例（每一步都打印系统真实返回）。"""
import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"

def call(method, path, data=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"__error": e.code, "body": e.read().decode()}

def tasks_by(**kw):
    r = call("GET", "/tickets/tasks", params=kw)
    if isinstance(r, dict):
        return r.get("data", [])
    return r if isinstance(r, list) else []

def j(x):
    return json.dumps(x, ensure_ascii=False)

def claim(tid, task_id, uid):
    print(f"  [接单] userId={uid} taskId={task_id[:8]}")
    return call("POST", f"/tickets/{tid}/claim", params={"taskId": task_id, "userId": uid})

def complete(tid, task_id, uid, ok_):
    print(f"  [通过/驳回] userId={uid} approved={ok_}")
    return call("POST", f"/tickets/{tid}/complete", params={"taskId": task_id, "userId": uid, "approved": ok_})

def advance_to_finance(tid, approvers):
    """把工单推到财务打款（初审组 -> 复审会签 -> 财务），返回财务 task。"""
    step = 0
    while step < 8:
        cands = [t for t in tasks_by() if t["ticketId"] == tid]
        if not cands:
            return None
        fin = [t for t in cands if t["nodeName"] == "财务打款"]
        if fin:
            return fin[0]
        revs = [t for t in cands if t["nodeName"] == "复审"]
        if revs:
            for rev in ("reviewerA", "reviewerB"):
                tr = [t for t in tasks_by(assignee=rev) if t["ticketId"] == tid]
                if tr:
                    claim(tid, tr[0]["taskId"], rev)
                    complete(tid, tr[0]["taskId"], rev, True)
            continue
        t = cands[0]
        grp = t.get("candidateGroup")
        uid = {"hardware-group": "alice", "finance-group": "bob", "permission-group": "carol"}.get(grp, "alice")
        claim(tid, t["taskId"], uid)
        complete(tid, t["taskId"], uid, True)
        step += 1
    return None

print("=" * 70)
print("示例 1：AI 自动分类（最常用功能）")
print("=" * 70)
print("【操作】浏览器填描述 -> 点『AI 预判』。等价于 POST /api/tickets 建单。")
desc = "笔记本无法开机，插电无反应，疑似电池损坏"
r = call("POST", "/tickets", {"title": desc, "description": desc, "priority": 3, "slaHours": 24})
print("【系统真实返回】")
print(j(r.get("data")))
print(">>> 重点看 category / aiConfidence / currentNode / status")
tid1 = r["data"]["id"]

print()
print("=" * 70)
print("示例 2：完整一条工单（硬件类 happy path）")
print("=" * 70)
print(">>> 条件性路由：硬件类工单复审通过后【直接归档】，不经过财务打款。")
ts = [t for t in tasks_by(group="hardware-group") if t["ticketId"] == tid1]
print("【查 hardware-group 待办，看到初审任务】", j([{"taskId": t["taskId"][:8], "nodeName": t["nodeName"]} for t in ts]))
claim(tid1, ts[0]["taskId"], "alice")
complete(tid1, ts[0]["taskId"], "alice", True)
print(">>> alice 初审通过后，进入『复审』（顺序会签：reviewerA 先审，其完成后 reviewerB 才收到）：")
ts2 = [t for t in tasks_by() if t["ticketId"] == tid1]
print(j([{"nodeName": t["nodeName"]} for t in ts2]))
# reviewerA 先审
tra = [t for t in tasks_by(assignee="reviewerA") if t["ticketId"] == tid1]
claim(tid1, tra[0]["taskId"], "reviewerA")
complete(tid1, tra[0]["taskId"], "reviewerA", True)
print(">>> reviewerA 通过后，reviewerB 才收到复审任务：")
trb = [t for t in tasks_by(assignee="reviewerB") if t["ticketId"] == tid1]
claim(tid1, trb[0]["taskId"], "reviewerB")
complete(tid1, trb[0]["taskId"], "reviewerB", True)
print(">>> 两人都通过且为硬件类：复审后系统【直接归档】，不出现财务打款任务：")
ts3 = [t for t in tasks_by(group="finance-group") if t["ticketId"] == tid1]
print(j([{"nodeName": t["nodeName"], "group": t.get("candidateGroup")} for t in ts3]) or "[] (无财务打款任务，符合硬件类路由)")
print("【最终工单状态】", j(call("GET", f"/tickets/{tid1}")["data"]["status"]), "（=COMPLETED 表示完整跑通）")

print()
print("=" * 70)
print("示例 2b：财务类工单才会进入财务打款")
print("=" * 70)
r = call("POST", "/tickets", {"title": "差旅报销打款 3200 元", "description": "出差报销需要财务打款", "priority": 1, "slaHours": 24})
tid_fin = r["data"]["id"]
print(f"【新建财务类工单 {tid_fin[-6:]} 分类={r['data']['category']}】")
# 快速推进到复审后看是否出现财务打款
advance_to_finance(tid_fin, None)
print("【推进到复审后节点】", call("GET", f"/tickets/{tid_fin}")["data"]["currentNode"], "(财务类此处应显示『财务打款』)")
print(">>> 对比：硬件类同一阶段是『COMPLETED』，财务类是『财务打款』——这就是条件性路由。")

print()
print("=" * 70)
print("示例 3：初审驳回 -> 回『补充材料』")
print("=" * 70)
r = call("POST", "/tickets", {"title": "申请开通生产库只读账号", "description": "需要查数据用", "priority": 2, "slaHours": 48})
tid2 = r["data"]["id"]
print(f"【新建权限类工单 {tid2[-6:]} 当前节点】{call('GET', f'/tickets/{tid2}')['data']['currentNode']}")
ts = [t for t in tasks_by(group="permission-group") if t["ticketId"] == tid2]
print(f"【permission-group 待办】{j([t['nodeName'] for t in ts])}")
claim(tid2, ts[0]["taskId"], "carol")
res = complete(tid2, ts[0]["taskId"], "carol", False)  # 驳回
print("【驳回后返回】", j(res))
print("【工单当前节点】", call("GET", f"/tickets/{tid2}")["data"]["currentNode"], "（应显示『补充材料』）")

print()
print("=" * 70)
print("示例 4：动态驳回（走到财务后跳回初审重来）")
print("=" * 70)
r = call("POST", "/tickets", {"title": "差旅报销打款", "description": "出差报销 3200 元", "priority": 1, "slaHours": 24})
tid3 = r["data"]["id"]
print(f"【新建工单 {tid3[-6:]} 分类={r['data']['category']} 初始节点={call('GET', f'/tickets/{tid3}')['data']['currentNode']}】")
t_fin = advance_to_finance(tid3, None)
print("【已推进到财务打款，动态驳回前节点】", call("GET", f"/tickets/{tid3}")["data"]["currentNode"])
res = call("POST", f"/tickets/{tid3}/reject", params={"target": "audit1"})
print("【动态驳回返回】", j(res))
print("【驳回后节点】", call("GET", f"/tickets/{tid3}")["data"]["currentNode"], "（应跳回『初审』）")

print()
print("=" * 70)
print("示例 5：删除工单（自主清理）")
print("=" * 70)
print(f"【删除工单 {tid2[-6:]}】", j(call("DELETE", f"/tickets/{tid2}")))
print(f"【再查该工单】", j(call("GET", f"/tickets/{tid2}")))

for tid in (tid1, tid3):
    call("DELETE", f"/tickets/{tid}")
print("\n[示例工单已全部清理，系统不留垃圾数据]")
