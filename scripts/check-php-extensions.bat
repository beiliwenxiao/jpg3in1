@echo off
setlocal enabledelayedexpansion

echo ====================================
echo PHP Extensions Checker
echo ====================================
echo.

set PHP_PATH=C:\php
set EXT_DIR=%PHP_PATH%\ext

if not exist "%EXT_DIR%" (
    echo [ERROR] Extension directory not found: %EXT_DIR%
    pause
    exit /b 1
)

echo Checking available extension DLLs in: %EXT_DIR%
echo.

echo Available Extensions:
echo ------------------------------------
dir /b "%EXT_DIR%\php_*.dll" 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] No extension DLLs found
)

echo.
echo ====================================
pause
