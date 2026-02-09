#!/bin/bash

# etcd 初始化脚本
# 用于初始化服务注册中心的命名空间和配置

set -e

# 配置
ETCD_ENDPOINTS=${ETCD_ENDPOINTS:-"http://localhost:2379"}
CONFIG_FILE=${CONFIG_FILE:-"docker/etcd/etcd.conf.yml"}
TIMEOUT=${TIMEOUT:-5}

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "=========================================="
echo "etcd 服务注册中心初始化"
echo "=========================================="
echo "端点: $ETCD_ENDPOINTS"
echo "配置文件: $CONFIG_FILE"
echo ""

# 等待 etcd 就绪
echo "等待 etcd 就绪..."
MAX_RETRIES=30
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s endpoint health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ etcd 已就绪${NC}"
        break
    fi
    
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "等待 etcd 启动... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 2
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo -e "${RED}✗ etcd 启动超时${NC}"
    exit 1
fi

echo ""

# 创建命名空间
echo "1. 创建命名空间..."

NAMESPACES=(
    "/framework"
    "/framework/services"
    "/framework/config"
    "/framework/locks"
    "/framework/health"
)

for namespace in "${NAMESPACES[@]}"; do
    # 创建命名空间标记
    KEY="${namespace}/.namespace"
    VALUE="created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    
    if etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "$KEY" "$VALUE" > /dev/null; then
        echo -e "${GREEN}✓ 创建命名空间: $namespace${NC}"
    else
        echo -e "${RED}✗ 创建命名空间失败: $namespace${NC}"
        exit 1
    fi
done

echo ""

# 设置框架配置
echo "2. 设置框架配置..."

# TTL 配置
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/ttl/service_registration" "30" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/ttl/heartbeat_interval" "10" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/ttl/discovery_cache" "60" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/ttl/config_cache" "300" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/ttl/lock" "60" > /dev/null
echo -e "${GREEN}✓ TTL 配置已设置${NC}"

# 服务注册配置
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/service_registration/auto_register" "true" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/service_registration/auto_deregister" "true" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/service_registration/health_check_enabled" "true" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/service_registration/health_check_interval" "10" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/service_registration/health_check_timeout" "5" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/service_registration/health_check_failure_threshold" "3" > /dev/null
echo -e "${GREEN}✓ 服务注册配置已设置${NC}"

# 负载均衡配置
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/load_balancing/default_strategy" "round_robin" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/load_balancing/health_check_filter" "true" > /dev/null
etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "/framework/config/load_balancing/version_filter" "false" > /dev/null
echo -e "${GREEN}✓ 负载均衡配置已设置${NC}"

echo ""

# 创建示例服务（用于测试）
echo "3. 创建示例服务元数据..."

# 示例服务 1: Java 服务
SERVICE_KEY="/framework/services/example-java-service/instance-1"
SERVICE_VALUE='{
  "id": "instance-1",
  "name": "example-java-service",
  "version": "1.0.0",
  "language": "java",
  "address": "localhost",
  "port": 8080,
  "protocols": ["grpc", "rest"],
  "metadata": {
    "region": "local",
    "environment": "development"
  },
  "registered_at": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"
}'

etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "$SERVICE_KEY" "$SERVICE_VALUE" > /dev/null
echo -e "${BLUE}ℹ 创建示例服务: example-java-service${NC}"

# 示例服务 2: Golang 服务
SERVICE_KEY="/framework/services/example-golang-service/instance-1"
SERVICE_VALUE='{
  "id": "instance-1",
  "name": "example-golang-service",
  "version": "1.0.0",
  "language": "golang",
  "address": "localhost",
  "port": 8081,
  "protocols": ["grpc", "rest"],
  "metadata": {
    "region": "local",
    "environment": "development"
  },
  "registered_at": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"
}'

etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "$SERVICE_KEY" "$SERVICE_VALUE" > /dev/null
echo -e "${BLUE}ℹ 创建示例服务: example-golang-service${NC}"

# 示例服务 3: PHP 服务
SERVICE_KEY="/framework/services/example-php-service/instance-1"
SERVICE_VALUE='{
  "id": "instance-1",
  "name": "example-php-service",
  "version": "1.0.0",
  "language": "php",
  "address": "localhost",
  "port": 8082,
  "protocols": ["grpc", "rest"],
  "metadata": {
    "region": "local",
    "environment": "development"
  },
  "registered_at": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"
}'

etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s put "$SERVICE_KEY" "$SERVICE_VALUE" > /dev/null
echo -e "${BLUE}ℹ 创建示例服务: example-php-service${NC}"

echo ""

# 验证配置
echo "4. 验证配置..."

# 检查命名空间
NAMESPACE_COUNT=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "/framework/" --prefix --keys-only | grep "/.namespace" | wc -l)
echo -e "${GREEN}✓ 已创建 $NAMESPACE_COUNT 个命名空间${NC}"

# 检查配置项
CONFIG_COUNT=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "/framework/config/" --prefix --keys-only | wc -l)
echo -e "${GREEN}✓ 已设置 $CONFIG_COUNT 个配置项${NC}"

# 检查服务
SERVICE_COUNT=$(etcdctl --endpoints=$ETCD_ENDPOINTS --command-timeout=${TIMEOUT}s get "/framework/services/" --prefix --keys-only | grep -v "/.namespace" | wc -l)
echo -e "${GREEN}✓ 已注册 $SERVICE_COUNT 个示例服务${NC}"

echo ""
echo "=========================================="
echo -e "${GREEN}✓ etcd 服务注册中心初始化完成${NC}"
echo "=========================================="
echo ""
echo "可用命令:"
echo "  - 查看所有服务: etcdctl --endpoints=$ETCD_ENDPOINTS get /framework/services/ --prefix"
echo "  - 查看配置: etcdctl --endpoints=$ETCD_ENDPOINTS get /framework/config/ --prefix"
echo "  - 健康检查: ./scripts/etcd-health-check.sh"
echo ""

exit 0
