<?php

declare(strict_types=1);

namespace Framework\Client;

use Framework\Config\ConfigManager;

/**
 * RPC 服务代理
 *
 * 通过配置文件定义远程服务，调用时像本地方法一样简单：
 *
 *   $rpc = RpcProxy::fromConfig('config.yaml');
 *   $msg = $rpc->call('go-service', 'hello.sayHello');
 *
 *   // 或者获取服务句柄，用魔术方法调用
 *   $go = $rpc->service('go-service');
 *   $msg = $go->hello_sayHello();  // 自动转换为 hello.sayHello
 *
 * 配置示例 (config.yaml):
 *   framework:
 *     services:
 *       go-service:
 *         host: localhost
 *         port: 8093
 */
class RpcProxy
{
    /** @var array<string, array{host: string, port: int}> */
    private array $services = [];

    private int $timeout;

    public function __construct(int $timeout = 5)
    {
        $this->timeout = $timeout;
    }

    /**
     * 从配置文件创建 RpcProxy
     */
    public static function fromConfig(string $configPath, int $timeout = 5): self
    {
        $config = new ConfigManager();
        $config->loadFile($configPath);
        return self::fromConfigManager($config, $timeout);
    }

    /**
     * 从 ConfigManager 创建 RpcProxy
     */
    public static function fromConfigManager(ConfigManager $config, int $timeout = 5): self
    {
        $proxy = new self($timeout);
        $services = $config->get('framework.services', []);
        if (is_array($services)) {
            foreach ($services as $name => $def) {
                $host = $def['host'] ?? 'localhost';
                $port = (int)($def['port'] ?? 8080);
                $proxy->addService($name, $host, $port);
            }
        }
        return $proxy;
    }

    /**
     * 手动添加远程服务
     */
    public function addService(string $name, string $host, int $port): self
    {
        $this->services[$name] = ['host' => $host, 'port' => $port];
        return $this;
    }

    /**
     * 调用远程服务
     *
     * @param string $service 服务名（配置中定义的 key）
     * @param string $method  JSON-RPC 方法名，如 "hello.sayHello"
     * @param mixed  $params  参数（可选）
     * @return mixed 返回结果
     */
    public function call(string $service, string $method, mixed $params = null): mixed
    {
        if (!isset($this->services[$service])) {
            throw new \InvalidArgumentException(
                "未知服务: $service，请在配置文件 framework.services 中定义"
            );
        }

        $svc  = $this->services[$service];
        $url  = "http://{$svc['host']}:{$svc['port']}/jsonrpc";
        $body = json_encode([
            'jsonrpc' => '2.0',
            'method'  => $method,
            'params'  => $params,
            'id'      => 1,
        ], JSON_UNESCAPED_UNICODE);

        $ctx = stream_context_create([
            'http' => [
                'method'        => 'POST',
                'header'        => "Content-Type: application/json; charset=utf-8\r\n",
                'content'       => $body,
                'timeout'       => $this->timeout,
                'ignore_errors' => true,
            ],
        ]);

        $resp = @file_get_contents($url, false, $ctx);
        if ($resp === false) {
            throw new \RuntimeException("RPC 调用失败: 无法连接 $url");
        }

        $data = json_decode($resp, true);
        if (isset($data['error'])) {
            throw new \RuntimeException(
                "RPC 错误: " . ($data['error']['message'] ?? json_encode($data['error']))
            );
        }

        return $data['result'] ?? null;
    }

    /**
     * 获取服务句柄（支持魔术方法调用）
     */
    public function service(string $name): RpcServiceHandle
    {
        return new RpcServiceHandle($this, $name);
    }
}
