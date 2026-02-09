package com.framework.connection;

/**
 * 连接池统计信息
 * 
 * **验证需求: 7.4**
 */
public class ConnectionPoolStats {

    private final int totalConnections;
    private final int activeConnections;
    private final int idleConnections;
    private final int closedConnections;
    private final int maxConnections;

    public ConnectionPoolStats(int totalConnections, int activeConnections,
                               int idleConnections, int closedConnections,
                               int maxConnections) {
        this.totalConnections = totalConnections;
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
        this.closedConnections = closedConnections;
        this.maxConnections = maxConnections;
    }

    public int getTotalConnections() { return totalConnections; }
    public int getActiveConnections() { return activeConnections; }
    public int getIdleConnections() { return idleConnections; }
    public int getClosedConnections() { return closedConnections; }
    public int getMaxConnections() { return maxConnections; }

    @Override
    public String toString() {
        return "ConnectionPoolStats{" +
                "total=" + totalConnections +
                ", active=" + activeConnections +
                ", idle=" + idleConnections +
                ", closed=" + closedConnections +
                ", max=" + maxConnections +
                '}';
    }
}
