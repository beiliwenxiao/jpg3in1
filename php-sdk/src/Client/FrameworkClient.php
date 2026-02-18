<?php

declare(strict_types=1);

namespace Framework\Client;

use React\Promise\PromiseInterface;

/**
 * 框架客户端接口
 */
interface FrameworkClient
{
    /**
     * 同步调用服务
     *
     * @param string $service 服务名称
     * @param string $method 方法名称
     * @param mixed $request 请求对象
     * @return mixed 响应对象
     */
    public function call(string $service, string $method, $request): mixed;

    /**
     * 异步调用服务
     *
     * @param string $service 服务名称
     * @param string $method 方法名称
     * @param mixed $request 请求对象
     * @return PromiseInterface 响应的 Promise
     */
    public function callAsync(string $service, string $method, $request): PromiseInterface;

    /**
     * 注册服务
     *
     * @param string $name 服务名称
     * @param callable $handler 服务处理器
     */
    public function registerService(string $name, callable $handler): void;

    /**
     * 启动客户端
     */
    public function start(): void;

    /**
     * 关闭客户端
     */
    public function shutdown(): void;
}
