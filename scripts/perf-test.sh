#!/bin/bash
# 性能测试脚本 - 测试各SDK的吞吐量和延迟

set -e

echo "=== Multi-Language Framework Performance Test ==="
echo ""

ITERATIONS=${1:-1000}
echo "Iterations: $ITERATIONS"
echo ""

# PHP SDK 性能测试
echo "--- PHP SDK Performance ---"
if command -v php &> /dev/null; then
    php -r "
    require 'php-sdk/vendor/autoload.php';
    use Framework\Serializer\JsonSerializer;
    use Framework\Registry\MemoryServiceRegistry;
    use Framework\Client\DefaultFrameworkClient;

    \$serializer = new JsonSerializer();
    \$registry = new MemoryServiceRegistry();
    \$client = new DefaultFrameworkClient(\$registry);
    \$client->registerService('bench', fn(\$m, \$r) => \$r);

    \$n = $ITERATIONS;
    \$data = ['key' => 'value', 'num' => 42, 'arr' => [1,2,3]];

    // Serialization benchmark
    \$start = microtime(true);
    for (\$i = 0; \$i < \$n; \$i++) {
        \$s = \$serializer->serialize(\$data);
        \$serializer->deserialize(\$s);
    }
    \$elapsed = (microtime(true) - \$start) * 1000;
    echo 'Serialization: ' . \$n . ' ops in ' . round(\$elapsed, 2) . 'ms (' . round(\$n/\$elapsed*1000) . ' ops/sec)' . PHP_EOL;

    // Service call benchmark
    \$start = microtime(true);
    for (\$i = 0; \$i < \$n; \$i++) {
        \$client->call('bench', 'echo', \$data);
    }
    \$elapsed = (microtime(true) - \$start) * 1000;
    echo 'Service call: ' . \$n . ' ops in ' . round(\$elapsed, 2) . 'ms (' . round(\$n/\$elapsed*1000) . ' ops/sec)' . PHP_EOL;
    "
else
    echo "PHP not found, skipping"
fi

echo ""

# Golang SDK 性能测试
echo "--- Golang SDK Performance ---"
if command -v go &> /dev/null; then
    (cd golang-sdk && go test ./serializer/... -bench=. -benchtime=3s -benchmem -run=^$)
else
    echo "Go not found, skipping"
fi

echo ""

# Java SDK 性能测试
echo "--- Java SDK Performance ---"
if command -v mvn &> /dev/null; then
    mvn -f java-sdk/pom.xml test -Dtest="*PerformanceTest" -q 2>/dev/null || echo "No performance tests defined"
else
    echo "Maven not found, skipping"
fi

echo ""
echo "=== Performance Test Complete ==="
