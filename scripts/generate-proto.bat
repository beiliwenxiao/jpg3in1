@echo off
REM 多语言通信框架 - Protocol Buffers 代码生成脚本 (Windows)

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set PROTO_DIR=%PROJECT_ROOT%\proto

echo === 开始生成 Protocol Buffers 代码 ===

REM 检查 protoc 是否安装
where protoc >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo 错误: protoc 未安装，请先安装 Protocol Buffers 编译器
    exit /b 1
)

protoc --version

REM 进入 proto 目录
cd /d "%PROTO_DIR%"

REM 生成 Java 代码
echo.
echo --- 生成 Java 代码 ---
set JAVA_OUT=%PROJECT_ROOT%\java-sdk\src\main\java
if not exist "%JAVA_OUT%" mkdir "%JAVA_OUT%"

protoc --java_out="%JAVA_OUT%" --grpc-java_out="%JAVA_OUT%" common.proto service.proto jsonrpc.proto custom_protocol.proto

if %ERRORLEVEL% equ 0 (
    echo √ Java 代码生成成功
) else (
    echo × Java 代码生成失败
    exit /b 1
)

REM 生成 Golang 代码
echo.
echo --- 生成 Golang 代码 ---
set GO_OUT=%PROJECT_ROOT%\golang-sdk\proto
if not exist "%GO_OUT%" mkdir "%GO_OUT%"

protoc --go_out="%GO_OUT%" --go-grpc_out="%GO_OUT%" --go_opt=paths=source_relative --go-grpc_opt=paths=source_relative common.proto service.proto jsonrpc.proto custom_protocol.proto

if %ERRORLEVEL% equ 0 (
    echo √ Golang 代码生成成功
) else (
    echo × Golang 代码生成失败
    exit /b 1
)

REM 生成 PHP 代码
echo.
echo --- 生成 PHP 代码 ---
set PHP_OUT=%PROJECT_ROOT%\php-sdk\src\Proto
if not exist "%PHP_OUT%" mkdir "%PHP_OUT%"

protoc --php_out="%PHP_OUT%" --grpc_out="%PHP_OUT%" --plugin=protoc-gen-grpc=grpc_php_plugin common.proto service.proto jsonrpc.proto custom_protocol.proto

if %ERRORLEVEL% equ 0 (
    echo √ PHP 代码生成成功
) else (
    echo × PHP 代码生成失败
    exit /b 1
)

echo.
echo === Protocol Buffers 代码生成完成 ===

endlocal
