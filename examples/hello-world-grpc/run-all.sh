#!/bin/bash
# Hello World gRPC - 跨语言示例 (Linux)

echo "========================================"
echo "  Hello World gRPC - 跨语言示例 (Linux)"
echo "========================================"
echo ""
echo "本示例将启动："
echo "  Java  gRPC:9091 HTTP:8091"
echo "  Go    gRPC:9093 HTTP:8093"
echo "  PHP   HTTP:8092 (gRPC 代理客户端)"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- 端口占用检查 ----
PORTS_BUSY=0
for PORT in 8091 8092 8093 9091 9093; do
    PID=$(lsof -ti :$PORT 2>/dev/null || ss -tlnp 2>/dev/null | grep ":$PORT " | grep -oP 'pid=\K\d+')
    if [ -n "$PID" ]; then
        echo "[警告] 端口 $PORT 已被占用：PID=$PID"
        PORTS_BUSY=1
    fi
done

if [ "$PORTS_BUSY" = "1" ]; then
    read -p "是否终止占用端口的进程？(y/n): " KILL_CHOICE
    if [ "$KILL_CHOICE" = "y" ] || [ "$KILL_CHOICE" = "Y" ]; then
        for PORT in 8091 8092 8093 9091 9093; do
            PID=$(lsof -ti :$PORT 2>/dev/null)
            [ -n "$PID" ] && kill -9 $PID 2>/dev/null
        done
        sleep 2
    fi
fi

# ---- 清理函数 ----
cleanup() {
    echo ""
    echo "正在关闭所有服务..."
    [ -n "$JAVA_PID" ] && kill "$JAVA_PID" 2>/dev/null
    [ -n "$GO_PID" ]   && kill "$GO_PID"   2>/dev/null
    [ -n "$PHP_PID" ]  && kill "$PHP_PID"  2>/dev/null
    echo "已关闭。"
}
trap cleanup EXIT INT TERM

# ---- 构建 Java ----
echo "[1/3] 构建 Java gRPC 示例..."
cd "$SCRIPT_DIR/java"
mvn package -q || { echo "[错误] Java 构建失败"; exit 1; }
echo "[1/3] Java 构建完成。"

# ---- 启动 Java ----
echo "[2/3] 启动 Java 服务..."
java -jar target/hello-world-grpc.jar &
JAVA_PID=$!
sleep 2

# ---- 编译并启动 Go ----
echo "[2/3] 编译并启动 Go 服务..."
cd "$SCRIPT_DIR/golang"
go build -o hello-world-grpc . || { echo "[错误] Go 编译失败"; exit 1; }
./hello-world-grpc &
GO_PID=$!
sleep 1

# ---- 启动 PHP ----
echo "[3/3] 启动 PHP gRPC 代理客户端..."
echo ""
echo "浏览器访问："
echo "  http://localhost:8091  (Java - gRPC 服务端)"
echo "  http://localhost:8092  (PHP - gRPC 代理客户端)"
echo "  http://localhost:8093  (Go - gRPC 服务端)"
echo ""
cd "$SCRIPT_DIR/php"
php hello-grpc.php &
PHP_PID=$!

wait
