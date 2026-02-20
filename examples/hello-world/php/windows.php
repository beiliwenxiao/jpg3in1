<?php
/**
 * Windows 启动入口（Webman）
 * 运行：php examples/hello-world/php/windows.php
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

$workerFile = $runtimePath . '/start_webman.php';
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
PHP);

$cmd = '"' . PHP_BINARY . '" "' . $workerFile . '" -q';
$resource = proc_open($cmd, [STDIN, STDOUT, STDOUT], $pipes, null, null, ['bypass_shell' => true]);
if (!$resource) {
    exit("无法启动 Webman 进程\n");
}

echo "========================================\n";
echo "  Hello World - PHP (Webman)\n";
echo "========================================\n";
echo "HTTP 由 Webman 处理，Worker 由 Workerman 管理\n";
echo "浏览器访问: http://localhost:8091\n";
echo "按 Ctrl+C 停止\n\n";

while (true) {
    sleep(1);
    $status = proc_get_status($resource);
    if (!$status['running']) {
        echo "子进程已退出，正在重启...\n";
        $resource = proc_open($cmd, [STDIN, STDOUT, STDOUT], $pipes, null, null, ['bypass_shell' => true]);
    }
}
