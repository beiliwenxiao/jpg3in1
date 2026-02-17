# Golang SDK 完整测试脚本 (Windows)
# 用于检查点任务23

Write-Host "=== Golang SDK 完整测试 ===" -ForegroundColor Cyan
Write-Host ""

$ErrorCount = 0
$WarningCount = 0

# 切换到golang-sdk目录
Push-Location $PSScriptRoot

try {
    # 1. 检查编译
    Write-Host "1. 检查编译..." -ForegroundColor Yellow
    $buildResult = go build ./... 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "编译失败:" -ForegroundColor Red
        Write-Host $buildResult
        $ErrorCount++
    } else {
        Write-Host "✓ 编译通过" -ForegroundColor Green
    }
    Write-Host ""

    # 2. 运行所有测试
    Write-Host "2. 运行所有测试..." -ForegroundColor Yellow
    
    $modules = @(
        "client",
        "config", 
        "connection",
        "errors",
        "resilience",
        "security",
        "serializer",
        "protocol/adapter",
        "protocol/router",
        "protocol/internal/grpc",
        "protocol/internal/jsonrpc",
        "protocol/internal/custom",
        "protocol/external/rest",
        "protocol/external/websocket",
        "protocol/external/jsonrpc",
        "protocol/external/mqtt",
        "registry",
        "observability"
    )

    foreach ($module in $modules) {
        Write-Host "  测试模块: $module" -ForegroundColor Cyan
        $testResult = go test "./$module" -v 2>&1
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "    ✗ 测试失败" -ForegroundColor Red
            # 显示错误详情
            $testResult | Select-String -Pattern "FAIL|Error|panic" | ForEach-Object {
                Write-Host "      $_" -ForegroundColor Red
            }
            $ErrorCount++
        } else {
            $passCount = ($testResult | Select-String -Pattern "PASS" | Measure-Object).Count
            $skipCount = ($testResult | Select-String -Pattern "SKIP" | Measure-Object).Count
            Write-Host "    ✓ 测试通过 (通过: $passCount, 跳过: $skipCount)" -ForegroundColor Green
            
            if ($skipCount -gt 0) {
                $WarningCount++
            }
        }
    }
    Write-Host ""

    # 3. 总结
    Write-Host "=== 测试总结 ===" -ForegroundColor Cyan
    Write-Host "错误数: $ErrorCount" -ForegroundColor $(if ($ErrorCount -eq 0) { "Green" } else { "Red" })
    Write-Host "警告数: $WarningCount" -ForegroundColor $(if ($WarningCount -eq 0) { "Green" } else { "Yellow" })
    Write-Host ""

    if ($ErrorCount -eq 0) {
        Write-Host "✓ 所有测试通过!" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "✗ 发现 $ErrorCount 个错误" -ForegroundColor Red
        exit 1
    }

} finally {
    Pop-Location
}
