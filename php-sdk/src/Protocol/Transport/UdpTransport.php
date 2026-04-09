<?php
/**
 * UdpTransport - UDP 传输层
 *
 * 提供 UDP 发送和接收能力，用于内部/外部的轻量级数据传输。
 * 适用于监控数据上报、日志采集等不要求可靠传输的场景。
 *
 * 发送端（客户端）：
 *   $sender = UdpTransport::createSender('127.0.0.1', 9501);
 *   $sender->send('hello');
 *   $sender->close();
 *
 * 接收端（需配合 Workerman Worker 使用）：
 *   $receiver = UdpTransport::createReceiver(9501, function($data, $conn) {
 *       echo "收到: $data\n";
 *   });
 *   $receiver->listen();  // 在 Worker::onWorkerStart 中调用
 */

declare(strict_types=1);

namespace Framework\Protocol\Transport;

use Workerman\Worker;

class UdpTransport
{
    // ---- 发送端 ----

    private string $host;
    private int $port;
    /** @var resource|null */
    private $socket = null;

    private function __construct(string $host, int $port)
    {
        $this->host = $host;
        $this->port = $port;
    }

    /**
     * 创建 UDP 发送端
     */
    public static function createSender(string $host = '127.0.0.1', int $port = 9501): self
    {
        return new self($host, $port);
    }

    /**
     * 发送数据
     */
    public function send(string $data): bool
    {
        try {
            if ($this->socket === null) {
                $this->socket = @socket_create(AF_INET, SOCK_DGRAM, SOL_UDP);
                if ($this->socket === false) {
                    return $this->sendFallback($data);
                }
            }
            $sent = @socket_sendto($this->socket, $data, strlen($data), 0, $this->host, $this->port);
            return $sent !== false;
        } catch (\Throwable $e) {
            return false;
        }
    }

    /**
     * 批量发送（换行分隔）
     */
    public function sendBatch(array $items): bool
    {
        return $this->send(implode("\n", $items));
    }

    /**
     * 降级发送（使用 stream）
     */
    private function sendFallback(string $data): bool
    {
        $fp = @fsockopen("udp://{$this->host}", $this->port, $errno, $errstr, 1);
        if (!$fp) return false;
        @fwrite($fp, $data);
        @fclose($fp);
        return true;
    }

    public function close(): void
    {
        if ($this->socket !== null) {
            @socket_close($this->socket);
            $this->socket = null;
        }
    }

    public function __destruct()
    {
        $this->close();
    }

    // ---- 接收端 ----

    /**
     * 创建 UDP 接收端（Workerman Worker）
     *
     * @param int      $port     监听端口
     * @param callable $onMessage 收到消息回调 function(string $data, $connection)
     * @param string   $host     监听地址
     * @return Worker
     */
    public static function createReceiver(int $port, callable $onMessage, string $host = '0.0.0.0'): Worker
    {
        $worker = new Worker("udp://{$host}:{$port}");
        $worker->name  = "UdpReceiver-{$port}";
        $worker->count = 1;
        $worker->onMessage = $onMessage;
        return $worker;
    }
}
