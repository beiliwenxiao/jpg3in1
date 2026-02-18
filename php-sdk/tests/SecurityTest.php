<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Security\JwtAuth;
use Framework\Security\ApiKeyAuth;
use Framework\Security\Rbac;
use Framework\Errors\FrameworkException;
use PHPUnit\Framework\TestCase;

class SecurityTest extends TestCase
{
    public function testJwtGenerateAndVerify(): void
    {
        $jwt = new JwtAuth('test-secret-key-must-be-at-least-32-chars!!');
        $token = $jwt->generate(['userId' => 'user1', 'role' => 'admin']);
        $payload = $jwt->verify($token);

        $this->assertEquals('user1', $payload['userId']);
        $this->assertEquals('admin', $payload['role']);
    }

    public function testJwtInvalidTokenThrows(): void
    {
        $jwt = new JwtAuth('test-secret-key');
        $this->expectException(FrameworkException::class);
        $jwt->verify('invalid.token.here');
    }

    public function testApiKeyAuth(): void
    {
        $auth = new ApiKeyAuth();
        $auth->addKey('my-api-key', ['owner' => 'user1']);

        $meta = $auth->verify('my-api-key');
        $this->assertEquals('user1', $meta['owner']);
    }

    public function testApiKeyInvalidThrows(): void
    {
        $auth = new ApiKeyAuth();
        $this->expectException(FrameworkException::class);
        $auth->verify('wrong-key');
    }

    public function testRbacAuthorize(): void
    {
        $rbac = new Rbac();
        $rbac->defineRole('admin', ['read', 'write', 'delete']);
        $rbac->assignRole('user1', 'admin');

        $this->assertTrue($rbac->hasPermission('user1', 'write'));
        $rbac->authorize('user1', 'delete'); // no exception
        $this->assertTrue(true);
    }

    public function testRbacDeniesUnauthorized(): void
    {
        $rbac = new Rbac();
        $rbac->defineRole('viewer', ['read']);
        $rbac->assignRole('user2', 'viewer');

        $this->expectException(FrameworkException::class);
        $rbac->authorize('user2', 'delete');
    }
}
