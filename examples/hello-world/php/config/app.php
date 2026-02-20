<?php
return [
    'debug'             => true,
    'error_reporting'   => E_ALL,
    'default_timezone'  => 'Asia/Shanghai',
    'request_class'     => support\Request::class,
    'public_path'       => __DIR__ . '/../public',
    'runtime_path'      => __DIR__ . '/../runtime',
    'controller_suffix' => '',
    'controller_reuse'  => true,
];
