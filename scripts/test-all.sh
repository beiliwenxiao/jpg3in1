#!/bin/bash
set -e

echo "=== Running All SDK Tests ==="
echo ""

PASS=0
FAIL=0

run_test() {
    local name=$1
    shift
    echo "--- $name ---"
    if "$@"; then
        echo "[PASS] $name"
        PASS=$((PASS+1))
    else
        echo "[FAIL] $name"
        FAIL=$((FAIL+1))
    fi
    echo ""
}

# Java SDK
run_test "Java SDK" mvn -f java-sdk/pom.xml test -q

# Golang SDK
run_test "Golang SDK" bash -c "cd golang-sdk && go test \$(go list ./... | grep -v examples) -timeout 120s"

# PHP SDK
PHP_BIN=${PHP_BIN:-php}
run_test "PHP SDK" $PHP_BIN php-sdk/vendor/bin/phpunit --configuration php-sdk/phpunit.xml --colors=never

echo "==================================="
echo "Results: $PASS passed, $FAIL failed"
echo "==================================="

if [ $FAIL -ne 0 ]; then
    exit 1
fi
