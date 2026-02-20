@echo off
chcp 65001 >nul
echo ========================================
echo   Hello World - 三语言互调示例 (Win10)
echo ========================================
echo.
echo 本示例将依次启动三个 JSON-RPC 服务:
echo   Java  端口 8091
echo   PHP   端口 8092  (Webman + Workerman)
echo   Go    端口 8093  (GoFrame)
echo.

:: ---- 端口占用检查 ----
set "PORTS_BUSY=0"
for %%P in (8091 8092 8093) do (
    netstat -ano | findstr /R "LISTENING" | findstr ":%%P " >nul 2>&1
    if not errorlevel 1 (
        echo [警告] 端口 %%P 已被占用
        set "PORTS_BUSY=1"
    )
)
setlocal enabledelayedexpansion
if "!PORTS_BUSY!"=="0" goto :ports_ok
endlocal

echo.
set "KILL_CHOICE=n"
set /p "KILL_CHOICE=是否终止占用端口的进程？(y/n，回车跳过): "
if /i not "%KILL_CHOICE%"=="y" (
    echo 跳过，继续启动...
    echo.
    goto :ports_ok
)
for %%P in (8091 8092 8093) do (
    for /f "tokens=5" %%A in ('netstat -ano ^| findstr /R "LISTENING" ^| findstr ":%%P "') do (
        echo 正在终止 PID %%A ...
        taskkill /f /pid %%A >nul 2>&1
    )
)
timeout /t 2 /nobreak >nul
echo 已清理。
echo.

:ports_ok

:: ---- 第一步：构建 Java fat jar ----
echo [1/4] 构建 Java 示例...
cd /d "%~dp0java"
call mvn package -q
if %errorlevel% neq 0 (
    echo [错误] Java 构建失败
    pause
    exit /b 1
)
cd /d "%~dp0"
echo [1/4] Java 构建完成。
echo.

:: ---- 第二步：启动 Java 服务（后台）----
echo [2/4] 启动 Java 服务（端口 8091）...
cd /d "%~dp0java"
start "Java-HelloWorld" /min java -jar target\hello-world.jar
cd /d "%~dp0"
timeout /t 2 /nobreak >nul

:: ---- 第三步：编译并启动 Go 服务（后台）----
echo [3/4] 编译并启动 Go 服务（端口 8093）...
cd /d "%~dp0golang"
set "GOPROXY=https://goproxy.cn,direct"
set "GOTMPDIR=%~dp0golang\tmp"
if not exist "%~dp0golang\tmp" mkdir "%~dp0golang\tmp"
go build -o hello-world.exe .
if %errorlevel% neq 0 (
    echo [错误] Go 编译失败
    pause
    exit /b 1
)
start "Go-HelloWorld" /min cmd /c "cd /d %~dp0golang && hello-world.exe"
cd /d "%~dp0"
timeout /t 2 /nobreak >nul

:: ---- 第四步：启动 PHP/Webman 服务（前台）----
echo [4/4] 启动 PHP/Webman 服务（端口 8092）...
echo.
echo 浏览器访问:
echo   http://localhost:8091  (Java)
echo   http://localhost:8092  (PHP - Webman)
echo   http://localhost:8093  (Go - GoFrame)
echo.
cd /d "%~dp0php"
php windows.php

echo.
pause
