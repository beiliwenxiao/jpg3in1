@echo off
REM 快速检查PHP环境
echo === PHP环境检查 ===
echo.

echo 1. PHP版本:
php -v
echo.

echo 2. 已加载的扩展:
php -m
echo.

echo 3. PHP配置文件位置:
php --ini
echo.

echo 4. Composer版本:
composer --version 2>nul
if %errorlevel% neq 0 (
    echo Composer未安装
)
echo.

echo === 检查完成 ===
pause
