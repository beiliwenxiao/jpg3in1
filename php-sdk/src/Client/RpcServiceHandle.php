<?php

declare(strict_types=1);

namespace Framework\Client;

/**
 * RPC 服务句柄 - 支持魔术方法调用
 *
 * 用法：
 *   $go = $rpc->service('go-service');
 *   $msg = $go->call('hello.sayHello');
 *
 *   // 或者用下划线替代点号
 *   $msg = $go->hello_sayHello();
 */
class RpcServiceHandle
{
    private RpcProxy $proxy;
    private string $serviceName;

    public function __construct(RpcProxy $proxy, string $serviceName)
    {
        $this->proxy = $proxy;
        $this->serviceName = $serviceName;
    }

    /**
     * 调用远程方法
     */
    public function call(string $method, mixed $params = null): mixed
    {
        return $this->proxy->call($this->serviceName, $method, $params);
    }

    /**
     * 魔术方法：将 hello_sayHello() 转换为 call('hello.sayHello')
     */
    public function __call(string $name, array $arguments): mixed
    {
        // 将下划线转换为点号（hello_sayHello → hello.sayHello）
        $method = str_replace('_', '.', $name);
        $params = $arguments[0] ?? null;
        return $this->call($method, $params);
    }
}
