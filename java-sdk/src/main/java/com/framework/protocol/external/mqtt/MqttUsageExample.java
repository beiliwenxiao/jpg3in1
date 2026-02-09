package com.framework.protocol.external.mqtt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT 协议处理器使用示例
 * 
 * 此类展示了如何使用 MQTT 协议处理器的各种功能
 * 
 * 注意：此类仅作为示例，实际使用时请根据业务需求调整
 */
@Component
public class MqttUsageExample {
    
    @Autowired(required = false)
    private MqttProtocolHandler mqttHandler;
    
    /**
     * 示例 1: 发布简单消息
     */
    public void example1_publishSimpleMessage() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            System.out.println("MQTT 处理器未启用或未连接");
            return;
        }
        
        // 发布 JSON 格式消息
        String topic = "framework/user/create";
        String payload = "{\"service\":\"user\",\"method\":\"create\",\"body\":{\"name\":\"张三\",\"age\":25}}";
        
        mqttHandler.publish(topic, payload);
        System.out.println("消息已发布到主题: " + topic);
    }
    
    /**
     * 示例 2: 发布消息并指定 QoS 和保留标志
     */
    public void example2_publishWithQos() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        String topic = "system/config/update";
        String payload = "{\"key\":\"max_connections\",\"value\":1000}";
        
        // QoS 2 = 恰好一次，retained = true（保留消息）
        mqttHandler.publish(topic, payload, 2, true);
        System.out.println("保留消息已发布，QoS=2");
    }
    
    /**
     * 示例 3: 订阅主题
     */
    public void example3_subscribeTopic() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        // 订阅单个主题
        mqttHandler.subscribe("framework/order/+", 1);
        System.out.println("已订阅主题: framework/order/+");
        
        // 订阅多层通配符主题
        mqttHandler.subscribe("system/events/#", 1);
        System.out.println("已订阅主题: system/events/#");
    }
    
    /**
     * 示例 4: 注册自定义主题处理器
     */
    public void example4_registerCustomHandler() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        // 注册用户事件处理器
        mqttHandler.registerTopicHandler("user/events/+", (topic, payload, qos, retained) -> {
            System.out.println("收到用户事件:");
            System.out.println("  主题: " + topic);
            System.out.println("  内容: " + payload);
            System.out.println("  QoS: " + qos);
            System.out.println("  保留: " + retained);
            
            // 处理用户事件的业务逻辑
            if (topic.endsWith("/created")) {
                handleUserCreated(payload);
            } else if (topic.endsWith("/updated")) {
                handleUserUpdated(payload);
            }
        });
        
        System.out.println("已注册用户事件处理器");
    }
    
    /**
     * 示例 5: 使用 Lambda 表达式注册处理器
     */
    public void example5_registerHandlerWithLambda() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        // 简洁的 Lambda 表达式
        mqttHandler.registerTopicHandler("system/alerts/#", 
            (topic, payload, qos, retained) -> 
                System.out.println("系统告警: " + payload)
        );
        
        // 多行 Lambda 表达式
        mqttHandler.registerTopicHandler("order/status/+", (topic, payload, qos, retained) -> {
            String orderId = topic.substring(topic.lastIndexOf('/') + 1);
            System.out.println("订单状态更新: orderId=" + orderId + ", status=" + payload);
            // 更新订单状态的业务逻辑
        });
    }
    
    /**
     * 示例 6: 请求-响应模式
     */
    public void example6_requestResponsePattern() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        // 生成唯一的响应主题
        String responseTopic = "framework/user/query/response/" + UUID.randomUUID();
        
        // 订阅响应主题
        mqttHandler.subscribe(responseTopic, 1);
        
        // 注册响应处理器
        mqttHandler.registerTopicHandler(responseTopic, (topic, payload, qos, retained) -> {
            System.out.println("收到查询响应: " + payload);
            
            // 处理响应后清理
            mqttHandler.unsubscribe(responseTopic);
            mqttHandler.removeTopicHandler(responseTopic);
        });
        
        // 发送查询请求
        String requestTopic = "framework/user/query";
        String request = String.format(
            "{\"service\":\"user\",\"method\":\"query\",\"body\":{\"id\":123},\"responseTopic\":\"%s\"}",
            responseTopic
        );
        mqttHandler.publish(requestTopic, request);
        
        System.out.println("查询请求已发送，等待响应...");
    }
    
    /**
     * 示例 7: 发布结构化消息
     */
    public void example7_publishStructuredMessage() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        // 构建结构化消息
        Map<String, Object> message = new HashMap<>();
        message.put("service", "order");
        message.put("method", "create");
        
        Map<String, Object> body = new HashMap<>();
        body.put("userId", 123);
        body.put("productId", 456);
        body.put("quantity", 2);
        body.put("totalPrice", 199.99);
        message.put("body", body);
        
        // 转换为 JSON 并发布
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(message);
            
            mqttHandler.publish("framework/order/create", json);
            System.out.println("订单创建消息已发布");
        } catch (Exception e) {
            System.err.println("发布消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 示例 8: 批量订阅主题
     */
    public void example8_subscribeMultipleTopics() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        String[] topics = {
            "framework/user/+",
            "framework/order/+",
            "framework/product/+",
            "system/events/#"
        };
        
        for (String topic : topics) {
            mqttHandler.subscribe(topic, 1);
            System.out.println("已订阅: " + topic);
        }
    }
    
    /**
     * 示例 9: 取消订阅和移除处理器
     */
    public void example9_unsubscribeAndRemoveHandler() {
        if (mqttHandler == null || !mqttHandler.isConnected()) {
            return;
        }
        
        String topic = "temp/topic";
        
        // 取消订阅
        mqttHandler.unsubscribe(topic);
        System.out.println("已取消订阅: " + topic);
        
        // 移除处理器
        mqttHandler.removeTopicHandler(topic);
        System.out.println("已移除处理器: " + topic);
    }
    
    /**
     * 示例 10: 检查连接状态
     */
    public void example10_checkConnectionStatus() {
        if (mqttHandler == null) {
            System.out.println("MQTT 处理器未启用");
            return;
        }
        
        boolean connected = mqttHandler.isConnected();
        String clientId = mqttHandler.getClientId();
        
        System.out.println("MQTT 连接状态: " + (connected ? "已连接" : "未连接"));
        System.out.println("客户端 ID: " + clientId);
    }
    
    // ========== 辅助方法 ==========
    
    private void handleUserCreated(String payload) {
        System.out.println("处理用户创建事件: " + payload);
        // 实现用户创建的业务逻辑
    }
    
    private void handleUserUpdated(String payload) {
        System.out.println("处理用户更新事件: " + payload);
        // 实现用户更新的业务逻辑
    }
    
    /**
     * 初始化时运行示例（可选）
     */
    @PostConstruct
    public void init() {
        // 取消注释以运行示例
        // example1_publishSimpleMessage();
        // example3_subscribeTopic();
        // example4_registerCustomHandler();
    }
}
