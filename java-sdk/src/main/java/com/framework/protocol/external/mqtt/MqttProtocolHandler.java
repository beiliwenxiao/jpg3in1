package com.framework.protocol.external.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.external.ExternalProtocolHandler;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 协议处理器
 * 
 * 基于 Eclipse Paho MQTT 客户端
 * 支持 MQTT 消息的接收和发布
 * 
 * **验证需求: 2.4**
 */
@Component
@ConditionalOnProperty(prefix = "framework.mqtt", name = "enabled", havingValue = "true")
public class MqttProtocolHandler implements ExternalProtocolHandler, MqttCallback {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttProtocolHandler.class);
    
    private final MqttConfig mqttConfig;
    private final MqttRequestProcessor requestProcessor;
    private final ObjectMapper objectMapper;
    
    private MqttClient mqttClient;
    private final Map<String, MqttMessageHandler> topicHandlers = new ConcurrentHashMap<>();
    
    public MqttProtocolHandler(MqttConfig mqttConfig, MqttRequestProcessor requestProcessor) {
        this.mqttConfig = mqttConfig;
        this.requestProcessor = requestProcessor;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 初始化 MQTT 客户端并连接到 Broker
     */
    @PostConstruct
    public void initialize() {
        if (!mqttConfig.isEnabled()) {
            logger.info("MQTT 处理器未启用");
            return;
        }
        
        try {
            logger.info("初始化 MQTT 客户端: brokerUrl={}, clientId={}", 
                       mqttConfig.getBrokerUrl(), mqttConfig.getClientId());
            
            // 创建 MQTT 客户端
            String clientId = mqttConfig.getClientId() + "-" + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttClient(mqttConfig.getBrokerUrl(), clientId, new MemoryPersistence());
            
            // 设置回调
            mqttClient.setCallback(this);
            
            // 配置连接选项
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(mqttConfig.isCleanSession());
            options.setConnectionTimeout(mqttConfig.getConnectionTimeout());
            options.setKeepAliveInterval(mqttConfig.getKeepAliveInterval());
            options.setAutomaticReconnect(mqttConfig.isAutomaticReconnect());
            
            // 设置用户名和密码（如果配置了）
            if (mqttConfig.getUsername() != null && !mqttConfig.getUsername().isEmpty()) {
                options.setUserName(mqttConfig.getUsername());
            }
            if (mqttConfig.getPassword() != null && !mqttConfig.getPassword().isEmpty()) {
                options.setPassword(mqttConfig.getPassword().toCharArray());
            }
            
            // 连接到 Broker
            logger.info("连接到 MQTT Broker: {}", mqttConfig.getBrokerUrl());
            mqttClient.connect(options);
            logger.info("MQTT 客户端连接成功");
            
            // 订阅配置的主题
            subscribeToConfiguredTopics();
            
        } catch (MqttException e) {
            logger.error("初始化 MQTT 客户端失败: {}", e.getMessage(), e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                "Failed to initialize MQTT client: " + e.getMessage(), e);
        }
    }
    
    /**
     * 订阅配置的主题
     */
    private void subscribeToConfiguredTopics() {
        if (mqttConfig.getSubscribeTopics() == null || mqttConfig.getSubscribeTopics().isEmpty()) {
            logger.info("没有配置订阅主题");
            return;
        }
        
        for (String topic : mqttConfig.getSubscribeTopics()) {
            try {
                subscribe(topic, mqttConfig.getDefaultQos());
            } catch (Exception e) {
                logger.error("订阅主题失败: topic={}, error={}", topic, e.getMessage(), e);
            }
        }
    }
    
    /**
     * 订阅主题
     * 
     * @param topic 主题
     * @param qos QoS 级别 (0, 1, 2)
     */
    public void subscribe(String topic, int qos) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "MQTT client is not connected");
            }
            
            logger.info("订阅 MQTT 主题: topic={}, qos={}", topic, qos);
            mqttClient.subscribe(topic, qos);
            
        } catch (MqttException e) {
            logger.error("订阅主题失败: topic={}, error={}", topic, e.getMessage(), e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                "Failed to subscribe to topic: " + topic, e);
        }
    }
    
    /**
     * 取消订阅主题
     * 
     * @param topic 主题
     */
    public void unsubscribe(String topic) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "MQTT client is not connected");
            }
            
            logger.info("取消订阅 MQTT 主题: topic={}", topic);
            mqttClient.unsubscribe(topic);
            topicHandlers.remove(topic);
            
        } catch (MqttException e) {
            logger.error("取消订阅主题失败: topic={}, error={}", topic, e.getMessage(), e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                "Failed to unsubscribe from topic: " + topic, e);
        }
    }
    
    /**
     * 发布消息到主题
     * 
     * @param topic 主题
     * @param payload 消息内容
     * @param qos QoS 级别
     * @param retained 是否保留消息
     */
    public void publish(String topic, String payload, int qos, boolean retained) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "MQTT client is not connected");
            }
            
            logger.debug("发布 MQTT 消息: topic={}, qos={}, retained={}, payload={}", 
                        topic, qos, retained, payload);
            
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            message.setRetained(retained);
            
            mqttClient.publish(topic, message);
            logger.debug("MQTT 消息发布成功: topic={}", topic);
            
        } catch (MqttException e) {
            logger.error("发布消息失败: topic={}, error={}", topic, e.getMessage(), e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                "Failed to publish message to topic: " + topic, e);
        }
    }
    
    /**
     * 发布消息到主题（使用默认 QoS 和不保留）
     * 
     * @param topic 主题
     * @param payload 消息内容
     */
    public void publish(String topic, String payload) {
        publish(topic, payload, mqttConfig.getDefaultQos(), false);
    }
    
    /**
     * 注册主题处理器
     * 
     * @param topic 主题
     * @param handler 处理器
     */
    public void registerTopicHandler(String topic, MqttMessageHandler handler) {
        topicHandlers.put(topic, handler);
        logger.info("注册 MQTT 主题处理器: topic={}", topic);
    }
    
    /**
     * 移除主题处理器
     * 
     * @param topic 主题
     */
    public void removeTopicHandler(String topic) {
        topicHandlers.remove(topic);
        logger.info("移除 MQTT 主题处理器: topic={}", topic);
    }
    
    @Override
    public ExternalResponse handle(ExternalRequest request) {
        return requestProcessor.process(request);
    }
    
    // ========== MqttCallback 接口实现 ==========
    
    /**
     * 连接丢失时调用
     */
    @Override
    public void connectionLost(Throwable cause) {
        logger.error("MQTT 连接丢失: {}", cause.getMessage(), cause);
        
        // 如果启用了自动重连，Paho 客户端会自动尝试重连
        if (mqttConfig.isAutomaticReconnect()) {
            logger.info("MQTT 客户端将自动重连");
        }
    }
    
    /**
     * 消息到达时调用
     */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        logger.debug("收到 MQTT 消息: topic={}, qos={}, retained={}, size={} bytes", 
                    topic, message.getQos(), message.isRetained(), message.getPayload().length);
        
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            
            // 检查是否有注册的主题处理器
            MqttMessageHandler handler = findTopicHandler(topic);
            if (handler != null) {
                handler.handleMessage(topic, payload, message.getQos(), message.isRetained());
                return;
            }
            
            // 默认处理：解析消息并转换为外部请求
            ExternalRequest externalRequest = parseMessage(topic, payload, message);
            
            // 处理请求
            ExternalResponse externalResponse = handle(externalRequest);
            
            // 如果需要响应，发布到响应主题
            if (externalRequest.getMetadata().containsKey("responseTopic")) {
                String responseTopic = (String) externalRequest.getMetadata().get("responseTopic");
                String responsePayload = objectMapper.writeValueAsString(externalResponse.getBody());
                publish(responseTopic, responsePayload);
            }
            
        } catch (Exception e) {
            logger.error("处理 MQTT 消息失败: topic={}, error={}", topic, e.getMessage(), e);
            // 不抛出异常，避免影响其他消息的处理
        }
    }
    
    /**
     * 消息发送完成时调用
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        try {
            String[] topics = token.getTopics();
            if (topics != null && topics.length > 0) {
                logger.debug("MQTT 消息发送完成: topics={}", String.join(", ", topics));
            }
        } catch (Exception e) {
            logger.debug("MQTT 消息发送完成");
        }
    }
    
    /**
     * 查找主题处理器（支持通配符匹配）
     */
    private MqttMessageHandler findTopicHandler(String topic) {
        // 精确匹配
        if (topicHandlers.containsKey(topic)) {
            return topicHandlers.get(topic);
        }
        
        // 通配符匹配
        for (Map.Entry<String, MqttMessageHandler> entry : topicHandlers.entrySet()) {
            if (topicMatches(entry.getKey(), topic)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 检查主题是否匹配（支持 MQTT 通配符 + 和 #）
     */
    private boolean topicMatches(String pattern, String topic) {
        // 将 MQTT 通配符转换为正则表达式
        String regex = pattern
            .replace("+", "[^/]+")  // + 匹配单层
            .replace("#", ".*");     // # 匹配多层
        
        return topic.matches(regex);
    }
    
    /**
     * 解析 MQTT 消息为外部请求
     */
    private ExternalRequest parseMessage(String topic, String payload, MqttMessage message) {
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("MQTT");
        
        try {
            // 尝试解析 JSON 格式的消息
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            
            // 提取服务和方法信息
            String service = (String) messageData.get("service");
            String method = (String) messageData.get("method");
            
            if (service == null || method == null) {
                // 如果消息中没有服务和方法，从主题中提取
                // 主题格式: framework/{service}/{method}
                String[] topicParts = topic.split("/");
                if (topicParts.length >= 3) {
                    service = topicParts[1];
                    method = topicParts[2];
                } else {
                    service = "default";
                    method = "process";
                }
            }
            
            request.setService(service);
            request.setMethod(method);
            
            // 提取请求体
            Object body = messageData.get("body");
            if (body != null) {
                request.setBody(body);
            } else {
                request.setBody(messageData);
            }
            
            // 提取响应主题
            String responseTopic = (String) messageData.get("responseTopic");
            if (responseTopic != null) {
                request.getMetadata().put("responseTopic", responseTopic);
            }
            
        } catch (Exception e) {
            // 如果不是 JSON 格式，将整个 payload 作为 body
            logger.debug("消息不是 JSON 格式，作为纯文本处理: topic={}", topic);
            
            // 从主题中提取服务和方法
            String[] topicParts = topic.split("/");
            if (topicParts.length >= 3) {
                request.setService(topicParts[1]);
                request.setMethod(topicParts[2]);
            } else {
                request.setService("default");
                request.setMethod("process");
            }
            
            request.setBody(payload);
        }
        
        // 添加元数据
        request.getMetadata().put("topic", topic);
        request.getMetadata().put("qos", message.getQos());
        request.getMetadata().put("retained", message.isRetained());
        request.getMetadata().put("timestamp", System.currentTimeMillis());
        
        return request;
    }
    
    /**
     * 关闭 MQTT 客户端
     */
    @PreDestroy
    public void shutdown() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                logger.info("关闭 MQTT 客户端");
                mqttClient.disconnect();
                mqttClient.close();
                logger.info("MQTT 客户端已关闭");
            } catch (MqttException e) {
                logger.error("关闭 MQTT 客户端失败: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 检查客户端是否已连接
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
    
    /**
     * 获取客户端 ID
     */
    public String getClientId() {
        return mqttClient != null ? mqttClient.getClientId() : null;
    }
    
    /**
     * MQTT 消息处理器接口
     */
    @FunctionalInterface
    public interface MqttMessageHandler {
        void handleMessage(String topic, String payload, int qos, boolean retained);
    }
}
