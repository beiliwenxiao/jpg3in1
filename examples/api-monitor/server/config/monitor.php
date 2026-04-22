<?php
/**
 * 监控存储配置
 *
 * driver 可选值：
 *   'memory' - 内存存储（无外部依赖，自动降为单 worker）
 *   'redis'  - Redis 存储（需要 php-redis 扩展，支持多 worker）
 *
 * 如果配置为 redis 但扩展未安装或连接失败，会自动回退到 memory。
 */
return [
    'driver' => 'memory',

    // 日志持久化配置
    'log' => [
        'enable'   => false,          // 是否启用日志持久化
        'interval' => 300,            // 导出间隔（秒），默认 5 分钟
    ],
];
