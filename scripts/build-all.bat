@echo off
setlocal

echo === 构建多语言通信框架 ===

echo 构建 Java SDK...
cd java-sdk
call mvn clean install -DskipTests
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo 构建 Golang SDK...
cd golang-sdk
call go mod tidy
call go build ./...
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo 构建 PHP SDK...
cd php-sdk
call composer install
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo === 构建完成 ===
