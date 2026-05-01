#!/usr/bin/env php
<?php
/**
 * API 监控服务端 - Linux 启动入口
 * 运行：php examples/api-monitor/php/start.php start
 */

define('BASE_PATH', __DIR__);

require_once __DIR__ . '/../../../php-sdk/vendor/autoload.php';

spl_autoload_register(function (string $class) {
    $file = __DIR__ . '/' . str_replace('\\', '/', $class) . '.php';
    if (file_exists($file)) { require_once $file; }
});

support\App::run();
