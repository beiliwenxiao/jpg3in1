#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================"
echo "  API 性能监控系统 - 一键启动"
echo "========================================"
echo

cleanup() {
    echo ""
    echo "正在停止所有服务..."
    kill $SERVER_PID $CLIENT_PID 2>/dev/null || true
    wait $SERVER_PID $CLIENT_PID 2>/dev/null || true
    echo "已停止"
    exit 0
}
trap cleanup SIGINT SIGTERM

echo "[1/2] 启动监控服务端..."
php "$SCRIPT_DIR/server/start.php" start -d 2>/dev/null || php "$SCRIPT_DIR/server/start.php" start &
SERVER_PID=$!

echo "等待服务端启动..."
sleep 3

echo "[2/2] 启动模拟客户端..."
php "$SCRIPT_DIR/client/simulate.php" &
CLIENT_PID=$!

echo
echo "========================================"
echo "  所有服务已启动！"
echo "  仪表盘: http://localhost:8095"
echo "  按 Ctrl+C 停止所有服务"
echo "========================================"

wait
