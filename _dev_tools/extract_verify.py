import zipfile, os
MAIN = r"D:\fenliu\ticket-web\target\ticket-web-1.0.0.jar"
OUT = r"D:\fenliu\_build\verify"
os.makedirs(OUT, exist_ok=True)
z = zipfile.ZipFile(MAIN)
# ticket-web 的 TicketService
z.extract("BOOT-INF/classes/com/ticket/web/service/TicketService.class", OUT)
# 两个嵌套模块 jar
for n in z.namelist():
    if n in ("BOOT-INF/lib/ticket-common-1.0.0.jar", "BOOT-INF/lib/ticket-agent-1.0.0.jar"):
        data = z.read(n)
        with open(os.path.join(OUT, os.path.basename(n)), "wb") as f:
            f.write(data)
z.close()
print("extracted to", OUT)
for root, _, files in os.walk(OUT):
    for f in files:
        print(os.path.join(root, f))
