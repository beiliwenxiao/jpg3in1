package com.framework.client;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 框架客户端接口
 * 提供统一的服务调用和注册功能
 */
public interface FrameworkClient {
    
    /**
     * 同步调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应对象
     */
    <T> T call(String service, String method, Object request, Class<T> responseType);
    
    /**
     * 异步调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应的 CompletableFuture
     */
    <T> CompletableFuture<T> callAsync(String service, String method, Object request, Class<T> responseType);
    
    /**
     * 流式调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应流
     */
    <T> Stream<T> stream(String service, String method, Object request, Class<T> responseType);
    
    /**
     * 注册服务
     * 
     * @param name 服务名称
     * @param implementation 服务实现
     */
    void registerService(String name, Object implementation);
    
    /**
     * 启动客户端
     */
    void start();
    
    /**
     * 关闭客户端
     */
    void shutdown();
}
