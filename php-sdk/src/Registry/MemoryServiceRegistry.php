<?php

declare(strict_types=1);

namespace Framework\Registry;

/**
 * 内存服务注册中心（零依赖，用于开发和测试）
 */
class MemoryServiceRegistry implements ServiceRegistry
{
    private array $services = [];

    public function register(array $serviceInfo): void
    {
        $id = $serviceInfo['id'] ?? ($serviceInfo['name'] . '-' . uniqid());
        $serviceInfo['id'] = $id;
        $serviceInfo['registeredAt'] = time();
        $serviceInfo['healthy'] = true;

        $name = $serviceInfo['name'];
        if (!isset($this->services[$name])) {
            $this->services[$name] = [];
        }
        $this->services[$name][$id] = $serviceInfo;
    }

    public function deregister(string $serviceId): void
    {
        foreach ($this->services as $name => &$instances) {
            if (isset($instances[$serviceId])) {
                unset($instances[$serviceId]);
                if (empty($instances)) {
                    unset($this->services[$name]);
                }
                return;
            }
        }
    }

    public function discover(string $serviceName): array
    {
        $instances = $this->services[$serviceName] ?? [];
        return array_values(array_filter(
            $instances,
            fn($s) => $s['healthy'] ?? true
        ));
    }

    public function healthCheck(string $serviceId): bool
    {
        foreach ($this->services as $instances) {
            if (isset($instances[$serviceId])) {
                return $instances[$serviceId]['healthy'] ?? false;
            }
        }
        return false;
    }

    public function getAllServices(): array
    {
        return $this->services;
    }
}
