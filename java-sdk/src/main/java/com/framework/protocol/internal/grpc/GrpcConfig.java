package com.framework.protocol.internal.grpc;

/**
 * gRPC 配置类
 */
public class GrpcConfig {
    
    /**
     * 服务端配置
     */
    public static class ServerConfig {
        private int port;
        private boolean enabled;
        private int maxInboundMessageSize;
        private int maxConnectionIdle;
        private int maxConnectionAge;
        private boolean useTls;
        private String certChainFile;
        private String privateKeyFile;
        
        public ServerConfig() {
            this.port = 9090;
            this.enabled = true;
            this.maxInboundMessageSize = 4 * 1024 * 1024; // 4MB
            this.maxConnectionIdle = 300; // 5 分钟
            this.maxConnectionAge = 3600; // 1 小时
            this.useTls = false;
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
        
        public int getMaxInboundMessageSize() {
            return maxInboundMessageSize;
        }
        
        public void setMaxInboundMessageSize(int maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
        }
        
        public int getMaxConnectionIdle() {
            return maxConnectionIdle;
        }
        
        public void setMaxConnectionIdle(int maxConnectionIdle) {
            this.maxConnectionIdle = maxConnectionIdle;
        }
        
        public int getMaxConnectionAge() {
            return maxConnectionAge;
        }
        
        public void setMaxConnectionAge(int maxConnectionAge) {
            this.maxConnectionAge = maxConnectionAge;
        }
        
        public boolean isUseTls() {
            return useTls;
        }
        
        public void setUseTls(boolean useTls) {
            this.useTls = useTls;
        }
        
        public String getCertChainFile() {
            return certChainFile;
        }
        
        public void setCertChainFile(String certChainFile) {
            this.certChainFile = certChainFile;
        }
        
        public String getPrivateKeyFile() {
            return privateKeyFile;
        }
        
        public void setPrivateKeyFile(String privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
        }
    }
    
    /**
     * 客户端配置
     */
    public static class ClientConfig {
        private int maxRetryAttempts;
        private long initialBackoffMs;
        private long maxBackoffMs;
        private double backoffMultiplier;
        private long keepAliveTimeMs;
        private long keepAliveTimeoutMs;
        private boolean keepAliveWithoutCalls;
        private int maxInboundMessageSize;
        private boolean useTls;
        private String trustCertCollectionFile;
        
        public ClientConfig() {
            this.maxRetryAttempts = 3;
            this.initialBackoffMs = 100;
            this.maxBackoffMs = 5000;
            this.backoffMultiplier = 2.0;
            this.keepAliveTimeMs = 30000; // 30 秒
            this.keepAliveTimeoutMs = 10000; // 10 秒
            this.keepAliveWithoutCalls = true;
            this.maxInboundMessageSize = 4 * 1024 * 1024; // 4MB
            this.useTls = false;
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
        
        public long getKeepAliveTimeMs() {
            return keepAliveTimeMs;
        }
        
        public void setKeepAliveTimeMs(long keepAliveTimeMs) {
            this.keepAliveTimeMs = keepAliveTimeMs;
        }
        
        public long getKeepAliveTimeoutMs() {
            return keepAliveTimeoutMs;
        }
        
        public void setKeepAliveTimeoutMs(long keepAliveTimeoutMs) {
            this.keepAliveTimeoutMs = keepAliveTimeoutMs;
        }
        
        public boolean isKeepAliveWithoutCalls() {
            return keepAliveWithoutCalls;
        }
        
        public void setKeepAliveWithoutCalls(boolean keepAliveWithoutCalls) {
            this.keepAliveWithoutCalls = keepAliveWithoutCalls;
        }
        
        public int getMaxInboundMessageSize() {
            return maxInboundMessageSize;
        }
        
        public void setMaxInboundMessageSize(int maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
        }
        
        public boolean isUseTls() {
            return useTls;
        }
        
        public void setUseTls(boolean useTls) {
            this.useTls = useTls;
        }
        
        public String getTrustCertCollectionFile() {
            return trustCertCollectionFile;
        }
        
        public void setTrustCertCollectionFile(String trustCertCollectionFile) {
            this.trustCertCollectionFile = trustCertCollectionFile;
        }
    }
    
    private ServerConfig server;
    private ClientConfig client;
    
    public GrpcConfig() {
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
