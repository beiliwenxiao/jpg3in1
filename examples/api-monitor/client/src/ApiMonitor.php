<?php
/**
 * ApiMonitor - ThinkPHP6 全局中间件
 *
 * 自动采集每个请求的性能数据，通过 UDP 发送到监控服务端。
 *
 * 接入步骤：
 * 1. 将 MonitorClient.php 和 ApiMonitor.php 复制到 app/middleware/ 和 app/common/
 * 2. 在 app/middleware.php 中注册：
 *    return [\app\middleware\ApiMonitor::class];
 * 3. 创建 config/monitor.php 配置文件
 *
 * 配置示例 (config/monitor.php)：
 *   return [
 *       'enabled'      => true,
 *       'udp_host'     => '127.0.0.1',
 *       'udp_port'     => 9501,
 *       'project'      => 'my-project',
 *       'sample_rate'  => 1.0,
 *       'buffer_size'  => 10,
 *       'exclude'      => ['/health', '/favicon.ico'],
 *   ];
 */

namespace app\middleware;

use app\common\MonitorClient;

class ApiMonitor
{
    /** @var MonitorClient|null */
    private static ?MonitorClient $client = null;

    /**
     * ThinkPHP6 中间件入口
     */
    public function handle($request, \Closure $next)
    {
        $config = config('monitor');
        if (empty($config['enabled'])) {
            return $next($request);
        }

        // 排除路径
        $uri = $request->pathinfo() ?: '/';
        $excludes = $config['exclude'] ?? [];
        foreach ($excludes as $pattern) {
            if (str_starts_with('/' . $uri, $pattern)) {
                return $next($request);
            }
        }

        $startTime = microtime(true);

        /** @var \think\Response $response */
        $response = $next($request);

        $duration = (microtime(true) - $startTime) * 1000; // 毫秒
        $status   = $response->getCode();

        // 解析控制器和方法
        [$class, $method] = $this->parseAction($request);

        $this->getClient($config)->report($class, $method, '/' . $uri, $status, $duration);

        return $response;
    }

    /**
     * 从请求中解析控制器类名和方法名
     */
    private function parseAction($request): array
    {
        // ThinkPHP6 的路由调度信息
        $dispatch = $request->controller();
        $action   = $request->action();

        if ($dispatch) {
            // 去掉命名空间前缀，保留短类名
            $parts = explode('\\', $dispatch);
            $class = end($parts);
            return [$class, $action ?: 'index'];
        }

        // 降级：从 URI 解析
        $uri   = $request->pathinfo() ?: 'index/index';
        $parts = explode('/', trim($uri, '/'));
        $class  = ucfirst($parts[0] ?? 'Index') . 'Controller';
        $method = $parts[1] ?? 'index';

        return [$class, $method];
    }

    private function getClient(array $config): MonitorClient
    {
        if (self::$client === null) {
            self::$client = new MonitorClient(
                $config['udp_host']    ?? '127.0.0.1',
                $config['udp_port']    ?? 9501,
                $config['project']     ?? 'default',
                $config['sample_rate'] ?? 1.0,
                $config['buffer_size'] ?? 10,
            );
        }
        return self::$client;
    }
}
