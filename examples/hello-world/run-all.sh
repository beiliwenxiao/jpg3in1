#!/bin/bash
# Hello World - 三语言互调示例 (Linux)

echo "========================================"
echo "  Hello World - 三语言互调示例 (Linux)"
echo "========================================"
echo ""
echo "本示例将依次启动三个 JSON-RPC 服务："
echo "  Java -> 端口 8091"
echo "  PHP  -> 端口 8092  (Webman + Workerman，多进程)"
echo "  Go   -> 端口 8093  (GoFrame)"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- 端口占用检查 ----
PORTS_BUSY=0
for PORT in 8091 8092 8093; do
    PID=$(lsof -ti :$PORT 2>/dev/null || ss -tlnp 2>/dev/null | grep ":$PORT " | grep -oP 'pid=\K\d+')
    if [ -n "$PID" ]; then
        PNAME=$(ps -p $PID -o comm= 2>/dev/null || echo "未知")
        echo "[警告] 端口 $PORT 已被占用：PID=$PID  进程=$PNAME"
        PORTS_BUSY=1
    fi
done

if [ "$PORTS_BUSY" = "1" ]; then
    echo ""
    read -p "是否终止占用端口的进程？(y/n，回车跳过): " KILL_CHOICE
    if [ "$KILL_CHOICE" = "y" ] || [ "$KILL_CHOICE" = "Y" ]; then
        for PORT in 8091 8092 8093; do
            PID=$(lsof -ti :$PORT 2>/dev/null || ss -tlnp 2>/dev/null | grep ":$PORT " | grep -oP 'pid=\K\d+')
            if [ -n "$PID" ]; then
                echo "正在终止 PID $PID ..."
                kill -9 $PID 2>/dev/null
            fi
        done
        sleep 2
        echo "已清理。"
        echo ""
    else
        echo "跳过，继续启动（可能会因端口冲突失败）..."
        echo ""
    fi
fi

# ---- 清理函数 ----
cleanup() {
    echo ""
    echo "正在关闭所有服务..."
    [ -n "$JAVA_PID" ] && kill "$JAVA_PID" 2>/dev/null
    [ -n "$GO_PID" ]   && kill "$GO_PID"   2>/dev/null
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

# ---- 第二步：启动 Java 服务（后台）----
echo "[2/4] 启动 Java 服务（端口 8091）..."
cd "$SCRIPT_DIR/java"
java -jar target/hello-world.jar &
JAVA_PID=$!
sleep 1

# ---- 第三步：编译并启动 Go 服务（后台）----
echo "[3/4] 编译并启动 Go 服务（端口 8093）..."
cd "$SCRIPT_DIR/golang"
export GOPROXY=https://goproxy.cn,direct
go build -o hello-world . || { echo "[错误] Go 编译失败"; exit 1; }
./hello-world &
GO_PID=$!
sleep 1

# ---- 第四步：启动 PHP/Webman 服务（前台，多进程）----
echo "[4/4] 启动 PHP/Webman 服务（端口 8092）..."
echo ""
echo "浏览器访问："
echo "  http://localhost:8091  (Java)"
echo "  http://localhost:8092  (PHP - Webman)"
echo "  http://localhost:8093  (Go - GoFrame)"
echo ""
cd "$SCRIPT_DIR/php"
php start.php start

wait
