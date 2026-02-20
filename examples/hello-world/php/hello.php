<?php
/**
 * Hello World - PHP 示例（Webman + Workerman）
 *
 * HTTP 路由由 Webman 框架处理，Worker 进程由 Workerman 管理。
 * 复用 php-sdk 的 JsonRpcHandler 处理 JSON-RPC 协议。
 *
 * 启动方式：
 *   Windows: php examples/hello-world/php/windows.php
 *   Linux:   php examples/hello-world/php/start.php start
 *
 * 端口：8092
 */

// 根据平台自动选择入口
if (DIRECTORY_SEPARATOR === '\\') {
    require __DIR__ . '/windows.php';
} else {
    require __DIR__ . '/start.php';
}
