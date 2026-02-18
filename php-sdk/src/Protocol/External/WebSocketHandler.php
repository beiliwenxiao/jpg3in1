<?php

declare(strict_types=1);

namespace Framework\Protocol\External;

use Workerman\Connection\TcpConnection;

/**
 * WebSocket协议处理器
 */
class WebSocketHandler
{
    private array $messageHandlers = [];
    private array $connections = [];

    public function onMessage(callable $handler): void
    {
        $this->messageHandlers[] = $handler;
    }

    public function handleMessage(TcpConnection $connection, string $data): void
    {
        foreach ($this->messageHandlers as $handler) {
            $handler($connection, $data);
        }
    }

    public function handleConnect(TcpConnection $connection): void
    {
        $this->connections[$connection->id] = $connection;
    }

    public function handleClose(TcpConnection $connection): void
    {
        unset($this->connections[$connection->id]);
    }

    public function broadcast(string $message): void
    {
        foreach ($this->connections as $connection) {
            $connection->send($message);
        }
    }

    public function send(TcpConnection $connection, mixed $data): void
    {
        $message = is_string($data) ? $data : json_encode($data);
        $connection->send($message);
    }

    public function getSupportedFormats(): array
    {
        return ['text', 'binary'];
    }

    public function getConnectionCount(): int
    {
        return count($this->connections);
    }
}
