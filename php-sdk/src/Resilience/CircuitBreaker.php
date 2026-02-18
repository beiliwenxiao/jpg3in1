<?php

declare(strict_types=1);

namespace Framework\Resilience;

use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;

/**
 * 熔断器 - 三态状态机
 */
class CircuitBreaker
{
    private string $state = 'closed'; // closed, open, half-open
    private int $failureCount = 0;
    private int $successCount = 0;
    private ?int $openedAt = null;

    public function __construct(
        private int $failureThreshold = 5,
        private int $successThreshold = 2,
        private int $timeoutSeconds = 60
    ) {}

    public function execute(callable $fn): mixed
    {
        if (!$this->allowRequest()) {
            throw new FrameworkException(
                'Circuit breaker is open',
                ErrorCode::SERVICE_UNAVAILABLE
            );
        }

        try {
            $result = $fn();
            $this->recordSuccess();
            return $result;
        } catch (\Throwable $e) {
            $this->recordFailure();
            throw $e;
        }
    }

    public function allowRequest(): bool
    {
        if ($this->state === 'closed') {
            return true;
        }

        if ($this->state === 'open') {
            if (time() - $this->openedAt >= $this->timeoutSeconds) {
                $this->state = 'half-open';
                $this->successCount = 0;
                return true;
            }
            return false;
        }

        // half-open: allow limited requests
        return true;
    }

    public function recordSuccess(): void
    {
        if ($this->state === 'half-open') {
            $this->successCount++;
            if ($this->successCount >= $this->successThreshold) {
                $this->state = 'closed';
                $this->failureCount = 0;
            }
        } else {
            $this->failureCount = 0;
        }
    }

    public function recordFailure(): void
    {
        $this->failureCount++;
        if ($this->failureCount >= $this->failureThreshold) {
            $this->state = 'open';
            $this->openedAt = time();
        }
    }

    public function getState(): string { return $this->state; }
    public function getFailureCount(): int { return $this->failureCount; }
}
