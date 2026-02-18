# 快速检查PHP环境
Write-Host "=== PHP环境检查 ===" -ForegroundColor Cyan

# 检查PHP版本
Write-Host "`n1. PHP版本:" -ForegroundColor Yellow
php -v

# 检查已加载的扩展
Write-Host "`n2. 已加载的扩展:" -ForegroundColor Yellow
php -m

# 检查php.ini位置
Write-Host "`n3. PHP配置文件位置:" -ForegroundColor Yellow
php --ini

# 检查Composer
Write-Host "`n4. Composer版本:" -ForegroundColor Yellow
composer --version 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Composer未安装" -ForegroundColor Red
}

Write-Host "`n=== 检查完成 ===" -ForegroundColor Cyan
