@echo off
chcp 65001 >nul
echo ========================================
echo   Hello World - 三语言互调示例 (Win10)
echo ========================================
echo.
echo 本示例将依次启动三个 JSON-RPC 服务：
echo   PHP  -^> 端口 8091  (Webman + Workerman)
echo   Java -^> 端口 8092
echo   Go   -^> 端口 8093
echo.

:: ---- 第一步：构建 Java fat jar ----
echo [1/4] 构建 Java 示例...
cd /d "%~dp0java"
call mvn package -q 2>nul
if %errorlevel% neq 0 (
    echo [错误] Java 构建失败，请确认已安装 Maven 和 Java 17
    pause
    exit /b 1
)
cd /d "%~dp0"
echo [1/4] Java 构建完成。
echo.

:: ---- 第二步：启动 Go 服务（后台）----
echo [2/4] 编译并启动 Go 服务（端口 8093）...
cd /d "%~dp0golang"
go build -o hello-world.exe . 2>nul
if %errorlevel% neq 0 (
    echo [错误] Go 编译失败，请确认已安装 Go 并执行过 go mod tidy
    pause
    exit /b 1
)
start "Go-HelloWorld" /min cmd /c "hello-world.exe"
cd /d "%~dp0"
timeout /t 2 /nobreak >nul

:: ---- 第三步：启动 Java 服务（后台）----
echo [3/4] 启动 Java 服务（端口 8092）...
start "Java-HelloWorld" /min cmd /c "cd /d "%~dp0java" && java -jar target\hello-world.jar"
timeout /t 2 /nobreak >nul

:: ---- 第四步：启动 PHP/Webman 服务（前台）----
echo [4/4] 启动 PHP/Webman 服务（端口 8091）...
echo.
echo 浏览器访问：
echo   http://localhost:8091  (PHP - Webman)
echo   http://localhost:8092  (Java)
echo   http://localhost:8093  (Go)
echo.
cd /d "%~dp0php"
php windows.php

echo.
pause
