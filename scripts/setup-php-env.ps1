# PHP环境配置脚本
# 用于Windows环境下配置PHP开发环境

param(
    [string]$PhpPath = "E:\php-8.1.34-Win32-vs16-x64"
)

Write-Host "=== PHP环境配置脚本 ===" -ForegroundColor Cyan
Write-Host "PHP路径: $PhpPath`n" -ForegroundColor Yellow

# 1. 检查PHP是否安装
$phpExe = Join-Path $PhpPath "php.exe"
if (-not (Test-Path $phpExe)) {
    Write-Host "错误: 找不到php.exe在 $phpExe" -ForegroundColor Red
    Write-Host "`n请先下载并安装PHP 8.1 Thread Safe x64版本" -ForegroundColor Yellow
    Write-Host "下载地址: https://windows.php.net/download/" -ForegroundColor Cyan
    Write-Host "推荐版本: PHP 8.1 (8.1.34) VS16 x64 Thread Safe Zip" -ForegroundColor Cyan
    exit 1
}

Write-Host "✓ 找到PHP可执行文件" -ForegroundColor Green

# 2. 检查php.ini文件
$phpIni = Join-Path $PhpPath "php.ini"
$phpIniDevelopment = Join-Path $PhpPath "php.ini-development"
$phpIniProduction = Join-Path $PhpPath "php.ini-production"

if (-not (Test-Path $phpIni)) {
    Write-Host "! php.ini不存在,正在创建..." -ForegroundColor Yellow
    
    if (Test-Path $phpIniDevelopment) {
        Copy-Item $phpIniDevelopment $phpIni
        Write-Host "✓ 已从php.ini-development创建php.ini" -ForegroundColor Green
    } elseif (Test-Path $phpIniProduction) {
        Copy-Item $phpIniProduction $phpIni
        Write-Host "✓ 已从php.ini-production创建php.ini" -ForegroundColor Green
    } else {
        Write-Host "错误: 找不到php.ini模板文件" -ForegroundColor Red
        exit 1
    }
}

# 3. 配置必需的PHP扩展
Write-Host "`n正在配置PHP扩展..." -ForegroundColor Cyan

$content = Get-Content $phpIni -Raw

# 设置扩展目录
$extDir = Join-Path $PhpPath "ext"
if ($content -notmatch 'extension_dir\s*=') {
    $content = "extension_dir = `"$extDir`"`n" + $content
} else {
    $content = $content -replace 'extension_dir\s*=.*', "extension_dir = `"$extDir`""
}

# 启用必需的扩展(用于Webman框架)
$requiredExtensions = @(
    @{name='opcache'; type='zend'; dll='php_opcache.dll'},
    @{name='curl'; type='ext'; dll='php_curl.dll'},
    @{name='fileinfo'; type='ext'; dll='php_fileinfo.dll'},
    @{name='mbstring'; type='ext'; dll='php_mbstring.dll'},
    @{name='openssl'; type='ext'; dll='php_openssl.dll'},
    @{name='pdo_mysql'; type='ext'; dll='php_pdo_mysql.dll'},
    @{name='pdo_sqlite'; type='ext'; dll='php_pdo_sqlite.dll'},
    @{name='sockets'; type='ext'; dll='php_sockets.dll'},
    @{name='zip'; type='ext'; dll='php_zip.dll'}
)

foreach ($ext in $requiredExtensions) {
    $dllPath = Join-Path $extDir $ext.dll
    
    if (Test-Path $dllPath) {
        # 移除旧的配置
        $content = $content -replace "(?m)^;?\s*extension\s*=\s*$($ext.name)\s*$", ""
        $content = $content -replace "(?m)^;?\s*extension\s*=\s*$($ext.dll)\s*$", ""
        $content = $content -replace "(?m)^;?\s*zend_extension\s*=\s*$($ext.name)\s*$", ""
        $content = $content -replace "(?m)^;?\s*zend_extension\s*=\s*$($ext.dll)\s*$", ""
        
        # 添加正确的配置
        if ($ext.type -eq 'zend') {
            if ($content -notmatch "zend_extension\s*=\s*$($ext.dll)") {
                $content += "`nzend_extension=$($ext.dll)"
                Write-Host "  ✓ 启用扩展: $($ext.name) (zend_extension)" -ForegroundColor Green
            }
        } else {
            if ($content -notmatch "extension\s*=\s*$($ext.dll)") {
                $content += "`nextension=$($ext.dll)"
                Write-Host "  ✓ 启用扩展: $($ext.name)" -ForegroundColor Green
            }
        }
    } else {
        Write-Host "  ! 警告: 找不到扩展DLL: $($ext.dll)" -ForegroundColor Yellow
    }
}

# 4. 配置其他重要设置
Write-Host "`n正在配置其他PHP设置..." -ForegroundColor Cyan

# 内存限制
if ($content -match 'memory_limit\s*=') {
    $content = $content -replace 'memory_limit\s*=.*', 'memory_limit = 256M'
} else {
    $content += "`nmemory_limit = 256M"
}
Write-Host "  ✓ 设置内存限制: 256M" -ForegroundColor Green

# 时区
if ($content -match 'date\.timezone\s*=') {
    $content = $content -replace 'date\.timezone\s*=.*', 'date.timezone = Asia/Shanghai'
} else {
    $content += "`ndate.timezone = Asia/Shanghai"
}
Write-Host "  ✓ 设置时区: Asia/Shanghai" -ForegroundColor Green

# 5. 保存配置
$backupPath = "$phpIni.backup." + (Get-Date -Format "yyyyMMdd_HHmmss")
Copy-Item $phpIni $backupPath -ErrorAction SilentlyContinue
$content | Set-Content $phpIni -NoNewline

Write-Host "`n✓ PHP配置已更新" -ForegroundColor Green
Write-Host "  备份文件: $backupPath" -ForegroundColor Gray

# 6. 验证配置
Write-Host "`n正在验证PHP配置..." -ForegroundColor Cyan

$env:Path = "$PhpPath;$env:Path"
$phpCheck = & $phpExe -v 2>&1 | Out-String

if ($phpCheck -match "PHP (\d+\.\d+\.\d+)") {
    Write-Host "`n✓ PHP版本: $($matches[1])" -ForegroundColor Green
    
    Write-Host "`n已加载的扩展:" -ForegroundColor Cyan
    $modules = & $phpExe -m 2>&1 | Out-String
    Write-Host $modules
    
    # 检查必需扩展是否加载
    $missingExtensions = @()
    foreach ($ext in $requiredExtensions) {
        if ($modules -notmatch $ext.name) {
            $missingExtensions += $ext.name
        }
    }
    
    if ($missingExtensions.Count -gt 0) {
        Write-Host "`n! 警告: 以下扩展未能加载:" -ForegroundColor Yellow
        $missingExtensions | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    } else {
        Write-Host "`n✓ 所有必需扩展已加载" -ForegroundColor Green
    }
} else {
    Write-Host "`n! 警告: PHP配置可能有问题" -ForegroundColor Yellow
    Write-Host $phpCheck
}

# 7. 检查Composer
Write-Host "`n正在检查Composer..." -ForegroundColor Cyan
$composerCheck = Get-Command composer -ErrorAction SilentlyContinue

if ($composerCheck) {
    $composerVersion = & composer --version 2>&1
    Write-Host "✓ Composer已安装: $composerVersion" -ForegroundColor Green
} else {
    Write-Host "! Composer未安装" -ForegroundColor Yellow
    Write-Host "`n请安装Composer:" -ForegroundColor Cyan
    Write-Host "  1. 下载: https://getcomposer.org/Composer-Setup.exe" -ForegroundColor Gray
    Write-Host "  2. 运行安装程序" -ForegroundColor Gray
    Write-Host "  3. 重启PowerShell" -ForegroundColor Gray
}

Write-Host "`n=== 配置完成 ===" -ForegroundColor Cyan
Write-Host "现在可以开始PHP SDK开发了!" -ForegroundColor Green
