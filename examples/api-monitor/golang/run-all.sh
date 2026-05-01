#!/bin/bash
# API 性能监控 - Golang 版一键启动脚本
# 编译并启动服务端和模拟客户端

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================"
echo "  API 性能监控 - Golang 版"
echo "========================================"
echo ""

# 编译服务端
echo "[编译] 服务端..."
go build -o server ./cmd/server/
echo "[编译] 服务端完成"

# 编译模拟客户端
echo "[编译] 模拟客户端..."
go build -o simulator ./cmd/simulator/
echo "[编译] 模拟客户端完成"
echo ""

# 清理函数
cleanup() {
    echo ""
    echo "[停止] 正在停止所有进程..."
    if [ -n "$SERVER_PID" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    if [ -n "$SIMULATOR_PID" ]; then
        kill "$SIMULATOR_PID" 2>/dev/null || true
        wait "$SIMULATOR_PID" 2>/dev/null || true
    fi
    echo "[停止] 所有进程已停止"
    exit 0
}

trap cleanup SIGINT SIGTERM

# 启动服务端
echo "[启动] 服务端..."
./server &
SERVER_PID=$!
echo "[启动] 服务端 PID: $SERVER_PID"

# 等待 3 秒
echo "[等待] 3 秒后启动模拟客户端..."
sleep 3

# 启动模拟客户端
echo "[启动] 模拟客户端..."
./simulator &
SIMULATOR_PID=$!
echo "[启动] 模拟客户端 PID: $SIMULATOR_PID"

echo ""
echo "========================================"
echo "  仪表盘地址: http://localhost:8095"
echo "  按 Ctrl+C 停止所有服务"
echo "========================================"
echo ""

# 等待子进程
wait
