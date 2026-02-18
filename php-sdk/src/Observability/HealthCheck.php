<?php

declare(strict_types=1);

namespace Framework\Observability;

/**
 * 健康检查
 */
class HealthCheck
{
    private array $checks = [];

    public function addCheck(string $name, callable $check): void
    {
        $this->checks[$name] = $check;
    }

    public function check(): array
    {
        $results = [];
        $allHealthy = true;

        foreach ($this->checks as $name => $check) {
            try {
                $healthy = (bool) $check();
                $results[$name] = ['status' => $healthy ? 'up' : 'down'];
                if (!$healthy) $allHealthy = false;
            } catch (\Throwable $e) {
                $results[$name] = ['status' => 'down', 'error' => $e->getMessage()];
                $allHealthy = false;
            }
        }

        return [
            'status'    => $allHealthy ? 'healthy' : 'unhealthy',
            'timestamp' => date('c'),
            'checks'    => $results,
        ];
    }
}
