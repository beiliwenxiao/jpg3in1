<?php
/**
 * UdpReceiver - UDP 数据接收器
 *
 * 使用 Workerman 的 Worker 监听 UDP 端口，接收客户端发送的性能数据。
 * 数据格式为 JSON，写入 MonitorStorage。
 */

namespace app\service;

use Workerman\Worker;

class UdpReceiver
{
    private int $port;
    private ?Worker $worker = null;

    public function __construct(int $port = 9501)
    {
        $this->port = $port;
    }

    /**
     * 创建 UDP Worker（在 onWorkerStart 中调用）
     */
    public function start(): void
    {
        $this->worker = new Worker("udp://0.0.0.0:{$this->port}");
        $this->worker->name  = 'ApiMonitor-UDP';
        $this->worker->count = 1;

        $this->worker->onMessage = function ($connection, $data) {
            $this->handleMessage($data);
        };

        // 不调用 Worker::runAll()，由主进程统一管理
        $this->worker->listen();
        echo "[监控] UDP 接收器启动，监听端口 {$this->port}\n";
    }

    private function handleMessage(string $data): void
    {
        // 支持批量数据（换行分隔）
        $lines = explode("\n", trim($data));
        $storage = MonitorStorage::getInstance();

        foreach ($lines as $line) {
            $line = trim($line);
            if ($line === '') continue;

            $record = json_decode($line, true);
            if (!$record || !is_array($record)) {
                continue;
            }

            $storage->record($record);
        }
    }
}
