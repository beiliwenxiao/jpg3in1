<?php
/**
 * MonitorClient - API 监控 UDP 客户端
 *
 * 通过 UDP 发送接口性能数据到监控服务端。
 * UDP 是无连接的，发送失败不会影响业务逻辑。
 *
 * 使用方式：
 *   $client = new MonitorClient('127.0.0.1', 9501, 'my-project');
 *   $client->report('UserController', 'index', '/api/user', 200, 35.6);
 *
 * ThinkPHP6 中间件接入见 ApiMonitor.php
 */

namespace app\common;

class MonitorClient
{
    private string $host;
    private int $port;
    private string $project;
    private float $sampleRate;
    /** @var resource|null */
    private $socket = null;
    /** @var array 缓冲区，批量发送 */
    private array $buffer = [];
    private int $bufferSize;

    public function __construct(
        string $host = '127.0.0.1',
        int    $port = 9501,
        string $project = 'default',
        float  $sampleRate = 1.0,
        int    $bufferSize = 10
    ) {
        $this->host       = $host;
        $this->port       = $port;
        $this->project    = $project;
        $this->sampleRate = $sampleRate;
        $this->bufferSize = $bufferSize;
    }

    /**
     * 上报一条接口性能数据
     *
     * @param string $class    控制器/类名
     * @param string $method   方法名
     * @param string $uri      请求URI
     * @param int    $status   HTTP状态码
     * @param float  $duration 耗时(毫秒)
     * @param array  $params   请求参数（可选）
     * @param array  $response 返回参数（可选）
     */
    public function report(string $class, string $method, string $uri, int $status, float $duration, array $params = [], array $response = []): void
    {
        // 采样率控制
        if ($this->sampleRate < 1.0 && mt_rand(1, 10000) / 10000 > $this->sampleRate) {
            return;
        }

        $record = [
            'project'   => $this->project,
            'class'     => $class,
            'method'    => $method,
            'uri'       => $uri,
            'status'    => $status,
            'duration'  => round($duration, 2),
            'timestamp' => time(),
        ];
        if (!empty($params)) {
            $record['params'] = $params;
        }
        if (!empty($response)) {
            $record['response'] = $response;
        }

        $data = json_encode($record, JSON_UNESCAPED_UNICODE);

        $this->buffer[] = $data;

        if (count($this->buffer) >= $this->bufferSize) {
            $this->flush();
        }
    }

    /**
     * 刷新缓冲区，发送所有待发送数据
     */
    public function flush(): void
    {
        if (empty($this->buffer)) return;

        try {
            $payload = implode("\n", $this->buffer);
            $this->buffer = [];
            $this->send($payload);
        } catch (\Throwable $e) {
            // UDP 发送失败不影响业务
        }
    }

    private function send(string $data): void
    {
        if ($this->socket === null) {
            $this->socket = @socket_create(AF_INET, SOCK_DGRAM, SOL_UDP);
            if ($this->socket === false) {
                // 降级：使用 stream
                $fp = @fsockopen("udp://{$this->host}", $this->port, $errno, $errstr, 1);
                if ($fp) {
                    @fwrite($fp, $data);
                    @fclose($fp);
                }
                return;
            }
        }
        @socket_sendto($this->socket, $data, strlen($data), 0, $this->host, $this->port);
    }

    /**
     * 析构时刷新缓冲区
     */
    public function __destruct()
    {
        $this->flush();
        if ($this->socket !== null) {
            @socket_close($this->socket);
        }
    }
}
