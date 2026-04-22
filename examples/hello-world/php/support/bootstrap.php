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

// 3. 加载 config/bootstrap.php 中配置的启动类
foreach (config('bootstrap', []) as $className) {
    if (class_exists($className) && method_exists($className, 'start')) {
        $className::start();
    }
}
