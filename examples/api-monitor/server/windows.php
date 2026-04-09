<?php
/**
 * API 监控服务端 - Windows 启动入口
 * 运行：php examples/api-monitor/server/windows.php
 *
 * 同时启动：
 *   1. Webman HTTP 服务（端口 8095）- 仪表盘 + API
 *   2. Workerman UDP 服务（端口 9501）- 接收监控数据
 */

define('BASE_PATH', __DIR__);

require_once __DIR__ . '/../../../php-sdk/vendor/autoload.php';

spl_autoload_register(function (string $class) {
    $map = [
        'app\\'     => __DIR__ . '/',
        'support\\' => __DIR__ . '/support/',
    ];
    foreach ($map as $prefix => $base) {
        if (str_starts_with($class, $prefix)) {
            $rel  = str_replace('\\', '/', substr($class, strlen($prefix)));
            $file = $base . $rel . '.php';
            if (file_exists($file)) {
                require_once $file;
                return;
            }
        }
    }
});

use support\App;
use Workerman\Worker;

ini_set('display_errors', 'on');
error_reporting(E_ALL);

App::loadAllConfig(['route']);

foreach ([__DIR__ . '/runtime', __DIR__ . '/runtime/logs'] as $dir) {
    is_dir($dir) || mkdir($dir, 0777, true);
}

$runtimePath = __DIR__ . '/runtime/windows';
is_dir($runtimePath) || mkdir($runtimePath, 0777, true);

$workerFile = $runtimePath . '/start_monitor.php';
file_put_contents($workerFile, <<<'PHP'
<?php
define('BASE_PATH', dirname(__DIR__, 2));

require_once BASE_PATH . '/../../../php-sdk/vendor/autoload.php';

spl_autoload_register(function (string $class) {
    $file = BASE_PATH . '/' . str_replace('\\', '/', $class) . '.php';
    if (file_exists($file)) { require_once $file; }
});

use Webman\App;
use Workerman\Worker;
use Workerman\Connection\TcpConnection;

ini_set('display_errors', 'on');
error_reporting(E_ALL);

support\App::loadAllConfig(['route', 'container']);

$config = config('server');
Worker::$pidFile    = $config['pid_file'];
Worker::$logFile    = $config['log_file'];
Worker::$stdoutFile = $config['stdout_file'];
TcpConnection::$defaultMaxPackageSize = $config['max_package_size'] ?? 10 * 1024 * 1024;

// ---- HTTP Worker (Webman) ----
$worker        = new Worker($config['listen'], $config['context'] ?? []);
$worker->name  = $config['name'] ?? 'webman';
$worker->count = 1;

$worker->onWorkerStart = function ($w) {
    require_once BASE_PATH . '/../../../php-sdk/vendor/workerman/webman-framework/src/support/bootstrap.php';

    $app = new App(
        config('app.request_class', support\Request::class),
        support\Log::channel('default'),
        BASE_PATH . '/app',
        BASE_PATH . '/public'
    );

    $ref = new \ReflectionProperty(App::class, 'callbacks');
    $ref->setAccessible(true);
    $ref->setValue(null, []);

    $w->onMessage = [$app, 'onMessage'];
    call_user_func([$app, 'onWorkerStart'], $w);

    // 启动 UDP 接收器
    $udp = new \app\service\UdpReceiver(9501);
    $udp->start();

    echo "[监控服务端] HTTP 仪表盘: http://localhost:8095\n";
    echo "[监控服务端] UDP 接收端口: 9501\n";
};

// ---- 启动 ----
Worker::runAll();
PHP);

$cmd = '"' . PHP_BINARY . '" "' . $workerFile . '" -q';
$resource = proc_open($cmd, [STDIN, STDOUT, STDOUT], $pipes, null, null, ['bypass_shell' => true]);
if (!$resource) {
    exit("无法启动监控服务进程\n");
}

echo "========================================\n";
echo "  API 性能监控服务端\n";
echo "========================================\n";
echo "HTTP 仪表盘: http://localhost:8095\n";
echo "UDP 接收端口: 9501\n";
echo "按 Ctrl+C 停止\n\n";

while (true) {
    sleep(1);
    $status = proc_get_status($resource);
    if (!$status['running']) {
        echo "子进程已退出，正在重启...\n";
        $resource = proc_open($cmd, [STDIN, STDOUT, STDOUT], $pipes, null, null, ['bypass_shell' => true]);
    }
}
