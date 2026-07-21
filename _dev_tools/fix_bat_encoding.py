"""Rewrite start-ticket-system.bat in GBK encoding for Windows cmd compatibility."""
import os

BAT_PATH = r'D:\fenliu\start-ticket-system.bat'

content = r"""@echo off
setlocal EnableDelayedExpansion
title Ticket System Launcher

cd /d "D:\fenliu"

set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_141"
set "PATH=%JAVA_HOME%\bin;C:\Users\xuanx\.workbuddy\binaries\node\versions\22.22.2;%PATH%"

echo ============================================
echo   TICKET SYSTEM - ONE CLICK LAUNCHER
echo ============================================

REM --- Step 1: Redis ---
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo [1/4] Redis already running.
) else (
  echo [1/4] Starting Redis ...
  start "Redis" "C:\Program Files\Redis\redis-server.exe"
  ping -n 3 127.0.0.1 >nul 2>&1
)

REM --- Step 2: Backend ---
REM Use expanded classpath startup (java -cp) instead of fat jar.
if not exist "D:\fenliu\data" mkdir "D:\fenliu\data"
if not exist "D:\fenliu\_run\classes" (
  echo [2/4] Runtime dir missing. Run: python D:\fenliu\_dev_tools\prepare_runtime.py
  pause
  goto :EOF
)
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo [2/4] Backend already running.
) else (
  echo [2/4] Starting Backend on port 8080 ...
  start "Backend" cmd /c "cd /d D:\fenliu && java -Dserver.port=8080 -Xmx512m -cp D:\fenliu\_run\classes;D:\fenliu\_build\lib\* com.ticket.web.TicketApplication"
)

REM --- Step 3: Frontend ---
netstat -ano | findstr ":5173" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo [3/4] Frontend already running.
) else (
  echo [3/4] Starting Frontend on port 5173 ...
  start "Frontend" cmd /c "cd /d D:\fenliu\ticket-frontend && npm run dev"
)

REM --- Step 4: Wait & Open Browser ---
echo [4/4] Waiting for services ...
for /L %%i in (1,1,30) do (
  ping -n 2 127.0.0.1 >nul 2>&1
  netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
  if not errorlevel 1 (
    netstat -ano | findstr ":5173" | findstr "LISTENING" >nul 2>&1
    if not errorlevel 1 goto READY
  )
)
:READY
echo Opening browser http://localhost:5173 ...
start "" "http://localhost:5173"

echo.
echo ============================================
echo   DONE!
echo   Frontend : http://localhost:5173
echo   API      : http://localhost:8080/api
echo   To stop  : close Redis / Backend / Frontend windows
echo ============================================
pause
"""

with open(BAT_PATH, 'w', encoding='gbk') as f:
    f.write(content)

print("OK - BAT rewritten in GBK encoding")
print("Size:", os.path.getsize(BAT_PATH), "bytes")

# Verify
with open(BAT_PATH, 'r', encoding='gbk') as f:
    lines = f.readlines()
print("Lines:", len(lines))
print("Line 1:", repr(lines[0].rstrip()))
# Check the Chinese line
for i, line in enumerate(lines):
    if 'Runtime dir' in line or 'missing' in line:
        print(f"Line {i+1} (Chinese):", repr(line.rstrip()))
