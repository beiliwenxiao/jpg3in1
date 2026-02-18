@echo off
setlocal enabledelayedexpansion

echo ====================================
echo Simple PHP Configuration Fix
echo ====================================
echo.

REM Directly use the known PHP path
set PHP_DIR=E:\php-8.1.34-Win32-vs16-x64
set PHP_INI=%PHP_DIR%\php.ini
set EXT_DIR=%PHP_DIR%\ext

echo PHP Directory: %PHP_DIR%
echo PHP INI: %PHP_INI%
echo Extension Directory: %EXT_DIR%
echo.

if not exist "%PHP_INI%" (
    echo [ERROR] php.ini not found at: %PHP_INI%
    echo.
    echo Creating from template...
    if exist "%PHP_DIR%\php.ini-development" (
        copy "%PHP_DIR%\php.ini-development" "%PHP_INI%"
        echo [OK] Created php.ini
    ) else (
        echo [ERROR] No template found
        pause
        exit /b 1
    )
)

if not exist "%EXT_DIR%" (
    echo [ERROR] Extension directory not found: %EXT_DIR%
    pause
    exit /b 1
)

REM Backup
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
copy "%PHP_INI%" "%PHP_INI%.backup.%TIMESTAMP%" >nul 2>&1
echo [OK] Backup: %PHP_INI%.backup.%TIMESTAMP%
echo.

echo Available extensions:
echo ------------------------------------
dir /b "%EXT_DIR%\php_*.dll"
echo.

REM Create PowerShell script
set TEMP_PS1=%TEMP%\fix-php-%RANDOM%.ps1

(
echo $iniPath = '%PHP_INI%'
echo $extDir = '%EXT_DIR%'
echo.
echo Write-Host "Reading php.ini..." -ForegroundColor Cyan
echo $content = Get-Content $iniPath -Raw -Encoding UTF8
echo.
echo Write-Host "Cleaning old configurations..." -ForegroundColor Yellow
echo $lines = $content -split "`n"
echo $newLines = @^(^)
echo foreach ^($line in $lines^) {
echo     if ^($line -notmatch '^\s*;?\s*^(extension^|zend_extension^)\s*='^ ) {
echo         $newLines += $line
echo     }
echo }
echo $content = $newLines -join "`n"
echo.
echo Write-Host "Setting extension_dir..." -ForegroundColor Yellow
echo if ^($content -match 'extension_dir'^) {
echo     $content = $content -replace '(?m^)^;?\s*extension_dir\s*=.*$', "extension_dir = `"$extDir`""
echo } else {
echo     $content = "extension_dir = `"$extDir`"`n" + $content
echo }
echo.
echo Write-Host "" 
echo Write-Host "Adding extensions..." -ForegroundColor Cyan
echo $content += "`n`n; ========================================`n"
echo $content += "; Extensions ^(Auto-configured^)`n"
echo $content += "; ========================================`n"
echo.
echo $dlls = Get-ChildItem "$extDir\php_*.dll" -ErrorAction SilentlyContinue ^| Sort-Object Name
echo foreach ^($dll in $dlls^) {
echo     $name = $dll.Name -replace '^php_', '' -replace '\.dll$', ''
echo     if ^($name -eq 'opcache'^) {
echo         $content += "zend_extension=$^($dll.Name^)`n"
echo         Write-Host "  [OK] $name ^(zend^)" -ForegroundColor Green
echo     } else {
echo         $content += "extension=$^($dll.Name^)`n"
echo         Write-Host "  [OK] $name" -ForegroundColor Green
echo     }
echo }
echo.
echo Write-Host ""
echo Write-Host "Saving..." -ForegroundColor Cyan
echo $content ^| Set-Content $iniPath -NoNewline -Encoding UTF8
echo Write-Host "[OK] Saved!" -ForegroundColor Green
) > "%TEMP_PS1%"

echo Configuring...
echo.
powershell -ExecutionPolicy Bypass -File "%TEMP_PS1%"
del "%TEMP_PS1%"

echo.
echo ====================================
echo Verification
echo ====================================
echo.

echo PHP Version:
php -v
echo.

if %errorlevel% equ 0 (
    echo.
    echo Loaded Extensions:
    php -m
    echo.
    echo [OK] PHP is configured!
) else (
    echo [ERROR] PHP has errors
)

echo.
echo ====================================
pause
