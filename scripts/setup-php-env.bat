@echo off
REM PHP环境配置脚本 - 批处理版本
echo === PHP环境配置脚本 ===
echo.

REM 检查PHP是否安装
set PHP_PATH=E:\php-8.1.34-Win32-vs16-x64
set PHP_EXE=%PHP_PATH%\php.exe

if not exist "%PHP_EXE%" (
    echo [错误] 找不到php.exe在 %PHP_EXE%
    echo.
    echo 请先下载并安装PHP 8.1 Thread Safe x64版本
    echo 下载地址: https://windows.php.net/download/
    echo 推荐版本: PHP 8.1 (8.1.34) VS16 x64 Thread Safe Zip
    pause
    exit /b 1
)

echo [OK] 找到PHP可执行文件: %PHP_EXE%
echo.

REM 检查php.ini
set PHP_INI=%PHP_PATH%\php.ini
set PHP_INI_DEV=%PHP_PATH%\php.ini-development

if not exist "%PHP_INI%" (
    echo [提示] php.ini不存在,正在创建...
    if exist "%PHP_INI_DEV%" (
        copy "%PHP_INI_DEV%" "%PHP_INI%"
        echo [OK] 已从php.ini-development创建php.ini
    ) else (
        echo [错误] 找不到php.ini模板文件
        pause
        exit /b 1
    )
)

REM 备份php.ini
set BACKUP_FILE=%PHP_INI%.backup.%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set BACKUP_FILE=%BACKUP_FILE: =0%
copy "%PHP_INI%" "%BACKUP_FILE%" >nul 2>&1
echo [OK] 已备份配置文件到: %BACKUP_FILE%
echo.

REM 使用PowerShell修复配置文件
echo 正在修复PHP配置...
powershell -ExecutionPolicy Bypass -Command ^
    "$content = Get-Content '%PHP_INI%' -Raw; ^
     $content = $content -replace 'extension\s*=\s*opcache', 'zend_extension=php_opcache.dll'; ^
     $content = $content -replace 'extension\s*=\s*curl\s*$', 'extension=php_curl.dll'; ^
     $content = $content -replace 'extension\s*=\s*fileinfo\s*$', 'extension=php_fileinfo.dll'; ^
     $content = $content -replace 'extension\s*=\s*mbstring\s*$', 'extension=php_mbstring.dll'; ^
     $content = $content -replace 'extension\s*=\s*openssl\s*$', 'extension=php_openssl.dll'; ^
     $content = $content -replace 'extension\s*=\s*pdo_mysql\s*$', 'extension=php_pdo_mysql.dll'; ^
     $content = $content -replace 'extension\s*=\s*sockets\s*$', 'extension=php_sockets.dll'; ^
     $content = $content -replace 'extension\s*=\s*zip\s*$', 'extension=php_zip.dll'; ^
     $content | Set-Content '%PHP_INI%' -NoNewline"

echo [OK] 配置文件已修复
echo.

REM 验证PHP配置
echo 正在验证PHP配置...
echo.
echo ========== PHP版本 ==========
"%PHP_EXE%" -v
echo.

echo ========== 已加载的扩展 ==========
"%PHP_EXE%" -m
echo.

echo ========== 配置文件位置 ==========
"%PHP_EXE%" --ini
echo.

REM 检查Composer
echo 正在检查Composer...
where composer >nul 2>&1
if %errorlevel% equ 0 (
    composer --version
    echo [OK] Composer已安装
) else (
    echo [提示] Composer未安装
    echo.
    echo 请安装Composer:
    echo   1. 下载: https://getcomposer.org/Composer-Setup.exe
    echo   2. 运行安装程序
    echo   3. 重启命令提示符
)

echo.
echo === 配置完成 ===
echo 现在可以开始PHP SDK开发了!
echo.
pause
