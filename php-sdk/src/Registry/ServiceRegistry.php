<?php

declare(strict_types=1);

namespace Framework\Registry;

/**
 * 服务注册中心接口
 */
interface ServiceRegistry
{
    public function register(array $serviceInfo): void;
    public function deregister(string $serviceId): void;
    public function discover(string $serviceName): array;
    public function healthCheck(string $serviceId): bool;
}
