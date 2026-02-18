<?php

declare(strict_types=1);

namespace Framework\Observability;

use Monolog\Logger as MonologLogger;
use Monolog\Handler\StreamHandler;
use Monolog\Formatter\JsonFormatter;

/**
 * 日志记录器 - 基于Monolog
 */
class Logger
{
    private MonologLogger $logger;
    private string $level;

    public function __construct(
        string $name = 'framework',
        string $level = 'info',
        string $output = 'php://stdout'
    ) {
        $this->level = $level;
        $this->logger = new MonologLogger($name);
        $handler = new StreamHandler($output, $this->resolveLevel($level));
        $handler->setFormatter(new JsonFormatter());
        $this->logger->pushHandler($handler);
    }

    public function info(string $message, array $context = []): void
    {
        $this->logger->info($message, $this->enrichContext($context));
    }

    public function error(string $message, array $context = []): void
    {
        $this->logger->error($message, $this->enrichContext($context));
    }

    public function warning(string $message, array $context = []): void
    {
        $this->logger->warning($message, $this->enrichContext($context));
    }

    public function debug(string $message, array $context = []): void
    {
        $this->logger->debug($message, $this->enrichContext($context));
    }

    public function setLevel(string $level): void
    {
        $this->level = $level;
    }

    public function getLevel(): string
    {
        return $this->level;
    }

    private function enrichContext(array $context): array
    {
        return array_merge([
            'timestamp'   => date('c'),
            'service'     => 'php-sdk',
            'request_id'  => $context['request_id'] ?? uniqid(),
        ], $context);
    }

    private function resolveLevel(string $level): int
    {
        return match (strtolower($level)) {
            'debug'   => MonologLogger::DEBUG,
            'warning' => MonologLogger::WARNING,
            'error'   => MonologLogger::ERROR,
            default   => MonologLogger::INFO,
        };
    }
}
