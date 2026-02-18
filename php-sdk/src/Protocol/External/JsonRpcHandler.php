<?php

declare(strict_types=1);

namespace Framework\Protocol\External;

/**
 * JSON-RPC 2.0 协议处理器
 */
class JsonRpcHandler
{
    private array $methods = [];

    public function register(string $method, callable $handler): void
    {
        $this->methods[$method] = $handler;
    }

    public function handle(string $rawRequest): string
    {
        try {
            $request = json_decode($rawRequest, true, 512, JSON_THROW_ON_ERROR);
        } catch (\JsonException $e) {
            return $this->errorResponse(null, -32700, 'Parse error');
        }

        // 批量请求
        if (array_is_list($request)) {
            $responses = array_map(fn($r) => $this->processSingle($r), $request);
            return json_encode(array_filter($responses));
        }

        return json_encode($this->processSingle($request));
    }

    private function processSingle(array $request): ?array
    {
        $id = $request['id'] ?? null;

        if (($request['jsonrpc'] ?? '') !== '2.0') {
            return $this->buildError($id, -32600, 'Invalid Request');
        }

        $method = $request['method'] ?? null;
        if (!$method || !isset($this->methods[$method])) {
            return $this->buildError($id, -32601, 'Method not found');
        }

        try {
            $params = $request['params'] ?? [];
            $result = ($this->methods[$method])($params);

            // notification (no id) - no response
            if ($id === null) return null;

            return ['jsonrpc' => '2.0', 'result' => $result, 'id' => $id];
        } catch (\Throwable $e) {
            return $this->buildError($id, -32603, $e->getMessage());
        }
    }

    private function buildError(?int $id, int $code, string $message): array
    {
        return [
            'jsonrpc' => '2.0',
            'error'   => ['code' => $code, 'message' => $message],
            'id'      => $id,
        ];
    }

    private function errorResponse(?int $id, int $code, string $message): string
    {
        return json_encode($this->buildError($id, $code, $message));
    }

    public function getMethods(): array
    {
        return array_keys($this->methods);
    }
}
