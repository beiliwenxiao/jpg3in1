<?php

declare(strict_types=1);

namespace Framework\Security;

use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;

/**
 * API Key认证
 */
class ApiKeyAuth
{
    private array $validKeys = [];

    public function addKey(string $key, array $metadata = []): void
    {
        $this->validKeys[$key] = array_merge($metadata, ['createdAt' => time()]);
    }

    public function removeKey(string $key): void
    {
        unset($this->validKeys[$key]);
    }

    public function verify(string $apiKey): array
    {
        if (!isset($this->validKeys[$apiKey])) {
            throw new FrameworkException('Invalid API key', ErrorCode::UNAUTHORIZED);
        }
        return $this->validKeys[$apiKey];
    }

    public function validateFromHeader(array $headers): array
    {
        $key = $headers['X-API-Key'] ?? $headers['x-api-key'] ?? null;
        if ($key === null) {
            throw new FrameworkException('Missing API key', ErrorCode::UNAUTHORIZED);
        }
        return $this->verify($key);
    }
}
