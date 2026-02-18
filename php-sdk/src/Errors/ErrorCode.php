<?php

declare(strict_types=1);

namespace Framework\Errors;

/**
 * 统一错误码定义
 */
class ErrorCode
{
    // 客户端错误 4xx
    const BAD_REQUEST       = 400;
    const UNAUTHORIZED      = 401;
    const FORBIDDEN         = 403;
    const NOT_FOUND         = 404;
    const TIMEOUT           = 408;

    // 服务端错误 5xx
    const INTERNAL_ERROR    = 500;
    const NOT_IMPLEMENTED   = 501;
    const SERVICE_UNAVAILABLE = 503;

    // 框架错误 6xx
    const PROTOCOL_ERROR    = 600;
    const SERIALIZATION_ERROR = 601;
    const ROUTING_ERROR     = 602;
    const CONNECTION_ERROR  = 603;

    private static array $messages = [
        self::BAD_REQUEST        => 'Bad Request',
        self::UNAUTHORIZED       => 'Unauthorized',
        self::FORBIDDEN          => 'Forbidden',
        self::NOT_FOUND          => 'Not Found',
        self::TIMEOUT            => 'Request Timeout',
        self::INTERNAL_ERROR     => 'Internal Server Error',
        self::NOT_IMPLEMENTED    => 'Not Implemented',
        self::SERVICE_UNAVAILABLE => 'Service Unavailable',
        self::PROTOCOL_ERROR     => 'Protocol Error',
        self::SERIALIZATION_ERROR => 'Serialization Error',
        self::ROUTING_ERROR      => 'Routing Error',
        self::CONNECTION_ERROR   => 'Connection Error',
    ];

    public static function getMessage(int $code): string
    {
        return self::$messages[$code] ?? 'Unknown Error';
    }
}
