package com.framework.connection;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.router.ServiceEndpoint;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Netty 的连接池
 * 
 * 为单个 ServiceEndpoint 管理一组连接，支持连接复用、空闲超时、健康检查等
 * 
 * **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 12.2**
 */
public class NettyConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(NettyConnectionPool.class);

    private final ServiceEndpoint endpoint;
    private volatile ConnectionConfig config;
    private final EventLoopGroup eventLoopGroup;
    private final boolean ownsEventLoopGroup;

    /** 所有连接（包括活跃和空闲） */
    private final CopyOnWriteArrayList<ManagedConnection> connections;

    /** 空闲连接队列 */
    private final LinkedBlockingDeque<ManagedConnection> idleQueue;

    /** 等待获取连接的信号量 */
    private final AtomicInteger totalCount;

    /** 是否已关闭 */
    private final AtomicBoolean closed;

    /** 健康检查定时任务 */
    private ScheduledExecutorService scheduler;

    public NettyConnectionPool(ServiceEndpoint endpoint, ConnectionConfig config) {
        this(endpoint, config, null);
    }

    public NettyConnectionPool(ServiceEndpoint endpoint, ConnectionConfig config,
                               EventLoopGroup sharedEventLoopGroup) {
        this.endpoint = endpoint;
        this.config = config;
        this.connections = new CopyOnWriteArrayList<>();
        this.idleQueue = new LinkedBlockingDeque<>();
        this.totalCount = new AtomicInteger(0);
        this.closed = new AtomicBoolean(false);

        if (sharedEventLoopGroup != null) {
            this.eventLoopGroup = sharedEventLoopGroup;
            this.ownsEventLoopGroup = false;
        } else {
            this.eventLoopGroup = new NioEventLoopGroup(2);
            this.ownsEventLoopGroup = true;
        }

        startMaintenanceTask();
    }

    /**
     * 获取连接（优先复用空闲连接）
     */
    public ManagedConnection acquire() {
        if (closed.get()) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "连接池已关闭");
        }

        // 1. 尝试从空闲队列获取
        ManagedConnection conn = tryAcquireIdle();
        if (conn != null) {
            return conn;
        }

        // 2. 尝试创建新连接
        if (totalCount.get() < config.getMaxConnections()) {
            conn = createConnection();
            if (conn != null) {
                return conn;
            }
        }

        // 3. 等待空闲连接
        try {
            conn = idleQueue.poll(config.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS);
            if (conn != null && conn.isHealthy() && conn.acquire()) {
                return conn;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "等待连接被中断", e);
        }

        // 4. 达到最大连接数且无可用连接
        throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                "无法获取连接: 已达最大连接数 " + config.getMaxConnections()
                        + ", endpoint=" + endpoint);
    }

    /**
     * 尝试从空闲队列获取健康连接
     */
    private ManagedConnection tryAcquireIdle() {
        while (true) {
            ManagedConnection conn = idleQueue.poll();
            if (conn == null) {
                return null;
            }
            // 检查连接是否健康
            if (conn.isHealthy() && !conn.isExpired(config.getMaxLifetimeMs())) {
                if (conn.acquire()) {
                    return conn;
                }
            }
            // 不健康的连接直接移除
            removeConnection(conn);
        }
    }

    /**
     * 创建新连接
     */
    private ManagedConnection createConnection() {
        // CAS 增加计数，防止超过最大连接数
        while (true) {
            int current = totalCount.get();
            if (current >= config.getMaxConnections()) {
                return null;
            }
            if (totalCount.compareAndSet(current, current + 1)) {
                break;
            }
        }

        try {
            Channel channel = doConnect();
            String connId = UUID.randomUUID().toString().substring(0, 8);
            ManagedConnection conn = new ManagedConnection(connId, endpoint, channel);
            conn.acquire();
            connections.add(conn);

            logger.debug("创建新连接: id={}, endpoint={}, total={}",
                    connId, endpoint, totalCount.get());
            return conn;

        } catch (Exception e) {
            totalCount.decrementAndGet();
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "创建连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行 Netty 连接
     */
    private Channel doConnect() {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, config.isKeepAlive())
                    .option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.getConnectTimeoutMs())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 基础 pipeline，具体协议处理器由使用方添加
                            ch.pipeline().addLast("idleHandler",
                                    new io.netty.handler.timeout.IdleStateHandler(
                                            0, 0, (int) (config.getIdleTimeoutMs() / 1000),
                                            TimeUnit.SECONDS));
                        }
                    });

            ChannelFuture future = bootstrap.connect(endpoint.getAddress(), endpoint.getPort())
                    .sync();
            return future.channel();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "连接被中断: " + endpoint, e);
        } catch (Exception e) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "连接失败: " + endpoint + ", " + e.getMessage(), e);
        }
    }

    /**
     * 释放连接回池
     */
    public void release(ManagedConnection connection) {
        if (connection == null) return;

        connection.release();

        if (closed.get() || !connection.isHealthy()
                || connection.isExpired(config.getMaxLifetimeMs())) {
            removeConnection(connection);
            return;
        }

        // 放回空闲队列
        if (connection.isIdle()) {
            idleQueue.offerFirst(connection);
        }
    }

    /**
     * 移除并关闭连接
     */
    private void removeConnection(ManagedConnection connection) {
        if (connections.remove(connection)) {
            totalCount.decrementAndGet();
            connection.close().whenComplete((v, ex) -> {
                if (ex != null) {
                    logger.warn("关闭连接失败: id={}", connection.getId(), ex);
                } else {
                    logger.debug("连接已关闭: id={}", connection.getId());
                }
            });
        }
    }

    /**
     * 启动维护定时任务（空闲超时清理、健康检查）
     */
    private void startMaintenanceTask() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "conn-pool-maintenance-" + endpoint.getServiceName());
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::performMaintenance,
                config.getHealthCheckIntervalMs(),
                config.getHealthCheckIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 执行维护：清理空闲超时和不健康的连接
     */
    void performMaintenance() {
        if (closed.get()) return;

        List<ManagedConnection> toRemove = new ArrayList<>();

        for (ManagedConnection conn : connections) {
            // 清理已关闭的连接
            if (conn.isClosed()) {
                toRemove.add(conn);
                continue;
            }
            // 清理空闲超时的连接（保留最小连接数）
            if (conn.isIdle() && conn.isIdleTimedOut(config.getIdleTimeoutMs())
                    && totalCount.get() > config.getMinConnections()) {
                toRemove.add(conn);
                continue;
            }
            // 清理超过最大存活时间的空闲连接
            if (conn.isIdle() && conn.isExpired(config.getMaxLifetimeMs())) {
                toRemove.add(conn);
            }
        }

        for (ManagedConnection conn : toRemove) {
            idleQueue.remove(conn);
            removeConnection(conn);
        }

        if (!toRemove.isEmpty()) {
            logger.debug("维护清理: 移除 {} 个连接, 剩余 {}, endpoint={}",
                    toRemove.size(), totalCount.get(), endpoint);
        }
    }

    /**
     * 关闭连接池中的所有连接
     */
    public CompletableFuture<Void> close() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("关闭连接池: endpoint={}, connections={}", endpoint, totalCount.get());

        // 停止维护任务
        if (scheduler != null) {
            scheduler.shutdown();
        }

        // 关闭所有连接
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ManagedConnection conn : connections) {
            futures.add(conn.close());
        }
        connections.clear();
        idleQueue.clear();
        totalCount.set(0);

        // 关闭 EventLoopGroup（如果是自己创建的）
        if (ownsEventLoopGroup) {
            eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 优雅关闭：等待活跃请求完成
     */
    public CompletableFuture<Void> shutdownGracefully(long timeoutMs) {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        logger.info("优雅关闭连接池: endpoint={}", endpoint);

        if (scheduler != null) {
            scheduler.shutdown();
        }

        return CompletableFuture.runAsync(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;

            // 等待所有活跃请求完成
            while (System.currentTimeMillis() < deadline) {
                boolean allIdle = connections.stream()
                        .allMatch(c -> c.isIdle() || c.isClosed());
                if (allIdle) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 关闭所有连接
            for (ManagedConnection conn : connections) {
                conn.close();
            }
            connections.clear();
            idleQueue.clear();
            totalCount.set(0);

            if (ownsEventLoopGroup) {
                eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
            }
        });
    }

    /**
     * 获取连接池统计信息
     */
    public ConnectionPoolStats getStats() {
        int total = 0, active = 0, idle = 0, closedCount = 0;
        for (ManagedConnection conn : connections) {
            total++;
            switch (conn.getState()) {
                case ACTIVE -> active++;
                case IDLE -> idle++;
                case CLOSED -> closedCount++;
            }
        }
        return new ConnectionPoolStats(total, active, idle, closedCount,
                config.getMaxConnections());
    }

    /**
     * 更新配置
     */
    public void updateConfig(ConnectionConfig newConfig) {
        this.config = newConfig;
    }

    public ServiceEndpoint getEndpoint() { return endpoint; }
    public boolean isClosed() { return closed.get(); }
    public int size() { return totalCount.get(); }
}
