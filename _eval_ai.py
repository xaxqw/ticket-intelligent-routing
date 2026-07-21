#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI 分派评测脚本（CI 门禁 / 简历证据）。

对标注评测集逐条调用 POST /api/ai/route，统计：
  - 整体准确率 accuracy
  - 每类 precision / recall / F1
  - 混淆矩阵
  - 置信度分布与“低置信转人工”命中情况

用法：python _eval_ai.py [eval.json] [base_url]
"""
import json, sys, urllib.request

LABEL2ENUM = {"硬件类": "HARDWARE", "财务类": "FINANCE", "权限类": "PERMISSION"}
ENUMS = ["HARDWARE", "FINANCE", "PERMISSION"]
BASE = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8080/api"
EVAL = sys.argv[1] if len(sys.argv) > 1 else "ai-eval.json"


def route(text):
    body = json.dumps({"title": text, "description": text}).encode()
    req = urllib.request.Request(BASE + "/ai/route", data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode()).get("data", {})


def main():
    with open(EVAL, encoding="utf-8") as f:
        cases = json.load(f)
    print(f"评测集: {EVAL}  样本数: {len(cases)}  API: {BASE}/ai/route\n")

    cm = {g: {p: 0 for p in ENUMS} for g in ENUMS}   # 混淆矩阵: cm[真实][预测]
    conf_correct, conf_wrong = [], []
    review_total = review_hits = 0
    mismatches = []
    skipped = 0

    for i, c in enumerate(cases, 1):
        gold = LABEL2ENUM.get(c["category"])
        pred = route(c["text"])
        pcat, conf, rev = pred.get("category"), pred.get("confidence", 0.0), pred.get("reviewRequired", False)
        if gold is None or pcat is None:
            skipped += 1
            continue
        cm[gold][pcat] += 1
        if pcat == gold:
            conf_correct.append(conf)
        else:
            conf_wrong.append(conf)
            mismatches.append((c["text"][:24], c["category"], pcat, round(conf, 2), rev))
        if rev:
            review_total += 1
            if pcat != gold:
                review_hits += 1

    total = sum(cm[g][g] for g in ENUMS)
    n = len(cases) - skipped
    acc = total / n if n else 0

    print("=" * 64)
    print(f"整体准确率 accuracy = {acc*100:.1f}%   (正确 {total}/{n})")
    print("=" * 64)
    print(f"{'类别':<10}{'P':>8}{'R':>8}{'F1':>8}{'支持':>8}")
    mp = mr = mf1 = 0
    for c in ENUMS:
        sup = sum(cm[c].values())
        tp = cm[c][c]
        fp = sum(cm[g][c] for g in ENUMS if g != c)
        fn = sup - tp
        p = tp / (tp + fp) if (tp + fp) else 0
        r = tp / (tp + fn) if (tp + fn) else 0
        f1 = 2 * p * r / (p + r) if (p + r) else 0
        mp += p; mr += r; mf1 += f1
        print(f"{c:<10}{p*100:>7.1f}{r*100:>8.1f}{f1*100:>8.1f}{sup:>8}")
    print("-" * 64)
    print(f"{'宏平均':<10}{mp/3*100:>7.1f}{mr/3*100:>8.1f}{mf1/3*100:>8.1f}")

    print("\n混淆矩阵（行=真实 / 列=预测）:")
    print(f"{'':<10}" + "".join(f"{c[:4]:>8}" for c in ENUMS))
    for g in ENUMS:
        print(f"{g:<10}" + "".join(f"{cm[g][p]:>8}" for p in ENUMS))

    print(f"\n置信度: 正确样本均值 {_mean(conf_correct):.2f} | 错误样本均值 {_mean(conf_wrong):.2f}")
    print(f"低置信转人工: {review_total} 条触发, 其中拦下潜在误分派 {review_hits} 条")
    if mismatches:
        print("\n错分样本（人工复核重点关注）:")
        for t, g, p, cf, rev in mismatches:
            print(f"  - {t:<26} 真实={g:<5} 预测={p:<9} 置信={cf} {'[已转人工]' if rev else ''}")


def _mean(xs):
    return sum(xs) / len(xs) if xs else 0.0


if __name__ == "__main__":
    main()
