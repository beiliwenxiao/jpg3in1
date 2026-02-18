@echo off
setlocal enabledelayedexpansion

echo ====================================
echo PHP Configuration Fix Script
echo ====================================
echo.

set PHP_PATH=E:\php-8.1.34-Win32-vs16-x64
set PHP_EXE=%PHP_PATH%\php.exe
set PHP_INI=%PHP_PATH%\php.ini

REM Check if PHP exists
if not exist "%PHP_EXE%" (
    echo [ERROR] PHP not found at: %PHP_EXE%
    echo.
    echo Please install PHP 8.1 Thread Safe x64 first
    echo Download from: https://windows.php.net/download/
    echo.
    pause
    exit /b 1
)

echo [OK] Found PHP at: %PHP_EXE%
echo.

REM Check if php.ini exists
if not exist "%PHP_INI%" (
    echo [INFO] php.ini not found, creating from template...
    if exist "%PHP_PATH%\php.ini-development" (
        copy "%PHP_PATH%\php.ini-development" "%PHP_INI%"
        echo [OK] Created php.ini from php.ini-development
    ) else (
        echo [ERROR] No php.ini template found
        pause
        exit /b 1
    )
)

REM Backup php.ini
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set BACKUP_FILE=%PHP_INI%.backup.%TIMESTAMP%
copy "%PHP_INI%" "%BACKUP_FILE%" >nul 2>&1
echo [OK] Backup created: %BACKUP_FILE%
echo.

REM Create temp PowerShell script to fix php.ini
set TEMP_PS1=%TEMP%\fix-php-ini-%RANDOM%.ps1

echo $content = Get-Content '%PHP_INI%' -Raw -Encoding UTF8 > "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*opcache\s*$', 'zend_extension=php_opcache.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*curl\s*$', 'extension=php_curl.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*fileinfo\s*$', 'extension=php_fileinfo.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*gd\s*$', 'extension=php_gd.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*mbstring\s*$', 'extension=php_mbstring.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*openssl\s*$', 'extension=php_openssl.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*pdo_mysql\s*$', 'extension=php_pdo_mysql.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*pdo_sqlite\s*$', 'extension=php_pdo_sqlite.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*mysqli\s*$', 'extension=php_mysqli.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*sockets\s*$', 'extension=php_sockets.dll' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*zip\s*$', 'extension=php_zip.dll' >> "%TEMP_PS1%"
echo $content ^| Set-Content '%PHP_INI%' -NoNewline -Encoding UTF8 >> "%TEMP_PS1%"

echo Fixing php.ini configuration...
powershell -ExecutionPolicy Bypass -File "%TEMP_PS1%"
del "%TEMP_PS1%"

echo [OK] Configuration fixed
echo.

REM Verify PHP
echo ====================================
echo Verifying PHP Configuration
echo ====================================
echo.

echo PHP Version:
echo -----------------------------------
"%PHP_EXE%" -v
echo.

echo Loaded Extensions:
echo -----------------------------------
"%PHP_EXE%" -m
echo.

echo Configuration File:
echo -----------------------------------
"%PHP_EXE%" --ini
echo.

REM Check Composer
echo Composer Status:
echo -----------------------------------
where composer >nul 2>&1
if %errorlevel% equ 0 (
    composer --version
    echo [OK] Composer is installed
) else (
    echo [INFO] Composer not found
    echo.
    echo To install Composer:
    echo 1. Download: https://getcomposer.org/Composer-Setup.exe
    echo 2. Run the installer
    echo 3. Restart your terminal
)

echo.
echo ====================================
echo Configuration Complete
echo ====================================
echo.
pause
