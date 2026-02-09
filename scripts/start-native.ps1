# 原生环境启动脚本 (PowerShell)
# 不依赖 Docker，直接使用本地安装的 etcd、mosquitto 等服务

param(
    [ValidateSet('all', 'etcd', 'mosquitto', 'status')]
    [string]$Component = 'all',
    [string]$EtcdDataDir = "$env:USERPROFILE\.framework\etcd-data",
    [string]$MosquittoConfigDir = "$env:USERPROFILE\.framework\mosquitto",
    [int]$EtcdClientPort = 2379,
    [int]$EtcdPeerPort = 2380,
    [int]$MqttPort = 1883,
    [switch]$Background
)

$ErrorActionPreference = "Stop"

function Write-Status {
    param([string]$Message, [string]$Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

function Test-PortInUse {
    param([int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $conn
}

function Start-Etcd {
    Write-Host ""
    Write-Status "--- 启动 etcd ---" "Cyan"

    # 检查 etcd 是否已安装
    $etcdPath = Get-Command etcd -ErrorAction SilentlyContinue
    if (-not $etcdPath) {
        Write-Status "✗ 未找到 etcd，请先安装" "Red"
        Write-Host ""
        Write-Host "安装方式:"
        Write-Host "  1. 从 https://github.com/etcd-io/etcd/releases 下载"
        Write-Host "  2. 使用 scoop: scoop install etcd"
        Write-Host "  3. 使用 chocolatey: choco install etcd"
        return $false
    }

    # 检查端口是否被占用
    if (Test-PortInUse $EtcdClientPort) {
        Write-Status "⚠ 端口 $EtcdClientPort 已被占用，etcd 可能已在运行" "Yellow"
        # 验证是否是 etcd
        try {
            $health = & etcdctl --endpoints="http://localhost:$EtcdClientPort" endpoint health 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Status "✓ etcd 已在运行中" "Green"
                return $true
            }
        } catch {}
        Write-Status "✗ 端口 $EtcdClientPort 被其他进程占用" "Red"
        return $false
    }

    # 创建数据目录
    if (-not (Test-Path $EtcdDataDir)) {
        New-Item -ItemType Directory -Force -Path $EtcdDataDir | Out-Null
        Write-Status "✓ 创建数据目录: $EtcdDataDir" "Green"
    }

    # 启动 etcd
    $etcdArgs = @(
        "--name", "framework-etcd",
        "--data-dir", $EtcdDataDir,
        "--listen-client-urls", "http://0.0.0.0:$EtcdClientPort",
        "--advertise-client-urls", "http://localhost:$EtcdClientPort",
        "--listen-peer-urls", "http://0.0.0.0:$EtcdPeerPort",
        "--initial-advertise-peer-urls", "http://localhost:$EtcdPeerPort",
        "--initial-cluster", "framework-etcd=http://localhost:$EtcdPeerPort",
        "--initial-cluster-token", "framework-cluster",
        "--initial-cluster-state", "new",
        "--auto-compaction-mode", "periodic",
        "--auto-compaction-retention", "1h",
        "--log-level", "info"
    )

    if ($Background) {
        $process = Start-Process -FilePath "etcd" -ArgumentList $etcdArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput "$EtcdDataDir\etcd.log" -RedirectStandardError "$EtcdDataDir\etcd-error.log"
        $process.Id | Out-File "$EtcdDataDir\etcd.pid" -Force
        Write-Status "✓ etcd 已在后台启动 (PID: $($process.Id))" "Green"
    } else {
        $process = Start-Process -FilePath "etcd" -ArgumentList $etcdArgs -PassThru -WindowStyle Minimized
        $process.Id | Out-File "$EtcdDataDir\etcd.pid" -Force
        Write-Status "✓ etcd 已启动 (PID: $($process.Id))" "Green"
    }

    # 等待 etcd 就绪
    Write-Host "等待 etcd 就绪..."
    $maxRetries = 15
    for ($i = 0; $i -lt $maxRetries; $i++) {
        Start-Sleep -Seconds 1
        try {
            $health = & etcdctl --endpoints="http://localhost:$EtcdClientPort" endpoint health 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Status "✓ etcd 已就绪" "Green"
                return $true
            }
        } catch {}
        Write-Host "  等待中... ($($i+1)/$maxRetries)"
    }

    Write-Status "✗ etcd 启动超时" "Red"
    return $false
}

function Start-Mosquitto {
    Write-Host ""
    Write-Status "--- 启动 Mosquitto MQTT ---" "Cyan"

    $mosquittoPath = Get-Command mosquitto -ErrorAction SilentlyContinue
    if (-not $mosquittoPath) {
        Write-Status "⚠ 未找到 mosquitto，跳过 MQTT 服务" "Yellow"
        Write-Host "  安装方式: https://mosquitto.org/download/"
        Write-Host "  或使用 scoop: scoop install mosquitto"
        return $false
    }

    if (Test-PortInUse $MqttPort) {
        Write-Status "⚠ 端口 $MqttPort 已被占用，Mosquitto 可能已在运行" "Yellow"
        return $true
    }

    # 创建配置目录
    if (-not (Test-Path $MosquittoConfigDir)) {
        New-Item -ItemType Directory -Force -Path $MosquittoConfigDir | Out-Null
    }

    # 生成配置文件
    $mosquittoConf = @"
listener $MqttPort
allow_anonymous true
persistence true
persistence_location $MosquittoConfigDir\data\
log_dest file $MosquittoConfigDir\mosquitto.log
"@
    $mosquittoConf | Out-File "$MosquittoConfigDir\mosquitto.conf" -Encoding UTF8 -Force
    if (-not (Test-Path "$MosquittoConfigDir\data")) {
        New-Item -ItemType Directory -Force -Path "$MosquittoConfigDir\data" | Out-Null
    }

    $process = Start-Process -FilePath "mosquitto" -ArgumentList "-c", "$MosquittoConfigDir\mosquitto.conf" -PassThru -WindowStyle Hidden
    $process.Id | Out-File "$MosquittoConfigDir\mosquitto.pid" -Force
    Write-Status "✓ Mosquitto 已启动 (PID: $($process.Id))" "Green"
    return $true
}

function Show-Status {
    Write-Host ""
    Write-Status "=== 服务状态 ===" "Cyan"

    # etcd
    Write-Host ""
    Write-Host "etcd:"
    try {
        $health = & etcdctl --endpoints="http://localhost:$EtcdClientPort" endpoint health 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Status "  状态: 运行中" "Green"
            Write-Host "  地址: http://localhost:$EtcdClientPort"
        } else {
            Write-Status "  状态: 未运行" "Red"
        }
    } catch {
        Write-Status "  状态: 未运行" "Red"
    }

    # Mosquitto
    Write-Host ""
    Write-Host "Mosquitto MQTT:"
    if (Test-PortInUse $MqttPort) {
        Write-Status "  状态: 运行中" "Green"
        Write-Host "  地址: tcp://localhost:$MqttPort"
    } else {
        Write-Status "  状态: 未运行" "Yellow"
    }
}

# 主逻辑
Write-Host "=========================================="
Write-Host "多语言通信框架 - 原生环境启动"
Write-Host "=========================================="

switch ($Component) {
    'etcd' { Start-Etcd }
    'mosquitto' { Start-Mosquitto }
    'status' { Show-Status }
    'all' {
        $etcdOk = Start-Etcd
        $mqttOk = Start-Mosquitto

        Write-Host ""
        Write-Status "=== 启动完成 ===" "Cyan"
        Write-Host ""
        Write-Host "服务地址:"
        if ($etcdOk) { Write-Host "  - etcd: http://localhost:$EtcdClientPort" }
        if ($mqttOk) { Write-Host "  - MQTT: tcp://localhost:$MqttPort" }
        Write-Host ""
        Write-Host "初始化 etcd:"
        Write-Host "  .\scripts\etcd-init.ps1"
        Write-Host ""
        Write-Host "停止服务:"
        Write-Host "  .\scripts\stop-native.ps1"
    }
}
