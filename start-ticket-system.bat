@echo off
setlocal EnableDelayedExpansion
title Ticket System Launcher

cd /d "D:\fenliu"

set "JAVA_HOME=D:\fenliu\_runtime\jdk8"
set "PATH=%JAVA_HOME%\bin;D:\fenliu\_runtime\node;%PATH%"

echo ============================================
echo   TICKET SYSTEM - ONE CLICK LAUNCHER
echo ============================================

REM --- Step 1: Redis ---
netstat -ano | findstr ":6379" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo [1/4] Redis already running.
) else (
  echo [1/4] Starting Redis ...
  start "Redis" "D:\fenliu\_runtime\redis\redis-server.exe"
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
  start "Backend" cmd /c "cd /d D:\fenliu && java -Dserver.port=8080 -XX:TieredStopAtLevel=1 -noverify -Xmx768m -cp D:\fenliu\_run\classes;D:\fenliu\_build\lib\* com.ticket.web.TicketApplication"
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
