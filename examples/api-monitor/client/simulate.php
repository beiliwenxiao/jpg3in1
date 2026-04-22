<?php
/**
 * 模拟客户端 - 生成测试数据发送到监控服务端
 *
 * 运行：php examples/api-monitor/client/simulate.php
 */

require_once __DIR__ . '/src/MonitorClient.php';

// MonitorClient 使用 socket 扩展发送 UDP，不需要额外依赖

echo "========================================\n";
echo "  API 监控 - 模拟客户端\n";
echo "========================================\n";
echo "目标: UDP 127.0.0.1:9501\n\n";

$client = new app\common\MonitorClient('127.0.0.1', 9501, 'demo-project', 1.0, 1);

// 模拟接口列表
$apis = [
    ['UserController',    'index',   '/api/user',          [10, 50]],
    ['UserController',    'show',    '/api/user/1',        [20, 80]],
    ['UserController',    'store',   '/api/user',          [30, 150]],
    ['OrderController',   'index',   '/api/order',         [15, 60]],
    ['OrderController',   'create',  '/api/order',         [50, 300]],
    ['OrderController',   'pay',     '/api/order/pay',     [100, 2000]],
    ['ProductController', 'list',    '/api/product',       [5, 30]],
    ['ProductController', 'detail',  '/api/product/1',     [10, 40]],
    ['ProductController', 'search',  '/api/product/search',[20, 500]],
    ['AuthController',    'login',   '/api/auth/login',    [30, 200]],
    ['AuthController',    'logout',  '/api/auth/logout',   [5, 20]],
    ['ReportController',  'daily',   '/api/report/daily',  [200, 3000]],
    ['ReportController',  'export',  '/api/report/export', [500, 5000]],
    ['CartController',    'add',     '/api/cart/add',      [10, 60]],
    ['CartController',    'list',    '/api/cart',          [8, 35]],
];

$statusWeights = [
    200 => 85,  // 85% 成功
    201 => 5,   // 5% 创建成功
    400 => 3,   // 3% 参数错误
    401 => 2,   // 2% 未授权
    404 => 2,   // 2% 未找到
    500 => 3,   // 3% 服务器错误
];

function randomStatus(array $weights): int {
    $total = array_sum($weights);
    $rand  = mt_rand(1, $total);
    $sum   = 0;
    foreach ($weights as $status => $weight) {
        $sum += $weight;
        if ($rand <= $sum) return $status;
    }
    return 200;
}

$count = 0;
echo "开始发送模拟数据（Ctrl+C 停止）...\n\n";

while (true) {
    // 每轮随机发送 1~5 条
    $batch = mt_rand(1, 5);
    for ($i = 0; $i < $batch; $i++) {
        $api = $apis[array_rand($apis)];
        [$class, $method, $uri, $timeRange] = $api;

        $duration = $timeRange[0] + mt_rand(0, ($timeRange[1] - $timeRange[0]) * 100) / 100;
        $status   = randomStatus($statusWeights);

        // 模拟请求参数
        $params = ['page' => mt_rand(1, 10), 'id' => mt_rand(1, 1000)];
        $response = ['code' => $status < 400 ? 0 : 1, 'msg' => $status < 400 ? 'ok' : 'error'];
        $client->report($class, $method, $uri, $status, $duration, $params, $response);
        $count++;

        $statusLabel = $status < 400 ? "\033[32m{$status}\033[0m" : "\033[31m{$status}\033[0m";
        echo sprintf("[%s] #%d %s %s.%s %.1fms\n",
            date('H:i:s'), $count, $statusLabel, $class, $method, $duration);
    }

    $client->flush();

    // 随机间隔 100ms~1s
    usleep(mt_rand(100000, 1000000));
}
