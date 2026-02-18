#!/bin/bash
# etcd 健康检查脚本 (Linux/macOS)
# 用于检查 etcd 服务注册中心的健康状态

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
ETCD_ENDPOINT="${ETCD_ENDPOINT:-http://localhost:2379}"
ETCD_CONTAINER="${ETCD_CONTAINER:-framework-etcd}"
FRAMEWORK_NAMESPACE="${FRAMEWORK_NAMESPACE:-/framework}"
MODE="${MODE:-auto}"  # auto, docker, native

echo "=========================================="
echo "etcd 服务注册中心健康检查"
echo "=========================================="
echo ""

# 自动检测运行模式
IS_DOCKER=false
IS_NATIVE=false

if [ "$MODE" = "auto" ]; then
    # 检查 Docker 容器是否存在
    if command -v docker &> /dev/null && docker ps --filter "name=${ETCD_CONTAINER}" --filter "status=running" 2>/dev/null | grep -q "${ETCD_CONTAINER}"; then
        IS_DOCKER=true
        MODE="docker"
    # 检查原生进程
    elif pgrep -f "etcd.*--name.*etcd0" > /dev/null 2>&1; then
        IS_NATIVE=true
        MODE="native"
    fi
elif [ "$MODE" = "docker" ]; then
    IS_DOCKER=true
elif [ "$MODE" = "native" ]; then
    IS_NATIVE=true
fi

echo -e "${BLUE}检测到的运行模式: ${MODE}${NC}"
echo ""

# Docker 模式健康检查
if [ "$MODE" = "docker" ]; then

# Docker 模式健康检查
if [ "$MODE" = "docker" ]; then
    # 检查 Docker 是否运行
    echo -n "检查 Docker 服务状态... "
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}失败${NC}"
        echo "错误: Docker 服务未运行"
        exit 1
    fi
    echo -e "${GREEN}正常${NC}"

    # 检查 etcd 容器是否运行
    echo -n "检查 etcd 容器状态... "
    if ! docker ps --filter "name=${ETCD_CONTAINER}" --filter "status=running" | grep -q "${ETCD_CONTAINER}"; then
        echo -e "${RED}失败${NC}"
        echo "错误: etcd 容器未运行"
        echo "提示: 运行 './scripts/start-etcd.sh' 启动 etcd"
        exit 1
    fi
    echo -e "${GREEN}运行中${NC}"

    # 检查 etcd 端点健康状态
    echo -n "检查 etcd 端点健康状态... "
    if docker exec ${ETCD_CONTAINER} etcdctl endpoint health > /dev/null 2>&1; then
        echo -e "${GREEN}健康${NC}"
    else
        echo -e "${RED}不健康${NC}"
        echo "错误: etcd 端点健康检查失败"
        exit 1
    fi

    # 检查 etcd 集群状态
    echo -n "检查 etcd 集群状态... "
    CLUSTER_STATUS=$(docker exec ${ETCD_CONTAINER} etcdctl endpoint status --write-out=json 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}正常${NC}"
        echo "集群信息:"
        echo "${CLUSTER_STATUS}" | jq -r '.[] | "  - 端点: \(.Endpoint), Leader: \(.Status.leader), 版本: \(.Status.version)"' 2>/dev/null || echo "  (无法解析集群状态详情)"
    else
        echo -e "${YELLOW}警告${NC}"
        echo "警告: 无法获取集群状态详情"
    fi

    # 检查命名空间
    echo -n "检查框架命名空间... "
    if docker exec ${ETCD_CONTAINER} etcdctl get ${FRAMEWORK_NAMESPACE} --prefix --keys-only --limit=1 > /dev/null 2>&1; then
        echo -e "${GREEN}存在${NC}"
        
        # 统计服务数量
        SERVICE_COUNT=$(docker exec ${ETCD_CONTAINER} etcdctl get ${FRAMEWORK_NAMESPACE}/services --prefix --keys-only 2>/dev/null | wc -l)
        echo "  - 已注册服务数量: ${SERVICE_COUNT}"
        
        # 列出已注册的服务
        if [ ${SERVICE_COUNT} -gt 0 ]; then
            echo "  - 已注册服务列表:"
            docker exec ${ETCD_CONTAINER} etcdctl get ${FRAMEWORK_NAMESPACE}/services --prefix --keys-only 2>/dev/null | while read key; do
                if [ ! -z "$key" ]; then
                    SERVICE_NAME=$(echo $key | sed "s|${FRAMEWORK_NAMESPACE}/services/||" | cut -d'/' -f1)
                    echo "    * ${SERVICE_NAME}"
                fi
            done | sort -u
        fi
    else
        echo -e "${YELLOW}不存在${NC}"
        echo "提示: 命名空间将在首次服务注册时自动创建"
    fi

    # 检查 etcd 性能指标
    echo ""
    echo "性能指标:"
    METRICS=$(docker exec ${ETCD_CONTAINER} etcdctl endpoint status --write-out=json 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "${METRICS}" | jq -r '.[] | "  - DB 大小: \(.Status.dbSize) bytes, Raft 索引: \(.Status.raftIndex)"' 2>/dev/null || echo "  (无法解析性能指标)"
    fi

    # 检查 etcd 日志（最后5行）
    echo ""
    echo "最近日志 (最后5行):"
    docker logs ${ETCD_CONTAINER} --tail 5 2>&1 | sed 's/^/  /'

# 原生模式健康检查
elif [ "$MODE" = "native" ]; then
    # 检查 etcd 进程是否运行
    echo -n "检查 etcd 进程状态... "
    ETCD_PID=$(pgrep -f "etcd.*--name.*etcd0" || true)
    if [ -z "$ETCD_PID" ]; then
        echo -e "${RED}未运行${NC}"
        echo "错误: etcd 进程未运行"
        echo "提示: 运行 './scripts/start-etcd.sh -m native' 启动 etcd"
        exit 1
    fi
    echo -e "${GREEN}运行中 (PID: ${ETCD_PID})${NC}"

    # 检查 etcdctl 是否可用
    if ! command -v etcdctl &> /dev/null; then
        echo -e "${YELLOW}警告: etcdctl 未找到,跳过详细检查${NC}"
        echo ""
        echo "=========================================="
        echo -e "${GREEN}基本健康检查完成 (etcd 进程运行中)${NC}"
        echo "=========================================="
        exit 0
    fi

    # 设置 etcdctl 环境变量
    export ETCDCTL_API=3

    # 检查 etcd 端点健康状态
    echo -n "检查 etcd 端点健康状态... "
    if etcdctl --endpoints=${ETCD_ENDPOINT} endpoint health > /dev/null 2>&1; then
        echo -e "${GREEN}健康${NC}"
    else
        echo -e "${RED}不健康${NC}"
        echo "错误: etcd 端点健康检查失败"
        exit 1
    fi

    # 检查 etcd 集群状态
    echo -n "检查 etcd 集群状态... "
    CLUSTER_STATUS=$(etcdctl --endpoints=${ETCD_ENDPOINT} endpoint status --write-out=json 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}正常${NC}"
        echo "集群信息:"
        echo "${CLUSTER_STATUS}" | jq -r '.[] | "  - 端点: \(.Endpoint), Leader: \(.Status.leader), 版本: \(.Status.version)"' 2>/dev/null || echo "  (无法解析集群状态详情)"
    else
        echo -e "${YELLOW}警告${NC}"
        echo "警告: 无法获取集群状态详情"
    fi

    # 检查命名空间
    echo -n "检查框架命名空间... "
    if etcdctl --endpoints=${ETCD_ENDPOINT} get ${FRAMEWORK_NAMESPACE} --prefix --keys-only --limit=1 2>/dev/null | grep -q "${FRAMEWORK_NAMESPACE}"; then
        echo -e "${GREEN}存在${NC}"
        
        # 统计服务数量
        SERVICE_COUNT=$(etcdctl --endpoints=${ETCD_ENDPOINT} get ${FRAMEWORK_NAMESPACE}/services --prefix --keys-only 2>/dev/null | wc -l)
        echo "  - 已注册服务数量: ${SERVICE_COUNT}"
        
        # 列出已注册的服务
        if [ ${SERVICE_COUNT} -gt 0 ]; then
            echo "  - 已注册服务列表:"
            etcdctl --endpoints=${ETCD_ENDPOINT} get ${FRAMEWORK_NAMESPACE}/services --prefix --keys-only 2>/dev/null | while read key; do
                if [ ! -z "$key" ]; then
                    SERVICE_NAME=$(echo $key | sed "s|${FRAMEWORK_NAMESPACE}/services/||" | cut -d'/' -f1)
                    echo "    * ${SERVICE_NAME}"
                fi
            done | sort -u
        fi
    else
        echo -e "${YELLOW}不存在${NC}"
        echo "提示: 命名空间将在首次服务注册时自动创建"
    fi

    # 检查 etcd 性能指标
    echo ""
    echo "性能指标:"
    METRICS=$(etcdctl --endpoints=${ETCD_ENDPOINT} endpoint status --write-out=json 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "${METRICS}" | jq -r '.[] | "  - DB 大小: \(.Status.dbSize) bytes, Raft 索引: \(.Status.raftIndex)"' 2>/dev/null || echo "  (无法解析性能指标)"
    fi

else
    echo -e "${RED}错误: 未检测到运行中的 etcd 实例${NC}"
    echo "提示: 运行 './scripts/start-etcd.sh' 或 './scripts/start-etcd.sh -m native' 启动 etcd"
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}健康检查完成！${NC}"
echo "=========================================="
