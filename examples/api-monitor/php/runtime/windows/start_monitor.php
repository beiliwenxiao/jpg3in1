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