# Golang SDK 完整测试脚本
# 用于检查点任务23 - 确保所有测试通过

Write-Host "=== Golang SDK 完整测试 ===" -ForegroundColor Cyan
Write-Host ""

$ErrorCount = 0
$PassCount = 0
$SkipCount = 0

# 切换到golang-sdk目录
Push-Location $PSScriptRoot

try {
    # 测试模块列表
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

    Write-Host "开始测试 $($modules.Count) 个模块..." -ForegroundColor Yellow
    Write-Host ""

    foreach ($module in $modules) {
        Write-Host "[$($modules.IndexOf($module) + 1)/$($modules.Count)] 测试模块: $module" -ForegroundColor Cyan
        
        $testOutput = go test "./$module" -v -timeout 60s 2>&1 | Out-String
        
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  ✗ 测试失败" -ForegroundColor Red
            # 显示错误详情
            $testOutput | Select-String -Pattern "FAIL|Error|panic" | ForEach-Object {
                Write-Host "    $_" -ForegroundColor Red
            }
            $ErrorCount++
        } else {
            $passTests = ($testOutput | Select-String -Pattern "--- PASS:" | Measure-Object).Count
            $skipTests = ($testOutput | Select-String -Pattern "--- SKIP:" | Measure-Object).Count
            Write-Host "  ✓ 测试通过 (通过: $passTests, 跳过: $skipTests)" -ForegroundColor Green
            $PassCount++
            $SkipCount += $skipTests
        }
    }
    
    Write-Host ""
    Write-Host "=== 测试总结 ===" -ForegroundColor Cyan
    Write-Host "总模块数: $($modules.Count)" -ForegroundColor White
    Write-Host "通过模块: $PassCount" -ForegroundColor Green
    Write-Host "失败模块: $ErrorCount" -ForegroundColor $(if ($ErrorCount -eq 0) { "Green" } else { "Red" })
    Write-Host "跳过测试: $SkipCount" -ForegroundColor Yellow
    Write-Host ""

    if ($ErrorCount -eq 0) {
        Write-Host "✓ 所有测试通过! Golang SDK 已准备就绪。" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "✗ 发现 $ErrorCount 个模块测试失败" -ForegroundColor Red
        exit 1
    }

} finally {
    Pop-Location
}
