<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Resilience\CircuitBreaker;
use Framework\Resilience\RetryPolicy;
use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;
use PHPUnit\Framework\TestCase;

class ResilienceTest extends TestCase
{
    public function testCircuitBreakerClosedByDefault(): void
    {
        $cb = new CircuitBreaker();
        $this->assertEquals('closed', $cb->getState());
    }

    public function testCircuitBreakerOpensAfterFailures(): void
    {
        $cb = new CircuitBreaker(failureThreshold: 3);
        $fn = fn() => throw new FrameworkException('fail', ErrorCode::CONNECTION_ERROR);

        for ($i = 0; $i < 3; $i++) {
            try { $cb->execute($fn); } catch (\Throwable) {}
        }

        $this->assertEquals('open', $cb->getState());
    }

    public function testCircuitBreakerBlocksWhenOpen(): void
    {
        $cb = new CircuitBreaker(failureThreshold: 1, timeoutSeconds: 9999);
        try { $cb->execute(fn() => throw new \RuntimeException('fail')); } catch (\Throwable) {}

        $this->expectException(FrameworkException::class);
        $cb->execute(fn() => 'ok');
    }

    public function testRetryPolicySucceedsOnRetry(): void
    {
        $attempts = 0;
        $policy = new RetryPolicy(maxAttempts: 3, initialDelayMs: 1);

        $result = $policy->execute(function () use (&$attempts) {
            $attempts++;
            if ($attempts < 3) {
                throw new FrameworkException('retry', ErrorCode::CONNECTION_ERROR);
            }
            return 'success';
        });

        $this->assertEquals('success', $result);
        $this->assertEquals(3, $attempts);
    }

    public function testRetryPolicyThrowsAfterMaxAttempts(): void
    {
        $policy = new RetryPolicy(maxAttempts: 2, initialDelayMs: 1);

        $this->expectException(FrameworkException::class);
        $policy->execute(fn() => throw new FrameworkException('fail', ErrorCode::CONNECTION_ERROR));
    }
}
