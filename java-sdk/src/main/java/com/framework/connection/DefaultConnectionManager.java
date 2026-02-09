package com.framework.connection;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.router.ServiceEndpoint;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 默认连接管理器实现
 * 
 * 为每个 ServiceEndpoint 维护独立的连接池，共享 Netty EventLoopGroup
 * 
 * **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 12.2**
 */
public class DefaultConnectionManager implements ConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConnectionManager.class);

    /** 每个端点对应一个连接池，key = address:port */
    private final ConcurrentHashMap<String, NettyConnectionPool> pools;

    /** 共享的 Netty EventLoopGroup */
    private final EventLoopGroup sharedEventLoopGroup;

    /** 连接池配置 */
    private volatile ConnectionConfig config;

    /** 是否已关闭 */
    private final AtomicBoolean closed;

    public DefaultConnectionManager(ConnectionConfig config) {
        this.config = config;
        this.pools = new ConcurrentHashMap<>();
        this.sharedEventLoopGroup = new NioEventLoopGroup(
                Runtime.getRuntime().availableProcessors() * 2);
        this.closed = new AtomicBoolean(false);

        logger.info("连接管理器已初始化: {}", config);
    }

    @Override
    public ManagedConnection getConnection(ServiceEndpoint endpoint) {
        if (closed.get()) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "连接管理器已关闭");
        }
        if (endpoint == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务端点不能为空");
        }

        String key = endpointKey(endpoint);
        NettyConnectionPool pool = pools.computeIfAbsent(key,
                k -> new NettyConnectionPool(endpoint, config, sharedEventLoopGroup));

        return pool.acquire();
    }

    @Override
    public void releaseConnection(ManagedConnection connection) {
        if (connection == null) return;

        String key = endpointKey(connection.getEndpoint());
        NettyConnectionPool pool = pools.get(key);
        if (pool != null) {
            pool.release(connection);
        } else {
            // 池已不存在，直接关闭连接
            connection.close();
        }
    }

    @Override
    public CompletableFuture<Void> closeConnections(ServiceEndpoint endpoint) {
        String key = endpointKey(endpoint);
        NettyConnectionPool pool = pools.remove(key);
        if (pool != null) {
            return pool.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> closeAll() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("关闭所有连接池, 共 {} 个", pools.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (NettyConnectionPool pool : pools.values()) {
            futures.add(pool.close());
        }
        pools.clear();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    sharedEventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
                    logger.info("连接管理器已关闭");
                });
    }

    @Override
    public CompletableFuture<Void> shutdownGracefully(long timeoutMs) {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("优雅关闭连接管理器, 超时={}ms", timeoutMs);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (NettyConnectionPool pool : pools.values()) {
            futures.add(pool.shutdownGracefully(timeoutMs));
        }
        pools.clear();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    sharedEventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
                    logger.info("连接管理器优雅关闭完成");
                });
    }

    @Override
    public ConnectionPoolStats getPoolStats(ServiceEndpoint endpoint) {
        String key = endpointKey(endpoint);
        NettyConnectionPool pool = pools.get(key);
        if (pool != null) {
            return pool.getStats();
        }
        return new ConnectionPoolStats(0, 0, 0, 0, config.getMaxConnections());
    }

    @Override
    public ConnectionPoolStats getTotalStats() {
        int total = 0, active = 0, idle = 0, closedCount = 0;
        for (NettyConnectionPool pool : pools.values()) {
            ConnectionPoolStats stats = pool.getStats();
            total += stats.getTotalConnections();
            active += stats.getActiveConnections();
            idle += stats.getIdleConnections();
            closedCount += stats.getClosedConnections();
        }
        return new ConnectionPoolStats(total, active, idle, closedCount,
                config.getMaxConnections() * Math.max(pools.size(), 1));
    }

    @Override
    public void updateConfig(ConnectionConfig config) {
        this.config = config;
        for (NettyConnectionPool pool : pools.values()) {
            pool.updateConfig(config);
        }
        logger.info("连接池配置已更新: {}", config);
    }

    /**
     * 生成端点的唯一 key
     */
    private String endpointKey(ServiceEndpoint endpoint) {
        return endpoint.getAddress() + ":" + endpoint.getPort();
    }

    /**
     * 获取当前管理的连接池数量
     */
    public int getPoolCount() {
        return pools.size();
    }

    /**
     * 检查是否已关闭
     */
    public boolean isClosed() {
        return closed.get();
    }
}
