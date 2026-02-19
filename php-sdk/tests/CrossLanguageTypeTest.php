<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Serializer\JsonSerializer;
use PHPUnit\Framework\TestCase;

/**
 * Feature: multi-language-communication-framework
 * Property 2: 跨语言数据类型往返一致性
 * 验证需求: 1.5
 */
class CrossLanguageTypeTest extends TestCase
{
    private JsonSerializer $serializer;

    protected function setUp(): void
    {
        $this->serializer = new JsonSerializer();
    }

    /**
     * Property 2: 跨语言数据类型往返一致性 - 100次迭代
     * 模拟Java/Golang序列化后PHP反序列化的场景
     */
    public function testBasicTypeRoundTrip(): void
    {
        $testCases = [
            // 整数
            ['type' => 'int32',  'value' => 42],
            ['type' => 'int32',  'value' => -100],
            ['type' => 'int32',  'value' => 0],
            // 浮点数
            ['type' => 'float64', 'value' => 3.14],
            ['type' => 'float64', 'value' => -2.718],
            // 字符串
            ['type' => 'string', 'value' => 'hello world'],
            ['type' => 'string', 'value' => ''],
            ['type' => 'string', 'value' => 'unicode: 你好世界'],
            // 布尔值
            ['type' => 'bool',   'value' => true],
            ['type' => 'bool',   'value' => false],
        ];

        for ($i = 0; $i < 100; $i++) {
            $case = $testCases[$i % count($testCases)];
            $serialized = $this->serializer->serialize($case['value']);
            $deserialized = $this->serializer->deserialize($serialized);
            $this->assertEquals($case['value'], $deserialized,
                "Type {$case['type']} round trip failed at iteration $i"
            );
        }
    }

    public function testComplexTypeRoundTrip(): void
    {
        // 模拟跨语言传输的复杂对象
        $javaStyleObject = [
            'userId'    => 'user-123',
            'userName'  => 'Alice',
            'age'       => 30,
            'score'     => 98.5,
            'active'    => true,
            'tags'      => ['admin', 'user'],
            'metadata'  => ['key' => 'value', 'count' => 5],
            'createdAt' => '2024-01-01T00:00:00Z',
        ];

        $serialized = $this->serializer->serialize($javaStyleObject);
        $deserialized = $this->serializer->deserialize($serialized);

        $this->assertEquals($javaStyleObject['userId'], $deserialized['userId']);
        $this->assertEquals($javaStyleObject['score'], $deserialized['score']);
        $this->assertEquals($javaStyleObject['tags'], $deserialized['tags']);
        $this->assertEquals($javaStyleObject['metadata'], $deserialized['metadata']);
    }

    public function testNullHandling(): void
    {
        $data = ['name' => 'test', 'optional' => null, 'value' => 42];
        $serialized = $this->serializer->serialize($data);
        $deserialized = $this->serializer->deserialize($serialized);

        $this->assertNull($deserialized['optional']);
        $this->assertEquals(42, $deserialized['value']);
    }

    public function testNestedArrayRoundTrip(): void
    {
        // 模拟Golang struct序列化后的格式
        $golangStyleData = [
            'services' => [
                ['name' => 'service-a', 'port' => 8080, 'healthy' => true],
                ['name' => 'service-b', 'port' => 8081, 'healthy' => false],
            ],
            'total' => 2,
        ];

        $serialized = $this->serializer->serialize($golangStyleData);
        $deserialized = $this->serializer->deserialize($serialized);

        $this->assertCount(2, $deserialized['services']);
        $this->assertEquals('service-a', $deserialized['services'][0]['name']);
        $this->assertEquals(8081, $deserialized['services'][1]['port']);
    }

    public function testErrorTypeMapping(): void
    {
        // 验证错误码映射一致性
        $errorCodes = [400, 401, 403, 404, 500, 503, 600, 601, 602, 603];

        foreach ($errorCodes as $code) {
            $errorObj = [
                'code'      => $code,
                'message'   => "Error $code",
                'timestamp' => time(),
            ];

            $serialized = $this->serializer->serialize($errorObj);
            $deserialized = $this->serializer->deserialize($serialized);

            $this->assertEquals($code, $deserialized['code']);
        }
    }
}
