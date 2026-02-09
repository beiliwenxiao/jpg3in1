<?php

declare(strict_types=1);

return [
    'framework' => [
        'name' => env('SERVICE_NAME', 'php-service'),
        'version' => '1.0.0',
        'language' => 'php',
        
        'network' => [
            'host' => env('SERVICE_HOST', '0.0.0.0'),
            'port' => (int) env('SERVICE_PORT', 8082),
            'max_connections' => 1000,
            'read_timeout' => 30000,
            'write_timeout' => 30000,
            'keep_alive' => true,
        ],
        
        'registry' => [
            'type' => 'etcd',
            'endpoints' => explode(',', env('ETCD_ENDPOINTS', 'http://localhost:2379')),
            'namespace' => '/framework/services',
            'ttl' => 30,
            'heartbeat_interval' => 10,
        ],
        
        'protocols' => [
            'external' => [
                [
                    'type' => 'REST',
                    'enabled' => true,
                    'port' => 8082,
                    'path' => '/api',
                ],
                [
                    'type' => 'WebSocket',
                    'enabled' => true,
                    'port' => 8082,
                    'path' => '/ws',
                ],
                [
                    'type' => 'JSON-RPC',
                    'enabled' => true,
                    'port' => 8082,
                    'path' => '/jsonrpc',
                ],
                [
                    'type' => 'MQTT',
                    'enabled' => false,
                    'port' => 1883,
                ],
            ],
            
            'internal' => [
                [
                    'type' => 'gRPC',
                    'enabled' => true,
                    'port' => 9002,
                    'serialization' => 'PROTOBUF',
                    'compression' => true,
                ],
                [
                    'type' => 'JSON-RPC',
                    'enabled' => true,
                    'serialization' => 'JSON',
                ],
                [
                    'type' => 'Custom',
                    'enabled' => false,
                ],
            ],
        ],
        
        'connection_pool' => [
            'max_connections' => 100,
            'min_connections' => 10,
            'idle_timeout' => 300000,
            'max_lifetime' => 1800000,
            'connection_timeout' => 5000,
        ],
        
        'security' => [
            'tls' => [
                'enabled' => false,
                'cert_file' => '',
                'key_file' => '',
                'ca_file' => '',
            ],
            'authentication' => [
                'enabled' => false,
                'type' => 'jwt',
            ],
            'authorization' => [
                'enabled' => false,
                'type' => 'rbac',
            ],
        ],
        
        'observability' => [
            'logging' => [
                'level' => 'info',
                'format' => 'json',
                'output' => 'stdout',
            ],
            'metrics' => [
                'enabled' => true,
                'port' => 9002,
                'path' => '/metrics',
            ],
            'tracing' => [
                'enabled' => true,
                'exporter' => 'jaeger',
                'endpoint' => 'http://localhost:14268/api/traces',
                'sampling_rate' => 1.0,
            ],
        ],
    ],
];
