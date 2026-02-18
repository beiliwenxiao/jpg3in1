# etcd 健康检查脚本 (Windows PowerShell)
# 用于检查 etcd 服务注册中心的健康状态

param(
    [string]$EtcdEndpoint = "http://localhost:2379",
    [string]$EtcdContainer = "framework-etcd",
    [string]$FrameworkNamespace = "/framework",
    [string]$Mode = "auto"  # auto, docker, native
)

# 颜色函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "etcd 服务注册中心健康检查" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 自动检测运行模式
$isDocker = $false
$isNative = $false

if ($Mode -eq "auto") {
    # 检查 Docker 容器是否存在
    try {
        $containerStatus = docker ps --filter "name=$EtcdContainer" --filter "status=running" --format "{{.Names}}" 2>&1
        if ($containerStatus -match $EtcdContainer) {
            $isDocker = $true
            $Mode = "docker"
        }
    } catch {
        # Docker 不可用,尝试原生模式
    }
    
    # 检查原生进程
    if (-not $isDocker) {
        $etcdProcess = Get-Process -Name "etcd" -ErrorAction SilentlyContinue
        if ($etcdProcess) {
            $isNative = $true
            $Mode = "native"
        }
    }
} elseif ($Mode -eq "docker") {
    $isDocker = $true
} elseif ($Mode -eq "native") {
    $isNative = $true
}

Write-Host "检测到的运行模式: $Mode" -ForegroundColor Blue
Write-Host ""

# Docker 模式健康检查
if ($Mode -eq "docker") {

# Docker 模式健康检查
if ($Mode -eq "docker") {
    # 检查 Docker 是否运行
    Write-Host "检查 Docker 服务状态... " -NoNewline
    try {
        $dockerInfo = docker info 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Docker 服务未运行"
        }
        Write-ColorOutput "正常" "Green"
    } catch {
        Write-ColorOutput "失败" "Red"
        Write-Host "错误: Docker 服务未运行"
        exit 1
    }

    # 检查 etcd 容器是否运行
    Write-Host "检查 etcd 容器状态... " -NoNewline
    $containerStatus = docker ps --filter "name=$EtcdContainer" --filter "status=running" --format "{{.Names}}" 2>&1
    if ($containerStatus -notmatch $EtcdContainer) {
        Write-ColorOutput "失败" "Red"
        Write-Host "错误: etcd 容器未运行"
        Write-Host "提示: 运行 '.\scripts\start-etcd.ps1' 启动 etcd"
        exit 1
    }
    Write-ColorOutput "运行中" "Green"

    # 检查 etcd 端点健康状态
    Write-Host "检查 etcd 端点健康状态... " -NoNewline
    $healthCheck = docker exec $EtcdContainer etcdctl endpoint health 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "健康" "Green"
    } else {
        Write-ColorOutput "不健康" "Red"
        Write-Host "错误: etcd 端点健康检查失败"
        exit 1
    }

    # 检查 etcd 集群状态
    Write-Host "检查 etcd 集群状态... " -NoNewline
    $clusterStatus = docker exec $EtcdContainer etcdctl endpoint status --write-out=json 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "正常" "Green"
        Write-Host "集群信息:"
        try {
            $statusJson = $clusterStatus | ConvertFrom-Json
            foreach ($endpoint in $statusJson) {
                $ep = $endpoint.Endpoint
                $leader = $endpoint.Status.leader
                $version = $endpoint.Status.version
                Write-Host "  - 端点: $ep, Leader: $leader, 版本: $version"
            }
        } catch {
            Write-Host "  (无法解析集群状态详情)"
        }
    } else {
        Write-ColorOutput "警告" "Yellow"
        Write-Host "警告: 无法获取集群状态详情"
    }

    # 检查命名空间
    Write-Host "检查框架命名空间... " -NoNewline
    $namespaceCheck = docker exec $EtcdContainer etcdctl get $FrameworkNamespace --prefix --keys-only --limit=1 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "存在" "Green"
        
        # 统计服务数量
        $serviceKeys = docker exec $EtcdContainer etcdctl get "$FrameworkNamespace/services" --prefix --keys-only 2>&1
        if ($LASTEXITCODE -eq 0 -and $serviceKeys) {
            $serviceCount = ($serviceKeys -split "`n" | Where-Object { $_ -match "/services/" }).Count
            Write-Host "  - 已注册服务数量: $serviceCount"
            
            # 列出已注册的服务
            if ($serviceCount -gt 0) {
                Write-Host "  - 已注册服务列表:"
                $services = $serviceKeys -split "`n" | Where-Object { $_ -match "/services/" } | ForEach-Object {
                    $_ -replace ".*$FrameworkNamespace/services/", "" -replace "/.*", ""
                } | Select-Object -Unique | Sort-Object
                foreach ($service in $services) {
                    if ($service) {
                        Write-Host "    * $service"
                    }
                }
            }
        } else {
            Write-Host "  - 已注册服务数量: 0"
        }
    } else {
        Write-ColorOutput "不存在" "Yellow"
        Write-Host "提示: 命名空间将在首次服务注册时自动创建"
    }

    # 检查 etcd 性能指标
    Write-Host ""
    Write-Host "性能指标:"
    $metrics = docker exec $EtcdContainer etcdctl endpoint status --write-out=json 2>&1
    if ($LASTEXITCODE -eq 0) {
        try {
            $metricsJson = $metrics | ConvertFrom-Json
            foreach ($metric in $metricsJson) {
                $dbSize = $metric.Status.dbSize
                $raftIndex = $metric.Status.raftIndex
                Write-Host "  - DB 大小: $dbSize bytes, Raft 索引: $raftIndex"
            }
        } catch {
            Write-Host "  (无法解析性能指标)"
        }
    }

    # 检查 etcd 日志（最后5行）
    Write-Host ""
    Write-Host "最近日志 (最后5行):"
    $logs = docker logs $EtcdContainer --tail 5 2>&1
    $logs -split "`n" | ForEach-Object { Write-Host "  $_" }

# 原生模式健康检查
} elseif ($Mode -eq "native") {
    # 检查 etcd 进程是否运行
    Write-Host "检查 etcd 进程状态... " -NoNewline
    $etcdProcess = Get-Process -Name "etcd" -ErrorAction SilentlyContinue
    if (-not $etcdProcess) {
        Write-ColorOutput "未运行" "Red"
        Write-Host "错误: etcd 进程未运行"
        Write-Host "提示: 运行 '.\scripts\start-etcd.ps1 -Mode native' 启动 etcd"
        exit 1
    }
    Write-ColorOutput "运行中 (PID: $($etcdProcess.Id))" "Green"

    # 检查 etcdctl 是否可用
    $etcdctlPath = Get-Command etcdctl -ErrorAction SilentlyContinue
    if (-not $etcdctlPath) {
        Write-ColorOutput "警告: etcdctl 未找到,跳过详细检查" "Yellow"
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Cyan
        Write-ColorOutput "基本健康检查完成 (etcd 进程运行中)" "Green"
        Write-Host "==========================================" -ForegroundColor Cyan
        exit 0
    }

    # 设置 etcdctl 环境变量
    $env:ETCDCTL_API = "3"

    # 检查 etcd 端点健康状态
    Write-Host "检查 etcd 端点健康状态... " -NoNewline
    $healthCheck = etcdctl --endpoints=$EtcdEndpoint endpoint health 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "健康" "Green"
    } else {
        Write-ColorOutput "不健康" "Red"
        Write-Host "错误: etcd 端点健康检查失败"
        Write-Host "输出: $healthCheck"
        exit 1
    }

    # 检查 etcd 集群状态
    Write-Host "检查 etcd 集群状态... " -NoNewline
    $clusterStatus = etcdctl --endpoints=$EtcdEndpoint endpoint status --write-out=json 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "正常" "Green"
        Write-Host "集群信息:"
        try {
            $statusJson = $clusterStatus | ConvertFrom-Json
            foreach ($endpoint in $statusJson) {
                $ep = $endpoint.Endpoint
                $leader = $endpoint.Status.leader
                $version = $endpoint.Status.version
                Write-Host "  - 端点: $ep, Leader: $leader, 版本: $version"
            }
        } catch {
            Write-Host "  (无法解析集群状态详情)"
        }
    } else {
        Write-ColorOutput "警告" "Yellow"
        Write-Host "警告: 无法获取集群状态详情"
    }

    # 检查命名空间
    Write-Host "检查框架命名空间... " -NoNewline
    $namespaceCheck = etcdctl --endpoints=$EtcdEndpoint get $FrameworkNamespace --prefix --keys-only --limit=1 2>&1
    if ($LASTEXITCODE -eq 0 -and $namespaceCheck) {
        Write-ColorOutput "存在" "Green"
        
        # 统计服务数量
        $serviceKeys = etcdctl --endpoints=$EtcdEndpoint get "$FrameworkNamespace/services" --prefix --keys-only 2>&1
        if ($LASTEXITCODE -eq 0 -and $serviceKeys) {
            $serviceCount = ($serviceKeys -split "`n" | Where-Object { $_ -match "/services/" }).Count
            Write-Host "  - 已注册服务数量: $serviceCount"
            
            # 列出已注册的服务
            if ($serviceCount -gt 0) {
                Write-Host "  - 已注册服务列表:"
                $services = $serviceKeys -split "`n" | Where-Object { $_ -match "/services/" } | ForEach-Object {
                    $_ -replace ".*$FrameworkNamespace/services/", "" -replace "/.*", ""
                } | Select-Object -Unique | Sort-Object
                foreach ($service in $services) {
                    if ($service) {
                        Write-Host "    * $service"
                    }
                }
            }
        } else {
            Write-Host "  - 已注册服务数量: 0"
        }
    } else {
        Write-ColorOutput "不存在" "Yellow"
        Write-Host "提示: 命名空间将在首次服务注册时自动创建"
    }

    # 检查 etcd 性能指标
    Write-Host ""
    Write-Host "性能指标:"
    $metrics = etcdctl --endpoints=$EtcdEndpoint endpoint status --write-out=json 2>&1
    if ($LASTEXITCODE -eq 0) {
        try {
            $metricsJson = $metrics | ConvertFrom-Json
            foreach ($metric in $metricsJson) {
                $dbSize = $metric.Status.dbSize
                $raftIndex = $metric.Status.raftIndex
                Write-Host "  - DB 大小: $dbSize bytes, Raft 索引: $raftIndex"
            }
        } catch {
            Write-Host "  (无法解析性能指标)"
        }
    }

} else {
    Write-ColorOutput "错误: 未检测到运行中的 etcd 实例" "Red"
    Write-Host "提示: 运行 '.\scripts\start-etcd.ps1' 或 '.\scripts\start-etcd.ps1 -Mode native' 启动 etcd"
    exit 1
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-ColorOutput "健康检查完成！" "Green"
Write-Host "==========================================" -ForegroundColor Cyan
