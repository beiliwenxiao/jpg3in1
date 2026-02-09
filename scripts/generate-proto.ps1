# 多语言通信框架 - Protocol Buffers 代码生成脚本 (Windows PowerShell)

$ErrorActionPreference = "Stop"

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_ROOT = Split-Path -Parent $SCRIPT_DIR
$PROTO_DIR = Join-Path $PROJECT_ROOT "proto"

Write-Host "=== 开始生成 Protocol Buffers 代码 ===" -ForegroundColor Cyan

# 检查 protoc 是否安装
try {
    $protocVersion = & protoc --version 2>&1
    Write-Host "protoc 版本: $protocVersion" -ForegroundColor Green
} catch {
    Write-Host "错误: protoc 未安装，请先安装 Protocol Buffers 编译器" -ForegroundColor Red
    Write-Host "下载地址: https://github.com/protocolbuffers/protobuf/releases" -ForegroundColor Yellow
    exit 1
}

# 进入 proto 目录
Push-Location $PROTO_DIR

try {
    # 生成 Java 代码
    Write-Host ""
    Write-Host "--- 生成 Java 代码 ---" -ForegroundColor Yellow
    $JAVA_OUT = Join-Path $PROJECT_ROOT "java-sdk\src\main\java"
    New-Item -ItemType Directory -Force -Path $JAVA_OUT | Out-Null

    & protoc --java_out="$JAVA_OUT" `
             --grpc-java_out="$JAVA_OUT" `
             common.proto service.proto jsonrpc.proto custom_protocol.proto

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Java 代码生成成功" -ForegroundColor Green
    } else {
        Write-Host "✗ Java 代码生成失败" -ForegroundColor Red
        exit 1
    }

    # 生成 Golang 代码
    Write-Host ""
    Write-Host "--- 生成 Golang 代码 ---" -ForegroundColor Yellow
    $GO_OUT = Join-Path $PROJECT_ROOT "golang-sdk\proto"
    New-Item -ItemType Directory -Force -Path $GO_OUT | Out-Null

    & protoc --go_out="$GO_OUT" `
             --go-grpc_out="$GO_OUT" `
             --go_opt=paths=source_relative `
             --go-grpc_opt=paths=source_relative `
             common.proto service.proto jsonrpc.proto custom_protocol.proto

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Golang 代码生成成功" -ForegroundColor Green
    } else {
        Write-Host "✗ Golang 代码生成失败" -ForegroundColor Red
        exit 1
    }

    # 生成 PHP 代码
    Write-Host ""
    Write-Host "--- 生成 PHP 代码 ---" -ForegroundColor Yellow
    $PHP_OUT = Join-Path $PROJECT_ROOT "php-sdk\src\Proto"
    New-Item -ItemType Directory -Force -Path $PHP_OUT | Out-Null

    # 检查 grpc_php_plugin 是否存在
    $grpcPlugin = Get-Command grpc_php_plugin -ErrorAction SilentlyContinue
    if ($grpcPlugin) {
        & protoc --php_out="$PHP_OUT" `
                 --grpc_out="$PHP_OUT" `
                 --plugin=protoc-gen-grpc=grpc_php_plugin `
                 common.proto service.proto jsonrpc.proto custom_protocol.proto
    } else {
        Write-Host "警告: grpc_php_plugin 未找到，仅生成 PHP 消息类" -ForegroundColor Yellow
        & protoc --php_out="$PHP_OUT" `
                 common.proto service.proto jsonrpc.proto custom_protocol.proto
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ PHP 代码生成成功" -ForegroundColor Green
    } else {
        Write-Host "✗ PHP 代码生成失败" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "=== Protocol Buffers 代码生成完成 ===" -ForegroundColor Cyan

} finally {
    # 返回原目录
    Pop-Location
}
