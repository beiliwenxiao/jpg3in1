package com.framework.protocol.internal.custom;

/**
 * 自定义二进制协议配置
 * 
 * 提供自定义协议的配置选项
 * 
 * **验证需求: 3.3, 9.1**
 */
public class CustomProtocolConfig {
    
    // 服务端配置
    private int serverPort;
    private boolean serverEnabled;
    
    // 客户端配置
    private int connectTimeout;
    private int requestTimeout;
    private boolean keepAlive;
    private boolean tcpNoDelay;
    
    // 连接池配置
    private int maxConnections;
    private int minConnections;
    private long idleTimeout;
    
    // 协议配置
    private int maxFrameSize;
    private int maxConcurrentStreams;
    private boolean compressionEnabled;
    private boolean checksumEnabled;
    
    // 性能配置
    private int workerThreads;
    private int bossThreads;
    
    /**
     * 默认配置
     */
    public CustomProtocolConfig() {
        // 服务端默认配置
        this.serverPort = 9090;
        this.serverEnabled = true;
        
        // 客户端默认配置
        this.connectTimeout = 5000;
        this.requestTimeout = 30000;
        this.keepAlive = true;
        this.tcpNoDelay = true;
        
        // 连接池默认配置
        this.maxConnections = 100;
        this.minConnections = 10;
        this.idleTimeout = 300000; // 5 分钟
        
        // 协议默认配置
        this.maxFrameSize = 16 * 1024 * 1024; // 16MB
        this.maxConcurrentStreams = 100;
        this.compressionEnabled = false;
        this.checksumEnabled = true;
        
        // 性能默认配置
        this.workerThreads = Runtime.getRuntime().availableProcessors() * 2;
        this.bossThreads = 1;
    }
    
    // Getters and Setters
    
    public int getServerPort() {
        return serverPort;
    }
    
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }
    
    public boolean isServerEnabled() {
        return serverEnabled;
    }
    
    public void setServerEnabled(boolean serverEnabled) {
        this.serverEnabled = serverEnabled;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    public int getRequestTimeout() {
        return requestTimeout;
    }
    
    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
    
    public boolean isKeepAlive() {
        return keepAlive;
    }
    
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }
    
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }
    
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    
    public int getMinConnections() {
        return minConnections;
    }
    
    public void setMinConnections(int minConnections) {
        this.minConnections = minConnections;
    }
    
    public long getIdleTimeout() {
        return idleTimeout;
    }
    
    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }
    
    public int getMaxFrameSize() {
        return maxFrameSize;
    }
    
    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }
    
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }
    
    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }
    
    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }
    
    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }
    
    public boolean isChecksumEnabled() {
        return checksumEnabled;
    }
    
    public void setChecksumEnabled(boolean checksumEnabled) {
        this.checksumEnabled = checksumEnabled;
    }
    
    public int getWorkerThreads() {
        return workerThreads;
    }
    
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }
    
    public int getBossThreads() {
        return bossThreads;
    }
    
    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }
    
    @Override
    public String toString() {
        return "CustomProtocolConfig{" +
                "serverPort=" + serverPort +
                ", serverEnabled=" + serverEnabled +
                ", connectTimeout=" + connectTimeout +
                ", requestTimeout=" + requestTimeout +
                ", keepAlive=" + keepAlive +
                ", tcpNoDelay=" + tcpNoDelay +
                ", maxConnections=" + maxConnections +
                ", minConnections=" + minConnections +
                ", idleTimeout=" + idleTimeout +
                ", maxFrameSize=" + maxFrameSize +
                ", maxConcurrentStreams=" + maxConcurrentStreams +
                ", compressionEnabled=" + compressionEnabled +
                ", checksumEnabled=" + checksumEnabled +
                ", workerThreads=" + workerThreads +
                ", bossThreads=" + bossThreads +
                '}';
    }
}
