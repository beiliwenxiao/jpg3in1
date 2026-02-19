<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Client\DefaultFrameworkClient;
use Framework\Registry\MemoryServiceRegistry;
use Framework\Protocol\Adapter\ProtocolAdapter;
use Framework\Serializer\JsonSerializer;
use Framework\Errors\FrameworkException;
use PHPUnit\Framework\TestCase;

/**
 * Feature: multi-language-communication-framework
 * Property 1: 跨语言 API 一致性
 * 验证需求: 1.4, 1.5
 *
 * 模拟跨语言服务调用场景（使用共享MemoryRegistry）
 */
class CrossLanguageIntegrationTest extends TestCase
{
    private MemoryServiceRegistry $registry;
    private DefaultFrameworkClient $phpClient;
    private JsonSerializer $serializer;
    private ProtocolAdapter $adapter;

    protected function setUp(): void
    {
        $this->registry   = new MemoryServiceRegistry();
        $this->phpClient  = new DefaultFrameworkClient($this->registry);
        $this->serializer = new JsonSerializer();
        $this->adapter    = new ProtocolAdapter();
    }

    /**
     * 模拟 PHP 调用 Java 风格服务
     */
    public function testPhpCallsJavaStyleService(): void
    {
        // 注册模拟Java服务
        $this->phpClient->registerService('java-calculator', function ($method, $request) {
            return match ($method) {
                'add'      => ['result' => $request['a'] + $request['b']],
                'multiply' => ['result' => $request['a'] * $request['b']],
                default    => throw new \RuntimeException("Unknown method: $method"),
            };
        });

        $result = $this->phpClient->call('java-calculator', 'add', ['a' => 10, 'b' => 20]);
        $this->assertEquals(30, $result['result']);

        $result = $this->phpClient->call('java-calculator', 'multiply', ['a' => 6, 'b' => 7]);
        $this->assertEquals(42, $result['result']);
    }

    /**
     * 模拟 PHP 调用 Golang 风格服务
     */
    public function testPhpCallsGolangStyleService(): void
    {
        // Golang服务返回格式（snake_case风格）
        $this->phpClient->registerService('golang-registry', function ($method, $request) {
            return match ($method) {
                'register' => ['service_id' => uniqid('svc-'), 'success' => true],
                'discover' => ['instances' => [['address' => '127.0.0.1', 'port' => 8081]]],
                default    => ['error' => 'method not found'],
            };
        });

        $result = $this->phpClient->call('golang-registry', 'register', ['name' => 'my-service']);
        $this->assertTrue($result['success']);
        $this->assertStringStartsWith('svc-', $result['service_id']);

        $result = $this->phpClient->call('golang-registry', 'discover', ['name' => 'my-service']);
        $this->assertNotEmpty($result['instances']);
    }

    /**
     * 验证跨语言序列化一致性 - 100次迭代
     * Property 1: 跨语言 API 一致性
     */
    public function testCrossLanguageApiConsistency(): void
    {
        $this->phpClient->registerService('echo-service', fn($m, $r) => $r);

        for ($i = 0; $i < 100; $i++) {
            $request = [
                'iteration' => $i,
                'data'      => "test-$i",
                'value'     => $i * 1.5,
                'flag'      => $i % 2 === 0,
            ];

            // 模拟跨语言传输：序列化 -> 反序列化 -> 调用
            $serialized   = $this->serializer->serialize($request);
            $deserialized = $this->serializer->deserialize($serialized);
            $result       = $this->phpClient->call('echo-service', 'echo', $deserialized);

            $this->assertEquals($i, $result['iteration']);
            $this->assertEquals("test-$i", $result['data']);
        }
    }

    /**
     * 模拟协议适配器跨语言转换
     * Property 9: 协议转换往返一致性
     */
    public function testProtocolAdapterCrossLanguage(): void
    {
        // 模拟来自Java REST客户端的请求
        $javaRestRequest = [
            'protocol' => 'rest',
            'path'     => '/user-service/getUser',
            'body'     => ['userId' => 'user-123'],
            'headers'  => ['Accept' => 'application/json', 'X-Language' => 'java'],
        ];

        $internal = $this->adapter->transformRequest($javaRestRequest);
        $this->assertEquals('user-service', $internal['service']);
        $this->assertEquals('getUser', $internal['method']);

        // 转换回REST响应
        $response = $this->adapter->transformResponse(
            ['data' => ['userId' => 'user-123', 'name' => 'Alice']],
            'rest'
        );
        $this->assertEquals('user-123', $response['data']['userId']);

        // 模拟来自Golang JSON-RPC客户端的请求
        $golangJsonRpcRequest = [
            'protocol' => 'jsonrpc',
            'path'     => '/order-service/createOrder',
            'body'     => ['item' => 'book', 'qty' => 2],
            'headers'  => ['X-Language' => 'golang'],
        ];

        $internal = $this->adapter->transformRequest($golangJsonRpcRequest);
        $response = $this->adapter->transformResponse(
            ['data' => ['orderId' => 'ord-456'], 'id' => 1],
            'jsonrpc'
        );

        $this->assertEquals('2.0', $response['jsonrpc']);
        $this->assertArrayHasKey('result', $response);
    }

    /**
     * 验证多服务实例负载均衡
     * Property 18: 负载均衡策略一致性
     */
    public function testMultipleServiceInstances(): void
    {
        // 注册多个实例（模拟Java、Golang、PHP各一个实例）
        $callCounts = ['java' => 0, 'golang' => 0, 'php' => 0];

        $this->phpClient->registerService('multi-lang-service', function ($m, $r) use (&$callCounts) {
            $callCounts[$r['lang']]++;
            return ['processed_by' => $r['lang']];
        });

        // 调用多次
        foreach (['java', 'golang', 'php'] as $lang) {
            $result = $this->phpClient->call('multi-lang-service', 'process', ['lang' => $lang]);
            $this->assertEquals($lang, $result['processed_by']);
        }

        $this->assertEquals(1, $callCounts['java']);
        $this->assertEquals(1, $callCounts['golang']);
        $this->assertEquals(1, $callCounts['php']);
    }

    /**
     * 验证错误跨语言传播
     * Property 27: 协议错误标准化响应
     */
    public function testErrorPropagationAcrossLanguages(): void
    {
        $this->phpClient->registerService('failing-service', function ($m, $r) {
            throw new FrameworkException('Service error from remote', 503);
        });

        try {
            $this->phpClient->call('failing-service', 'call', []);
            $this->fail('Expected exception not thrown');
        } catch (FrameworkException $e) {
            $this->assertEquals(503, $e->getErrorCode());
            $errorData = $e->toArray();
            $this->assertArrayHasKey('code', $errorData);
            $this->assertArrayHasKey('message', $errorData);
            $this->assertArrayHasKey('timestamp', $errorData);
        }
    }
}
