# 用本项目专用的 Python 解释器（含 pywin32）创建桌面 .lnk 快捷方式。
# 运行: D:\RAG\rag_proj_env\Scripts\python.exe create-desktop-shortcut.py
import os
import pythoncom
from win32com.shell import shell, shellcon

DESKTOP = r"C:\Users\xuanx\OneDrive\桌面"
PROJECT = r"D:\fenliu"
TARGET = os.path.join(PROJECT, "start-ticket-system.bat")
LINK = os.path.join(DESKTOP, "工单智能分流系统.lnk")
ICON = os.path.join(PROJECT, "_runtime", "jdk8", "bin", "java.exe")  # Java 图标，代表这是 Java 系统

# 若桌面目录被重定向到 OneDrive，但旧路径也存在，优先 OneDrive 真实桌面
alt = r"C:\Users\xuanx\Desktop"
if not os.path.isdir(DESKTOP) and os.path.isdir(alt):
    DESKTOP = alt
    LINK = os.path.join(DESKTOP, "工单智能分流系统.lnk")

# 直接用 cmd.exe /c 启动 .bat，避免依赖 .bat 文件关联（被改坏时双击会"没反应"）
CMD = r"C:\Windows\System32\cmd.exe"

shortcut = pythoncom.CoCreateInstance(
    shell.CLSID_ShellLink, None,
    pythoncom.CLSCTX_INPROC_SERVER, shell.IID_IShellLink)
shortcut.SetPath(CMD)
shortcut.SetArguments('/c "{}"'.format(TARGET))
shortcut.SetWorkingDirectory(PROJECT)
shortcut.SetDescription("企业级工单智能分流与协同处理系统 - 一键启动")
shortcut.SetIconLocation(ICON, 0)
try:
    shortcut.SetShowCmd(1)  # SW_SHOWNORMAL：正常窗口，避免被最小化导致"看不到"
except Exception:
    pass

persist = shortcut.QueryInterface(pythoncom.IID_IPersistFile)
persist.Save(LINK, 0)
print("快捷方式已创建:", LINK)
print("  目标 :", CMD)
print("  参数 :", '/c "{}"'.format(TARGET))
