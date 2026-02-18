<?php

declare(strict_types=1);

namespace Framework\Client;

use Framework\Registry\ServiceRegistry;
use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;
use React\Promise\PromiseInterface;
use React\Promise\Deferred;

/**
 * 默认框架客户端实现
 */
class DefaultFrameworkClient implements FrameworkClient
{
    private array $handlers = [];
    private bool $running = false;

    public function __construct(
        private readonly ServiceRegistry $registry
    ) {}

    public function call(string $service, string $method, $request): mixed
    {
        $instances = $this->registry->discover($service);
        if (empty($instances)) {
            throw new FrameworkException(
                "Service not found: $service",
                ErrorCode::ROUTING_ERROR
            );
        }

        $instance = $instances[array_rand($instances)];
        $handler = $this->handlers[$service] ?? null;

        if ($handler !== null) {
            return $handler($method, $request);
        }

        // HTTP调用远程服务
        $url = "http://{$instance['address']}:{$instance['port']}/$method";
        $context = stream_context_create([
            'http' => [
                'method' => 'POST',
                'header' => 'Content-Type: application/json',
                'content' => json_encode($request),
                'timeout' => 30,
            ]
        ]);

        $response = file_get_contents($url, false, $context);
        if ($response === false) {
            throw new FrameworkException(
                "Failed to call service: $service.$method",
                ErrorCode::CONNECTION_ERROR
            );
        }

        return json_decode($response, true);
    }

    public function callAsync(string $service, string $method, $request): PromiseInterface
    {
        $deferred = new Deferred();
        try {
            $result = $this->call($service, $method, $request);
            $deferred->resolve($result);
        } catch (\Throwable $e) {
            $deferred->reject($e);
        }
        return $deferred->promise();
    }

    public function registerService(string $name, callable $handler): void
    {
        $this->handlers[$name] = $handler;
        $this->registry->register([
            'name' => $name,
            'address' => '127.0.0.1',
            'port' => 8082,
            'language' => 'php',
            'protocols' => ['http'],
        ]);
    }

    public function start(): void
    {
        $this->running = true;
    }

    public function shutdown(): void
    {
        $this->running = false;
    }
}
