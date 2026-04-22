<?php
// 内存模式自动降为单 worker，Redis 模式支持多 worker
$monitorConfig = is_file(__DIR__ . '/monitor.php') ? require __DIR__ . '/monitor.php' : [];
$driver = $monitorConfig['driver'] ?? 'memory';
$isMemory = ($driver !== 'redis') || !extension_loaded('redis');
$workerCount = $isMemory ? 1 : ((DIRECTORY_SEPARATOR === '/') ? (int)(shell_exec('nproc') ?: 4) : 1);

return [
    'listen'           => 'http://0.0.0.0:8095',
    'transport'        => 'tcp',
    'context'          => [],
    'name'             => 'ApiMonitor-Server',
    'count'            => $workerCount,
    'user'             => '',
    'group'            => '',
    'reusePort'        => false,
    'event_loop'       => '',
    'stop_timeout'     => 2,
    'pid_file'         => __DIR__ . '/../runtime/webman.pid',
    'status_file'      => __DIR__ . '/../runtime/webman.status',
    'stdout_file'      => __DIR__ . '/../runtime/stdout.log',
    'log_file'         => __DIR__ . '/../runtime/workerman.log',
    'max_package_size' => 10 * 1024 * 1024,
];
