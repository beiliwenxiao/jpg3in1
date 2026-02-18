<?php

declare(strict_types=1);

namespace Framework\Security;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;

/**
 * JWT认证
 */
class JwtAuth
{
    public function __construct(
        private string $secret,
        private string $algorithm = 'HS256',
        private int $ttl = 3600
    ) {}

    public function generate(array $payload): string
    {
        $payload['iat'] = time();
        $payload['exp'] = time() + $this->ttl;
        return JWT::encode($payload, $this->secret, $this->algorithm);
    }

    public function verify(string $token): array
    {
        try {
            $decoded = JWT::decode($token, new Key($this->secret, $this->algorithm));
            return (array) $decoded;
        } catch (\Throwable $e) {
            throw new FrameworkException(
                'Invalid token: ' . $e->getMessage(),
                ErrorCode::UNAUTHORIZED
            );
        }
    }

    public function validateFromHeader(string $authHeader): array
    {
        if (!str_starts_with($authHeader, 'Bearer ')) {
            throw new FrameworkException('Missing Bearer token', ErrorCode::UNAUTHORIZED);
        }
        return $this->verify(substr($authHeader, 7));
    }
}
