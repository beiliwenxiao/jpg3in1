<?php

declare(strict_types=1);

namespace Framework\Config;

use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;

/**
 * 配置管理器 - 支持文件和环境变量
 */
class ConfigManager
{
    private array $config = [];

    public function __construct(array $defaults = [])
    {
        $this->config = $defaults;
    }

    public function loadFile(string $path): void
    {
        if (!file_exists($path)) {
            throw new FrameworkException("Config file not found: $path", ErrorCode::BAD_REQUEST);
        }

        $ext = pathinfo($path, PATHINFO_EXTENSION);
        $data = match ($ext) {
            'json' => json_decode(file_get_contents($path), true, 512, JSON_THROW_ON_ERROR),
            'php'  => require $path,
            default => throw new FrameworkException("Unsupported config format: $ext", ErrorCode::BAD_REQUEST),
        };

        $this->config = array_merge_recursive($this->config, $data);
    }

    public function get(string $key, mixed $default = null): mixed
    {
        // 环境变量优先
        $envKey = strtoupper(str_replace('.', '_', $key));
        $envVal = getenv($envKey);
        if ($envVal !== false) {
            return $envVal;
        }

        // 点号路径访问
        $keys = explode('.', $key);
        $value = $this->config;
        foreach ($keys as $k) {
            if (!is_array($value) || !array_key_exists($k, $value)) {
                return $default;
            }
            $value = $value[$k];
        }
        return $value;
    }

    public function set(string $key, mixed $value): void
    {
        $keys = explode('.', $key);
        $config = &$this->config;
        foreach ($keys as $k) {
            if (!isset($config[$k]) || !is_array($config[$k])) {
                $config[$k] = [];
            }
            $config = &$config[$k];
        }
        $config = $value;
    }

    public function all(): array
    {
        return $this->config;
    }

    public function validate(array $required): void
    {
        foreach ($required as $key) {
            if ($this->get($key) === null) {
                throw new FrameworkException(
                    "Required config missing: $key",
                    ErrorCode::BAD_REQUEST
                );
            }
        }
    }
}
