#!/bin/bash
# Hello World - 三语言互调示例 (Linux)

echo "========================================"
echo "  Hello World - 三语言互调示例 (Linux)"
echo "========================================"
echo ""
echo "本示例将依次启动三个 JSON-RPC 服务："
echo "  PHP  -> 端口 8091  (Webman + Workerman，多进程)"
echo "  Java -> 端口 8092"
echo "  Go   -> 端口 8093"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 清理函数：退出时关闭后台进程
cleanup() {
    echo ""
    echo "正在关闭所有服务..."
    [ -n "$GO_PID" ]   && kill "$GO_PID"   2>/dev/null
    [ -n "$JAVA_PID" ] && kill "$JAVA_PID" 2>/dev/null
    # Webman 用 stop 命令优雅退出
    php "$SCRIPT_DIR/php/start.php" stop 2>/dev/null
    echo "已关闭。"
}
trap cleanup EXIT INT TERM

# ---- 第一步：构建 Java fat jar ----
echo "[1/4] 构建 Java 示例..."
cd "$SCRIPT_DIR/java"
mvn package -q
if [ $? -ne 0 ]; then
    echo "[错误] Java 构建失败，请确认已安装 Maven 和 Java 17"
    exit 1
fi
echo "[1/4] Java 构建完成。"
echo ""

# ---- 第二步：编译并启动 Go 服务（后台）----
echo "[2/4] 编译并启动 Go 服务（端口 8093）..."
cd "$SCRIPT_DIR/golang"
go build -o hello-world . || { echo "[错误] Go 编译失败"; exit 1; }
./hello-world &
GO_PID=$!
sleep 1

# ---- 第三步：启动 Java 服务（后台）----
echo "[3/4] 启动 Java 服务（端口 8092）..."
cd "$SCRIPT_DIR/java"
java -jar target/hello-world.jar &
JAVA_PID=$!
sleep 1

# ---- 第四步：启动 PHP/Webman 服务（前台，多进程）----
echo "[4/4] 启动 PHP/Webman 服务（端口 8091）..."
echo ""
echo "浏览器访问："
echo "  http://localhost:8091  (PHP - Webman)"
echo "  http://localhost:8092  (Java)"
echo "  http://localhost:8093  (Go)"
echo ""
cd "$SCRIPT_DIR/php"
php start.php start

# 等待所有后台进程
wait
