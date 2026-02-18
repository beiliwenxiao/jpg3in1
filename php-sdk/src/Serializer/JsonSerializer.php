<?php

declare(strict_types=1);

namespace Framework\Serializer;

use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;

/**
 * JSON序列化器
 */
class JsonSerializer implements Serializer
{
    public function serialize(mixed $data): string
    {
        $result = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        if ($result === false) {
            throw new FrameworkException(
                'JSON serialization failed: ' . json_last_error_msg(),
                ErrorCode::SERIALIZATION_ERROR
            );
        }
        return $result;
    }

    public function deserialize(string $data, string $type = 'array'): mixed
    {
        try {
            $result = json_decode($data, true, 512, JSON_THROW_ON_ERROR);
        } catch (\JsonException $e) {
            throw new FrameworkException(
                'JSON deserialization failed: ' . $e->getMessage(),
                ErrorCode::SERIALIZATION_ERROR,
                previous: $e
            );
        }

        if ($type !== 'array' && class_exists($type)) {
            $obj = new $type();
            foreach ($result as $key => $value) {
                if (property_exists($obj, $key)) {
                    $obj->$key = $value;
                }
            }
            return $obj;
        }

        return $result;
    }

    public function getFormat(): string
    {
        return 'json';
    }
}
