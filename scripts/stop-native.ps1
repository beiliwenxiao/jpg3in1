# 原生环境停止脚本 (PowerShell)

param(
    [ValidateSet('all', 'etcd', 'mosquitto')]
    [string]$Component = 'all',
    [string]$EtcdDataDir = "$env:USERPROFILE\.framework\etcd-data",
    [string]$MosquittoConfigDir = "$env:USERPROFILE\.framework\mosquitto",
    [switch]$CleanData
)

$ErrorActionPreference = "SilentlyContinue"

function Write-Status {
    param([string]$Message, [string]$Color = "White")
    Write-Host $Message -ForegroundColor $Color
}

function Stop-Etcd {
    Write-Host "停止 etcd..."

    # 通过 PID 文件停止
    $pidFile = "$EtcdDataDir\etcd.pid"
    if (Test-Path $pidFile) {
        $pid = Get-Content $pidFile
        $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($proc) {
            Stop-Process -Id $pid -Force
            Write-Status "✓ etcd 已停止 (PID: $pid)" "Green"
        }
        Remove-Item $pidFile -Force
    }

    # 兜底：按进程名停止
    $etcdProcs = Get-Process -Name "etcd" -ErrorAction SilentlyContinue
    if ($etcdProcs) {
        $etcdProcs | Stop-Process -Force
        Write-Status "✓ 已停止所有 etcd 进程" "Green"
    }

    if ($CleanData -and (Test-Path $EtcdDataDir)) {
        Remove-Item -Recurse -Force $EtcdDataDir
        Write-Status "✓ 已清理 etcd 数据目录" "Green"
    }
}

function Stop-Mosquitto {
    Write-Host "停止 Mosquitto..."

    $pidFile = "$MosquittoConfigDir\mosquitto.pid"
    if (Test-Path $pidFile) {
        $pid = Get-Content $pidFile
        $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($proc) {
            Stop-Process -Id $pid -Force
            Write-Status "✓ Mosquitto 已停止 (PID: $pid)" "Green"
        }
        Remove-Item $pidFile -Force
    }

    $mqttProcs = Get-Process -Name "mosquitto" -ErrorAction SilentlyContinue
    if ($mqttProcs) {
        $mqttProcs | Stop-Process -Force
        Write-Status "✓ 已停止所有 Mosquitto 进程" "Green"
    }

    if ($CleanData -and (Test-Path $MosquittoConfigDir)) {
        Remove-Item -Recurse -Force $MosquittoConfigDir
        Write-Status "✓ 已清理 Mosquitto 数据目录" "Green"
    }
}

Write-Host "=========================================="
Write-Host "多语言通信框架 - 停止原生服务"
Write-Host "=========================================="

switch ($Component) {
    'etcd' { Stop-Etcd }
    'mosquitto' { Stop-Mosquitto }
    'all' {
        Stop-Etcd
        Stop-Mosquitto
        Write-Host ""
        Write-Status "✓ 所有服务已停止" "Green"
    }
}
