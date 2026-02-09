package com.framework.connection;

import com.framework.protocol.router.ServiceEndpoint;

import java.util.concurrent.CompletableFuture;

/**
 * 连接管理器接口
 * 
 * 管理到各服务端点的连接池，提供连接获取、释放和生命周期管理
 * 
 * **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 12.2**
 */
public interface ConnectionManager {

    /**
     * 获取到指定端点的连接
     * 优先复用空闲连接，如果没有则创建新连接
     * 
     * @param endpoint 目标服务端点
     * @return 受管连接
     */
    ManagedConnection getConnection(ServiceEndpoint endpoint);

    /**
     * 释放连接回连接池
     * 
     * @param connection 要释放的连接
     */
    void releaseConnection(ManagedConnection connection);

    /**
     * 关闭到指定端点的所有连接
     * 
     * @param endpoint 目标服务端点
     * @return 关闭完成的 Future
     */
    CompletableFuture<Void> closeConnections(ServiceEndpoint endpoint);

    /**
     * 关闭所有连接并释放资源
     * 
     * @return 关闭完成的 Future
     */
    CompletableFuture<Void> closeAll();

    /**
     * 优雅关闭：等待所有活跃请求完成后关闭
     * 
     * @param timeoutMs 等待超时时间（毫秒）
     * @return 关闭完成的 Future
     */
    CompletableFuture<Void> shutdownGracefully(long timeoutMs);

    /**
     * 获取到指定端点的连接池统计信息
     * 
     * @param endpoint 目标服务端点
     * @return 连接池统计
     */
    ConnectionPoolStats getPoolStats(ServiceEndpoint endpoint);

    /**
     * 获取全局连接池统计信息
     * 
     * @return 全局统计
     */
    ConnectionPoolStats getTotalStats();

    /**
     * 更新连接池配置
     * 
     * @param config 新配置
     */
    void updateConfig(ConnectionConfig config);
}
