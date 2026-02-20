<?php
return [
    'default' => [
        'handlers' => [
            [
                'class'       => Monolog\Handler\StreamHandler::class,
                'constructor' => [
                    __DIR__ . '/../runtime/logs/webman.log',
                    Monolog\Logger::DEBUG,
                ],
                'formatter' => [
                    'class'       => Monolog\Formatter\LineFormatter::class,
                    'constructor' => [null, 'Y-m-d H:i:s', true],
                ],
            ],
        ],
    ],
];
