<?php
/**
 * API 监控配置 - 复制到 ThinkPHP6 项目的 config/monitor.php
 */
return [
    // 是否启用监控
    'enabled'      => true,

    // 监控服务端 UDP 地址
    'udp_host'     => '127.0.0.1',
    'udp_port'     => 9501,

    // 项目名称（用于区分不同项目）
    'project'      => 'my-project',

    // 采样率 0.0~1.0（1.0 = 100% 采集）
    'sample_rate'  => 1.0,

    // 缓冲区大小（累积多少条后批量发送）
    'buffer_size'  => 10,

    // 排除的路径（不采集）
    'exclude'      => [
        '/health',
        '/favicon.ico',
    ],
];
