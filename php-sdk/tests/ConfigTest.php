<?php

declare(strict_types=1);

namespace FrameworkTest;

use Framework\Config\ConfigManager;
use Framework\Errors\FrameworkException;
use PHPUnit\Framework\TestCase;

class ConfigTest extends TestCase
{
    public function testGetWithDefault(): void
    {
        $config = new ConfigManager(['app' => ['name' => 'test']]);
        $this->assertEquals('test', $config->get('app.name'));
        $this->assertEquals('default', $config->get('app.missing', 'default'));
    }

    public function testSetAndGet(): void
    {
        $config = new ConfigManager();
        $config->set('db.host', 'localhost');
        $config->set('db.port', 3306);

        $this->assertEquals('localhost', $config->get('db.host'));
        $this->assertEquals(3306, $config->get('db.port'));
    }

    public function testEnvOverride(): void
    {
        $config = new ConfigManager(['app' => ['debug' => false]]);
        putenv('APP_DEBUG=true');

        $this->assertEquals('true', $config->get('app.debug'));
        putenv('APP_DEBUG'); // cleanup
    }

    public function testValidateMissingKeyThrows(): void
    {
        $config = new ConfigManager();
        $this->expectException(FrameworkException::class);
        $config->validate(['required.key']);
    }

    public function testValidatePassesWhenAllPresent(): void
    {
        $config = new ConfigManager(['db' => ['host' => 'localhost']]);
        $config->validate(['db.host']); // no exception
        $this->assertTrue(true);
    }

    public function testLoadInvalidFileThrows(): void
    {
        $config = new ConfigManager();
        $this->expectException(FrameworkException::class);
        $config->loadFile('/nonexistent/path/config.json');
    }
}
