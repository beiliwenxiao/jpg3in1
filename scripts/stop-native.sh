#!/bin/bash
# 原生环境停止脚本 (Bash)

COMPONENT=${1:-all}
ETCD_DATA_DIR="${ETCD_DATA_DIR:-$HOME/.framework/etcd-data}"
MOSQUITTO_CONFIG_DIR="${MOSQUITTO_CONFIG_DIR:-$HOME/.framework/mosquitto}"
CLEAN_DATA=${2:-}

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

stop_etcd() {
    echo "停止 etcd..."

    local pid_file="$ETCD_DATA_DIR/etcd.pid"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo -e "${GREEN}✓ etcd 已停止 (PID: $pid)${NC}"
        fi
        rm -f "$pid_file"
    fi

    # 兜底
    pkill -f "etcd --name framework-etcd" 2>/dev/null || true

    if [ "$CLEAN_DATA" = "--clean" ] && [ -d "$ETCD_DATA_DIR" ]; then
        rm -rf "$ETCD_DATA_DIR"
        echo -e "${GREEN}✓ 已清理 etcd 数据目录${NC}"
    fi
}

stop_mosquitto() {
    echo "停止 Mosquitto..."

    local pid_file="$MOSQUITTO_CONFIG_DIR/mosquitto.pid"
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo -e "${GREEN}✓ Mosquitto 已停止 (PID: $pid)${NC}"
        fi
        rm -f "$pid_file"
    fi

    pkill -f "mosquitto -c.*framework" 2>/dev/null || true

    if [ "$CLEAN_DATA" = "--clean" ] && [ -d "$MOSQUITTO_CONFIG_DIR" ]; then
        rm -rf "$MOSQUITTO_CONFIG_DIR"
        echo -e "${GREEN}✓ 已清理 Mosquitto 数据目录${NC}"
    fi
}

echo "=========================================="
echo "多语言通信框架 - 停止原生服务"
echo "=========================================="

case "$COMPONENT" in
    etcd)      stop_etcd ;;
    mosquitto) stop_mosquitto ;;
    all)
        stop_etcd
        stop_mosquitto
        echo ""
        echo -e "${GREEN}✓ 所有服务已停止${NC}"
        ;;
    *)
        echo "用法: $0 [all|etcd|mosquitto] [--clean]"
        exit 1
        ;;
esac
