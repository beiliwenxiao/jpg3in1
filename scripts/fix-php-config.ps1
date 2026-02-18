# PHP配置修复脚本
# 用于修复Windows环境下的PHP扩展加载问题

$phpIniPath = "C:\php\php.ini"

Write-Host "正在检查PHP配置文件: $phpIniPath" -ForegroundColor Cyan

if (-not (Test-Path $phpIniPath)) {
    Write-Host "错误: 找不到php.ini文件在 $phpIniPath" -ForegroundColor Red
    Write-Host "请确认PHP安装路径是否正确" -ForegroundColor Yellow
    exit 1
}

# 备份原配置文件
$backupPath = "$phpIniPath.backup." + (Get-Date -Format "yyyyMMdd_HHmmss")
Copy-Item $phpIniPath $backupPath
Write-Host "已备份原配置文件到: $backupPath" -ForegroundColor Green

# 读取配置文件
$content = Get-Content $phpIniPath -Raw

# 修复opcache配置 - 应该使用zend_extension
$content = $content -replace 'extension\s*=\s*opcache', 'zend_extension=php_opcache.dll'
$content = $content -replace 'extension\s*=\s*php_opcache', 'zend_extension=php_opcache.dll'

# 修复其他扩展配置 - 确保使用正确的DLL名称
$extensions = @(
    'curl',
    'fileinfo',
    'gd',
    'mbstring',
    'openssl',
    'pdo_mysql',
    'pdo_sqlite',
    'mysqli',
    'sockets',
    'zip',
    'redis',
    'swoole'
)

foreach ($ext in $extensions) {
    # 修复没有php_前缀的
    $content = $content -replace "extension\s*=\s*$ext\s*$", "extension=php_$ext.dll"
    # 修复有php_前缀但没有.dll后缀的
    $content = $content -replace "extension\s*=\s*php_$ext\s*$", "extension=php_$ext.dll"
}

# 写回配置文件
$content | Set-Content $phpIniPath -NoNewline

Write-Host "`n配置文件已修复!" -ForegroundColor Green
Write-Host "`n正在验证PHP配置..." -ForegroundColor Cyan

# 验证PHP配置
$phpVersion = & php -v 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nPHP版本信息:" -ForegroundColor Green
    Write-Host $phpVersion
    
    Write-Host "`n已加载的扩展:" -ForegroundColor Cyan
    & php -m
} else {
    Write-Host "`n警告: PHP配置可能仍有问题" -ForegroundColor Yellow
    Write-Host $phpVersion
}

Write-Host "`n如果还有问题,可以恢复备份文件: $backupPath" -ForegroundColor Yellow
