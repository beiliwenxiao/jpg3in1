<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Serializer\JsonSerializer;
use Framework\Errors\FrameworkException;
use PHPUnit\Framework\TestCase;

/**
 * Feature: multi-language-communication-framework
 * Property 7: 序列化往返一致性
 */
class SerializerTest extends TestCase
{
    private JsonSerializer $serializer;

    protected function setUp(): void
    {
        $this->serializer = new JsonSerializer();
    }

    public function testSerializeString(): void
    {
        $data = 'hello world';
        $serialized = $this->serializer->serialize($data);
        $deserialized = $this->serializer->deserialize($serialized);
        $this->assertEquals($data, $deserialized);
    }

    public function testSerializeArray(): void
    {
        $data = ['key' => 'value', 'num' => 42, 'flag' => true];
        $serialized = $this->serializer->serialize($data);
        $deserialized = $this->serializer->deserialize($serialized);
        $this->assertEquals($data, $deserialized);
    }

    public function testSerializeNestedArray(): void
    {
        $data = ['nested' => ['a' => [1, 2, 3]], 'str' => 'test'];
        $serialized = $this->serializer->serialize($data);
        $deserialized = $this->serializer->deserialize($serialized);
        $this->assertEquals($data, $deserialized);
    }

    public function testRoundTripProperty(): void
    {
        // Property 7: 序列化往返一致性 - 100次迭代
        $testCases = [
            42, 3.14, 'string', true, false,
            [], ['a' => 1], [1, 2, 3],
            ['nested' => ['deep' => 'value']],
        ];

        for ($i = 0; $i < 100; $i++) {
            $data = $testCases[$i % count($testCases)];
            $serialized = $this->serializer->serialize($data);
            $deserialized = $this->serializer->deserialize($serialized);
            $this->assertEquals($data, $deserialized, "Round trip failed for iteration $i");
        }
    }

    public function testInvalidJsonThrows(): void
    {
        $this->expectException(FrameworkException::class);
        $this->serializer->deserialize('{invalid json}');
    }

    public function testGetFormat(): void
    {
        $this->assertEquals('json', $this->serializer->getFormat());
    }
}
