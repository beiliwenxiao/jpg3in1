<?php
return [
    'listen'           => 'http://0.0.0.0:8091',
    'transport'        => 'tcp',
    'context'          => [],
    'name'             => 'HelloWorld-PHP',
    // Linux 多进程；Windows 固定为 1
    'count'            => (DIRECTORY_SEPARATOR === '/') ? (int)(shell_exec('nproc') ?: 4) : 1,
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
