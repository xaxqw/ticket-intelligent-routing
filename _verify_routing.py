#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证条件性路由：财务类走财务打款，硬件/权限类复审后直接归档。"""
import json, urllib.request, urllib.error

BASE = "http://localhost:8080/api"

def call(method, path, data=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={urllib.parse.quote(str(v))}" for k, v in params.items())
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"__error": e.code, "body": e.read().decode()}

import urllib.parse

def tasks_by(**kw):
    r = call("GET", "/tickets/tasks", params=kw)
    if isinstance(r, dict):
        return r.get("data", []) or []
    return r if isinstance(r, list) else []

def claim(tid, task_id, uid):
    return call("POST", f"/tickets/{tid}/claim", params={"taskId": task_id, "userId": uid})

def complete(tid, task_id, uid, ok_):
    return call("POST", f"/tickets/{tid}/complete", params={"taskId": task_id, "userId": uid, "approved": ok_})

GROUP_USER = {"hardware-group": "alice", "finance-group": "bob", "permission-group": "carol"}

def run_path(tid, label):
    """把工单一路通过到底，返回 ['财务打款' 是否出现, 最终状态]。"""
    seen_finance = False
    for step in range(10):
        cands = [t for t in tasks_by() if t["ticketId"] == tid]
        if not cands:
            break
        fin = [t for t in cands if t["nodeName"] == "财务打款"]
        if fin:
            seen_finance = True
            claim(tid, fin[0]["taskId"], "bob")
            complete(tid, fin[0]["taskId"], "bob", True)
            continue
        revs = [t for t in cands if t["nodeName"] == "复审"]
        if revs:
            for rev in ("reviewerA", "reviewerB"):
                tr = [t for t in tasks_by(assignee=rev) if t["ticketId"] == tid]
                if tr:
                    claim(tid, tr[0]["taskId"], rev)
                    complete(tid, tr[0]["taskId"], rev, True)
            continue
        # 初审 / 补充材料
        t = cands[0]
        grp = t.get("candidateGroup")
        uid = GROUP_USER.get(grp, "alice")
        claim(tid, t["taskId"], uid)
        complete(tid, t["taskId"], uid, True)
    status = call("GET", f"/tickets/{tid}")["data"].get("status")
    return seen_finance, status

def create(desc):
    r = call("POST", "/tickets", {"title": desc, "description": desc, "priority": 2, "slaHours": 24})
    if "__error" in r:
        print("  建单失败:", r); return None, None
    return r["data"]["id"], r["data"]["category"]

print("=" * 64)
print("条件性路由验证")
print("=" * 64)

cases = [
    ("财务类", "差旅报销打款 3200 元，请财务尽快处理"),
    ("硬件类", "笔记本无法开机，插电无反应，疑似电池损坏"),
    ("权限类", "申请开通生产数据库只读查询账号"),
]

results = []
for label, desc in cases:
    print(f"\n--- {label}工单: {desc} ---")
    tid, cat = create(desc)
    if not tid: continue
    print(f"  分类={cat}")
    seen_finance, status = run_path(tid, label)
    print(f"  是否经过财务打款: {seen_finance}  |  最终状态: {status}")
    results.append((label, cat, seen_finance, status))

print("\n" + "=" * 64)
print("结论")
print("=" * 64)
ok = True
for label, cat, seen_finance, status in results:
    expect_finance = (cat == "FINANCE")
    passed = (seen_finance == expect_finance) and (status == "COMPLETED")
    ok = ok and passed
    print(f"  {label:4s} | 分类={cat:4s} | 期望财务打款={expect_finance} 实际={seen_finance} | 状态={status} | {'✅' if passed else '❌'}")
print("\n总体:", "✅ 全部通过" if ok else "❌ 存在失败")
