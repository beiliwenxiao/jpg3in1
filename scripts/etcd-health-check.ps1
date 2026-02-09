# etcd 健康检查脚本 (PowerShell 版本)
# 用于检查 etcd 集群的健康状态

param(
    [string]$EtcdEndpoints = "http://localhost:2379",
    [string]$EtcdNamespace = "/framework",
    [int]$Timeout = 5
)

$ErrorActionPreference = "Stop"

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

Write-Host "=========================================="
Write-Host "etcd 健康检查"
Write-Host "=========================================="
Write-Host "端点: $EtcdEndpoints"
Write-Host "命名空间: $EtcdNamespace"
Write-Host "超时: ${Timeout}s"
Write-Host ""

try {
    # 检查 etcdctl 是否可用
    $etcdctlPath = Get-Command etcdctl -ErrorAction SilentlyContinue
    if (-not $etcdctlPath) {
        Write-ColorOutput "✗ 未找到 etcdctl 命令，请确保已安装 etcd 客户端" "Red"
        Write-Host ""
        Write-Host "安装说明:"
        Write-Host "  1. 下载 etcd: https://github.com/etcd-io/etcd/releases"
        Write-Host "  2. 解压并将 etcdctl.exe 添加到 PATH 环境变量"
        exit 1
    }

    # 检查 etcd 是否可访问
    Write-Host "1. 检查 etcd 端点健康状态..."
    $healthOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" endpoint health 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ etcd 端点健康" "Green"
    } else {
        Write-ColorOutput "✗ etcd 端点不健康" "Red"
        Write-Host $healthOutput
        exit 1
    }

    Write-Host ""

    # 检查 etcd 端点状态
    Write-Host "2. 检查 etcd 端点状态..."
    $statusOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" endpoint status --write-out=table 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host $statusOutput
        Write-ColorOutput "✓ etcd 端点状态正常" "Green"
    } else {
        Write-ColorOutput "✗ 无法获取 etcd 端点状态" "Red"
        Write-Host $statusOutput
        exit 1
    }

    Write-Host ""

    # 检查集群成员
    Write-Host "3. 检查 etcd 集群成员..."
    $memberOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" member list --write-out=table 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host $memberOutput
        Write-ColorOutput "✓ etcd 集群成员列表正常" "Green"
    } else {
        Write-ColorOutput "✗ 无法获取 etcd 集群成员列表" "Red"
        Write-Host $memberOutput
        exit 1
    }

    Write-Host ""

    # 测试读写操作
    Write-Host "4. 测试 etcd 读写操作..."
    $testKey = "$EtcdNamespace/health-check/test"
    $testValue = "health-check-$(Get-Date -Format 'yyyyMMddHHmmss')"

    # 写入测试数据
    $putOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put $testKey $testValue 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ 写入测试数据成功" "Green"
    } else {
        Write-ColorOutput "✗ 写入测试数据失败" "Red"
        Write-Host $putOutput
        exit 1
    }

    # 读取测试数据
    $readValue = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get $testKey --print-value-only 2>&1
    if ($LASTEXITCODE -eq 0 -and $readValue -eq $testValue) {
        Write-ColorOutput "✓ 读取测试数据成功" "Green"
    } else {
        Write-ColorOutput "✗ 读取测试数据失败 (期望: $testValue, 实际: $readValue)" "Red"
        exit 1
    }

    # 删除测试数据
    $delOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" del $testKey 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ 删除测试数据成功" "Green"
    } else {
        Write-ColorOutput "⚠ 删除测试数据失败（非致命错误）" "Yellow"
    }

    Write-Host ""

    # 检查命名空间下的服务
    Write-Host "5. 检查已注册的服务..."
    $servicePrefix = "$EtcdNamespace/services/"
    $servicesOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get $servicePrefix --prefix --keys-only 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        $serviceKeys = $servicesOutput | Where-Object { $_ -match "^$servicePrefix" }
        $serviceCount = ($serviceKeys | Measure-Object).Count

        if ($serviceCount -gt 0) {
            Write-ColorOutput "✓ 发现 $serviceCount 个已注册的服务" "Green"
            Write-Host ""
            Write-Host "已注册的服务列表:"
            foreach ($key in $serviceKeys) {
                Write-Host "  - $key"
            }
        } else {
            Write-ColorOutput "⚠ 未发现已注册的服务" "Yellow"
        }
    } else {
        Write-ColorOutput "⚠ 无法查询已注册的服务" "Yellow"
    }

    Write-Host ""
    Write-Host "=========================================="
    Write-ColorOutput "✓ etcd 健康检查完成" "Green"
    Write-Host "=========================================="

    exit 0

} catch {
    Write-ColorOutput "✗ 健康检查过程中发生错误: $_" "Red"
    exit 1
}
