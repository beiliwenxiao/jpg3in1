# 测试安全模块 (Windows PowerShell)

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Write-Host "当前目录: $(Get-Location)"

Write-Host "下载依赖..."
go mod download

Write-Host "运行安全模块测试..."
go test -v ./security/

Write-Host "测试完成"
