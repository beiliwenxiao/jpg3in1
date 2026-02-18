<?php

declare(strict_types=1);

namespace Framework\Protocol\External;

use Workerman\Worker;
use Workerman\Connection\TcpConnection;
use Workerman\Protocols\Http\Request;
use Workerman\Protocols\Http\Response;

/**
 * REST协议处理器 - 基于Workerman
 */
class RestHandler
{
    private array $routes = [];
    private ?Worker $worker = null;

    public function __construct(
        private string $host = '0.0.0.0',
        private int $port = 8082
    ) {}

    public function get(string $path, callable $handler): void
    {
        $this->routes['GET'][$path] = $handler;
    }

    public function post(string $path, callable $handler): void
    {
        $this->routes['POST'][$path] = $handler;
    }

    public function put(string $path, callable $handler): void
    {
        $this->routes['PUT'][$path] = $handler;
    }

    public function delete(string $path, callable $handler): void
    {
        $this->routes['DELETE'][$path] = $handler;
    }

    public function patch(string $path, callable $handler): void
    {
        $this->routes['PATCH'][$path] = $handler;
    }

    public function handle(Request $request): Response
    {
        $method = $request->method();
        $path = $request->path();

        $handler = $this->routes[$method][$path] ?? null;

        if ($handler === null) {
            return new Response(404, ['Content-Type' => 'application/json'],
                json_encode(['error' => 'Not Found', 'code' => 404])
            );
        }

        try {
            $result = $handler($request);
            $body = is_string($result) ? $result : json_encode($result);
            return new Response(200, ['Content-Type' => 'application/json'], $body);
        } catch (\Throwable $e) {
            return new Response(500, ['Content-Type' => 'application/json'],
                json_encode(['error' => $e->getMessage(), 'code' => 500])
            );
        }
    }

    public function getSupportedMethods(): array
    {
        return ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
    }
}
