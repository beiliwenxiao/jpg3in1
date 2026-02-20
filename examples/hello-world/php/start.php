#!/usr/bin/env php
<?php
/**
 * Linux 启动入口（Webman）
 * 运行：php examples/hello-world/php/start.php start
 *       php examples/hello-world/php/start.php stop
 */

define('BASE_PATH', __DIR__);

require_once __DIR__ . '/../../../php-sdk/vendor/autoload.php';

spl_autoload_register(function (string $class) {
    $file = __DIR__ . '/' . str_replace('\\', '/', $class) . '.php';
    if (file_exists($file)) { require_once $file; }
});

support\App::run();
