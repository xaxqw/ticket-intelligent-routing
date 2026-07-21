"""Copy all external runtime dependencies into D:\fenliu\_runtime so the project is self-contained."""
import shutil, os

BASE = r"D:\fenliu"
RT = os.path.join(BASE, "_runtime")
os.makedirs(RT, exist_ok=True)

copies = [
    (r"C:\Program Files\Java\jdk1.8.0_141",
     os.path.join(RT, "jdk8"),
     "JDK"),
    (r"C:\Program Files\Redis",
     os.path.join(RT, "redis"),
     "Redis"),
    (r"C:\Users\xuanx\.workbuddy\binaries\node\versions\22.22.2",
     os.path.join(RT, "node"),
     "Node"),
]

for s, d, label in copies:
    if not os.path.isdir(s):
        print("SKIP (not found):", s)
        continue
    if os.path.isdir(d):
        shutil.rmtree(d)
    print("COPYING %s %s -> %s ..." % (label, s, d))
    shutil.copytree(s, d)
    print("  done:", label)

# Lombok jar
libdir = os.path.join(RT, "lib")
os.makedirs(libdir, exist_ok=True)
lombok_src = r"C:\Users\xuanx\.m2\repository\org\projectlombok\lombok\1.18.30\lombok-1.18.30.jar"
lombok_dst = os.path.join(libdir, "lombok-1.18.30.jar")
if os.path.isfile(lombok_src):
    shutil.copy(lombok_src, lombok_dst)
    print("Lombok copied ->", lombok_dst)
else:
    print("Lombok NOT FOUND:", lombok_src)

# Portable copy of the desktop shortcut (entry point backup)
lnk_src = r"C:\Users\xuanx\OneDrive\桌面\工单智能分流系统.lnk"
lnk_dst = os.path.join(BASE, "工单智能分流系统.lnk")
if os.path.isfile(lnk_src):
    shutil.copy(lnk_src, lnk_dst)
    print("Shortcut backup copied ->", lnk_dst)

print("\nALL COPIES DONE")
