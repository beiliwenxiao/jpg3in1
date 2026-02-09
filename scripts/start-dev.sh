#!/bin/bash
set -e

MODE=${1:-docker}

echo "=== 启动开发环境 ==="

if [ "$MODE" = "native" ]; then
    echo "使用原生模式启动..."
    bash "$(dirname "$0")/start-native.sh" all
    exit 0
fi

echo "使用 Docker 模式启动..."
echo "(使用 '$0 native' 切换到原生模式)"
echo ""

if ! command -v docker &> /dev/null; then
    echo "错误: 未找到 docker，请安装 Docker 或使用原生模式:"
    echo "  $0 native"
    exit 1
fi

docker-compose up -d etcd mosquitto prometheus jaeger

echo "等待服务就绪..."
sleep 10

echo "检查 etcd 状态..."
docker-compose exec etcd etcdctl endpoint health

echo "=== 开发环境已启动 ==="
echo ""
echo "服务地址:"
echo "  - etcd: http://localhost:2379"
echo "  - MQTT: tcp://localhost:1883"
echo "  - Prometheus: http://localhost:9090"
echo "  - Jaeger UI: http://localhost:16686"
echo ""
echo "使用 'docker-compose logs -f' 查看日志"
echo "使用 'scripts/stop-dev.sh' 停止服务"
