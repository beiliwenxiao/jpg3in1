#!/bin/bash

set -e

echo "=== 运行所有测试 ==="

# 测试 Java SDK
echo "测试 Java SDK..."
cd java-sdk
mvn test
cd ..

# 测试 Golang SDK
echo "测试 Golang SDK..."
cd golang-sdk
go test ./...
cd ..

# 测试 PHP SDK
echo "测试 PHP SDK..."
cd php-sdk
composer test
cd ..

echo "=== 测试完成 ==="
