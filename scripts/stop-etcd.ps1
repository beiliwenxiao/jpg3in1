# etcd 停止脚本 (Windows PowerShell)

param(
    [string]$EtcdContainer = "framework-etcd"
)

# 颜色函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

Write-Host "==========================================" -ForegroundColor Blue
Write-Host "停止 etcd 服务注册中心" -ForegroundColor Blue
Write-Host "==========================================" -ForegroundColor Blue
Write-Host ""

# 检查是否使用 Docker
$containerExists = docker ps -a --filter "name=$EtcdContainer" --format "{{.Names}}" 2>&1
if ($containerExists -match $EtcdContainer) {
    Write-Host "检测到 Docker 容器，正在停止..."
    
    # 尝试使用 docker compose (新版本)
    $composeCommand = "docker compose"
    $testCompose = docker compose version 2>&1
    if ($LASTEXITCODE -ne 0) {
        $composeCommand = "docker-compose"
    }
    
    if ($composeCommand -eq "docker compose") {
        docker compose stop etcd
    } else {
        docker-compose stop etcd
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ etcd 容器已停止" "Green"
    } else {
        Write-ColorOutput "✗ 停止失败" "Red"
        exit 1
    }
} else {
    # 原生安装模式
    Write-Host "正在查找 etcd 进程..."
    $etcdProcess = Get-Process -Name "etcd" -ErrorAction SilentlyContinue
    
    if (-not $etcdProcess) {
        Write-ColorOutput "未找到运行中的 etcd 进程" "Yellow"
        exit 0
    }
    
    Write-Host "找到 etcd 进程 (PID: $($etcdProcess.Id))，正在停止..."
    
    try {
        Stop-Process -Id $etcdProcess.Id -Force
        Write-ColorOutput "✓ etcd 进程已停止" "Green"
    } catch {
        Write-ColorOutput "✗ 停止失败: $_" "Red"
        exit 1
    }
}
