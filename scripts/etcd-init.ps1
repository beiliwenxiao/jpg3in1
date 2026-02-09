# etcd 初始化脚本 (PowerShell 版本)
# 用于初始化服务注册中心的命名空间和配置

param(
    [string]$EtcdEndpoints = "http://localhost:2379",
    [string]$ConfigFile = "docker/etcd/etcd.conf.yml",
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
Write-Host "etcd 服务注册中心初始化"
Write-Host "=========================================="
Write-Host "端点: $EtcdEndpoints"
Write-Host "配置文件: $ConfigFile"
Write-Host ""

try {
    # 检查 etcdctl 是否可用
    $etcdctlPath = Get-Command etcdctl -ErrorAction SilentlyContinue
    if (-not $etcdctlPath) {
        Write-ColorOutput "✗ 未找到 etcdctl 命令，请确保已安装 etcd 客户端" "Red"
        exit 1
    }

    # 等待 etcd 就绪
    Write-Host "等待 etcd 就绪..."
    $maxRetries = 30
    $retryCount = 0

    while ($retryCount -lt $maxRetries) {
        $healthCheck = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" endpoint health 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-ColorOutput "✓ etcd 已就绪" "Green"
            break
        }
        
        $retryCount++
        Write-Host "等待 etcd 启动... ($retryCount/$maxRetries)"
        Start-Sleep -Seconds 2
    }

    if ($retryCount -eq $maxRetries) {
        Write-ColorOutput "✗ etcd 启动超时" "Red"
        exit 1
    }

    Write-Host ""

    # 创建命名空间
    Write-Host "1. 创建命名空间..."

    $namespaces = @(
        "/framework",
        "/framework/services",
        "/framework/config",
        "/framework/locks",
        "/framework/health"
    )

    foreach ($namespace in $namespaces) {
        $key = "$namespace/.namespace"
        $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        $value = "created_at=$timestamp"
        
        $output = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put $key $value 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-ColorOutput "✓ 创建命名空间: $namespace" "Green"
        } else {
            Write-ColorOutput "✗ 创建命名空间失败: $namespace" "Red"
            Write-Host $output
            exit 1
        }
    }

    Write-Host ""

    # 设置框架配置
    Write-Host "2. 设置框架配置..."

    # TTL 配置
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/ttl/service_registration" "30" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/ttl/heartbeat_interval" "10" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/ttl/discovery_cache" "60" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/ttl/config_cache" "300" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/ttl/lock" "60" | Out-Null
    Write-ColorOutput "✓ TTL 配置已设置" "Green"

    # 服务注册配置
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/service_registration/auto_register" "true" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/service_registration/auto_deregister" "true" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/service_registration/health_check_enabled" "true" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/service_registration/health_check_interval" "10" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/service_registration/health_check_timeout" "5" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/service_registration/health_check_failure_threshold" "3" | Out-Null
    Write-ColorOutput "✓ 服务注册配置已设置" "Green"

    # 负载均衡配置
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/load_balancing/default_strategy" "round_robin" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/load_balancing/health_check_filter" "true" | Out-Null
    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put "/framework/config/load_balancing/version_filter" "false" | Out-Null
    Write-ColorOutput "✓ 负载均衡配置已设置" "Green"

    Write-Host ""

    # 创建示例服务（用于测试）
    Write-Host "3. 创建示例服务元数据..."

    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

    # 示例服务 1: Java 服务
    $serviceKey = "/framework/services/example-java-service/instance-1"
    $serviceValue = @"
{
  "id": "instance-1",
  "name": "example-java-service",
  "version": "1.0.0",
  "language": "java",
  "address": "localhost",
  "port": 8080,
  "protocols": ["grpc", "rest"],
  "metadata": {
    "region": "local",
    "environment": "development"
  },
  "registered_at": "$timestamp"
}
"@

    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put $serviceKey $serviceValue | Out-Null
    Write-ColorOutput "ℹ 创建示例服务: example-java-service" "Cyan"

    # 示例服务 2: Golang 服务
    $serviceKey = "/framework/services/example-golang-service/instance-1"
    $serviceValue = @"
{
  "id": "instance-1",
  "name": "example-golang-service",
  "version": "1.0.0",
  "language": "golang",
  "address": "localhost",
  "port": 8081,
  "protocols": ["grpc", "rest"],
  "metadata": {
    "region": "local",
    "environment": "development"
  },
  "registered_at": "$timestamp"
}
"@

    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put $serviceKey $serviceValue | Out-Null
    Write-ColorOutput "ℹ 创建示例服务: example-golang-service" "Cyan"

    # 示例服务 3: PHP 服务
    $serviceKey = "/framework/services/example-php-service/instance-1"
    $serviceValue = @"
{
  "id": "instance-1",
  "name": "example-php-service",
  "version": "1.0.0",
  "language": "php",
  "address": "localhost",
  "port": 8082,
  "protocols": ["grpc", "rest"],
  "metadata": {
    "region": "local",
    "environment": "development"
  },
  "registered_at": "$timestamp"
}
"@

    & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put $serviceKey $serviceValue | Out-Null
    Write-ColorOutput "ℹ 创建示例服务: example-php-service" "Cyan"

    Write-Host ""

    # 验证配置
    Write-Host "4. 验证配置..."

    # 检查命名空间
    $namespaceOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get "/framework/" --prefix --keys-only 2>&1
    $namespaceCount = ($namespaceOutput | Select-String "/.namespace" | Measure-Object).Count
    Write-ColorOutput "✓ 已创建 $namespaceCount 个命名空间" "Green"

    # 检查配置项
    $configOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get "/framework/config/" --prefix --keys-only 2>&1
    $configCount = ($configOutput | Where-Object { $_ -match "^/framework/config/" } | Measure-Object).Count
    Write-ColorOutput "✓ 已设置 $configCount 个配置项" "Green"

    # 检查服务
    $serviceOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get "/framework/services/" --prefix --keys-only 2>&1
    $serviceCount = ($serviceOutput | Where-Object { $_ -match "^/framework/services/" -and $_ -notmatch "/.namespace" } | Measure-Object).Count
    Write-ColorOutput "✓ 已注册 $serviceCount 个示例服务" "Green"

    Write-Host ""
    Write-Host "=========================================="
    Write-ColorOutput "✓ etcd 服务注册中心初始化完成" "Green"
    Write-Host "=========================================="
    Write-Host ""
    Write-Host "可用命令:"
    Write-Host "  - 查看所有服务: etcdctl --endpoints=$EtcdEndpoints get /framework/services/ --prefix"
    Write-Host "  - 查看配置: etcdctl --endpoints=$EtcdEndpoints get /framework/config/ --prefix"
    Write-Host "  - 健康检查: .\scripts\etcd-health-check.ps1"
    Write-Host ""

    exit 0

} catch {
    Write-ColorOutput "✗ 初始化过程中发生错误: $_" "Red"
    exit 1
}
