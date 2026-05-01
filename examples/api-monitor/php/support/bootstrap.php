<?php
/**
 * Webman bootstrap file
 * 在 Worker 启动时执行，初始化框架核心组件
 *
 * 由于本项目不是标准 Webman 目录结构（vendor 在 php-sdk/ 中），
 * 框架的部分自动初始化机制无法正常工作，需要在此手动补充。
 */

use Webman\Config;
use Webman\Route;

// 1. 注入 container 配置（被 loadAllConfig 排除）
if (is_file($containerFile = config_path('container.php'))) {
    $container = require $containerFile;
    $ref = new ReflectionClass(Config::class);
    $prop = $ref->getProperty('config');
    $prop->setAccessible(true);
    $config = $prop->getValue();
    $config['container'] = $container;
    $prop->setValue(null, $config);
}

// 2. 加载路由（被 loadAllConfig 排除，需要通过 Route::load 初始化 dispatcher）
Route::load([config_path()]);

// 3. 启动 UDP 监控数据接收器
$udp = new \app\service\UdpReceiver(9501);
$udp->start();

// 4. 启动日志持久化定时器（根据配置决定是否启用）
$monitorConfig = is_file(config_path('monitor.php')) ? require config_path('monitor.php') : [];
$logConfig = $monitorConfig['log'] ?? [];
if (!empty($logConfig['enable'])) {
    $interval = (int)($logConfig['interval'] ?? 300);
    $logger = new \app\service\MonitorLogger($interval);
    $logger->start();

    // worker 停止时导出一次，确保重启不丢数据
    register_shutdown_function(function () use ($logger) {
        $logger->export();
    });
}

// 5. 加载 config/bootstrap.php 中配置的启动类
foreach (config('bootstrap', []) as $className) {
    if (class_exists($className) && method_exists($className, 'start')) {
        $className::start();
    }
}
