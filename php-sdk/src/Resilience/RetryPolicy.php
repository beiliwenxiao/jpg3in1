<?php

declare(strict_types=1);

namespace Framework\Resilience;

use Framework\Errors\ErrorCode;

/**
 * 重试策略 - 指数退避
 */
class RetryPolicy
{
    public function __construct(
        private int $maxAttempts = 3,
        private int $initialDelayMs = 100,
        private int $maxDelayMs = 5000,
        private float $multiplier = 2.0,
        private array $retryableErrors = [
            ErrorCode::TIMEOUT,
            ErrorCode::SERVICE_UNAVAILABLE,
            ErrorCode::CONNECTION_ERROR,
        ]
    ) {}

    public function execute(callable $fn): mixed
    {
        $attempt = 0;
        $delay = $this->initialDelayMs;

        while (true) {
            try {
                return $fn();
            } catch (\Throwable $e) {
                $attempt++;
                $code = $e->getCode();

                if ($attempt >= $this->maxAttempts || !in_array($code, $this->retryableErrors)) {
                    throw $e;
                }

                usleep($delay * 1000);
                $delay = (int) min($delay * $this->multiplier, $this->maxDelayMs);
            }
        }
    }

    public function getMaxAttempts(): int { return $this->maxAttempts; }
    public function getInitialDelayMs(): int { return $this->initialDelayMs; }
    public function getMaxDelayMs(): int { return $this->maxDelayMs; }
    public function getMultiplier(): float { return $this->multiplier; }
}
