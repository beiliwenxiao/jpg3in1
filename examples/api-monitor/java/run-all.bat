@echo off
chcp 65001 >nul 2>&1
title API 性能监控 - Java 版

echo ========================================
echo   API 性能监控 - Java 版
echo ========================================
echo.

cd /d "%~dp0"

set SERVER_JAR=target\api-monitor-server.jar
set SIMULATOR_JAR=target\api-monitor-simulator.jar

if not exist "%SERVER_JAR%" (
    echo [编译] Maven 打包中...
    call mvn -q clean package -DskipTests
    if errorlevel 1 (
        echo [错误] Maven 打包失败
        pause
        exit /b 1
    )
    echo [编译] 完成
    echo.
)

echo [启动] 服务端...
start "API Monitor Server (Java)" cmd /c "java -jar %SERVER_JAR% config.yaml"

echo [等待] 3 秒后启动模拟客户端...
timeout /t 3 /nobreak >nul

echo [启动] 模拟客户端...
start "API Monitor Simulator (Java)" cmd /c "java -jar %SIMULATOR_JAR% config.yaml"

echo.
echo ========================================
echo   仪表盘地址: http://localhost:8095
echo   关闭对应窗口即可停止服务
echo ========================================
echo.
pause
