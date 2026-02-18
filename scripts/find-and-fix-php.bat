@echo off
setlocal enabledelayedexpansion

echo ====================================
echo Find and Fix PHP Configuration
echo ====================================
echo.

REM Find which php.ini is being used
echo Checking which php.ini is being used...
php --ini > %TEMP%\php-ini-info.txt 2>&1

echo.
type %TEMP%\php-ini-info.txt
echo.

REM Extract the loaded configuration file path
for /f "tokens=2* delims=:" %%i in ('findstr /C:"Loaded Configuration File" %TEMP%\php-ini-info.txt') do (
    set PHP_INI=%%i:%%j
    set PHP_INI=!PHP_INI:~1!
)

if "%PHP_INI%"=="" (
    echo [ERROR] Could not find php.ini location
    del %TEMP%\php-ini-info.txt
    pause
    exit /b 1
)

echo [INFO] Using php.ini: %PHP_INI%
echo.

REM Get PHP directory
for %%i in ("%PHP_INI%") do set PHP_DIR=%%~dpi
set PHP_DIR=%PHP_DIR:~0,-1%
set EXT_DIR=%PHP_DIR%\ext

echo [INFO] PHP Directory: %PHP_DIR%
echo [INFO] Extension Directory: %EXT_DIR%
echo.

if not exist "%EXT_DIR%" (
    echo [ERROR] Extension directory not found: %EXT_DIR%
    pause
    exit /b 1
)

REM Backup php.ini
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
copy "%PHP_INI%" "%PHP_INI%.backup.%TIMESTAMP%" >nul 2>&1
echo [OK] Backup created: %PHP_INI%.backup.%TIMESTAMP%
echo.

REM List available extensions
echo Available extensions in %EXT_DIR%:
echo ------------------------------------
dir /b "%EXT_DIR%\php_*.dll" 2>nul
echo.

REM Create PowerShell script to fix php.ini
set TEMP_PS1=%TEMP%\fix-php-%RANDOM%.ps1

echo $iniPath = '%PHP_INI%' > "%TEMP_PS1%"
echo $extDir = '%EXT_DIR%' >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "Reading php.ini from: $iniPath" -ForegroundColor Cyan >> "%TEMP_PS1%"
echo $content = Get-Content $iniPath -Raw -Encoding UTF8 >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "Removing old extension configurations..." -ForegroundColor Yellow >> "%TEMP_PS1%"
echo $lines = $content -split "`n" >> "%TEMP_PS1%"
echo $newLines = @() >> "%TEMP_PS1%"
echo $inExtSection = $false >> "%TEMP_PS1%"
echo foreach ($line in $lines) { >> "%TEMP_PS1%"
echo     if ($line -match '^\s*;?\s*(extension^|zend_extension)\s*=') { >> "%TEMP_PS1%"
echo         # Skip old extension lines >> "%TEMP_PS1%"
echo         continue >> "%TEMP_PS1%"
echo     } >> "%TEMP_PS1%"
echo     $newLines += $line >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo $content = $newLines -join "`n" >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "Setting extension_dir to: $extDir" -ForegroundColor Yellow >> "%TEMP_PS1%"
echo if ($content -match 'extension_dir') { >> "%TEMP_PS1%"
echo     $content = $content -replace '(?m)^;?\s*extension_dir\s*=.*$', "extension_dir = `"$extDir`"" >> "%TEMP_PS1%"
echo } else { >> "%TEMP_PS1%"
echo     $content = "extension_dir = `"$extDir`"`n" + $content >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "" >> "%TEMP_PS1%"
echo Write-Host "Adding extensions..." -ForegroundColor Cyan >> "%TEMP_PS1%"
echo $content += "`n`n; ========================================`n" >> "%TEMP_PS1%"
echo $content += "; Extensions (Auto-configured)`n" >> "%TEMP_PS1%"
echo $content += "; ========================================`n" >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo $dlls = Get-ChildItem "$extDir\php_*.dll" -ErrorAction SilentlyContinue ^| Sort-Object Name >> "%TEMP_PS1%"
echo if ($dlls.Count -eq 0) { >> "%TEMP_PS1%"
echo     Write-Host "[WARNING] No extension DLLs found in $extDir" -ForegroundColor Red >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo foreach ($dll in $dlls) { >> "%TEMP_PS1%"
echo     $name = $dll.Name -replace '^php_', '' -replace '\.dll$', '' >> "%TEMP_PS1%"
echo     if ($name -eq 'opcache') { >> "%TEMP_PS1%"
echo         $content += "zend_extension=$($dll.Name)`n" >> "%TEMP_PS1%"
echo         Write-Host "  [OK] $name (zend_extension)" -ForegroundColor Green >> "%TEMP_PS1%"
echo     } else { >> "%TEMP_PS1%"
echo         $content += "extension=$($dll.Name)`n" >> "%TEMP_PS1%"
echo         Write-Host "  [OK] $name" -ForegroundColor Green >> "%TEMP_PS1%"
echo     } >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo Write-Host "" >> "%TEMP_PS1%"
echo Write-Host "Saving configuration to: $iniPath" -ForegroundColor Cyan >> "%TEMP_PS1%"
echo $content ^| Set-Content $iniPath -NoNewline -Encoding UTF8 >> "%TEMP_PS1%"
echo Write-Host "[OK] Configuration saved successfully" -ForegroundColor Green >> "%TEMP_PS1%"

echo Configuring PHP...
echo.
powershell -ExecutionPolicy Bypass -File "%TEMP_PS1%"
del "%TEMP_PS1%"
del %TEMP%\php-ini-info.txt

echo.
echo ====================================
echo Verification
echo ====================================
echo.

echo Testing PHP...
php -v
echo.

if %errorlevel% equ 0 (
    echo [OK] PHP is working!
    echo.
    echo Loaded extensions:
    php -m
) else (
    echo [ERROR] PHP has errors
)

echo.
echo ====================================
echo Done!
echo ====================================
pause
