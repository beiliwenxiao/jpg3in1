#!/bin/bash
# etcd 启动脚本 (Linux/macOS)
# 支持 Docker 和原生安装两种方式

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
ETCD_VERSION="${ETCD_VERSION:-v3.5.11}"
ETCD_DATA_DIR="${ETCD_DATA_DIR:-./data/etcd}"
ETCD_PORT="${ETCD_PORT:-2379}"
ETCD_PEER_PORT="${ETCD_PEER_PORT:-2380}"
FRAMEWORK_NAMESPACE="${FRAMEWORK_NAMESPACE:-/framework}"
SERVICE_TTL="${SERVICE_TTL:-30}"  # 服务注册 TTL (秒)

# 显示帮助信息
show_help() {
    cat << EOF
用法: $0 [选项]

选项:
  -m, --mode MODE       启动模式: docker (默认) 或 native
  -d, --detach          后台运行 (仅 Docker 模式)
  -h, --help            显示此帮助信息

示例:
  $0                    # 使用 Docker 启动 (前台)
  $0 -d                 # 使用 Docker 启动 (后台)
  $0 -m native          # 使用原生方式启动

EOF
}

# 解析命令行参数
MODE="docker"
DETACH=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -m|--mode)
            MODE="$2"
            shift 2
            ;;
        -d|--detach)
            DETACH="-d"
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

echo -e "${BLUE}=========================================="
echo "etcd 服务注册中心启动脚本"
echo -e "==========================================${NC}"
echo ""

# Docker 模式
if [ "$MODE" = "docker" ]; then
    echo -e "${BLUE}启动模式: Docker${NC}"
    echo ""
    
    # 检查 Docker 是否安装
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}错误: Docker 未安装${NC}"
        echo "请先安装 Docker: https://docs.docker.com/get-docker/"
        exit 1
    fi
    
    # 检查 Docker Compose 是否安装
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo -e "${RED}错误: Docker Compose 未安装${NC}"
        echo "请先安装 Docker Compose: https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    # 检查 docker-compose.yml 是否存在
    if [ ! -f "docker-compose.yml" ]; then
        echo -e "${RED}错误: docker-compose.yml 文件不存在${NC}"
        exit 1
    fi
    
    # 启动 etcd
    echo "正在启动 etcd 容器..."
    if docker compose version &> /dev/null; then
        docker compose up $DETACH etcd
    else
        docker-compose up $DETACH etcd
    fi
    
    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ etcd 启动成功！${NC}"
        echo ""
        echo "访问信息:"
        echo "  - 客户端端点: http://localhost:${ETCD_PORT}"
        echo "  - 对等端点: http://localhost:${ETCD_PEER_PORT}"
        echo "  - 框架命名空间: ${FRAMEWORK_NAMESPACE}"
        echo "  - 服务 TTL: ${SERVICE_TTL} 秒"
        echo ""
        echo "常用命令:"
        echo "  - 查看日志: docker logs framework-etcd -f"
        echo "  - 停止服务: docker-compose stop etcd"
        echo "  - 健康检查: ./scripts/health-check-etcd.sh"
        echo ""
        echo "配置说明:"
        echo "  - 命名空间用于隔离不同应用的服务注册数据"
        echo "  - TTL 定义服务注册的有效期,服务需要定期续约"
    else
        echo -e "${RED}✗ etcd 启动失败${NC}"
        exit 1
    fi

# 原生模式
elif [ "$MODE" = "native" ]; then
    echo -e "${BLUE}启动模式: 原生安装${NC}"
    echo ""
    
    # 检查 etcd 是否安装
    if ! command -v etcd &> /dev/null; then
        echo -e "${YELLOW}警告: etcd 未安装${NC}"
        echo ""
        echo "正在下载 etcd ${ETCD_VERSION}..."
        
        # 检测操作系统
        OS=$(uname -s | tr '[:upper:]' '[:lower:]')
        ARCH=$(uname -m)
        
        if [ "$ARCH" = "x86_64" ]; then
            ARCH="amd64"
        elif [ "$ARCH" = "aarch64" ]; then
            ARCH="arm64"
        fi
        
        ETCD_DOWNLOAD_URL="https://github.com/etcd-io/etcd/releases/download/${ETCD_VERSION}/etcd-${ETCD_VERSION}-${OS}-${ARCH}.tar.gz"
        ETCD_DOWNLOAD_DIR="/tmp/etcd-download"
        
        mkdir -p ${ETCD_DOWNLOAD_DIR}
        
        echo "下载地址: ${ETCD_DOWNLOAD_URL}"
        if curl -L ${ETCD_DOWNLOAD_URL} -o ${ETCD_DOWNLOAD_DIR}/etcd.tar.gz; then
            echo "正在解压..."
            tar xzf ${ETCD_DOWNLOAD_DIR}/etcd.tar.gz -C ${ETCD_DOWNLOAD_DIR} --strip-components=1
            
            echo "正在安装到 /usr/local/bin..."
            sudo mv ${ETCD_DOWNLOAD_DIR}/etcd /usr/local/bin/
            sudo mv ${ETCD_DOWNLOAD_DIR}/etcdctl /usr/local/bin/
            
            echo -e "${GREEN}✓ etcd 安装成功${NC}"
        else
            echo -e "${RED}✗ etcd 下载失败${NC}"
            echo "请手动下载并安装: https://github.com/etcd-io/etcd/releases"
            exit 1
        fi
    fi
    
    # 创建数据目录
    mkdir -p ${ETCD_DATA_DIR}
    
    # 启动 etcd
    echo "正在启动 etcd..."
    echo ""
    echo "配置信息:"
    echo "  - 数据目录: ${ETCD_DATA_DIR}"
    echo "  - 客户端端口: ${ETCD_PORT}"
    echo "  - 对等端口: ${ETCD_PEER_PORT}"
    echo "  - 框架命名空间: ${FRAMEWORK_NAMESPACE}"
    echo "  - 服务 TTL: ${SERVICE_TTL} 秒"
    echo ""
    
    echo -e "${GREEN}✓ etcd 启动中...${NC}"
    echo ""
    echo "提示:"
    echo "  - 按 Ctrl+C 停止 etcd"
    echo "  - 或在另一个终端运行: ./scripts/stop-etcd.sh"
    echo "  - 健康检查: ./scripts/health-check-etcd.sh"
    echo ""
    echo "配置说明:"
    echo "  - 命名空间 (${FRAMEWORK_NAMESPACE}) 用于隔离不同应用的服务注册数据"
    echo "  - TTL (${SERVICE_TTL} 秒) 定义服务注册的有效期,服务需要定期续约"
    echo ""
    echo "=========================================="
    echo ""
    
    etcd \
        --name etcd0 \
        --data-dir ${ETCD_DATA_DIR} \
        --listen-client-urls http://0.0.0.0:${ETCD_PORT} \
        --advertise-client-urls http://localhost:${ETCD_PORT} \
        --listen-peer-urls http://0.0.0.0:${ETCD_PEER_PORT} \
        --initial-advertise-peer-urls http://localhost:${ETCD_PEER_PORT} \
        --initial-cluster etcd0=http://localhost:${ETCD_PEER_PORT} \
        --initial-cluster-token etcd-cluster \
        --initial-cluster-state new \
        --log-level info \
        --auto-compaction-mode periodic \
        --auto-compaction-retention 1h
    
else
    echo -e "${RED}错误: 未知的启动模式: ${MODE}${NC}"
    echo "支持的模式: docker, native"
    exit 1
fi
