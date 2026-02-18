<?php

declare(strict_types=1);

namespace Framework\Protocol\Adapter;

/**
 * 协议适配器 - 外部协议与内部协议转换
 */
class ProtocolAdapter
{
    /**
     * 外部请求转内部格式
     */
    public function transformRequest(array $externalRequest): array
    {
        return [
            'service'   => $externalRequest['service'] ?? $this->extractService($externalRequest),
            'method'    => $externalRequest['method'] ?? $this->extractMethod($externalRequest),
            'payload'   => $externalRequest['body'] ?? $externalRequest['params'] ?? [],
            'headers'   => $externalRequest['headers'] ?? [],
            'traceId'   => $externalRequest['traceId'] ?? uniqid('trace-', true),
            'timestamp' => time(),
            'protocol'  => $externalRequest['protocol'] ?? 'unknown',
        ];
    }

    /**
     * 内部响应转外部格式
     */
    public function transformResponse(array $internalResponse, string $protocol = 'rest'): array
    {
        return match ($protocol) {
            'jsonrpc' => [
                'jsonrpc' => '2.0',
                'result'  => $internalResponse['data'] ?? $internalResponse,
                'id'      => $internalResponse['id'] ?? null,
            ],
            'mqtt' => [
                'topic'   => $internalResponse['topic'] ?? 'response',
                'payload' => json_encode($internalResponse['data'] ?? $internalResponse),
            ],
            default => [
                'status'  => $internalResponse['status'] ?? 200,
                'data'    => $internalResponse['data'] ?? $internalResponse,
                'traceId' => $internalResponse['traceId'] ?? null,
            ],
        };
    }

    public function getSupportedProtocols(): array
    {
        return ['rest', 'websocket', 'jsonrpc', 'mqtt'];
    }

    private function extractService(array $request): string
    {
        // 从URL路径提取服务名: /service/method -> service
        $path = $request['path'] ?? '';
        $parts = explode('/', trim($path, '/'));
        return $parts[0] ?? 'unknown';
    }

    private function extractMethod(array $request): string
    {
        $path = $request['path'] ?? '';
        $parts = explode('/', trim($path, '/'));
        return $parts[1] ?? 'call';
    }
}
