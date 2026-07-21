import zipfile, io, os

main = r"D:\fenliu\ticket-web\target\ticket-web-1.0.0.jar"
new_json = open(r"D:\fenliu\ticket-ai\src\main\resources\ai\historical-tickets.json", "rb").read()

zin = zipfile.ZipFile(main)
tmp = main + ".tmp"

with zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED) as zout:
    for it in zin.infolist():
        name = it.filename
        if name == "BOOT-INF/lib/ticket-ai-1.0.0.jar":
            inner_data = zin.read(name)
            iz = zipfile.ZipFile(io.BytesIO(inner_data))
            bio = io.BytesIO()
            with zipfile.ZipFile(bio, "w", zipfile.ZIP_DEFLATED) as izout:
                for iit in iz.infolist():
                    if iit.filename == "ai/historical-tickets.json":
                        izout.writestr(iit, new_json)
                    else:
                        izout.writestr(iit, iz.read(iit.filename))
            inner_bytes = bio.getvalue()
            zi = zipfile.ZipInfo(name)
            zi.date_time = it.date_time
            zout.writestr(zi, inner_bytes)
            print("REPLACED inner ai jar, new inner bytes =", len(inner_bytes))
        else:
            data = zin.read(name)
            zi = zipfile.ZipInfo(name)
            zi.date_time = it.date_time
            zout.writestr(zi, data)

os.replace(tmp, main)
print("REBUILT main jar (all STORED) OK")
print("NEW_MAIN_SIZE", os.path.getsize(main))
