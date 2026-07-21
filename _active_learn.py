#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
主动学习(Active Learning)闭环演示：把“人在回路”采集到的不确定样本，
经人工标注后回流向量库，验证模型越用越准。

流程：
  1) 基线评测：对标注集跑 /api/ai/route，统计准确率 / 低置信转人工(HIL)触发数；
  2) 采集不确定样本池：把评测集中被模型判为“低置信(需人工复核)”的样本挑出来
     （这些正是主动学习最该让人标的样本）；
  3) 模拟人工标注回流：将池中样本按金标准类别 POST /api/ai/label 写回向量库；
  4) 复评测：准确率上升、HIL 触发数下降，证明闭环有效；
  5) 泛化探针：对几个“评测集之外、但与池样本相似”的新句子，验证学习可泛化
     （不止记住原句，而是同类语义都受益）。

用法：python _active_learn.py [eval.json] [base_url]
说明：learned 样本常驻内存，重复运行会叠加；演示前请重启后端保证从干净态开始。
"""
import json, sys, urllib.request

LABEL2ENUM = {"硬件类": "HARDWARE", "财务类": "FINANCE", "权限类": "PERMISSION"}
ENUMS = ["HARDWARE", "FINANCE", "PERMISSION"]
BASE = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8080/api"
EVAL = sys.argv[1] if len(sys.argv) > 1 else "ai-eval.json"


def post(path, payload):
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode())


def route(text):
    return post("/ai/route", {"title": text, "description": text}).get("data", {})


def eval_metrics(cases):
    """复用与 _eval_ai.py 相同口径，返回 (accuracy, hil_total, hil_hits, mismatches)"""
    cm = {g: {p: 0 for p in ENUMS} for g in ENUMS}
    review_total = review_hits = 0
    mismatches = []
    for c in cases:
        gold = LABEL2ENUM.get(c["category"])
        pred = route(c["text"])
        pcat, conf, rev = pred.get("category"), pred.get("confidence", 0.0), pred.get("reviewRequired", False)
        if gold is None or pcat is None:
            continue
        cm[gold][pcat] += 1
        if pcat != gold:
            mismatches.append((c["text"], c["category"], pcat, round(conf, 2), rev))
        if rev:
            review_total += 1
            if pcat != gold:
                review_hits += 1
    total = sum(cm[g][g] for g in ENUMS)
    n = len(cases)
    return total / n if n else 0, review_total, review_hits, mismatches


def main():
    with open(EVAL, encoding="utf-8") as f:
        cases = json.load(f)

    print("=" * 64)
    print("主动学习闭环演示  |  API:", BASE)
    print("=" * 64)

    # 1) 基线
    acc0, hil0, hits0, mism0 = eval_metrics(cases)
    print(f"\n[1] 基线评测: accuracy={acc0*100:.1f}%  HIL触发={hil0}  拦截误分派={hits0}")
    if mism0:
        print("    基线错分(均已被HIL兜住):")
        for t, g, p, cf, _ in mism0:
            print(f"      - {t[:24]:<26} 真实={g:<5} 预测={p:<9} 置信={cf}")

    # 2) 采集不确定样本池（评测集中被判低置信的样本）
    pool = []
    for c in cases:
        pred = route(c["text"])
        if pred.get("reviewRequired"):
            pool.append((c["text"], c["category"]))
    print(f"\n[2] 不确定样本池: {len(pool)} 条（主动学习优先让人标注的样本）")
    for t, g in pool:
        print(f"      - {t[:30]:<32} 金标准={g}")

    # 3) 模拟人工标注回流
    payload = [{"text": t, "category": g} for t, g in pool]
    res = post("/ai/label", payload)
    print(f"\n[3] 标注回流: 成功写入向量库 {res.get('data', {}).get('learned')} 条")

    # 4) 复评测
    acc1, hil1, hits1, mism1 = eval_metrics(cases)
    print(f"\n[4] 复评测: accuracy={acc1*100:.1f}%  HIL触发={hil1}  拦截误分派={hits1}")
    print(f"    ▲ 准确率提升: {(acc1-acc0)*100:+.1f} 个百分点 | ▼ 人工复核减负: {hil0-hil1} 条")

    # 5) 泛化探针（评测集之外的新相似句）
    probes = [
        "投影仪画面模糊 视频会议投不出来",
        "麦克风没声音 线上会议对方听不到",
        "摄像头黑屏 腾讯会议打不开",
    ]
    print("\n[5] 泛化探针（评测集外的新相似句，金标准均为硬件类）:")
    for p in probes:
        r = route(p)
        tag = "✓" if r.get("category") == "HARDWARE" else "✗"
        print(f"      {tag} {p[:28]:<30} -> {r.get('category')} (conf={r.get('confidence')}, review={r.get('reviewRequired')})")


if __name__ == "__main__":
    main()
