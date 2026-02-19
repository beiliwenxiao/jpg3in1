@echo off
setlocal enabledelayedexpansion

REM 切换到项目根目录（脚本所在目录的上级）
pushd "%~dp0.."

set ITERATIONS=1000
if not "%1"=="" set ITERATIONS=%1

echo === Multi-Language Framework Performance Test ===
echo.
echo Iterations: %ITERATIONS%
echo.
echo --- PHP SDK Performance ---
where php >nul 2>&1
if %errorlevel% equ 0 (
    php -r "require 'php-sdk/vendor/autoload.php'; use Framework\Serializer\JsonSerializer; use Framework\Registry\MemoryServiceRegistry; use Framework\Client\DefaultFrameworkClient; $serializer = new JsonSerializer(); $registry = new MemoryServiceRegistry(); $client = new DefaultFrameworkClient($registry); $client->registerService('bench', fn($m,$r) => $r); $n = %ITERATIONS%; $data = ['key'=>'value','num'=>42,'arr'=>[1,2,3]]; $start = microtime(true); for ($i=0;$i<$n;$i++){$s=$serializer->serialize($data);$serializer->deserialize($s);} $e=(microtime(true)-$start)*1000; echo 'Serialization: '.$n.' ops in '.round($e,2).'ms ('.round($n/$e*1000).' ops/sec)'.PHP_EOL; $start=microtime(true); for($i=0;$i<$n;$i++){$client->call('bench','echo',$data);} $e=(microtime(true)-$start)*1000; echo 'Service call: '.$n.' ops in '.round($e,2).'ms ('.round($n/$e*1000).' ops/sec)'.PHP_EOL;"
) else (
    echo PHP not found, skipping
)

echo.
echo --- Golang SDK Performance ---
where go >nul 2>&1
if %errorlevel% equ 0 (
    pushd golang-sdk
    go test ./serializer/... -bench=. -benchtime=3s -benchmem -run=^$
    popd
) else (
    echo Go not found, skipping
)

echo.
echo --- Java SDK Performance ---
where mvn >nul 2>&1
if %errorlevel% equ 0 (
    mvn -f java-sdk/pom.xml test -Dtest=*PerformanceTest -q 2>nul || echo No performance tests defined
) else (
    echo Maven not found, skipping
)

echo.
echo === Performance Test Complete ===
popd
pause
