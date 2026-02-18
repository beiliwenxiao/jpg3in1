<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Protocol\External\JsonRpcHandler;
use Framework\Protocol\External\RestHandler;
use Framework\Protocol\External\WebSocketHandler;
use Framework\Protocol\Adapter\ProtocolAdapter;
use PHPUnit\Framework\TestCase;

/**
 * Feature: multi-language-communication-framework
 * Property 3: 外部协议处理完整性
 * Property 9: 协议转换往返一致性
 */
class ProtocolTest extends TestCase
{
    public function testJsonRpcValidRequest(): void
    {
        $handler = new JsonRpcHandler();
        $handler->register('add', fn($p) => $p[0] + $p[1]);

        $response = json_decode($handler->handle(json_encode([
            'jsonrpc' => '2.0', 'method' => 'add', 'params' => [1, 2], 'id' => 1
        ])), true);

        $this->assertEquals('2.0', $response['jsonrpc']);
        $this->assertEquals(3, $response['result']);
        $this->assertEquals(1, $response['id']);
    }

    public function testJsonRpcMethodNotFound(): void
    {
        $handler = new JsonRpcHandler();
        $response = json_decode($handler->handle(json_encode([
            'jsonrpc' => '2.0', 'method' => 'unknown', 'params' => [], 'id' => 1
        ])), true);

        $this->assertEquals(-32601, $response['error']['code']);
    }

    public function testJsonRpcParseError(): void
    {
        $handler = new JsonRpcHandler();
        $response = json_decode($handler->handle('{invalid}'), true);
        $this->assertEquals(-32700, $response['error']['code']);
    }

    public function testRestHandlerSupportedMethods(): void
    {
        $handler = new RestHandler();
        $methods = $handler->getSupportedMethods();

        foreach (['GET', 'POST', 'PUT', 'DELETE', 'PATCH'] as $method) {
            $this->assertContains($method, $methods);
        }
    }

    public function testWebSocketHandlerFormats(): void
    {
        $handler = new WebSocketHandler();
        $formats = $handler->getSupportedFormats();
        $this->assertContains('text', $formats);
        $this->assertContains('binary', $formats);
    }

    public function testProtocolAdapterTransformRequest(): void
    {
        $adapter = new ProtocolAdapter();
        $external = [
            'protocol' => 'rest',
            'path'     => '/my-service/my-method',
            'body'     => ['key' => 'value'],
            'headers'  => ['Content-Type' => 'application/json'],
        ];

        $internal = $adapter->transformRequest($external);

        $this->assertEquals('my-service', $internal['service']);
        $this->assertEquals('my-method', $internal['method']);
        $this->assertArrayHasKey('traceId', $internal);
    }

    public function testProtocolAdapterRoundTrip(): void
    {
        // Property 9: 协议转换往返一致性 - 100次迭代
        $adapter = new ProtocolAdapter();

        for ($i = 0; $i < 100; $i++) {
            $external = [
                'protocol' => 'rest',
                'path'     => "/service-$i/method-$i",
                'body'     => ['iteration' => $i],
                'headers'  => [],
            ];

            $internal = $adapter->transformRequest($external);
            $response = $adapter->transformResponse(['data' => $internal['payload']], 'rest');

            $this->assertArrayHasKey('data', $response);
            $this->assertEquals($i, $response['data']['iteration']);
        }
    }

    public function testProtocolAdapterSupportedProtocols(): void
    {
        $adapter = new ProtocolAdapter();
        $protocols = $adapter->getSupportedProtocols();

        foreach (['rest', 'websocket', 'jsonrpc', 'mqtt'] as $p) {
            $this->assertContains($p, $protocols);
        }
    }
}
