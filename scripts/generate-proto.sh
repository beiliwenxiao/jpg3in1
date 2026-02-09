#!/bin/bash

# 多语言通信框架 - Protocol Buffers 代码生成脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PROTO_DIR="$PROJECT_ROOT/proto"

echo "=== 开始生成 Protocol Buffers 代码 ==="

# 检查 protoc 是否安装
if ! command -v protoc &> /dev/null; then
    echo "错误: protoc 未安装，请先安装 Protocol Buffers 编译器"
    exit 1
fi

echo "protoc 版本: $(protoc --version)"

# 进入 proto 目录
cd "$PROTO_DIR"

# 生成 Java 代码
echo ""
echo "--- 生成 Java 代码 ---"
JAVA_OUT="$PROJECT_ROOT/java-sdk/src/main/java"
mkdir -p "$JAVA_OUT"

protoc --java_out="$JAVA_OUT" \
       --grpc-java_out="$JAVA_OUT" \
       common.proto service.proto jsonrpc.proto custom_protocol.proto

if [ $? -eq 0 ]; then
    echo "✓ Java 代码生成成功"
else
    echo "✗ Java 代码生成失败"
    exit 1
fi

# 生成 Golang 代码
echo ""
echo "--- 生成 Golang 代码 ---"
GO_OUT="$PROJECT_ROOT/golang-sdk/proto"
mkdir -p "$GO_OUT"

protoc --go_out="$GO_OUT" \
       --go-grpc_out="$GO_OUT" \
       --go_opt=paths=source_relative \
       --go-grpc_opt=paths=source_relative \
       common.proto service.proto jsonrpc.proto custom_protocol.proto

if [ $? -eq 0 ]; then
    echo "✓ Golang 代码生成成功"
else
    echo "✗ Golang 代码生成失败"
    exit 1
fi

# 生成 PHP 代码
echo ""
echo "--- 生成 PHP 代码 ---"
PHP_OUT="$PROJECT_ROOT/php-sdk/src/Proto"
mkdir -p "$PHP_OUT"

protoc --php_out="$PHP_OUT" \
       --grpc_out="$PHP_OUT" \
       --plugin=protoc-gen-grpc=grpc_php_plugin \
       common.proto service.proto jsonrpc.proto custom_protocol.proto

if [ $? -eq 0 ]; then
    echo "✓ PHP 代码生成成功"
else
    echo "✗ PHP 代码生成失败"
    exit 1
fi

echo ""
echo "=== Protocol Buffers 代码生成完成 ==="
