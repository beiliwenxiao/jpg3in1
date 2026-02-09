#!/bin/bash
# 原生环境启动脚本 (Bash)
# 不依赖 Docker，直接使用本地安装的 etcd、mosquitto 等服务

set -e

COMPONENT=${1:-all}
ETCD_DATA_DIR="${ETCD_DATA_DIR:-$HOME/.framework/etcd-data}"
MOSQUITTO_CONFIG_DIR="${MOSQUITTO_CONFIG_DIR:-$HOME/.framework/mosquitto}"
ETCD_CLIENT_PORT=${ETCD_CLIENT_PORT:-2379}
ETCD_PEER_PORT=${ETCD_PEER_PORT:-2380}
MQTT_PORT=${MQTT_PORT:-1883}

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

check_port() {
    if command -v lsof &> /dev/null; then
        lsof -i :"$1" &> /dev/null
    elif command -v ss &> /dev/null; then
        ss -tlnp | grep -q ":$1 "
    elif command -v netstat &> /dev/null; then
        netstat -tlnp 2>/dev/null | grep -q ":$1 "
    else
        return 1
    fi
}

start_etcd() {
    echo ""
    echo -e "${CYAN}--- 启动 etcd ---${NC}"

    if ! command -v etcd &> /dev/null; then
        echo -e "${RED}✗ 未找到 etcd，请先安装${NC}"
        echo ""
        echo "安装方式:"
        echo "  macOS:  brew install etcd"
        echo "  Linux:  下载 https://github.com/etcd-io/etcd/releases"
        echo "          解压后将 etcd 和 etcdctl 放入 /usr/local/bin/"
        return 1
    fi

    if check_port "$ETCD_CLIENT_PORT"; then
        echo -e "${YELLOW}⚠ 端口 $ETCD_CLIENT_PORT 已被占用${NC}"
        if etcdctl --endpoints="http://localhost:$ETCD_CLIENT_PORT" endpoint health &> /dev/null; then
            echo -e "${GREEN}✓ etcd 已在运行中${NC}"
            return 0
        fi
        echo -e "${RED}✗ 端口被其他进程占用${NC}"
        return 1
    fi

    mkdir -p "$ETCD_DATA_DIR"

    nohup etcd \
        --name framework-etcd \
        --data-dir "$ETCD_DATA_DIR" \
        --listen-client-urls "http://0.0.0.0:$ETCD_CLIENT_PORT" \
        --advertise-client-urls "http://localhost:$ETCD_CLIENT_PORT" \
        --listen-peer-urls "http://0.0.0.0:$ETCD_PEER_PORT" \
        --initial-advertise-peer-urls "http://localhost:$ETCD_PEER_PORT" \
        --initial-cluster "framework-etcd=http://localhost:$ETCD_PEER_PORT" \
        --initial-cluster-token framework-cluster \
        --initial-cluster-state new \
        --auto-compaction-mode periodic \
        --auto-compaction-retention 1h \
        --log-level info \
        > "$ETCD_DATA_DIR/etcd.log" 2>&1 &

    echo $! > "$ETCD_DATA_DIR/etcd.pid"
    echo -e "${GREEN}✓ etcd 已在后台启动 (PID: $!)${NC}"

    echo "等待 etcd 就绪..."
    for i in $(seq 1 15); do
        sleep 1
        if etcdctl --endpoints="http://localhost:$ETCD_CLIENT_PORT" endpoint health &> /dev/null; then
            echo -e "${GREEN}✓ etcd 已就绪${NC}"
            return 0
        fi
        echo "  等待中... ($i/15)"
    done

    echo -e "${RED}✗ etcd 启动超时${NC}"
    return 1
}

start_mosquitto() {
    echo ""
    echo -e "${CYAN}--- 启动 Mosquitto MQTT ---${NC}"

    if ! command -v mosquitto &> /dev/null; then
        echo -e "${YELLOW}⚠ 未找到 mosquitto，跳过 MQTT 服务${NC}"
        echo "  macOS:  brew install mosquitto"
        echo "  Linux:  sudo apt install mosquitto"
        return 1
    fi

    if check_port "$MQTT_PORT"; then
        echo -e "${YELLOW}⚠ 端口 $MQTT_PORT 已被占用，Mosquitto 可能已在运行${NC}"
        return 0
    fi

    mkdir -p "$MOSQUITTO_CONFIG_DIR/data"

    cat > "$MOSQUITTO_CONFIG_DIR/mosquitto.conf" <<EOF
listener $MQTT_PORT
allow_anonymous true
persistence true
persistence_location $MOSQUITTO_CONFIG_DIR/data/
log_dest file $MOSQUITTO_CONFIG_DIR/mosquitto.log
EOF

    nohup mosquitto -c "$MOSQUITTO_CONFIG_DIR/mosquitto.conf" > /dev/null 2>&1 &
    echo $! > "$MOSQUITTO_CONFIG_DIR/mosquitto.pid"
    echo -e "${GREEN}✓ Mosquitto 已启动 (PID: $!)${NC}"
    return 0
}

show_status() {
    echo ""
    echo -e "${CYAN}=== 服务状态 ===${NC}"

    echo ""
    echo "etcd:"
    if etcdctl --endpoints="http://localhost:$ETCD_CLIENT_PORT" endpoint health &> /dev/null; then
        echo -e "  ${GREEN}状态: 运行中${NC}"
        echo "  地址: http://localhost:$ETCD_CLIENT_PORT"
    else
        echo -e "  ${RED}状态: 未运行${NC}"
    fi

    echo ""
    echo "Mosquitto MQTT:"
    if check_port "$MQTT_PORT"; then
        echo -e "  ${GREEN}状态: 运行中${NC}"
        echo "  地址: tcp://localhost:$MQTT_PORT"
    else
        echo -e "  ${YELLOW}状态: 未运行${NC}"
    fi
}

echo "=========================================="
echo "多语言通信框架 - 原生环境启动"
echo "=========================================="

case "$COMPONENT" in
    etcd)      start_etcd ;;
    mosquitto) start_mosquitto ;;
    status)    show_status ;;
    all)
        etcd_ok=0
        mqtt_ok=0
        start_etcd && etcd_ok=1
        start_mosquitto && mqtt_ok=1

        echo ""
        echo -e "${CYAN}=== 启动完成 ===${NC}"
        echo ""
        echo "服务地址:"
        [ $etcd_ok -eq 1 ] && echo "  - etcd: http://localhost:$ETCD_CLIENT_PORT"
        [ $mqtt_ok -eq 1 ] && echo "  - MQTT: tcp://localhost:$MQTT_PORT"
        echo ""
        echo "初始化 etcd:"
        echo "  ./scripts/etcd-init.sh"
        echo ""
        echo "停止服务:"
        echo "  ./scripts/stop-native.sh"
        ;;
    *)
        echo "用法: $0 [all|etcd|mosquitto|status]"
        exit 1
        ;;
esac
