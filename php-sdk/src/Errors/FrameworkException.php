<?php

declare(strict_types=1);

namespace Framework\Errors;

/**
 * 框架统一异常类
 */
class FrameworkException extends \RuntimeException
{
    private int $errorCode;
    private mixed $details;
    private ?FrameworkException $cause;

    public function __construct(
        string $message,
        int $errorCode = ErrorCode::INTERNAL_ERROR,
        mixed $details = null,
        ?FrameworkException $cause = null,
        ?\Throwable $previous = null
    ) {
        parent::__construct($message, $errorCode, $previous);
        $this->errorCode = $errorCode;
        $this->details = $details;
        $this->cause = $cause;
    }

    public function getErrorCode(): int
    {
        return $this->errorCode;
    }

    public function getDetails(): mixed
    {
        return $this->details;
    }

    public function getCause(): ?FrameworkException
    {
        return $this->cause;
    }

    public function toArray(): array
    {
        return [
            'code'      => $this->errorCode,
            'message'   => $this->getMessage(),
            'details'   => $this->details,
            'cause'     => $this->cause?->toArray(),
            'timestamp' => time(),
        ];
    }
}
