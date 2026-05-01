@echo off
chcp 65001 >nul
echo ========================================
echo   API 性能监控系统 - 一键启动
echo ========================================
echo.

echo [1/2] 启动监控服务端...
start "API-Monitor-Server" cmd /c "php %~dp0windows.php"

echo 等待服务端启动...
timeout /t 3 /nobreak >nul

echo [2/2] 启动模拟客户端...
start "API-Monitor-Client" cmd /c "php %~dp0client\simulate.php"

echo.
echo ========================================
echo   所有服务已启动！
echo   仪表盘: http://localhost:8095
echo   关闭窗口即可停止服务
echo ========================================
pause
