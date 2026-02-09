package com.framework.connection;

/**
 * 连接池配置
 * 
 * 提供连接池的各项配置参数
 * 
 * **验证需求: 7.1, 12.2**
 */
public class ConnectionConfig {

    /** 最大连接数 */
    private int maxConnections;

    /** 最小连接数（核心连接数） */
    private int minConnections;

    /** 连接空闲超时时间（毫秒） */
    private long idleTimeoutMs;

    /** 连接最大存活时间（毫秒） */
    private long maxLifetimeMs;

    /** 获取连接超时时间（毫秒） */
    private long connectionTimeoutMs;

    /** 连接建立超时时间（毫秒） */
    private long connectTimeoutMs;

    /** 健康检查间隔（毫秒） */
    private long healthCheckIntervalMs;

    /** 重连延迟（毫秒） */
    private long reconnectDelayMs;

    /** 最大重连次数 */
    private int maxReconnectAttempts;

    /** 是否启用 TCP KeepAlive */
    private boolean keepAlive;

    /** 是否启用 TCP NoDelay */
    private boolean tcpNoDelay;

    public ConnectionConfig() {
        this.maxConnections = 100;
        this.minConnections = 10;
        this.idleTimeoutMs = 300_000;       // 5 分钟
        this.maxLifetimeMs = 1_800_000;     // 30 分钟
        this.connectionTimeoutMs = 5_000;   // 5 秒
        this.connectTimeoutMs = 5_000;      // 5 秒
        this.healthCheckIntervalMs = 30_000; // 30 秒
        this.reconnectDelayMs = 1_000;      // 1 秒
        this.maxReconnectAttempts = 3;
        this.keepAlive = true;
        this.tcpNoDelay = true;
    }

    // Getters and Setters

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    public int getMinConnections() { return minConnections; }
    public void setMinConnections(int minConnections) { this.minConnections = minConnections; }

    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }

    public long getMaxLifetimeMs() { return maxLifetimeMs; }
    public void setMaxLifetimeMs(long maxLifetimeMs) { this.maxLifetimeMs = maxLifetimeMs; }

    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) { this.healthCheckIntervalMs = healthCheckIntervalMs; }

    public long getReconnectDelayMs() { return reconnectDelayMs; }
    public void setReconnectDelayMs(long reconnectDelayMs) { this.reconnectDelayMs = reconnectDelayMs; }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

    public boolean isKeepAlive() { return keepAlive; }
    public void setKeepAlive(boolean keepAlive) { this.keepAlive = keepAlive; }

    public boolean isTcpNoDelay() { return tcpNoDelay; }
    public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }

    @Override
    public String toString() {
        return "ConnectionConfig{" +
                "maxConnections=" + maxConnections +
                ", minConnections=" + minConnections +
                ", idleTimeoutMs=" + idleTimeoutMs +
                ", maxLifetimeMs=" + maxLifetimeMs +
                ", connectionTimeoutMs=" + connectionTimeoutMs +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", healthCheckIntervalMs=" + healthCheckIntervalMs +
                '}';
    }
}
