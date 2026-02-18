<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Registry\MemoryServiceRegistry;
use PHPUnit\Framework\TestCase;

/**
 * Feature: multi-language-communication-framework
 * Property 13: 服务注册可发现性
 * Property 14: 服务注销不可见性
 */
class RegistryTest extends TestCase
{
    private MemoryServiceRegistry $registry;

    protected function setUp(): void
    {
        $this->registry = new MemoryServiceRegistry();
    }

    public function testRegisterAndDiscover(): void
    {
        $this->registry->register([
            'id' => 'svc-1', 'name' => 'my-service',
            'address' => '127.0.0.1', 'port' => 8080,
        ]);

        $instances = $this->registry->discover('my-service');
        $this->assertCount(1, $instances);
        $this->assertEquals('my-service', $instances[0]['name']);
    }

    public function testDeregisterRemovesService(): void
    {
        $this->registry->register([
            'id' => 'svc-1', 'name' => 'my-service',
            'address' => '127.0.0.1', 'port' => 8080,
        ]);
        $this->registry->deregister('svc-1');

        $instances = $this->registry->discover('my-service');
        $this->assertEmpty($instances);
    }

    public function testDiscoverUnknownServiceReturnsEmpty(): void
    {
        $instances = $this->registry->discover('nonexistent');
        $this->assertEmpty($instances);
    }

    public function testMultipleInstancesRegistration(): void
    {
        for ($i = 1; $i <= 3; $i++) {
            $this->registry->register([
                'id' => "svc-$i", 'name' => 'scalable-service',
                'address' => '127.0.0.1', 'port' => 8080 + $i,
            ]);
        }

        $instances = $this->registry->discover('scalable-service');
        $this->assertCount(3, $instances);
    }

    public function testHealthCheck(): void
    {
        $this->registry->register([
            'id' => 'svc-1', 'name' => 'healthy-service',
            'address' => '127.0.0.1', 'port' => 8080,
        ]);

        $this->assertTrue($this->registry->healthCheck('svc-1'));
        $this->assertFalse($this->registry->healthCheck('nonexistent'));
    }
}
