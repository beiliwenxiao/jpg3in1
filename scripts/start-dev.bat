@echo off
setlocal

echo === 启动开发环境 ===
echo.
echo 请选择启动模式:
echo   1. Docker 模式 (需要 Docker Desktop)
echo   2. 原生模式 (需要本地安装 etcd 等)
echo.

set /p MODE="请输入选择 (1/2): "

if "%MODE%"=="2" (
    echo.
    echo 使用原生模式启动...
    powershell -ExecutionPolicy Bypass -File "%~dp0start-native.ps1"
    goto :end
)

echo.
echo 使用 Docker 模式启动...

where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo 错误: 未找到 docker，请安装 Docker Desktop
    echo 或选择原生模式启动
    exit /b 1
)

docker-compose up -d etcd mosquitto prometheus jaeger

echo 等待服务就绪...
timeout /t 10 /nobreak >nul

echo 检查 etcd 状态...
docker-compose exec etcd etcdctl endpoint health

echo === 开发环境已启动 ===
echo.
echo 服务地址:
echo   - etcd: http://localhost:2379
echo   - MQTT: tcp://localhost:1883
echo   - Prometheus: http://localhost:9090
echo   - Jaeger UI: http://localhost:16686
echo.
echo 使用 'docker-compose logs -f' 查看日志
echo 使用 'scripts\stop-dev.bat' 停止服务

:end
endlocal
