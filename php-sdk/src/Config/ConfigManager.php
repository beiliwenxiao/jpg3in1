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
            'yaml', 'yml' => self::parseYaml(file_get_contents($path)),
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

    /**
     * 简易 YAML 解析（支持基本的 key: value 和嵌套结构，无需 ext-yaml 扩展）
     */
    private static function parseYaml(string $content): array
    {
        // 如果安装了 yaml 扩展，优先使用
        if (function_exists('yaml_parse')) {
            return yaml_parse($content) ?: [];
        }
        // 简易解析器：支持缩进嵌套的 key: value
        $result = [];
        $stack  = [&$result];
        $indents = [0];
        foreach (explode("\n", $content) as $line) {
            $trimmed = rtrim($line);
            if ($trimmed === '' || $trimmed[0] === '#') continue;
            $indent = strlen($line) - strlen(ltrim($line));
            $trimmed = ltrim($trimmed);
            // 回退到正确的层级
            while (count($indents) > 1 && $indent <= end($indents)) {
                array_pop($stack);
                array_pop($indents);
            }
            if (str_contains($trimmed, ':')) {
                $pos = strpos($trimmed, ':');
                $key = trim(substr($trimmed, 0, $pos));
                $val = trim(substr($trimmed, $pos + 1));
                if ($val === '' || $val === '~') {
                    // 嵌套对象
                    $stack[count($stack) - 1][$key] = [];
                    $stack[] = &$stack[count($stack) - 1][$key];
                    $indents[] = $indent;
                } else {
                    // 去掉引号
                    $val = trim($val, '"\'');
                    // 类型推断
                    if (is_numeric($val) && !str_contains($val, '.')) {
                        $val = (int)$val;
                    } elseif (is_numeric($val)) {
                        $val = (float)$val;
                    } elseif ($val === 'true') {
                        $val = true;
                    } elseif ($val === 'false') {
                        $val = false;
                    }
                    $stack[count($stack) - 1][$key] = $val;
                }
            }
        }
        return $result;
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
