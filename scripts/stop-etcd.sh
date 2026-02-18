#!/bin/bash
# etcd 停止脚本 (Linux/macOS)

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

ETCD_CONTAINER="${ETCD_CONTAINER:-framework-etcd}"

echo -e "${BLUE}=========================================="
echo "停止 etcd 服务注册中心"
echo -e "==========================================${NC}"
echo ""

# 检查是否使用 Docker
if docker ps -a --filter "name=${ETCD_CONTAINER}" --format "{{.Names}}" | grep -q "${ETCD_CONTAINER}"; then
    echo "检测到 Docker 容器，正在停止..."
    
    if docker compose version &> /dev/null; then
        docker compose stop etcd
    else
        docker-compose stop etcd
    fi
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ etcd 容器已停止${NC}"
    else
        echo -e "${RED}✗ 停止失败${NC}"
        exit 1
    fi
else
    # 原生安装模式
    echo "正在查找 etcd 进程..."
    ETCD_PID=$(pgrep -f "etcd.*--name.*etcd0" || true)
    
    if [ -z "$ETCD_PID" ]; then
        echo -e "${YELLOW}未找到运行中的 etcd 进程${NC}"
        exit 0
    fi
    
    echo "找到 etcd 进程 (PID: $ETCD_PID)，正在停止..."
    kill -TERM $ETCD_PID
    
    # 等待进程结束
    for i in {1..10}; do
        if ! kill -0 $ETCD_PID 2>/dev/null; then
            echo -e "${GREEN}✓ etcd 进程已停止${NC}"
            exit 0
        fi
        sleep 1
    done
    
    # 如果还没停止，强制终止
    echo -e "${YELLOW}进程未响应，强制终止...${NC}"
    kill -9 $ETCD_PID
    echo -e "${GREEN}✓ etcd 进程已强制终止${NC}"
fi
