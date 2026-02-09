#!/bin/bash

# etcd 健康检查脚本
# 用于检查 etcd 集群的健康状态

set -e

# 配置
ETCD_ENDPOINTS=${ETCD_ENDPOINTS:-"http://localhost:2379"}
ETCD_NAMESPACE=${ETCD_NAMESPACE:-"/framework"}
TIMEOUT=${TIMEOUT:-5}

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "etcd 健康检查"
echo "=========================================="
echo "端点: $ETCD_ENDPOINTS"
echo "命名空间: $ETCD_NAMESPACE"
echo "超时: ${TIMEOUT}s"
echo ""

# 检查 etcd 是否可访问
echo "1. 检查 etcd 端点健康状态..."
if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s endpoint health; then
    echo -e "${GREEN}✓ etcd 端点健康${NC}"
else
    echo -e "${RED}✗ etcd 端点不健康${NC}"
    exit 1
fi

echo ""

# 检查 etcd 端点状态
echo "2. 检查 etcd 端点状态..."
if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s endpoint status --write-out=table; then
    echo -e "${GREEN}✓ etcd 端点状态正常${NC}"
else
    echo -e "${RED}✗ 无法获取 etcd 端点状态${NC}"
    exit 1
fi

echo ""

# 检查集群成员
echo "3. 检查 etcd 集群成员..."
if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s member list --write-out=table; then
    echo -e "${GREEN}✓ etcd 集群成员列表正常${NC}"
else
    echo -e "${RED}✗ 无法获取 etcd 集群成员列表${NC}"
    exit 1
fi

echo ""

# 测试读写操作
echo "4. 测试 etcd 读写操作..."
TEST_KEY="${ETCD_NAMESPACE}/health-check/test"
TEST_VALUE="health-check-$(date +%s)"

# 写入测试数据
if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "$TEST_KEY" "$TEST_VALUE" > /dev/null; then
    echo -e "${GREEN}✓ 写入测试数据成功${NC}"
else
    echo -e "${RED}✗ 写入测试数据失败${NC}"
    exit 1
fi

# 读取测试数据
READ_VALUE=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "$TEST_KEY" --print-value-only)
if [ "$READ_VALUE" = "$TEST_VALUE" ]; then
    echo -e "${GREEN}✓ 读取测试数据成功${NC}"
else
    echo -e "${RED}✗ 读取测试数据失败 (期望: $TEST_VALUE, 实际: $READ_VALUE)${NC}"
    exit 1
fi

# 删除测试数据
if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s del "$TEST_KEY" > /dev/null; then
    echo -e "${GREEN}✓ 删除测试数据成功${NC}"
else
    echo -e "${YELLOW}⚠ 删除测试数据失败（非致命错误）${NC}"
fi

echo ""

# 检查命名空间下的服务
echo "5. 检查已注册的服务..."
SERVICE_PREFIX="${ETCD_NAMESPACE}/services/"
SERVICE_COUNT=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "$SERVICE_PREFIX" --prefix --keys-only 2>/dev/null | wc -l)

if [ $SERVICE_COUNT -gt 0 ]; then
    echo -e "${GREEN}✓ 发现 $SERVICE_COUNT 个已注册的服务${NC}"
    echo ""
    echo "已注册的服务列表:"
    etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "$SERVICE_PREFIX" --prefix --keys-only 2>/dev/null | while read -r key; do
        if [ -n "$key" ]; then
            echo "  - $key"
        fi
    done
else
    echo -e "${YELLOW}⚠ 未发现已注册的服务${NC}"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}✓ etcd 健康检查完成${NC}"
echo "=========================================="

exit 0
