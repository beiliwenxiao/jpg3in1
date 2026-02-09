package com.framework.protocol.internal.jsonrpc;

import com.framework.model.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JSON-RPC 内部协议使用示例
 * 
 * 演示如何使用 JSON-RPC 内部协议进行服务间通信
 */
public class JsonRpcInternalUsageExample {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcInternalUsageExample.class);
    
    public static void main(String[] args) throws Exception {
        // 示例 1: 启动服务端并注册服务
        exampleServerSetup();
        
        // 示例 2: 客户端同步调用
        exampleSyncCall();
        
        // 示例 3: 客户端异步调用
        exampleAsyncCall();
    }
    
    /**
     * 示例 1: 启动服务端并注册服务
     */
    public static void exampleServerSetup() throws Exception {
        logger.info("=== 示例 1: 启动服务端并注册服务 ===");
        
        // 创建协议处理器
        JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();
        
        // 启动服务端，监听 9091 端口
        handler.start(9091);
        
        // 注册用户服务
        handler.registerService("UserService", new JsonRpcInternalServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.info("处理用户服务请求: method={}", method);
                
                try {
                    if ("getUser".equals(method)) {
                        // 模拟获取用户信息
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", 1);
                        user.put("name", "张三");
                        user.put("email", "zhangsan@example.com");
                        
                        // 序列化响应
                        return new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsBytes(user);
                    } else if ("createUser".equals(method)) {
                        // 模拟创建用户
                        Map<String, Object> result = new HashMap<>();
                        result.put("success", true);
                        result.put("userId", 123);
                        
                        return new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsBytes(result);
                    }
                    
                    throw new RuntimeException("未知方法: " + method);
                    
                } catch (Exception e) {
                    logger.error("处理请求失败", e);
                    throw new RuntimeException(e);
                }
            }
        });
        
        logger.info("服务端启动成功，已注册 UserService");
        
        // 注意：实际使用中不要立即关闭
        // handler.shutdown();
    }
    
    /**
     * 示例 2: 客户端同步调用
     */
    public static void exampleSyncCall() throws Exception {
        logger.info("=== 示例 2: 客户端同步调用 ===");
        
        // 创建协议处理器（仅客户端模式）
        JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();
        handler.start(0); // 端口为 0 表示不启动服务端
        
        // 构建服务信息
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName("UserService");
        serviceInfo.setAddress("localhost");
        serviceInfo.setPort(9091);
        serviceInfo.setLanguage("java");
        
        try {
            // 同步调用 getUser 方法
            Map<String, Object> request = new HashMap<>();
            request.put("userId", 1);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = handler.call(
                    serviceInfo,
                    "getUser",
                    request,
                    Map.class
            );
            
            logger.info("获取用户信息成功: {}", response);
            
        } catch (Exception e) {
            logger.error("调用失败", e);
        } finally {
            handler.shutdown();
        }
    }
    
    /**
     * 示例 3: 客户端异步调用
     */
    public static void exampleAsyncCall() throws Exception {
        logger.info("=== 示例 3: 客户端异步调用 ===");
        
        // 创建协议处理器（仅客户端模式）
        JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();
        handler.start(0);
        
        // 构建服务信息
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName("UserService");
        serviceInfo.setAddress("localhost");
        serviceInfo.setPort(9091);
        serviceInfo.setLanguage("java");
        
        try {
            // 异步调用 createUser 方法
            Map<String, Object> request = new HashMap<>();
            request.put("name", "李四");
            request.put("email", "lisi@example.com");
            
            CompletableFuture<Map> future = handler.callAsync(
                    serviceInfo,
                    "createUser",
                    request,
                    Map.class
            );
            
            // 处理异步响应
            future.thenAccept(response -> {
                logger.info("创建用户成功: {}", response);
            }).exceptionally(e -> {
                logger.error("创建用户失败", e);
                return null;
            });
            
            // 等待异步调用完成
            future.join();
            
        } catch (Exception e) {
            logger.error("调用失败", e);
        } finally {
            handler.shutdown();
        }
    }
    
    /**
     * 示例 4: 完整的服务间通信场景
     */
    public static void exampleFullScenario() throws Exception {
        logger.info("=== 示例 4: 完整的服务间通信场景 ===");
        
        // 服务端 A：订单服务
        JsonRpcInternalProtocolHandler orderService = new JsonRpcInternalProtocolHandler();
        orderService.start(9091);
        
        orderService.registerService("OrderService", new JsonRpcInternalServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.info("订单服务处理请求: method={}", method);
                
                try {
                    if ("createOrder".equals(method)) {
                        Map<String, Object> order = new HashMap<>();
                        order.put("orderId", "ORD-001");
                        order.put("status", "created");
                        order.put("timestamp", System.currentTimeMillis());
                        
                        return new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsBytes(order);
                    }
                    
                    throw new RuntimeException("未知方法: " + method);
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        
        // 服务端 B：支付服务
        JsonRpcInternalProtocolHandler paymentService = new JsonRpcInternalProtocolHandler();
        paymentService.start(9092);
        
        paymentService.registerService("PaymentService", new JsonRpcInternalServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.info("支付服务处理请求: method={}", method);
                
                try {
                    if ("processPayment".equals(method)) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("success", true);
                        result.put("transactionId", "TXN-001");
                        
                        return new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsBytes(result);
                    }
                    
                    throw new RuntimeException("未知方法: " + method);
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        
        // 客户端：调用订单服务和支付服务
        JsonRpcInternalProtocolHandler client = new JsonRpcInternalProtocolHandler();
        client.start(0);
        
        try {
            // 1. 创建订单
            ServiceInfo orderServiceInfo = new ServiceInfo();
            orderServiceInfo.setName("OrderService");
            orderServiceInfo.setAddress("localhost");
            orderServiceInfo.setPort(9091);
            
            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("userId", 1);
            orderRequest.put("productId", 100);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> orderResponse = client.call(
                    orderServiceInfo,
                    "createOrder",
                    orderRequest,
                    Map.class
            );
            
            logger.info("订单创建成功: {}", orderResponse);
            
            // 2. 处理支付
            ServiceInfo paymentServiceInfo = new ServiceInfo();
            paymentServiceInfo.setName("PaymentService");
            paymentServiceInfo.setAddress("localhost");
            paymentServiceInfo.setPort(9092);
            
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("orderId", orderResponse.get("orderId"));
            paymentRequest.put("amount", 99.99);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentResponse = client.call(
                    paymentServiceInfo,
                    "processPayment",
                    paymentRequest,
                    Map.class
            );
            
            logger.info("支付处理成功: {}", paymentResponse);
            
        } finally {
            client.shutdown();
            orderService.shutdown();
            paymentService.shutdown();
        }
    }
}
