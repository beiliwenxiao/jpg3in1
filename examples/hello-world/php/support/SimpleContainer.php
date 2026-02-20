<?php
namespace support;

use Psr\Container\ContainerInterface;

/**
 * 简单 PSR-11 容器实现（避免与 Webman 自带的 support\Container 代理类冲突）
 */
class SimpleContainer implements ContainerInterface
{
    protected array $instances = [];

    public function get(string $id): mixed
    {
        if (!isset($this->instances[$id])) {
            $this->instances[$id] = new $id();
        }
        return $this->instances[$id];
    }

    public function has(string $id): bool
    {
        return class_exists($id);
    }

    public function make(string $id, array $parameters = []): mixed
    {
        if (empty($parameters)) {
            return $this->get($id);
        }
        return new $id(...array_values($parameters));
    }
}
