package com.framework.connection;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.router.ServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 连接生命周期管理器
 * 
 * 负责连接的空闲超时关闭、失败重连和优雅关闭
 * 包装 DefaultConnectionManager，增加重连和生命周期管理能力
 * 
 * **验证需求: 7.2, 7.3, 7.5**
 */
public class ConnectionLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionLifecycleManager.class);

    private final DefaultConnectionManager connectionManager;
    private final ConnectionConfig config;
    private final AtomicBoolean closed;

    /** 重连任务调度器 */
    private final ScheduledExecutorService reconnectScheduler;

    /** 正在重连的端点集合 */
    private final ConcurrentHashMap<String, AtomicBoolean> reconnecting;

    public ConnectionLifecycleManager(DefaultConnectionManager connectionManager,
                                      ConnectionConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.closed = new AtomicBoolean(false);
        this.reconnecting = new ConcurrentHashMap<>();

        this.reconnectScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "conn-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 获取连接，失败时自动重连
     */
    public ManagedConnection getConnectionWithRetry(ServiceEndpoint endpoint) {
        if (closed.get()) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "生命周期管理器已关闭");
        }

        int attempts = 0;
        long delay = config.getReconnectDelayMs();
        FrameworkException lastException = null;

        while (attempts <= config.getMaxReconnectAttempts()) {
            try {
                return connectionManager.getConnection(endpoint);
            } catch (FrameworkException e) {
                lastException = e;
                if (e.getErrorCode() != ErrorCode.CONNECTION_ERROR) {
                    throw e; // 非连接错误直接抛出
                }

                attempts++;
                if (attempts > config.getMaxReconnectAttempts()) {
                    break;
                }

                logger.warn("连接失败, 第 {}/{} 次重试, endpoint={}, delay={}ms",
                        attempts, config.getMaxReconnectAttempts(), endpoint, delay);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                            "重连被中断", ie);
                }

                // 指数退避
                delay = Math.min(delay * 2, 30_000);
            }
        }

        throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                "重连失败: 已达最大重试次数 " + config.getMaxReconnectAttempts()
                        + ", endpoint=" + endpoint,
                lastException);
    }

    /**
     * 异步重连到指定端点
     * 使用指数退避策略
     */
    public CompletableFuture<ManagedConnection> reconnectAsync(ServiceEndpoint endpoint) {
        String key = endpoint.getAddress() + ":" + endpoint.getPort();
        AtomicBoolean inProgress = reconnecting.computeIfAbsent(key, k -> new AtomicBoolean(false));

        if (!inProgress.compareAndSet(false, true)) {
            // 已有重连任务在进行
            return CompletableFuture.failedFuture(
                    new FrameworkException(ErrorCode.CONNECTION_ERROR,
                            "重连已在进行中: " + endpoint));
        }

        CompletableFuture<ManagedConnection> future = new CompletableFuture<>();

        reconnectScheduler.submit(() -> {
            try {
                ManagedConnection conn = getConnectionWithRetry(endpoint);
                future.complete(conn);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                inProgress.set(false);
            }
        });

        return future;
    }

    /**
     * 释放连接
     */
    public void releaseConnection(ManagedConnection connection) {
        connectionManager.releaseConnection(connection);
    }

    /**
     * 优雅关闭
     * 等待所有活跃请求完成后关闭所有连接
     */
    public CompletableFuture<Void> shutdownGracefully(long timeoutMs) {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("优雅关闭连接生命周期管理器, 超时={}ms", timeoutMs);

        reconnectScheduler.shutdown();
        try {
            if (!reconnectScheduler.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                reconnectScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return connectionManager.shutdownGracefully(timeoutMs);
    }

    /**
     * 强制关闭
     */
    public CompletableFuture<Void> close() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        reconnectScheduler.shutdownNow();
        return connectionManager.closeAll();
    }

    /**
     * 获取连接池统计
     */
    public ConnectionPoolStats getPoolStats(ServiceEndpoint endpoint) {
        return connectionManager.getPoolStats(endpoint);
    }

    /**
     * 获取全局统计
     */
    public ConnectionPoolStats getTotalStats() {
        return connectionManager.getTotalStats();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public DefaultConnectionManager getConnectionManager() {
        return connectionManager;
    }
}
