@echo off
chcp 65001 >nul 2>&1
title API 性能监控 - Golang 版

echo ========================================
echo   API 性能监控 - Golang 版
echo ========================================
echo.

cd /d "%~dp0"

echo [编译] 服务端...
go build -o server.exe ./cmd/server/
if errorlevel 1 (
    echo [错误] 服务端编译失败
    pause
    exit /b 1
)
echo [编译] 服务端完成

echo [编译] 模拟客户端...
go build -o simulator.exe ./cmd/simulator/
if errorlevel 1 (
    echo [错误] 模拟客户端编译失败
    pause
    exit /b 1
)
echo [编译] 模拟客户端完成
echo.

echo [启动] 服务端...
start "API Monitor Server" server.exe

echo [等待] 3 秒后启动模拟客户端...
timeout /t 3 /nobreak >nul

echo [启动] 模拟客户端...
start "API Monitor Simulator" simulator.exe

echo.
echo ========================================
echo   仪表盘地址: http://localhost:8095
echo   关闭窗口即可停止对应服务
echo ========================================
echo.
pause
