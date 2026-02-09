package com.framework.protocol.internal.jsonrpc;

/**
 * JSON-RPC 内部协议配置类
 * 
 * 用于配置 JSON-RPC 内部协议的服务端和客户端参数
 */
public class JsonRpcInternalConfig {
    
    /**
     * 服务端配置
     */
    public static class ServerConfig {
        private int port;
        private boolean enabled;
        private int maxConnections;
        private int readTimeout;
        private int writeTimeout;
        private boolean reuseAddress;
        
        public ServerConfig() {
            this.port = 9091;
            this.enabled = true;
            this.maxConnections = 1000;
            this.readTimeout = 30000; // 30 秒
            this.writeTimeout = 30000; // 30 秒
            this.reuseAddress = true;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getMaxConnections() {
            return maxConnections;
        }
        
        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }
        
        public int getReadTimeout() {
            return readTimeout;
        }
        
        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
        
        public int getWriteTimeout() {
            return writeTimeout;
        }
        
        public void setWriteTimeout(int writeTimeout) {
            this.writeTimeout = writeTimeout;
        }
        
        public boolean isReuseAddress() {
            return reuseAddress;
        }
        
        public void setReuseAddress(boolean reuseAddress) {
            this.reuseAddress = reuseAddress;
        }
    }
    
    /**
     * 客户端配置
     */
    public static class ClientConfig {
        private int connectionTimeout;
        private int readTimeout;
        private int maxRetryAttempts;
        private long initialBackoffMs;
        private long maxBackoffMs;
        private double backoffMultiplier;
        private boolean keepAlive;
        
        public ClientConfig() {
            this.connectionTimeout = 5000; // 5 秒
            this.readTimeout = 30000; // 30 秒
            this.maxRetryAttempts = 3;
            this.initialBackoffMs = 100;
            this.maxBackoffMs = 5000;
            this.backoffMultiplier = 2.0;
            this.keepAlive = true;
        }
        
        public int getConnectionTimeout() {
            return connectionTimeout;
        }
        
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
        
        public int getReadTimeout() {
            return readTimeout;
        }
        
        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
        
        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }
        
        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }
        
        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }
        
        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }
        
        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }
        
        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }
        
        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }
        
        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
        
        public boolean isKeepAlive() {
            return keepAlive;
        }
        
        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }
    }
    
    private ServerConfig server;
    private ClientConfig client;
    
    public JsonRpcInternalConfig() {
        this.server = new ServerConfig();
        this.client = new ClientConfig();
    }
    
    public ServerConfig getServer() {
        return server;
    }
    
    public void setServer(ServerConfig server) {
        this.server = server;
    }
    
    public ClientConfig getClient() {
        return client;
    }
    
    public void setClient(ClientConfig client) {
        this.client = client;
    }
}
