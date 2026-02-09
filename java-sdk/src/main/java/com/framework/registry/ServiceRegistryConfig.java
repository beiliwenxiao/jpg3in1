package com.framework.registry;

import java.util.Arrays;
import java.util.List;

/**
 * 服务注册中心配置
 * 
 * 支持通过代码配置或环境变量覆盖。
 * 环境变量优先级高于代码配置。
 * 
 * 需求: 5.1, 5.2, 9.1, 9.2
 */
public class ServiceRegistryConfig {

    /** etcd 端点列表 */
    private List<String> endpoints;

    /** 命名空间前缀 */
    private String namespace;

    /** 服务注册 TTL（秒） */
    private long serviceTtl;

    /** 心跳间隔（秒） */
    private long heartbeatInterval;

    /** 连接超时（毫秒） */
    private long connectionTimeout;

    /** 请求超时（毫秒） */
    private long requestTimeout;

    /** 是否启用健康检查 */
    private boolean healthCheckEnabled;

    /** 健康检查间隔（秒） */
    private long healthCheckInterval;

    /** 健康检查失败阈值 */
    private int healthCheckFailureThreshold;

    /** 是否启用 TLS */
    private boolean tlsEnabled;

    /** TLS 证书路径 */
    private String tlsCertFile;

    /** TLS 密钥路径 */
    private String tlsKeyFile;

    /** TLS CA 证书路径 */
    private String tlsCaFile;

    public ServiceRegistryConfig() {
        // 默认值，与 etcd.conf.yml 保持一致
        this.endpoints = Arrays.asList("http://localhost:2379");
        this.namespace = "/framework/services";
        this.serviceTtl = 30;
        this.heartbeatInterval = 10;
        this.connectionTimeout = 3000;
        this.requestTimeout = 5000;
        this.healthCheckEnabled = true;
        this.healthCheckInterval = 10;
        this.healthCheckFailureThreshold = 3;
        this.tlsEnabled = false;

        // 环境变量覆盖
        applyEnvironmentOverrides();
    }

    /**
     * 从环境变量覆盖配置
     */
    private void applyEnvironmentOverrides() {
        String envEndpoints = System.getenv("ETCD_ENDPOINTS");
        if (envEndpoints != null && !envEndpoints.isEmpty()) {
            this.endpoints = Arrays.asList(envEndpoints.split(","));
        }

        String envNamespace = System.getenv("FRAMEWORK_NAMESPACE");
        if (envNamespace != null && !envNamespace.isEmpty()) {
            this.namespace = envNamespace + "/services";
        }

        String envTtl = System.getenv("FRAMEWORK_SERVICE_TTL");
        if (envTtl != null && !envTtl.isEmpty()) {
            this.serviceTtl = Long.parseLong(envTtl);
        }

        String envHeartbeat = System.getenv("FRAMEWORK_HEARTBEAT_INTERVAL");
        if (envHeartbeat != null && !envHeartbeat.isEmpty()) {
            this.heartbeatInterval = Long.parseLong(envHeartbeat);
        }
    }

    // Getters and Setters

    public List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public long getServiceTtl() { return serviceTtl; }
    public void setServiceTtl(long serviceTtl) { this.serviceTtl = serviceTtl; }

    public long getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(long heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }

    public long getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public long getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(long requestTimeout) { this.requestTimeout = requestTimeout; }

    public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
    public void setHealthCheckEnabled(boolean healthCheckEnabled) { this.healthCheckEnabled = healthCheckEnabled; }

    public long getHealthCheckInterval() { return healthCheckInterval; }
    public void setHealthCheckInterval(long healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }

    public int getHealthCheckFailureThreshold() { return healthCheckFailureThreshold; }
    public void setHealthCheckFailureThreshold(int threshold) { this.healthCheckFailureThreshold = threshold; }

    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }

    public String getTlsCertFile() { return tlsCertFile; }
    public void setTlsCertFile(String tlsCertFile) { this.tlsCertFile = tlsCertFile; }

    public String getTlsKeyFile() { return tlsKeyFile; }
    public void setTlsKeyFile(String tlsKeyFile) { this.tlsKeyFile = tlsKeyFile; }

    public String getTlsCaFile() { return tlsCaFile; }
    public void setTlsCaFile(String tlsCaFile) { this.tlsCaFile = tlsCaFile; }
}
