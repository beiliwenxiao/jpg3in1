package com.framework.protocol.internal.custom;

import com.framework.model.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 自定义二进制协议使用示例
 * 
 * 演示如何使用自定义协议进行服务间通信
 * 
 * **验证需求: 3.3**
 */
public class CustomProtocolUsageExample {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolUsageExample.class);
    
    public static void main(String[] args) {
        // 示例 1: 启动服务端并注册服务
        serverExample();
        
        // 等待服务端启动
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 示例 2: 客户端调用服务
        clientExample();
        
        // 示例 3: 异步调用
        asyncExample();
        
        // 示例 4: 健康检查
        healthCheckExample();
    }
    
    /**
     * 示例 1: 服务端示例
     */
    private static void serverExample() {
        logger.info("=== 服务端示例 ===");
        
        try {
            // 创建协议处理器
            CustomProtocolHandler handler = new CustomProtocolHandler();
            
            // 启动服务端（监听 9090 端口）
            handler.start(9090);
            
            // 注册服务处理器
            handler.registerService("UserService", new CustomProtocolServer.ServiceHandler() {
                @Override
                public byte[] handle(String method, byte[] data, Map<String, String> metadata) {
                    logger.info("处理请求: method={}, dataLength={}", method, data.length);
                    
                    // 根据方法名处理不同的业务逻辑
                    return switch (method) {
                        case "getUser" -> handleGetUser(data);
                        case "createUser" -> handleCreateUser(data);
                        case "updateUser" -> handleUpdateUser(data);
                        case "deleteUser" -> handleDeleteUser(data);
                        default -> {
                            logger.warn("未知方法: {}", method);
                            yield "未知方法".getBytes();
                        }
                    };
                }
                
                private byte[] handleGetUser(byte[] data) {
                    String userId = new String(data);
                    logger.info("获取用户: userId={}", userId);
                    
                    // 模拟返回用户信息
                    String response = "{\"id\":\"" + userId + "\",\"name\":\"张三\",\"age\":30}";
                    return response.getBytes();
                }
                
                private byte[] handleCreateUser(byte[] data) {
                    String userData = new String(data);
                    logger.info("创建用户: userData={}", userData);
                    
                    // 模拟创建用户
                    String response = "{\"success\":true,\"userId\":\"12345\"}";
                    return response.getBytes();
                }
                
                private byte[] handleUpdateUser(byte[] data) {
                    String userData = new String(data);
                    logger.info("更新用户: userData={}", userData);
                    
                    // 模拟更新用户
                    String response = "{\"success\":true}";
                    return response.getBytes();
                }
                
                private byte[] handleDeleteUser(byte[] data) {
                    String userId = new String(data);
                    logger.info("删除用户: userId={}", userId);
                    
                    // 模拟删除用户
                    String response = "{\"success\":true}";
                    return response.getBytes();
                }
            });
            
            logger.info("服务端启动成功，已注册 UserService");
            
        } catch (Exception e) {
            logger.error("服务端示例失败", e);
        }
    }
    
    /**
     * 示例 2: 客户端同步调用示例
     */
    private static void clientExample() {
        logger.info("=== 客户端同步调用示例 ===");
        
        try {
            // 创建协议处理器（仅客户端模式）
            CustomProtocolHandler handler = new CustomProtocolHandler();
            handler.start(0); // 端口为 0 表示不启动服务端
            
            // 创建服务信息
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setName("UserService");
            serviceInfo.setAddress("localhost");
            serviceInfo.setPort(9090);
            
            // 同步调用 - 获取用户
            logger.info("调用 getUser 方法");
            String response = handler.call(
                    serviceInfo,
                    "getUser",
                    "user123",
                    String.class
            );
            logger.info("响应: {}", response);
            
            // 同步调用 - 创建用户
            logger.info("调用 createUser 方法");
            String createRequest = "{\"name\":\"李四\",\"age\":25}";
            String createResponse = handler.call(
                    serviceInfo,
                    "createUser",
                    createRequest,
                    String.class
            );
            logger.info("响应: {}", createResponse);
            
        } catch (Exception e) {
            logger.error("客户端示例失败", e);
        }
    }
    
    /**
     * 示例 3: 异步调用示例
     */
    private static void asyncExample() {
        logger.info("=== 异步调用示例 ===");
        
        try {
            // 创建协议处理器
            CustomProtocolHandler handler = new CustomProtocolHandler();
            handler.start(0);
            
            // 创建服务信息
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setName("UserService");
            serviceInfo.setAddress("localhost");
            serviceInfo.setPort(9090);
            
            // 异步调用
            logger.info("发起异步调用");
            CompletableFuture<String> future = handler.callAsync(
                    serviceInfo,
                    "getUser",
                    "user456",
                    String.class
            );
            
            // 处理响应
            future.thenAccept(response -> {
                logger.info("异步响应: {}", response);
            }).exceptionally(throwable -> {
                logger.error("异步调用失败", throwable);
                return null;
            });
            
            // 等待异步调用完成
            future.join();
            
        } catch (Exception e) {
            logger.error("异步调用示例失败", e);
        }
    }
    
    /**
     * 示例 4: 健康检查示例
     */
    private static void healthCheckExample() {
        logger.info("=== 健康检查示例 ===");
        
        try {
            // 创建协议处理器
            CustomProtocolHandler handler = new CustomProtocolHandler();
            handler.start(0);
            
            // 创建服务信息
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setName("UserService");
            serviceInfo.setAddress("localhost");
            serviceInfo.setPort(9090);
            
            // 执行健康检查
            boolean healthy = handler.healthCheck(serviceInfo);
            logger.info("服务健康状态: {}", healthy ? "健康" : "不健康");
            
        } catch (Exception e) {
            logger.error("健康检查示例失败", e);
        }
    }
    
    /**
     * 示例 5: 使用配置
     */
    private static void configExample() {
        logger.info("=== 配置示例 ===");
        
        // 创建自定义配置
        CustomProtocolConfig config = new CustomProtocolConfig();
        config.setServerPort(9091);
        config.setServerEnabled(true);
        config.setConnectTimeout(10000);
        config.setRequestTimeout(60000);
        config.setMaxConnections(200);
        config.setCompressionEnabled(true);
        
        logger.info("配置: {}", config);
        
        // 注意：当前实现尚未完全集成配置类
        // 后续可以扩展 CustomProtocolHandler 以支持配置
    }
    
    /**
     * 示例 6: 错误处理
     */
    private static void errorHandlingExample() {
        logger.info("=== 错误处理示例 ===");
        
        try {
            CustomProtocolHandler handler = new CustomProtocolHandler();
            handler.start(0);
            
            // 创建不存在的服务信息
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setName("NonExistentService");
            serviceInfo.setAddress("localhost");
            serviceInfo.setPort(9090);
            
            // 尝试调用不存在的服务
            try {
                handler.call(serviceInfo, "someMethod", "data", String.class);
            } catch (Exception e) {
                logger.error("预期的错误: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("错误处理示例失败", e);
        }
    }
}
