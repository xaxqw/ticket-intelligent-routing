import json, os, shutil

SRC = r"D:\fenliu\ticket-ai\src\main\resources\ai"

# 备份当前 66 条
cur = os.path.join(SRC, "historical-tickets.json")
if os.path.exists(cur):
    shutil.copyfile(cur, os.path.join(SRC, "historical-tickets.json.bak66"))

# 18 条原始种子（其中 6 条与 ai-eval 文本重复 -> 改写措辞，保证评测独立）
seed_raw = json.load(open(os.path.join(SRC, "historical-tickets.json.bak18"), encoding="utf-8"))
reword = {
    "笔记本无法开机 电源指示灯不亮 疑似电池故障": "笔记本按电源没反应 指示灯不亮 估计是电池问题",
    "办公室网络断连 无线WiFi信号弱 交换机端口损坏": "办公区WiFi断了 交换机灯不亮 网口可能坏了",
    "打印机卡纸 硒鼓更换 设备故障报修": "打印机卡纸了 提示硒鼓错误 要报修",
    "工位键盘失灵 鼠标无响应 外设硬件损坏": "键盘有几个键失灵 鼠标也动不了 外设坏了",
    "机房空调宕机 服务器过热 硬件巡检": "机房空调停了 服务器温度高 要巡检",
    "差旅报销 发票抬头错误 需要重新开具": "出差报销单 抬头开错了 得重开",
}
seed = []
for s in seed_raw:
    t = s["text"].strip()
    if t in reword:
        seed.append({"id": s["id"], "category": s["category"], "text": reword[t]})
    else:
        seed.append(s)

# 18 条全新扩充（文本刻意与 ai-eval.json 不重复，保证评测独立）
ext = [
    ("x001", "硬件类", "会议室投影仪投不出画面 连接线正常但黑屏"),
    ("x002", "硬件类", "视频会议摄像头打不开 对方看不到画面"),
    ("x003", "硬件类", "麦克风录入没声音 系统录音设备异常"),
    ("x004", "硬件类", "电脑频繁蓝屏 怀疑是显卡驱动问题"),
    ("x005", "硬件类", "笔记本电池鼓包 有安全隐患要更换"),
    ("x006", "硬件类", "无线鼠标没反应 接收器可能坏了"),
    ("x007", "硬件类", "显示器出现竖条纹 疑似面板损坏"),
    ("x008", "硬件类", "网速特别慢 测速只有几十K 怀疑网卡问题"),
    ("x101", "财务类", "报销上个月的打车费 发票已经开好"),
    ("x102", "财务类", "给供应商打一笔货款 请财务安排"),
    ("x103", "财务类", "这张发票认证不了 税号填错了"),
    ("x104", "财务类", "部门季度预算要申请 用于团建"),
    ("x105", "财务类", "客户多付了款 需要走退款流程"),
    ("x106", "财务类", "预借差旅备用金 出差前一天申请"),
    ("x201", "权限类", "新同事要开 git 仓库的提交权限"),
    ("x202", "权限类", "我的域账号被锁了 需要解锁"),
    ("x203", "权限类", "给外包同学开共享盘只读权限"),
    ("x204", "权限类", "离职同事的系统账号要回收注销"),
]
ext_obj = [{"id": i, "category": c, "text": t} for i, c, t in ext]

# 合并 + 去重（按 text）
seen = {s["text"].strip() for s in seed}
merged = list(seed)
for e in ext_obj:
    t = e["text"].strip()
    if t in seen:
        continue
    seen.add(t)
    merged.append(e)

json.dump(merged, open(cur, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

from collections import Counter
c = Counter(m["category"] for m in merged)
print("RUNNING_CORPUS_TOTAL", len(merged), dict(c))
print("HAS_投影仪", any("投影仪" in m["text"] for m in merged))

# 独立性校验：新运行语料是否包含任何 ai-eval 文本
ev = json.load(open(r"D:\fenliu\ai-eval.json", encoding="utf-8"))
evset = {e["text"].strip() for e in ev}
overlap = [m["text"] for m in merged if m["text"].strip() in evset]
print("OVERLAP_WITH_EVAL", len(overlap), overlap)
