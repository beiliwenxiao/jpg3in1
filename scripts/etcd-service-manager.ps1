# etcd 服务管理脚本 (PowerShell 版本)
# 用于注册、注销和查询服务

param(
    [Parameter(Position=0)]
    [ValidateSet('register', 'deregister', 'list', 'get', 'watch', 'help')]
    [string]$Command,
    
    [string]$Name,
    [string]$Id,
    [string]$Language,
    [string]$Address,
    [int]$Port,
    [string]$Version = "1.0.0",
    [string]$Protocols = "grpc,rest",
    [int]$Ttl = 30,
    [string]$EtcdEndpoints = "http://localhost:2379",
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

# 显示帮助信息
function Show-Help {
    Write-Host @"
用法: .\etcd-service-manager.ps1 <命令> [选项]

命令:
  register    注册服务
  deregister  注销服务
  list        列出所有服务
  get         获取服务详情
  watch       监听服务变化
  help        显示帮助信息

注册服务选项:
  -Name <服务名称>        (必需)
  -Id <实例ID>            (必需)
  -Language <语言>        (必需: java, golang, php)
  -Address <地址>         (必需)
  -Port <端口>            (必需)
  -Version <版本>         (可选, 默认: 1.0.0)
  -Protocols <协议列表>   (可选, 默认: grpc,rest)
  -Ttl <TTL秒数>          (可选, 默认: 30)

注销服务选项:
  -Name <服务名称>        (必需)
  -Id <实例ID>            (可选, 如果不指定则注销所有实例)

获取服务选项:
  -Name <服务名称>        (必需)
  -Id <实例ID>            (可选)

监听服务选项:
  -Name <服务名称>        (可选, 如果不指定则监听所有服务)

通用选项:
  -EtcdEndpoints <端点>   (可选, 默认: http://localhost:2379)
  -Timeout <超时秒数>     (可选, 默认: 5)

示例:
  # 注册服务
  .\etcd-service-manager.ps1 register -Name my-service -Id instance-1 -Language java -Address localhost -Port 8080

  # 注销服务
  .\etcd-service-manager.ps1 deregister -Name my-service -Id instance-1

  # 列出所有服务
  .\etcd-service-manager.ps1 list

  # 获取服务详情
  .\etcd-service-manager.ps1 get -Name my-service

  # 监听服务变化
  .\etcd-service-manager.ps1 watch -Name my-service

"@
}

# 检查 etcdctl 是否可用
function Test-EtcdctlAvailable {
    $etcdctlPath = Get-Command etcdctl -ErrorAction SilentlyContinue
    if (-not $etcdctlPath) {
        Write-ColorOutput "✗ 未找到 etcdctl 命令，请确保已安装 etcd 客户端" "Red"
        Write-Host ""
        Write-Host "安装说明:"
        Write-Host "  1. 下载 etcd: https://github.com/etcd-io/etcd/releases"
        Write-Host "  2. 解压并将 etcdctl.exe 添加到 PATH 环境变量"
        exit 1
    }
}

# 注册服务
function Register-Service {
    # 验证必需参数
    if (-not $Name -or -not $Id -or -not $Language -or -not $Address -or -not $Port) {
        Write-ColorOutput "错误: 缺少必需参数" "Red"
        Show-Help
        exit 1
    }
    
    # 验证语言
    if ($Language -notin @('java', 'golang', 'php')) {
        Write-ColorOutput "错误: 语言必须是 java, golang 或 php" "Red"
        exit 1
    }
    
    # 构建服务键和值
    $serviceKey = "/framework/services/$Name/$Id"
    $protocolsArray = $Protocols -split ',' | ForEach-Object { "`"$_`"" }
    $protocolsJson = "[" + ($protocolsArray -join ", ") + "]"
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    
    $serviceValue = @"
{
  "id": "$Id",
  "name": "$Name",
  "version": "$Version",
  "language": "$Language",
  "address": "$Address",
  "port": $Port,
  "protocols": $protocolsJson,
  "metadata": {
    "registered_at": "$timestamp"
  }
}
"@
    
    # 创建租约
    Write-Host "创建租约 (TTL: ${Ttl}s)..."
    $leaseOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" lease grant $Ttl 2>&1
    $leaseId = $leaseOutput | Select-String -Pattern "lease ([0-9a-f]+)" | ForEach-Object { $_.Matches.Groups[1].Value }
    
    if (-not $leaseId) {
        Write-ColorOutput "✗ 创建租约失败" "Red"
        Write-Host $leaseOutput
        exit 1
    }
    
    Write-ColorOutput "✓ 租约创建成功: $leaseId" "Green"
    
    # 注册服务（关联租约）
    Write-Host "注册服务..."
    $putOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" put $serviceKey $serviceValue --lease=$leaseId 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-ColorOutput "✓ 服务注册成功" "Green"
        Write-Host ""
        Write-Host "服务信息:"
        Write-Host "  名称: $Name"
        Write-Host "  实例ID: $Id"
        Write-Host "  语言: $Language"
        Write-Host "  地址: ${Address}:${Port}"
        Write-Host "  版本: $Version"
        Write-Host "  协议: $Protocols"
        Write-Host "  租约ID: $leaseId"
        Write-Host "  TTL: ${Ttl}s"
        Write-Host ""
        Write-ColorOutput "注意: 服务将在 $Ttl 秒后自动过期，需要定期续约" "Yellow"
    } else {
        Write-ColorOutput "✗ 服务注册失败" "Red"
        Write-Host $putOutput
        exit 1
    }
}

# 注销服务
function Deregister-Service {
    # 验证必需参数
    if (-not $Name) {
        Write-ColorOutput "错误: 缺少服务名称" "Red"
        Show-Help
        exit 1
    }
    
    if (-not $Id) {
        # 注销所有实例
        $servicePrefix = "/framework/services/$Name/"
        Write-Host "注销服务的所有实例: $Name"
        
        $delOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" del $servicePrefix --prefix 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-ColorOutput "✓ 服务注销成功" "Green"
        } else {
            Write-ColorOutput "✗ 服务注销失败" "Red"
            Write-Host $delOutput
            exit 1
        }
    } else {
        # 注销指定实例
        $serviceKey = "/framework/services/$Name/$Id"
        Write-Host "注销服务实例: $Name/$Id"
        
        $delOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" del $serviceKey 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-ColorOutput "✓ 服务实例注销成功" "Green"
        } else {
            Write-ColorOutput "✗ 服务实例注销失败" "Red"
            Write-Host $delOutput
            exit 1
        }
    }
}

# 列出所有服务
function List-Services {
    Write-Host "已注册的服务:"
    Write-Host ""
    
    $servicesOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get "/framework/services/" --prefix --keys-only 2>&1
    $serviceKeys = $servicesOutput | Where-Object { $_ -match "^/framework/services/" -and $_ -notmatch "/.namespace" }
    
    if (-not $serviceKeys) {
        Write-ColorOutput "未发现已注册的服务" "Yellow"
        return
    }
    
    # 提取服务名称
    $serviceNames = @()
    foreach ($key in $serviceKeys) {
        $serviceName = ($key -replace '^/framework/services/', '') -replace '/.*$', ''
        if ($serviceName -and $serviceNames -notcontains $serviceName) {
            $serviceNames += $serviceName
        }
    }
    
    # 显示每个服务的实例
    foreach ($serviceName in $serviceNames) {
        Write-ColorOutput "服务: $serviceName" "Blue"
        
        $instancesOutput = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get "/framework/services/$serviceName/" --prefix 2>&1
        $lines = $instancesOutput -split "`n"
        
        $instanceCount = 0
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            if ($line -match "^/framework/services/$serviceName/") {
                $instanceCount++
                $instanceId = $line -replace "^/framework/services/$serviceName/", ""
                
                # 读取下一行（值）
                if ($i + 1 -lt $lines.Length) {
                    $value = $lines[$i + 1]
                    
                    # 解析 JSON（简单提取）
                    $language = if ($value -match '"language":\s*"([^"]+)"') { $matches[1] } else { "unknown" }
                    $address = if ($value -match '"address":\s*"([^"]+)"') { $matches[1] } else { "unknown" }
                    $port = if ($value -match '"port":\s*(\d+)') { $matches[1] } else { "unknown" }
                    $version = if ($value -match '"version":\s*"([^"]+)"') { $matches[1] } else { "unknown" }
                    
                    Write-Host "  ├─ 实例: $instanceId"
                    Write-Host "  │  ├─ 语言: $language"
                    Write-Host "  │  ├─ 地址: ${address}:${port}"
                    Write-Host "  │  └─ 版本: $version"
                }
                
                $i++ # 跳过值行
            }
        }
        
        if ($instanceCount -eq 0) {
            Write-Host "  └─ (无实例)"
        }
        
        Write-Host ""
    }
}

# 获取服务详情
function Get-ServiceDetails {
    # 验证必需参数
    if (-not $Name) {
        Write-ColorOutput "错误: 缺少服务名称" "Red"
        Show-Help
        exit 1
    }
    
    if (-not $Id) {
        # 获取所有实例
        $servicePrefix = "/framework/services/$Name/"
        Write-Host "服务详情: $Name"
        Write-Host ""
        
        $output = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get $servicePrefix --prefix 2>&1
        $lines = $output -split "`n"
        
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            if ($line -match "^/framework/services/$Name/") {
                Write-ColorOutput "键: $line" "Blue"
                if ($i + 1 -lt $lines.Length) {
                    $value = $lines[$i + 1]
                    # 尝试格式化 JSON
                    try {
                        $json = $value | ConvertFrom-Json | ConvertTo-Json -Depth 10
                        Write-Host $json
                    } catch {
                        Write-Host $value
                    }
                    Write-Host ""
                }
                $i++ # 跳过值行
            }
        }
    } else {
        # 获取指定实例
        $serviceKey = "/framework/services/$Name/$Id"
        Write-Host "服务实例详情: $Name/$Id"
        Write-Host ""
        
        $value = & etcdctl --endpoints=$EtcdEndpoints --command-timeout="${Timeout}s" get $serviceKey --print-value-only 2>&1
        
        if (-not $value -or $LASTEXITCODE -ne 0) {
            Write-ColorOutput "✗ 服务实例不存在" "Red"
            exit 1
        }
        
        # 尝试格式化 JSON
        try {
            $json = $value | ConvertFrom-Json | ConvertTo-Json -Depth 10
            Write-Host $json
        } catch {
            Write-Host $value
        }
    }
}

# 监听服务变化
function Watch-ServiceChanges {
    if (-not $Name) {
        # 监听所有服务
        Write-Host "监听所有服务的变化..."
        Write-Host "按 Ctrl+C 停止监听"
        Write-Host ""
        
        & etcdctl --endpoints=$EtcdEndpoints watch "/framework/services/" --prefix
    } else {
        # 监听指定服务
        Write-Host "监听服务变化: $Name"
        Write-Host "按 Ctrl+C 停止监听"
        Write-Host ""
        
        & etcdctl --endpoints=$EtcdEndpoints watch "/framework/services/$Name/" --prefix
    }
}

# 主函数
try {
    if (-not $Command) {
        Show-Help
        exit 1
    }
    
    Test-EtcdctlAvailable
    
    switch ($Command) {
        'register' {
            Register-Service
        }
        'deregister' {
            Deregister-Service
        }
        'list' {
            List-Services
        }
        'get' {
            Get-ServiceDetails
        }
        'watch' {
            Watch-ServiceChanges
        }
        'help' {
            Show-Help
        }
        default {
            Write-ColorOutput "未知命令: $Command" "Red"
            Show-Help
            exit 1
        }
    }
} catch {
    Write-ColorOutput "✗ 执行过程中发生错误: $_" "Red"
    exit 1
}