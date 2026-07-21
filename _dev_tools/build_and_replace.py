#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
无 Maven 环境下：用 javac 重新编译被改动的模块（ticket-common / ticket-agent / ticket-web），
并把编译产物写回 Spring Boot 可执行 fat jar（嵌套 jar 必须 STORED）。
依赖 jar 直接从当前 fat jar 的 BOOT-INF/lib 抽取，Lombok 取自本地 .m2。
"""
import zipfile, io, os, subprocess, sys

BASE = r"D:\fenliu"
MAIN = r"D:\fenliu\ticket-web\target\ticket-web-1.0.0.jar"
BUILD = r"D:\fenliu\_build"
LIB = os.path.join(BUILD, "lib")
MODULES = {
    "ticket-common": r"D:\fenliu\ticket-common",
    "ticket-agent":  r"D:\fenliu\ticket-agent",
    "ticket-web":    r"D:\fenliu\ticket-web",
}
LOMBOK = os.path.join(BASE, "_runtime", "lib", "lombok-1.18.30.jar")
JAVAC = os.path.join(BASE, "_runtime", "jdk8", "bin", "javac.exe")

# 1) 抽取 BOOT-INF/lib/*.jar 作为编译 classpath
os.makedirs(LIB, exist_ok=True)
z = zipfile.ZipFile(MAIN)
for n in z.namelist():
    if n.startswith("BOOT-INF/lib/") and n.endswith(".jar"):
        with open(os.path.join(LIB, os.path.basename(n)), "wb") as f:
            f.write(z.read(n))
z.close()
print("extracted lib jars:", len(os.listdir(LIB)))

lib_jars = [os.path.join(LIB, f) for f in os.listdir(LIB) if f.endswith(".jar")]
mod_classes = [os.path.join(MODULES[m], "target", "classes") for m in MODULES]
# 模块编译产物必须排在 lib 之前，否则会解析到 BOOT-INF/lib 里旧的嵌套 jar
cp = ";".join(mod_classes + lib_jars + [LOMBOK])

# 2) 依次编译三个模块（common -> agent -> web，满足依赖顺序）
for m, base in MODULES.items():
    src = os.path.join(base, "src", "main", "java")
    out = os.path.join(base, "target", "classes")
    os.makedirs(out, exist_ok=True)
    srcs = []
    for root, _, files in os.walk(src):
        for f in files:
            if f.endswith(".java"):
                srcs.append(os.path.join(root, f))
    cmd = [JAVAC, "-encoding", "UTF-8", "-cp", cp, "-d", out, "-sourcepath", src] + srcs
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write("COMPILE FAIL: %s\n%s\n%s\n" % (m, r.stdout[-3000:], r.stderr[-3000:]))
        sys.exit(1)
    print("COMPILED %s : %d files" % (m, len(srcs)))

# 3) 重建嵌套模块 jar（common / agent），从各自 target/classes（+ src/main/resources 若有）
def build_nested(module):
    base = MODULES[module]
    out = os.path.join(base, "target", "classes")
    res = os.path.join(base, "src", "main", "resources")
    bio = io.BytesIO()
    with zipfile.ZipFile(bio, "w", zipfile.ZIP_DEFLATED) as nz:
        for root, _, files in os.walk(out):
            for f in files:
                fp = os.path.join(root, f)
                arc = os.path.relpath(fp, out).replace("\\", "/")
                nz.write(fp, arc)
        if os.path.isdir(res):
            for root, _, files in os.walk(res):
                for f in files:
                    fp = os.path.join(root, f)
                    arc = os.path.relpath(fp, res).replace("\\", "/")
                    nz.write(fp, arc)
    return bio.getvalue()

web_classes = os.path.join(MODULES["ticket-web"], "target", "classes")
nested = {
    "BOOT-INF/lib/ticket-common-1.0.0.jar": build_nested("ticket-common"),
    "BOOT-INF/lib/ticket-agent-1.0.0.jar": build_nested("ticket-agent"),
}

# 4) 重写主 jar：嵌套 jar STORED；BOOT-INF/classes 优先用新编译产物，缺失则保留原字节
tmp = MAIN + ".tmp"
zin = zipfile.ZipFile(MAIN)
written = set()
with zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED) as zout:
    def add(name, data, date_time):
        if name in written:
            return
        written.add(name)
        zi = zipfile.ZipInfo(name); zi.date_time = date_time
        zout.writestr(zi, data)
    for it in zin.infolist():
        name = it.filename
        if name in nested:
            add(name, nested[name], it.date_time); continue
        if name.startswith("BOOT-INF/classes/"):
            if name == "BOOT-INF/classes/" or name.endswith("/"):
                add(name, zin.read(name), it.date_time); continue
            rel = name[len("BOOT-INF/classes/"):].replace("/", "\\")
            fp = os.path.join(web_classes, rel)
            if os.path.isfile(fp):
                with open(fp, "rb") as fh:
                    add(name, fh.read(), it.date_time)
            else:
                add(name, zin.read(name), it.date_time)
            continue
        add(name, zin.read(name), it.date_time)
    # 5) 把 target/classes 中“原 jar 没有”的新文件补进 jar（如新增的 notification 包）
    for root, _, files in os.walk(web_classes):
        for f in files:
            fp = os.path.join(root, f)
            arc = "BOOT-INF/classes/" + os.path.relpath(fp, web_classes).replace("\\", "/")
            if arc not in written:
                with open(fp, "rb") as fh:
                    add(arc, fh.read(), (1980, 1, 1, 0, 0, 0))
    zin.close()
os.replace(tmp, MAIN)
print("MAIN JAR REWRITTEN ->", os.path.getsize(MAIN), "bytes")
