@echo off
setlocal enabledelayedexpansion

echo === Running All SDK Tests ===
echo.

set PASS=0
set FAIL=0
set PHP_BIN=php

REM Allow override via environment variable
if not "%PHP_HOME%"=="" set PHP_BIN=%PHP_HOME%\php.exe

echo --- Java SDK ---
call mvn -f java-sdk\pom.xml test -q
if %errorlevel% equ 0 (
    echo [PASS] Java SDK
    set /a PASS+=1
) else (
    echo [FAIL] Java SDK
    set /a FAIL+=1
)
echo.

echo --- Golang SDK ---
pushd golang-sdk
for /f "delims=" %%p in ('go list ./... ^| findstr /v "examples"') do set GOPKGS=!GOPKGS! %%p
go test %GOPKGS% -timeout 120s
popd
if %errorlevel% equ 0 (
    echo [PASS] Golang SDK
    set /a PASS+=1
) else (
    echo [FAIL] Golang SDK
    set /a FAIL+=1
)
echo.

echo --- PHP SDK ---
"%PHP_BIN%" php-sdk\vendor\bin\phpunit --configuration php-sdk\phpunit.xml --colors=never
if %errorlevel% equ 0 (
    echo [PASS] PHP SDK
    set /a PASS+=1
) else (
    echo [FAIL] PHP SDK
    set /a FAIL+=1
)
echo.

echo ===================================
echo Results: %PASS% passed, %FAIL% failed
echo ===================================

if %FAIL% neq 0 exit /b 1
