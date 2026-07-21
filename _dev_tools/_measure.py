import json, urllib.request

url = "http://localhost:8080/api/ai/route"
cat_map = {"硬件类": "HARDWARE", "财务类": "FINANCE", "权限类": "PERMISSION"}

def call(text):
    payload = json.dumps({"title": text, "description": ""}).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"}, method="POST")
    r = json.load(urllib.request.urlopen(req, timeout=15))
    d = r.get("data", r)
    return d.get("category"), d.get("confidence"), d.get("reviewRequired")

# ① 独立评测集 54 条（运行语料不含这些文本）
ev = json.load(open(r"D:\fenliu\ai-eval.json", encoding="utf-8"))
c = rev = t = 0
pc = {"HARDWARE": [0, 0], "FINANCE": [0, 0], "PERMISSION": [0, 0]}
wrong = []
for e in ev:
    pred, conf, rv = call(e["text"]); exp = cat_map[e["category"]]; t += 1
    if pred == exp: c += 1
    else: wrong.append((e["text"], exp, pred, round(conf, 3)))
    if rv: rev += 1
    pc[exp][1] += 1
    if pred == exp: pc[exp][0] += 1
print(f"[独立评测54条] 正确 {c}/{t} = {c/t*100:.1f}%   触发HIL {rev}/{t}")
for k, v in pc.items():
    print(f"    {k}: {v[0]}/{v[1]}")
if wrong:
    print("    错误:", [(w[0], w[1], w[2], w[3]) for w in wrong][:12])

# ② 演示边界句（运行语料已覆盖，应高置信正确）
demo = [
    "投影仪打不开 会议要用了",
    "摄像头没画面 视频会议用不了",
    "麦克风没声音 对方听不到我说话",
    "笔记本无法开机 电源指示灯不亮 疑似电池故障",
    "差旅报销 发票抬头错误 需要重新开具",
    "申请开通生产数据库只读账号",
]
print("\n[演示边界句]")
for t0 in demo:
    pred, conf, rv = call(t0)
    print(f"    {t0} -> {pred} conf={conf} rv={rv}")

# ③ 库外泛化 10 条（同类别、措辞不在运行语料也不在评测集）
out = [
    ("台式机开机风扇狂转但不显示画面", "HARDWARE"),
    ("笔记本触摸板失灵 左键点不动", "HARDWARE"),
    ("会议室音响没声音 投屏也没画面", "HARDWARE"),
    ("我要报销打车费 发票弄丢了", "FINANCE"),
    ("供应商说没收到上个月的款项", "FINANCE"),
    ("月底要结一次广告投放费", "FINANCE"),
    ("新同事要接 git 仓库权限", "PERMISSION"),
    ("我想把文件夹共享给外包同学", "PERMISSION"),
    ("离职流程要关掉他的所有系统账号", "PERMISSION"),
    ("VPN连不上 提示证书过期", "PERMISSION"),
]
c2 = t2 = rv2 = 0
print("\n[库外泛化10条]")
for text, exp in out:
    pred, conf, rv = call(text); t2 += 1
    ok = pred == exp
    if ok: c2 += 1
    if rv: rv2 += 1
    print(f"    {text} -> {pred} conf={conf} rv={rv} {'OK' if ok else 'WRONG(exp '+exp+')'}")
print(f"  正确 {c2}/{t2} = {c2/t2*100:.1f}%   触发HIL {rv2}/{t2}")
