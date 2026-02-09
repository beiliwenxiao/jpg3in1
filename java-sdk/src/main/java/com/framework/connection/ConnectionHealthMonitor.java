package com.framework.connection;

import com.framework.protocol.router.ServiceEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 连接健康监控器
 * 
 * 定期检查连接池中连接的健康状态，清理不健康的连接，
 * 并在连接数低于最小值时触发补充
 * 
 * **验证需求: 7.4, 7.6**
 */
public class ConnectionHealthMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionHealthMonitor.class);

    private final DefaultConnectionManager connectionManager;
    private final ConnectionConfig config;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    /** 健康检查监听器 */
    private volatile HealthCheckListener listener;

    public ConnectionHealthMonitor(DefaultConnectionManager connectionManager,
                                   ConnectionConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.running = new AtomicBoolean(false);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "conn-health-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动健康监控
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("健康监控已在运行");
            return;
        }

        logger.info("启动连接健康监控, 检查间隔={}ms", config.getHealthCheckIntervalMs());

        scheduler.scheduleAtFixedRate(this::checkHealth,
                config.getHealthCheckIntervalMs(),
                config.getHealthCheckIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 停止健康监控
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        logger.info("停止连接健康监控");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 执行健康检查
     */
    void checkHealth() {
        if (!running.get() || connectionManager.isClosed()) return;

        try {
            ConnectionPoolStats totalStats = connectionManager.getTotalStats();

            logger.debug("健康检查: total={}, active={}, idle={}, closed={}",
                    totalStats.getTotalConnections(),
                    totalStats.getActiveConnections(),
                    totalStats.getIdleConnections(),
                    totalStats.getClosedConnections());

            // 检查是否达到最大连接数
            if (totalStats.getActiveConnections() >= config.getMaxConnections()) {
                logger.warn("连接池已达最大连接数: active={}, max={}",
                        totalStats.getActiveConnections(), config.getMaxConnections());

                if (listener != null) {
                    listener.onMaxConnectionsReached(totalStats);
                }
            }

            // 检查连接池是否健康
            if (totalStats.getTotalConnections() > 0) {
                double healthRatio = (double) (totalStats.getTotalConnections()
                        - totalStats.getClosedConnections())
                        / totalStats.getTotalConnections();

                if (healthRatio < 0.5) {
                    logger.warn("连接池健康度低: healthRatio={}, stats={}",
                            String.format("%.2f", healthRatio), totalStats);

                    if (listener != null) {
                        listener.onPoolUnhealthy(totalStats, healthRatio);
                    }
                }
            }

            // 通知监听器
            if (listener != null) {
                listener.onHealthCheckComplete(totalStats);
            }

        } catch (Exception e) {
            logger.error("健康检查异常", e);
        }
    }

    /**
     * 获取指定端点的健康状态
     */
    public HealthStatus getEndpointHealth(ServiceEndpoint endpoint) {
        ConnectionPoolStats stats = connectionManager.getPoolStats(endpoint);

        if (stats.getTotalConnections() == 0) {
            return new HealthStatus(endpoint, HealthStatus.Status.UNKNOWN, stats,
                    "无连接");
        }

        int healthy = stats.getTotalConnections() - stats.getClosedConnections();
        double healthRatio = (double) healthy / stats.getTotalConnections();

        if (healthRatio >= 0.8) {
            return new HealthStatus(endpoint, HealthStatus.Status.HEALTHY, stats,
                    "连接池健康");
        } else if (healthRatio >= 0.5) {
            return new HealthStatus(endpoint, HealthStatus.Status.DEGRADED, stats,
                    "连接池部分降级, 健康比例=" + String.format("%.2f", healthRatio));
        } else {
            return new HealthStatus(endpoint, HealthStatus.Status.UNHEALTHY, stats,
                    "连接池不健康, 健康比例=" + String.format("%.2f", healthRatio));
        }
    }

    /**
     * 设置健康检查监听器
     */
    public void setListener(HealthCheckListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 健康状态
     */
    public static class HealthStatus {

        public enum Status {
            HEALTHY,    // 健康
            DEGRADED,   // 降级
            UNHEALTHY,  // 不健康
            UNKNOWN     // 未知
        }

        private final ServiceEndpoint endpoint;
        private final Status status;
        private final ConnectionPoolStats stats;
        private final String message;

        public HealthStatus(ServiceEndpoint endpoint, Status status,
                           ConnectionPoolStats stats, String message) {
            this.endpoint = endpoint;
            this.status = status;
            this.stats = stats;
            this.message = message;
        }

        public ServiceEndpoint getEndpoint() { return endpoint; }
        public Status getStatus() { return status; }
        public ConnectionPoolStats getStats() { return stats; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "HealthStatus{" +
                    "endpoint=" + endpoint +
                    ", status=" + status +
                    ", message='" + message + '\'' +
                    ", stats=" + stats +
                    '}';
        }
    }

    /**
     * 健康检查监听器接口
     */
    public interface HealthCheckListener {

        /** 健康检查完成 */
        void onHealthCheckComplete(ConnectionPoolStats stats);

        /** 达到最大连接数 */
        void onMaxConnectionsReached(ConnectionPoolStats stats);

        /** 连接池不健康 */
        void onPoolUnhealthy(ConnectionPoolStats stats, double healthRatio);
    }
}
