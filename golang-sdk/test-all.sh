#!/bin/bash
# Golang SDK 完整测试脚本 (Linux)
# 用于检查点任务23

echo "=== Golang SDK 完整测试 ==="
echo ""

ERROR_COUNT=0
WARNING_COUNT=0

# 切换到golang-sdk目录
cd "$(dirname "$0")"

# 1. 检查编译
echo "1. 检查编译..."
if go build ./... 2>&1; then
    echo "✓ 编译通过"
else
    echo "✗ 编译失败"
    ((ERROR_COUNT++))
fi
echo ""

# 2. 运行所有测试
echo "2. 运行所有测试..."

modules=(
    "client"
    "config"
    "connection"
    "errors"
    "resilience"
    "security"
    "serializer"
    "protocol/adapter"
    "protocol/router"
    "protocol/internal/grpc"
    "protocol/internal/jsonrpc"
    "protocol/internal/custom"
    "protocol/external/rest"
    "protocol/external/websocket"
    "protocol/external/jsonrpc"
    "protocol/external/mqtt"
    "registry"
    "observability"
)

for module in "${modules[@]}"; do
    echo "  测试模块: $module"
    if go test "./$module" -v 2>&1 | tee /tmp/test_output.txt; then
        pass_count=$(grep -c "PASS" /tmp/test_output.txt || echo "0")
        skip_count=$(grep -c "SKIP" /tmp/test_output.txt || echo "0")
        echo "    ✓ 测试通过 (通过: $pass_count, 跳过: $skip_count)"
        
        if [ "$skip_count" -gt 0 ]; then
            ((WARNING_COUNT++))
        fi
    else
        echo "    ✗ 测试失败"
        grep -E "FAIL|Error|panic" /tmp/test_output.txt || true
        ((ERROR_COUNT++))
    fi
done
echo ""

# 3. 总结
echo "=== 测试总结 ==="
echo "错误数: $ERROR_COUNT"
echo "警告数: $WARNING_COUNT"
echo ""

if [ $ERROR_COUNT -eq 0 ]; then
    echo "✓ 所有测试通过!"
    exit 0
else
    echo "✗ 发现 $ERROR_COUNT 个错误"
    exit 1
fi
