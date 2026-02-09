#!/bin/bash

set -e

echo "=== 构建多语言通信框架 ==="

# 构建 Java SDK
echo "构建 Java SDK..."
cd java-sdk
mvn clean install -DskipTests
cd ..

# 构建 Golang SDK
echo "构建 Golang SDK..."
cd golang-sdk
go mod tidy
go build ./...
cd ..

# 构建 PHP SDK
echo "构建 PHP SDK..."
cd php-sdk
composer install
cd ..

echo "=== 构建完成 ==="
