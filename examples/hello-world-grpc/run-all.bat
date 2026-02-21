@echo off
chcp 65001 >nul
echo ========================================
echo   Hello World gRPC - 跨语言示例 (Win10)
echo ========================================
echo.
echo 本示例将启动:
echo   Java  gRPC:9091 HTTP:8091
echo   Go    gRPC:9093 HTTP:8093
echo   PHP   HTTP:8092 (gRPC 代理客户端)
echo.

:: ---- 端口占用检查 ----
set "PORTS_BUSY=0"
for %%P in (8091 8092 8093 9091 9093) do (
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
set /p "KILL_CHOICE=是否终止占用端口的进程？(y/n): "
if /i not "%KILL_CHOICE%"=="y" goto :ports_ok
for %%P in (8091 8092 8093 9091 9093) do (
    for /f "tokens=5" %%A in ('netstat -ano ^| findstr /R "LISTENING" ^| findstr ":%%P "') do (
        taskkill /f /pid %%A >nul 2>&1
    )
)
timeout /t 2 /nobreak >nul

:ports_ok

:: ---- 构建 Java ----
echo [1/3] 构建 Java gRPC 示例...
cd /d "%~dp0java"
call mvn package -q
if %errorlevel% neq 0 (
    echo [错误] Java 构建失败
    pause
    exit /b 1
)
cd /d "%~dp0"
echo [1/3] Java 构建完成。

:: ---- 启动 Java ----
echo [2/3] 启动 Java 服务...
cd /d "%~dp0java"
start "Java-gRPC" /min java -jar target\hello-world-grpc.jar
cd /d "%~dp0"
timeout /t 3 /nobreak >nul

:: ---- 编译并启动 Go ----
echo [2/3] 编译并启动 Go 服务...
cd /d "%~dp0golang"
set "GOPROXY=https://goproxy.cn,direct"
set "GOTMPDIR=%~dp0golang\tmp"
if not exist "%~dp0golang\tmp" mkdir "%~dp0golang\tmp"
go build -o hello-world-grpc.exe .
if %errorlevel% neq 0 (
    echo [错误] Go 编译失败
    pause
    exit /b 1
)
start "Go-gRPC" /min cmd /c "cd /d %~dp0golang && hello-world-grpc.exe"
cd /d "%~dp0"
timeout /t 2 /nobreak >nul

:: ---- 启动 PHP ----
echo [3/3] 启动 PHP gRPC 代理客户端...
echo.
echo 浏览器访问:
echo   http://localhost:8091  (Java - gRPC 服务端)
echo   http://localhost:8092  (PHP - gRPC 代理客户端)
echo   http://localhost:8093  (Go - gRPC 服务端)
echo.
cd /d "%~dp0php"
php hello-grpc.php

echo.
pause
