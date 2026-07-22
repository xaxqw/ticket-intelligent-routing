import pythoncom
from win32com.shell import shell

lnk_path = r"C:\Users\xuanx\OneDrive\桌面\工单智能分流系统.lnk"
cmd = r"C:\Windows\System32\cmd.exe"
args = r'/c "D:\fenliu\start-ticket-system.bat"'
work_dir = r"D:\fenliu"
icon = r"D:\fenliu\_runtime\jdk8\bin\java.exe"

sl = pythoncom.CoCreateInstance(
    shell.CLSID_ShellLink, None,
    pythoncom.CLSCTX_INPROC_SERVER, shell.IID_IShellLink)
sl.SetPath(cmd)
sl.SetArguments(args)
sl.SetWorkingDirectory(work_dir)
sl.SetDescription("企业级工单智能分流与协同处理系统 - 一键启动")
sl.SetIconLocation(icon, 0)

sl.QueryInterface(pythoncom.IID_IPersistFile).Save(lnk_path, 0)
print("REBUILT ->", lnk_path)

# verify read-back
sl2 = pythoncom.CoCreateInstance(shell.CLSID_ShellLink, None,
    pythoncom.CLSCTX_INPROC_SERVER, shell.IID_IShellLink)
sl2.QueryInterface(pythoncom.IID_IPersistFile).Load(lnk_path, 0)
buf = pythoncom.CreateBuffer(600)
n = sl2.GetPath(buf, 0)
print("TARGET :", buf[:n].decode("utf-16-le", "ignore") if isinstance(buf, (bytes, bytearray)) else buf)
print("ARGS   :", sl2.GetArguments(600, 0))
print("WORKDIR:", sl2.GetWorkingDirectory(600, 0))
print("ICON   :", sl2.GetIconLocation(600))
