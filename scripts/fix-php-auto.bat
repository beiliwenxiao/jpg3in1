@echo off
setlocal enabledelayedexpansion

echo ====================================
echo Auto PHP Configuration Fix
echo ====================================
echo.

REM Try to find PHP from PATH
where php.exe >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] PHP not found in PATH
    echo Please add PHP to your PATH or run: set PATH=E:\php-8.1.34-Win32-vs16-x64;%%PATH%%
    pause
    exit /b 1
)

REM Get PHP path
for /f "delims=" %%i in ('where php.exe') do set PHP_EXE=%%i
for %%i in ("%PHP_EXE%") do set PHP_DIR=%%~dpi
set PHP_DIR=%PHP_DIR:~0,-1%

echo [OK] Found PHP at: %PHP_DIR%
echo.

set PHP_INI=%PHP_DIR%\php.ini
set EXT_DIR=%PHP_DIR%\ext

REM Check php.ini
if not exist "%PHP_INI%" (
    echo [INFO] Creating php.ini...
    if exist "%PHP_DIR%\php.ini-development" (
        copy "%PHP_DIR%\php.ini-development" "%PHP_INI%" >nul
        echo [OK] Created php.ini
    ) else (
        echo [ERROR] No php.ini template found
        pause
        exit /b 1
    )
)

REM Backup
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
copy "%PHP_INI%" "%PHP_INI%.backup.%TIMESTAMP%" >nul 2>&1
echo [OK] Backup: %PHP_INI%.backup.%TIMESTAMP%
echo.

REM Create PowerShell script
set TEMP_PS1=%TEMP%\fix-php-%RANDOM%.ps1

echo $iniPath = '%PHP_INI%' > "%TEMP_PS1%"
echo $extDir = '%EXT_DIR%' >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "Reading php.ini..." >> "%TEMP_PS1%"
echo $content = Get-Content $iniPath -Raw -Encoding UTF8 >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "Cleaning old extension configurations..." >> "%TEMP_PS1%"
echo $lines = $content -split "`n" >> "%TEMP_PS1%"
echo $newLines = @() >> "%TEMP_PS1%"
echo foreach ($line in $lines) { >> "%TEMP_PS1%"
echo     if ($line -notmatch '^\s*;?\s*(extension^|zend_extension)\s*=') { >> "%TEMP_PS1%"
echo         $newLines += $line >> "%TEMP_PS1%"
echo     } >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo $content = $newLines -join "`n" >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "Setting extension directory..." >> "%TEMP_PS1%"
echo if ($content -notmatch 'extension_dir') { >> "%TEMP_PS1%"
echo     $content = "extension_dir = `"$extDir`"`n" + $content >> "%TEMP_PS1%"
echo } else { >> "%TEMP_PS1%"
echo     $content = $content -replace 'extension_dir\s*=.*', "extension_dir = `"$extDir`"" >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "" >> "%TEMP_PS1%"
echo Write-Host "Scanning available extensions..." >> "%TEMP_PS1%"
echo $content += "`n`n; === Extensions (Auto-configured) ===`n" >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo $dlls = Get-ChildItem "$extDir\php_*.dll" -ErrorAction SilentlyContinue >> "%TEMP_PS1%"
echo foreach ($dll in $dlls) { >> "%TEMP_PS1%"
echo     $name = $dll.Name -replace '^php_', '' -replace '\.dll$', '' >> "%TEMP_PS1%"
echo     if ($name -eq 'opcache') { >> "%TEMP_PS1%"
echo         $content += "zend_extension=$($dll.Name)`n" >> "%TEMP_PS1%"
echo         Write-Host "[OK] Enabled: $name (zend)" -ForegroundColor Green >> "%TEMP_PS1%"
echo     } else { >> "%TEMP_PS1%"
echo         $content += "extension=$($dll.Name)`n" >> "%TEMP_PS1%"
echo         Write-Host "[OK] Enabled: $name" -ForegroundColor Green >> "%TEMP_PS1%"
echo     } >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "" >> "%TEMP_PS1%"
echo Write-Host "Saving configuration..." >> "%TEMP_PS1%"
echo $content ^| Set-Content $iniPath -NoNewline -Encoding UTF8 >> "%TEMP_PS1%"
echo Write-Host "[OK] Configuration saved" -ForegroundColor Green >> "%TEMP_PS1%"

echo Configuring PHP extensions...
echo.
powershell -ExecutionPolicy Bypass -File "%TEMP_PS1%"
del "%TEMP_PS1%"

echo.
echo ====================================
echo Verification
echo ====================================
echo.

echo PHP Version:
php -v 2>&1 | findstr /v "Warning"
echo.

echo Loaded Extensions:
php -m 2>&1 | findstr /v "Warning"
echo.

echo Configuration File:
php --ini 2>&1 | findstr /v "Warning"
echo.

echo ====================================
echo Done!
echo ====================================
echo.
echo Your PHP is now configured.
echo.
echo For Webman framework, you need:
echo - mbstring (text processing)
echo - openssl (security)
echo - pdo (database)
echo - json (built-in)
echo.
pause
