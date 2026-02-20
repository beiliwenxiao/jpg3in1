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

$worker        = new Worker($config['listen'], $config['context'] ?? []);
$worker->name  = $config['name']  ?? 'webman';
$worker->count = $config['count'] ?? 1;

$worker->onWorkerStart = function ($w) {
    require_once BASE_PATH . '/../../../php-sdk/vendor/workerman/webman-framework/src/support/bootstrap.php';

    $app = new App(
        config('app.request_class', support\Request::class),
        support\Log::channel('default'),
        BASE_PATH . '/app',
        BASE_PATH . '/public'
    );

    // 清空 App::$callbacks 静态缓存，防止 loadAllConfig 阶段的旧条目干扰路由匹配
    $ref = new \ReflectionProperty(App::class, 'callbacks');
    $ref->setAccessible(true);
    $ref->setValue(null, []);

    $w->onMessage = [$app, 'onMessage'];
    call_user_func([$app, 'onWorkerStart'], $w);

    echo "[PHP/Webman] Worker 启动，监听端口 8091...\n";
    echo "[PHP/Webman] 浏览器访问: http://localhost:8091\n";
};

Worker::runAll();