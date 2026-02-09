#!/bin/bash

# 使用 Docker 生成 Protocol Buffers 代码

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=== 使用 Docker 生成 Protocol Buffers 代码 ==="

# 使用官方 protoc Docker 镜像生成代码
docker run --rm \
  -v "$PROJECT_ROOT/proto:/proto" \
  -v "$PROJECT_ROOT/java-sdk/src/main/java:/java-out" \
  -v "$PROJECT_ROOT/golang-sdk/proto:/go-out" \
  -v "$PROJECT_ROOT/php-sdk/src/Proto:/php-out" \
  namely/protoc-all:1.51_1 \
  -d /proto \
  -l java \
  -l go \
  -l php \
  --with-grpc \
  --go-source-relative

echo "✓ Protocol Buffers 代码生成完成"
