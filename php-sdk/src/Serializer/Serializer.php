<?php

declare(strict_types=1);

namespace Framework\Serializer;

/**
 * 序列化器接口
 */
interface Serializer
{
    public function serialize(mixed $data): string;
    public function deserialize(string $data, string $type = 'array'): mixed;
    public function getFormat(): string;
}
