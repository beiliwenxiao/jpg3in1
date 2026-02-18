# etcd 启动脚本 (Windows PowerShell)
# 支持 Docker 和原生安装两种方式

param(
    [string]$Mode = "docker",
    [switch]$Detach = $false,
    [switch]$Help = $false
)

# 颜色函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

# 显示帮助信息
function Show-Help {
    Write-Host @"
用法: .\start-etcd.ps1 [选项]

选项:
  -Mode MODE       启动模式: docker (默认) 或 native
  -Detach          后台运行 (仅 Docker 模式)
  -Help            显示此帮助信息

示例:
  .\start-etcd.ps1                    # 使用 Docker 启动 (前台)
  .\start-etcd.ps1 -Detach            # 使用 Docker 启动 (后台)
  .\start-etcd.ps1 -Mode native       # 使用原生方式启动

"@
}

if ($Help) {
    Show-Help
    exit 0
}

# 配置
$EtcdVersion = if ($env:ETCD_VERSION) { $env:ETCD_VERSION } else { "v3.5.11" }
$EtcdDataDir = if ($env:ETCD_DATA_DIR) { $env:ETCD_DATA_DIR } else { ".\data\etcd" }
$EtcdPort = if ($env:ETCD_PORT) { $env:ETCD_PORT } else { "2379" }
$EtcdPeerPort = if ($env:ETCD_PEER_PORT) { $env:ETCD_PEER_PORT } else { "2380" }
$FrameworkNamespace = if ($env:FRAMEWORK_NAMESPACE) { $env:FRAMEWORK_NAMESPACE } else { "/framework" }
$ServiceTTL = if ($env:SERVICE_TTL) { $env:SERVICE_TTL } else { "30" }  # 服务注册 TTL (秒)

Write-Host "==========================================" -ForegroundColor Blue
Write-Host "etcd 服务注册中心启动脚本" -ForegroundColor Blue
Write-Host "==========================================" -ForegroundColor Blue
Write-Host ""

# Docker 模式
if ($Mode -eq "docker") {
    Write-Host "启动模式: Docker" -ForegroundColor Blue
    Write-Host ""
    
    # 检查 Docker 是否安装
    try {
        $dockerVersion = docker --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Docker 未安装"
        }
    } catch {
        Write-ColorOutput "错误: Docker 未安装" "Red"
        Write-Host "请先安装 Docker Desktop: https://docs.docker.com/desktop/install/windows-install/"
        exit 1
    }
    
    # 检查 Docker 是否运行
    try {
        $dockerInfo = docker info 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "Docker 服务未运行"
        }
    } catch {
        Write-ColorOutput "错误: Docker 服务未运行" "Red"
        Write-Host "请启动 Docker Desktop"
        exit 1
    }
    
    # 检查 docker-compose.yml 是否存在
    if (-not (Test-Path "docker-compose.yml")) {
        Write-ColorOutput "错误: docker-compose.yml 文件不存在" "Red"
        exit 1
    }
    
    # 启动 etcd
    Write-Host "正在启动 etcd 容器..."
    
    $composeArgs = @("up")
    if ($Detach) {
        $composeArgs += "-d"
    }
    $composeArgs += "etcd"
    
    # 尝试使用 docker compose (新版本)
    $composeCommand = "docker compose"
    $testCompose = docker compose version 2>&1
    if ($LASTEXITCODE -ne 0) {
        # 回退到 docker-compose (旧版本)
        $composeCommand = "docker-compose"
        $testCompose = docker-compose --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-ColorOutput "错误: Docker Compose 未安装" "Red"
            Write-Host "请先安装 Docker Compose: https://docs.docker.com/compose/install/"
            exit 1
        }
    }
    
    if ($composeCommand -eq "docker compose") {
        & docker compose $composeArgs
    } else {
        & docker-compose $composeArgs
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-ColorOutput "✓ etcd 启动成功！" "Green"
        Write-Host ""
        Write-Host "访问信息:"
        Write-Host "  - 客户端端点: http://localhost:$EtcdPort"
        Write-Host "  - 对等端点: http://localhost:$EtcdPeerPort"
        Write-Host "  - 框架命名空间: $FrameworkNamespace"
        Write-Host "  - 服务 TTL: $ServiceTTL 秒"
        Write-Host ""
        Write-Host "常用命令:"
        Write-Host "  - 查看日志: docker logs framework-etcd -f"
        Write-Host "  - 停止服务: docker-compose stop etcd"
        Write-Host "  - 健康检查: .\scripts\health-check-etcd.ps1"
        Write-Host ""
        Write-Host "配置说明:"
        Write-Host "  - 命名空间用于隔离不同应用的服务注册数据"
        Write-Host "  - TTL 定义服务注册的有效期,服务需要定期续约"
    } else {
        Write-ColorOutput "✗ etcd 启动失败" "Red"
        exit 1
    }
}
# 原生模式
elseif ($Mode -eq "native") {
    Write-Host "启动模式: 原生安装" -ForegroundColor Blue
    Write-Host ""
    
    # 检查 etcd 是否安装
    $etcdPath = Get-Command etcd -ErrorAction SilentlyContinue
    
    if (-not $etcdPath) {
        Write-ColorOutput "警告: etcd 未安装" "Yellow"
        Write-Host ""
        Write-Host "正在下载 etcd $EtcdVersion..."
        
        # Windows 下载 URL
        $EtcdDownloadUrl = "https://github.com/etcd-io/etcd/releases/download/$EtcdVersion/etcd-$EtcdVersion-windows-amd64.zip"
        $EtcdDownloadDir = "$env:TEMP\etcd-download"
        $EtcdZipFile = "$EtcdDownloadDir\etcd.zip"
        
        # 创建下载目录
        New-Item -ItemType Directory -Force -Path $EtcdDownloadDir | Out-Null
        
        Write-Host "下载地址: $EtcdDownloadUrl"
        try {
            # 下载 etcd
            Invoke-WebRequest -Uri $EtcdDownloadUrl -OutFile $EtcdZipFile -UseBasicParsing
            
            Write-Host "正在解压..."
            Expand-Archive -Path $EtcdZipFile -DestinationPath $EtcdDownloadDir -Force
            
            # 查找解压后的目录
            $extractedDir = Get-ChildItem -Path $EtcdDownloadDir -Directory | Where-Object { $_.Name -like "etcd-*" } | Select-Object -First 1
            
            if ($extractedDir) {
                # 安装到 Program Files
                $installDir = "$env:ProgramFiles\etcd"
                Write-Host "正在安装到 $installDir..."
                
                New-Item -ItemType Directory -Force -Path $installDir | Out-Null
                Copy-Item -Path "$($extractedDir.FullName)\etcd.exe" -Destination $installDir -Force
                Copy-Item -Path "$($extractedDir.FullName)\etcdctl.exe" -Destination $installDir -Force
                
                # 添加到 PATH
                $currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
                if ($currentPath -notlike "*$installDir*") {
                    Write-Host "正在添加到系统 PATH..."
                    [Environment]::SetEnvironmentVariable("Path", "$currentPath;$installDir", "Machine")
                    $env:Path = "$env:Path;$installDir"
                }
                
                Write-ColorOutput "✓ etcd 安装成功" "Green"
                Write-Host "注意: 可能需要重新打开 PowerShell 窗口以使 PATH 生效"
            } else {
                throw "无法找到解压后的 etcd 目录"
            }
        } catch {
            Write-ColorOutput "✗ etcd 下载/安装失败" "Red"
            Write-Host "错误: $_"
            Write-Host "请手动下载并安装: https://github.com/etcd-io/etcd/releases"
            exit 1
        }
        
        # 清理下载文件
        Remove-Item -Path $EtcdDownloadDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    
    # 创建数据目录
    New-Item -ItemType Directory -Force -Path $EtcdDataDir | Out-Null
    
    # 启动 etcd
    Write-Host "正在启动 etcd..."
    Write-Host ""
    Write-Host "配置信息:"
    Write-Host "  - 数据目录: $EtcdDataDir"
    Write-Host "  - 客户端端口: $EtcdPort"
    Write-Host "  - 对等端口: $EtcdPeerPort"
    Write-Host "  - 框架命名空间: $FrameworkNamespace"
    Write-Host "  - 服务 TTL: $ServiceTTL 秒"
    Write-Host ""
    
    $etcdArgs = @(
        "--name", "etcd0",
        "--data-dir", $EtcdDataDir,
        "--listen-client-urls", "http://0.0.0.0:$EtcdPort",
        "--advertise-client-urls", "http://localhost:$EtcdPort",
        "--listen-peer-urls", "http://0.0.0.0:$EtcdPeerPort",
        "--initial-advertise-peer-urls", "http://localhost:$EtcdPeerPort",
        "--initial-cluster", "etcd0=http://localhost:$EtcdPeerPort",
        "--initial-cluster-token", "etcd-cluster",
        "--initial-cluster-state", "new",
        "--log-level", "info",
        "--auto-compaction-mode", "periodic",
        "--auto-compaction-retention", "1h"
    )
    
    Write-ColorOutput "✓ etcd 启动中..." "Green"
    Write-Host ""
    Write-Host "提示:"
    Write-Host "  - 按 Ctrl+C 停止 etcd"
    Write-Host "  - 或在另一个终端运行: .\scripts\stop-etcd.ps1"
    Write-Host "  - 健康检查: .\scripts\health-check-etcd.ps1"
    Write-Host ""
    Write-Host "配置说明:"
    Write-Host "  - 命名空间 ($FrameworkNamespace) 用于隔离不同应用的服务注册数据"
    Write-Host "  - TTL ($ServiceTTL 秒) 定义服务注册的有效期,服务需要定期续约"
    Write-Host ""
    Write-Host "=========================================="
    Write-Host ""
    
    & etcd $etcdArgs
}
else {
    Write-ColorOutput "错误: 未知的启动模式: $Mode" "Red"
    Write-Host "支持的模式: docker, native"
    exit 1
}
