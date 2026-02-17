#!/bin/bash
# 测试安全模块

cd "$(dirname "$0")"
echo "当前目录: $(pwd)"

echo "下载依赖..."
go mod download

echo "运行安全模块测试..."
go test -v ./security/

echo "测试完成"
