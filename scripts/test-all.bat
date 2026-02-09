@echo off
setlocal

echo === 运行所有测试 ===

echo 测试 Java SDK...
cd java-sdk
call mvn test
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo 测试 Golang SDK...
cd golang-sdk
call go test ./...
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo 测试 PHP SDK...
cd php-sdk
call composer test
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo === 测试完成 ===
