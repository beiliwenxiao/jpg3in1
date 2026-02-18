<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Client\DefaultFrameworkClient;
use Framework\Registry\MemoryServiceRegistry;
use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;
use PHPUnit\Framework\TestCase;

/**
 * Feature: multi-language-communication-framework
 * Property 1: 跨语言 API 一致性
 */
class ClientTest extends TestCase
{
    private MemoryServiceRegistry $registry;
    private DefaultFrameworkClient $client;

    protected function setUp(): void
    {
        $this->registry = new MemoryServiceRegistry();
        $this->client = new DefaultFrameworkClient($this->registry);
    }

    public function testRegisterService(): void
    {
        $this->client->registerService('test-service', fn($method, $req) => ['result' => 'ok']);
        $instances = $this->registry->discover('test-service');
        $this->assertNotEmpty($instances);
        $this->assertEquals('test-service', $instances[0]['name']);
    }

    public function testCallRegisteredService(): void
    {
        $this->client->registerService('calc', fn($method, $req) => ['sum' => $req['a'] + $req['b']]);
        $result = $this->client->call('calc', 'add', ['a' => 1, 'b' => 2]);
        $this->assertEquals(['sum' => 3], $result);
    }

    public function testCallUnknownServiceThrows(): void
    {
        $this->expectException(FrameworkException::class);
        $this->client->call('unknown-service', 'method', []);
    }

    public function testCallAsyncReturnsPromise(): void
    {
        $this->client->registerService('async-svc', fn($m, $r) => ['ok' => true]);
        $promise = $this->client->callAsync('async-svc', 'test', []);
        $this->assertNotNull($promise);
    }

    public function testStartAndShutdown(): void
    {
        $this->client->start();
        $this->client->shutdown();
        $this->assertTrue(true); // no exception
    }
}
