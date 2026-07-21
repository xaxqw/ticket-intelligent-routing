#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
无 Maven 环境下准备“展开 classpath 运行目录”，用于直接 `java -cp` 启动，
绕开 Spring Boot fat jar 的嵌套 jar 加载问题（此前表现为 JPA Not a managed type）。

步骤：
1. 从 fat jar 抽取 BOOT-INF/lib/*.jar 到 _build/lib（编译/运行依赖）
2. 用 javac 重编译 ticket-common / ticket-agent / ticket-web，**必须带 -parameters**
   （否则 Controller 的 @RequestParam 参数名丢失，Spring MVC 报 500）
3. 重建 _run/classes：
   - 抽 fat jar 的 BOOT-INF/classes（web 模块类 + 资源如 application.yml）
   - 展开所有 ticket-*.jar 嵌套模块（common/agent/ai/workflow 的类与资源）
   - 用第 2 步重编译的产物（带 -parameters）覆盖对应类
4. 清理所有 *.bpmn：Flowable 直接使用 H2 文件库里已部署的流程定义，
   classpath 里留多份同 key 流程会触发 "same key not allowed" 部署失败。
"""
import zipfile, os, io, subprocess, shutil, sys

BASE = r"D:\fenliu"
MAIN = os.path.join(BASE, "ticket-web", "target", "ticket-web-1.0.0.jar")
LIB = os.path.join(BASE, "_build", "lib")
RUN = os.path.join(BASE, "_run", "classes")
JAVAC = os.path.join(BASE, "_runtime", "jdk8", "bin", "javac.exe")
LOMBOK = os.path.join(BASE, "_runtime", "lib", "lombok-1.18.30.jar")
MODS = [("common", os.path.join(BASE, "ticket-common")),
        ("agent", os.path.join(BASE, "ticket-agent")),
        ("web", os.path.join(BASE, "ticket-web"))]

# 1) 抽取依赖 lib
os.makedirs(LIB, exist_ok=True)
z = zipfile.ZipFile(MAIN)
for n in z.namelist():
    if n.startswith("BOOT-INF/lib/") and n.endswith(".jar"):
        with open(os.path.join(LIB, os.path.basename(n)), "wb") as f:
            f.write(z.read(n))
print("extracted lib jars:", len(os.listdir(LIB)))

# 2) 带 -parameters 重编译三模块
for m, base in MODS:
    src = os.path.join(base, "src", "main", "java")
    out = os.path.join(base, "target", "classes")
    os.makedirs(out, exist_ok=True)
    srcs = [os.path.join(r, f) for r, _, fs in os.walk(src) for f in fs if f.endswith(".java")]
    deps = [os.path.join(b, "target", "classes") for _, b in MODS
            if b != base and os.path.isdir(os.path.join(b, "target", "classes"))]
    cp = ";".join(deps + [LIB + r"\*", LOMBOK])
    cmd = [JAVAC, "-parameters", "-encoding", "UTF-8", "-cp", cp, "-d", out, "-sourcepath", src] + srcs
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write("COMPILE FAIL %s\n%s\n%s\n" % (m, r.stdout[-2000:], r.stderr[-2000:]))
        sys.exit(1)
    print("COMPILED %s : %d files (带 -parameters)" % (m, len(srcs)))

# 3) 重建 _run/classes
if os.path.isdir(RUN):
    shutil.rmtree(RUN)
os.makedirs(RUN)
# 3a) web 模块的 BOOT-INF/classes（类 + 资源）
for n in z.namelist():
    if n.startswith("BOOT-INF/classes/"):
        rel = n[len("BOOT-INF/classes/"):]
        if not rel or rel.endswith("/"):
            continue
        tp = os.path.join(RUN, *rel.split("/"))
        os.makedirs(os.path.dirname(tp), exist_ok=True)
        open(tp, "wb").write(z.read(n))
# 3b) 所有 ticket-* 嵌套 jar 展开（common/agent/ai/workflow 类 + 资源）
for n in z.namelist():
    if n.startswith("BOOT-INF/lib/") and "ticket-" in n and n.endswith(".jar"):
        with zipfile.ZipFile(io.BytesIO(z.read(n))) as nz:
            for nn in nz.namelist():
                if nn.endswith("/"):
                    continue
                tp = os.path.join(RUN, *nn.split("/"))
                os.makedirs(os.path.dirname(tp), exist_ok=True)
                open(tp, "wb").write(nz.read(nn))
z.close()
# 3c) 用重编译产物（带 -parameters）覆盖
for m, base in MODS:
    cd = os.path.join(base, "target", "classes")
    for r, _, fs in os.walk(cd):
        for f in fs:
            fp = os.path.join(r, f)
            arc = os.path.relpath(fp, cd)
            tp = os.path.join(RUN, *arc.split(os.sep))
            os.makedirs(os.path.dirname(tp), exist_ok=True)
            shutil.copy(fp, tp)

# 4) 清理 bpmn（Flowable 用 H2 已部署流程，避免同 key 冲突）
removed = 0
for r, _, fs in os.walk(RUN):
    for f in fs:
        if f.endswith(".bpmn") or f.endswith(".bpmn20.xml"):
            os.remove(os.path.join(r, f))
            removed += 1
print("removed bpmn files:", removed)
print("RUN ready ->", RUN)
