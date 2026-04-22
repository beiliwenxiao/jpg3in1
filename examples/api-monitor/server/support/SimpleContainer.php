<?php

namespace support;

use Psr\Container\ContainerInterface;

class SimpleContainer implements ContainerInterface
{
    private array $instances = [];

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

    public function make(string $name, array $constructor = []): mixed
    {
        if (!class_exists($name)) {
            throw new \RuntimeException("Class '$name' not found");
        }
        return new $name(...array_values($constructor));
    }
}
