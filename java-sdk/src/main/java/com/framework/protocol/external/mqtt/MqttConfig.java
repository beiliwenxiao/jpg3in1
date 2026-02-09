package com.framework.protocol.external.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT 配置
 * 
 * 配置 MQTT 客户端的连接参数和订阅主题
 */
@Configuration
@ConfigurationProperties(prefix = "framework.mqtt")
public class MqttConfig {
    
    /**
     * MQTT Broker 地址
     * 格式: tcp://host:port 或 ssl://host:port
     */
    private String brokerUrl = "tcp://localhost:1883";
    
    /**
     * 客户端 ID
     */
    private String clientId = "framework-mqtt-client";
    
    /**
     * 用户名（可选）
     */
    private String username;
    
    /**
     * 密码（可选）
     */
    private String password;
    
    /**
     * 连接超时时间（秒）
     */
    private int connectionTimeout = 30;
    
    /**
     * 保持连接时间（秒）
     */
    private int keepAliveInterval = 60;
    
    /**
     * 是否自动重连
     */
    private boolean automaticReconnect = true;
    
    /**
     * 是否清除会话
     */
    private boolean cleanSession = true;
    
    /**
     * 订阅的主题列表
     */
    private List<String> subscribeTopics = new ArrayList<>();
    
    /**
     * 默认 QoS 级别
     * 0 = 最多一次
     * 1 = 至少一次
     * 2 = 恰好一次
     */
    private int defaultQos = 1;
    
    /**
     * 是否启用 MQTT 处理器
     */
    private boolean enabled = false;
    
    // Getters and Setters
    
    public String getBrokerUrl() {
        return brokerUrl;
    }
    
    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }
    
    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }
    
    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }
    
    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }
    
    public boolean isCleanSession() {
        return cleanSession;
    }
    
    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }
    
    public List<String> getSubscribeTopics() {
        return subscribeTopics;
    }
    
    public void setSubscribeTopics(List<String> subscribeTopics) {
        this.subscribeTopics = subscribeTopics;
    }
    
    public int getDefaultQos() {
        return defaultQos;
    }
    
    public void setDefaultQos(int defaultQos) {
        this.defaultQos = defaultQos;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
