# -*- coding: utf-8 -*-
"""工单系统全流程一致性自动化测试。用法: python _selftest.py"""
import json, urllib.request, urllib.error, time, threading, uuid, sys

BASE = "http://localhost:8080"
results = []  # (scenario, check, detail, ok)

def req(method, path, params=None, body=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {"raw": e.reason}

def create(title, category=None, key=None):
    body = {"title": title, "description": "test", "idempotencyKey": key or ("k-"+uuid.uuid4().hex[:8]), "priority": 3}
    if category:
        body["category"] = category
    st, j = req("POST", "/api/tickets", body=body)
    assert st == 200, f"create failed {st} {j}"
    return j["data"]

def get(tid):
    st, j = req("GET", f"/api/tickets/{tid}")
    return j["data"]

def all_tasks():
    st, j = req("GET", "/api/tickets/tasks")
    return j["data"]

def tasks_for(tid):
    return [t for t in all_tasks() if t.get("ticketId") == tid]

def tasks_assignee(who):
    st, j = req("GET", "/api/tickets/tasks", params={"assignee": who})
    return j.get("data", []) if isinstance(j, dict) else j

def review_pass_seq(tid):
    """顺序复审: 先 reviewerA 通过, 其完成后 reviewerB 任务才出现, 再通过。"""
    for who in ("reviewerA", "reviewerB"):
        tk = None
        for _ in range(20):
            ts = [t for t in tasks_assignee(who) if t["ticketId"] == tid and t["nodeName"] == "复审"]
            if ts:
                tk = ts[0]; break
            time.sleep(0.4)
        if not tk:
            continue
        claim(tid, tk["taskId"], who)
        complete(tid, tk["taskId"], who, True)

def claim(tid, task_id, user):
    return req("POST", f"/api/tickets/{tid}/claim", params={"taskId": task_id, "userId": user})

def complete(tid, task_id, user, approved):
    return req("POST", f"/api/tickets/{tid}/complete", params={"taskId": task_id, "userId": user, "approved": str(approved).lower()})

def suspend(tid):
    return req("POST", f"/api/tickets/{tid}/suspend")

def resume(tid):
    return req("POST", f"/api/tickets/{tid}/resume")

def reject_dynamic(tid, target):
    return req("POST", f"/api/tickets/{tid}/reject", params={"target": target})

def delete(tid):
    return req("DELETE", f"/api/tickets/{tid}")

def check(scenario, name, cond, detail=""):
    results.append((scenario, name, detail, bool(cond)))
    mark = "PASS" if cond else "FAIL"
    print(f"  [{mark}] {scenario} / {name} {detail}")

def node_set(tid):
    """集合中活跃任务节点名"""
    return set(t["nodeName"] for t in tasks_for(tid))

# ============ 场景1: 各类工单 happy path ============
def scenario_happy(title, category, label):
    s = f"happy-{label}"
    t = create(title, category=category, key="happy-"+uuid.uuid4().hex[:6])
    tid = t["id"]
    check(s, "初始 PROCESSING", t["status"] == "PROCESSING", f"status={t['status']}")
    check(s, "初始节点含初审", "初审" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
    # 初审通过
    ts = tasks_for(tid)
    audit1 = [x for x in ts if x["nodeName"] == "初审"]
    check(s, "初审任务存在", len(audit1) == 1, f"count={len(audit1)}")
    claim(tid, audit1[0]["taskId"], "zhang")
    complete(tid, audit1[0]["taskId"], "zhang", True)
    t = get(tid)
    check(s, "初审后进入复审", "复审" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
    cnset = node_set(tid)
    # 顺序复审: 同时只存在 1 个复审任务(reviewerA), reviewerB 此时不应出现
    check(s, "复审顺序: 同时仅1个复审任务", len([t for t in tasks_for(tid) if t["nodeName"] == "复审"]) == 1, f"nodes={cnset}")
    check(s, "复审顺序: reviewerB 暂未出现", len([t for t in tasks_assignee('reviewerB') if t['ticketId'] == tid]) == 0, "rb=0")
    review_pass_seq(tid)
    t = get(tid)
    if label == "FINANCE":
        # 财务类：复审通过后才进入财务打款
        check(s, "复审后进入财务打款", "财务打款" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
        pay = [x for x in tasks_for(tid) if x["nodeName"] == "财务打款"]
        check(s, "财务打款任务存在", len(pay) == 1)
        claim(tid, pay[0]["taskId"], "fin1")
        complete(tid, pay[0]["taskId"], "fin1", True)
    else:
        # 硬件类/权限类：条件性路由，复审通过后直接归档（无财务打款）
        check(s, "复审后直接 COMPLETED(跳过财务打款)", t["status"] == "COMPLETED", f"status={t['status']} node={t.get('currentNode')}")
        pay = [x for x in tasks_for(tid) if x["nodeName"] == "财务打款"]
        check(s, "财务打款任务不应存在", len(pay) == 0, f"count={len(pay)}")
    t = get(tid)
    check(s, "最终 COMPLETED", t["status"] == "COMPLETED", f"status={t['status']}")
    check(s, "完成后无活跃任务", len(tasks_for(tid)) == 0, f"left={len(tasks_for(tid))}")
    check(s, "完成后 currentNode 空", not t.get("currentNode"), f"node={t.get('currentNode')}")
    return tid  # 留给动态驳回测试

# ============ 场景2: 初审驳回 ============
def scenario_reject_audit1():
    s = "reject-初审"
    t = create("差旅报销申请", category="财务类", key="r1-"+uuid.uuid4().hex[:6])
    tid = t["id"]
    audit1 = [x for x in tasks_for(tid) if x["nodeName"] == "初审"][0]
    complete(tid, audit1["taskId"], "zhang", False)
    t = get(tid)
    check(s, "初审驳回后进入补充材料", "补充材料" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
    revise = [x for x in tasks_for(tid) if x["nodeName"] == "补充材料"]
    check(s, "补充材料任务存在", len(revise) == 1, f"count={len(revise)}")
    complete(tid, revise[0]["taskId"], "zhang", True)
    t = get(tid)
    check(s, "补充材料后回到初审", "初审" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
    delete(tid)

# ============ 场景3+4: 复审驳回 + 重批(无限循环检测) ============
def scenario_reject_review():
    s = "reject-复审"
    t = create("差旅报销申请", category="财务类", key="r2-"+uuid.uuid4().hex[:6])
    tid = t["id"]
    # 初审通过
    audit1 = [x for x in tasks_for(tid) if x["nodeName"] == "初审"][0]
    complete(tid, audit1["taskId"], "zhang", True)
    # 顺序复审: reviewerA 通过, reviewerB 驳回
    ra = [t for t in tasks_assignee("reviewerA") if t["ticketId"] == tid and t["nodeName"] == "复审"][0]
    complete(tid, ra["taskId"], "ra", True)
    rb = [t for t in tasks_assignee("reviewerB") if t["ticketId"] == tid and t["nodeName"] == "复审"][0]
    complete(tid, rb["taskId"], "rb", False)
    t = get(tid)
    check(s, "复审一人驳回 -> 补充材料", "补充材料" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
    # 补充材料 -> 初审 -> 复审(重批)
    revise = [x for x in tasks_for(tid) if x["nodeName"] == "补充材料"][0]
    complete(tid, revise["taskId"], "zhang", True)
    audit1b = [x for x in tasks_for(tid) if x["nodeName"] == "初审"][0]
    complete(tid, audit1b["taskId"], "zhang", True)
    # 二次复审: 顺序两人都通过
    review_pass_seq(tid)
    t = get(tid)
    check(s, "二次复审全通过 -> 财务打款(非死循环)", "财务打款" in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
    delete(tid)

# ============ 场景5: 动态驳回从 COMPLETED 回退（覆盖三个目标节点） ============
def make_completed(title):
    """快速建单并走完，返回已 COMPLETED 的工单 id（无断言，仅供重开测试复用）"""
    t = create(title, category="财务类", key="mc-"+uuid.uuid4().hex[:6])
    tid = t["id"]
    a1 = [x for x in tasks_for(tid) if x["nodeName"] == "初审"][0]
    complete(tid, a1["taskId"], "zhang", True)
    review_pass_seq(tid)
    pay = [x for x in tasks_for(tid) if x["nodeName"] == "财务打款"][0]
    complete(tid, pay["taskId"], "fin1", True)
    return tid

def scenario_dynamic_reject(seed_tid):
    for target, expect in [("audit1", "初审"), ("revise", "补充材料"), ("pay", "财务打款")]:
        s = f"dynamic-reject-{target}"
        tid = seed_tid if target == "audit1" else make_completed("重开-"+target)
        t0 = get(tid)
        check(s, "前置: 工单已完成", t0["status"] == "COMPLETED", f"status={t0['status']}")
        st, j = reject_dynamic(tid, target)
        check(s, "动态驳回接口成功", st == 200, f"st={st} j={j}")
        t = get(tid)
        check(s, "动态驳回后状态回退 PROCESSING", t["status"] == "PROCESSING", f"status={t['status']}")
        check(s, f"动态驳回后节点含{expect}", expect in (t.get("currentNode") or ""), f"node={t.get('currentNode')}")
        nt = [x for x in tasks_for(tid) if x["nodeName"] == expect]
        check(s, f"动态驳回后生成{expect}任务", len(nt) >= 1, f"count={len(nt)}")
        if target != "audit1":
            delete(tid)

# ============ 场景6: 并发接单 (Redis 锁) ============
def scenario_concurrent_claim():
    s = "concurrent-claim"
    t = create("并发测试工单", category="硬件类", key="cc-"+uuid.uuid4().hex[:6])
    tid = t["id"]
    audit1 = [x for x in tasks_for(tid) if x["nodeName"] == "初审"][0]
    tid_task = audit1["taskId"]
    resp = {}
    def worker(user):
        st, j = claim(tid, tid_task, user)
        resp[user] = (st, j)
    ths = [threading.Thread(target=worker, args=(u,)) for u in ("u1", "u2")]
    for th in ths: th.start()
    for th in ths: th.join()
    ok_count = sum(1 for st, _ in resp.values() if st == 200)
    check(s, "并发接单仅一人成功(锁生效)", ok_count == 1, f"ok={ok_count} resp={resp}")
    t = get(tid)
    check(s, "工单只有一个处理人", t.get("assignee") in ("u1", "u2"), f"assignee={t.get('assignee')}")
    delete(tid)

# ============ 场景7: currentNode 与真实任务一致性 ============
def scenario_consistency():
    s = "consistency"
    t = create("一致性测试", category="硬件类", key="cs-"+uuid.uuid4().hex[:6])
    tid = t["id"]
    t = get(tid)
    api_nodes = set((t.get("currentNode") or "").split(",")) - {""}
    real_nodes = node_set(tid)
    check(s, "currentNode 与活跃任务一致(初审)", api_nodes == real_nodes, f"api={api_nodes} real={real_nodes}")
    # 走一步再看
    audit1 = [x for x in tasks_for(tid) if x["nodeName"] == "初审"][0]
    complete(tid, audit1["taskId"], "zhang", True)
    t = get(tid)
    api_nodes = set((t.get("currentNode") or "").split(",")) - {""}
    real_nodes = node_set(tid)
    check(s, "currentNode 与活跃任务一致(复审)", api_nodes == real_nodes, f"api={api_nodes} real={real_nodes}")
    delete(tid)

# ============ 场景8: 跨工单误完成应被拒绝 ============
def scenario_ownership():
    s = "ownership"
    t1 = create("工单A-硬件", category="硬件类", key="own1-"+uuid.uuid4().hex[:6])
    t2 = create("工单B-财务", category="财务类", key="own2-"+uuid.uuid4().hex[:6])
    a1 = [x for x in tasks_for(t1["id"]) if x["nodeName"] == "初审"][0]
    a2 = [x for x in tasks_for(t2["id"]) if x["nodeName"] == "初审"][0]
    # 用 t1 的 taskId 去完成 t2 的工单
    st, j = complete(t2["id"], a1["taskId"], "x", True)
    rejected = (st != 200) or (isinstance(j, dict) and j.get("code", 0) != 0)
    check(s, "跨工单完成任务应被拒绝", rejected, f"st={st} j={j}")
    # t2 自身流程不受影响
    t2now = get(t2["id"])
    check(s, "被攻击工单状态未变", t2now["status"] == "PROCESSING", f"status={t2now['status']}")
    delete(t1["id"]); delete(t2["id"])

if __name__ == "__main__":
    print("===== 工单系统自测开始 =====")
    completed = scenario_happy("笔记本无法开机", "硬件类", "HARDWARE")
    scenario_happy("差旅报销申请", "财务类", "FINANCE")
    scenario_happy("申请VPN账号", "权限类", "PERMISSION")
    scenario_reject_audit1()
    scenario_reject_review()
    scenario_dynamic_reject(completed)
    scenario_concurrent_claim()
    scenario_consistency()
    scenario_ownership()
    delete(completed)
    print("\n===== 汇总 =====")
    fails = [r for r in results if not r[3]]
    for r in results:
        print(f"  [{'PASS' if r[3] else 'FAIL'}] {r[0]} / {r[1]} {r[2]}")
    print(f"\n总计 {len(results)} 项, 失败 {len(fails)} 项")
    if fails:
        print("失败项:")
        for r in fails:
            print(f"  - {r[0]} / {r[1]}: {r[2]}")
        sys.exit(1)
    print("ALL PASS")
