@echo off
setlocal enabledelayedexpansion

echo ====================================
echo Smart PHP Configuration Fix
echo ====================================
echo.

set PHP_PATH=C:\php
set PHP_EXE=%PHP_PATH%\php.exe
set PHP_INI=%PHP_PATH%\php.ini
set EXT_DIR=%PHP_PATH%\ext

REM Check PHP
if not exist "%PHP_EXE%" (
    echo [ERROR] PHP not found at: %PHP_EXE%
    pause
    exit /b 1
)

echo [OK] Found PHP
echo.

REM Check php.ini
if not exist "%PHP_INI%" (
    echo [INFO] Creating php.ini from template...
    if exist "%PHP_PATH%\php.ini-development" (
        copy "%PHP_PATH%\php.ini-development" "%PHP_INI%" >nul
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
echo [OK] Backup created
echo.

REM Check available extensions
echo Checking available extensions...
echo.

set TEMP_LIST=%TEMP%\php_ext_list_%RANDOM%.txt
dir /b "%EXT_DIR%\php_*.dll" > "%TEMP_LIST%" 2>nul

REM Create PowerShell script to fix php.ini
set TEMP_PS1=%TEMP%\fix-php-%RANDOM%.ps1

echo $iniPath = '%PHP_INI%' > "%TEMP_PS1%"
echo $extDir = '%EXT_DIR%' >> "%TEMP_PS1%"
echo $content = Get-Content $iniPath -Raw -Encoding UTF8 >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo # Remove all existing extension lines >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*extension\s*=\s*\w+.*$', '' >> "%TEMP_PS1%"
echo $content = $content -replace '(?m)^;?\s*zend_extension\s*=\s*\w+.*$', '' >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo # Add extension_dir >> "%TEMP_PS1%"
echo if ($content -notmatch 'extension_dir') { >> "%TEMP_PS1%"
echo     $content = "extension_dir = `"$extDir`"`n" + $content >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo # Add extensions section >> "%TEMP_PS1%"
echo $content += "`n`n; === Extensions (Auto-configured) ===`n" >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo # Check and add each extension >> "%TEMP_PS1%"
echo $extensions = @( >> "%TEMP_PS1%"

REM Add extensions that exist
for /f "delims=" %%i in ('dir /b "%EXT_DIR%\php_*.dll" 2^>nul') do (
    set "dll=%%i"
    set "name=!dll:php_=!"
    set "name=!name:.dll=!"
    
    REM Special handling for opcache
    if /i "!name!"=="opcache" (
        echo     @{name='opcache'; dll='%%i'; type='zend'}, >> "%TEMP_PS1%"
    ) else (
        echo     @{name='!name!'; dll='%%i'; type='ext'}, >> "%TEMP_PS1%"
    )
)

echo ) >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo foreach ($ext in $extensions) { >> "%TEMP_PS1%"
echo     $dllPath = Join-Path $extDir $ext.dll >> "%TEMP_PS1%"
echo     if (Test-Path $dllPath) { >> "%TEMP_PS1%"
echo         if ($ext.type -eq 'zend') { >> "%TEMP_PS1%"
echo             $content += "zend_extension=$($ext.dll)`n" >> "%TEMP_PS1%"
echo             Write-Host "[OK] Enabled: $($ext.name) (zend)" >> "%TEMP_PS1%"
echo         } else { >> "%TEMP_PS1%"
echo             $content += "extension=$($ext.dll)`n" >> "%TEMP_PS1%"
echo             Write-Host "[OK] Enabled: $($ext.name)" >> "%TEMP_PS1%"
echo         } >> "%TEMP_PS1%"
echo     } >> "%TEMP_PS1%"
echo } >> "%TEMP_PS1%"
echo. >> "%TEMP_PS1%"
echo $content ^| Set-Content $iniPath -NoNewline -Encoding UTF8 >> "%TEMP_PS1%"

echo Configuring extensions...
powershell -ExecutionPolicy Bypass -File "%TEMP_PS1%"
del "%TEMP_PS1%"
del "%TEMP_LIST%"

echo.
echo [OK] Configuration complete
echo.

REM Verify
echo ====================================
echo Verification
echo ====================================
echo.

echo PHP Version:
"%PHP_EXE%" -v 2>&1 | findstr /v "Warning"
echo.

echo Loaded Extensions:
"%PHP_EXE%" -m 2>&1 | findstr /v "Warning"
echo.

echo ====================================
echo Done
echo ====================================
echo.
echo If you see warnings above, some extensions
echo may require additional DLL dependencies.
echo.
echo For Webman framework, you need at least:
echo - mbstring
echo - openssl  
echo - pdo
echo - json (built-in)
echo.
pause
