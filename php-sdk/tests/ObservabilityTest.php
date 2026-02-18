<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Observability\HealthCheck;
use Framework\Observability\Logger;
use PHPUnit\Framework\TestCase;

class ObservabilityTest extends TestCase
{
    public function testHealthCheckAllHealthy(): void
    {
        $hc = new HealthCheck();
        $hc->addCheck('db', fn() => true);
        $hc->addCheck('cache', fn() => true);

        $result = $hc->check();
        $this->assertEquals('healthy', $result['status']);
        $this->assertEquals('up', $result['checks']['db']['status']);
    }

    public function testHealthCheckUnhealthyWhenOneFails(): void
    {
        $hc = new HealthCheck();
        $hc->addCheck('db', fn() => true);
        $hc->addCheck('cache', fn() => false);

        $result = $hc->check();
        $this->assertEquals('unhealthy', $result['status']);
        $this->assertEquals('down', $result['checks']['cache']['status']);
    }

    public function testHealthCheckHandlesException(): void
    {
        $hc = new HealthCheck();
        $hc->addCheck('broken', fn() => throw new \RuntimeException('connection failed'));

        $result = $hc->check();
        $this->assertEquals('unhealthy', $result['status']);
        $this->assertArrayHasKey('error', $result['checks']['broken']);
    }

    public function testLoggerCreation(): void
    {
        $logger = new Logger('test', 'debug', 'php://memory');
        $this->assertEquals('debug', $logger->getLevel());
    }

    public function testLoggerSetLevel(): void
    {
        $logger = new Logger('test', 'info', 'php://memory');
        $logger->setLevel('error');
        $this->assertEquals('error', $logger->getLevel());
    }
}
