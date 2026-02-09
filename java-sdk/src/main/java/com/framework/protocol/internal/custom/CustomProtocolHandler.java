package com.framework.protocol.internal.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import com.framework.proto.CustomProtocolProto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义二进制协议处理器
 * 
 * 管理自定义协议客户端连接池和服务端
 * 提供统一的协议处理接口
 * 
 * **验证需求: 3.3**
 */
public class CustomProtocolHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolHandler.class);
    
    private final ObjectMapper objectMapper;
    private final Map<String, CustomProtocolClient> clientPool;
    private CustomProtocolServer server;
    private volatile boolean started;
    
    public CustomProtocolHandler() {
        this.objectMapper = new ObjectMapper();
        this.clientPool = new ConcurrentHashMap<>();
        this.started = false;
    }
    
    /**
     * 启动协议处理器
     * 
     * @param port 服务端监听端口（如果为 0 则不启动服务端）
     */
    public void start(int port) {
        if (started) {
            logger.warn("自定义协议处理器已经启动");
            return;
        }
        
        logger.info("启动自定义协议处理器");
        
        try {
            // 启动服务端（如果需要）
            if (port > 0) {
                server = new CustomProtocolServer(port);
                server.start();
            }
            
            started = true;
            logger.info("自定义协议处理器启动成功");
            
        } catch (Exception e) {
            logger.error("自定义协议处理器启动失败", e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "自定义协议处理器启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 关闭协议处理器
     */
    public void shutdown() {
        if (!started) {
            logger.warn("自定义协议处理器未启动");
            return;
        }
        
        logger.info("关闭自定义协议处理器");
        
        try {
            // 关闭所有客户端
            for (CustomProtocolClient client : clientPool.values()) {
                try {
                    client.shutdown();
                } catch (Exception e) {
                    logger.error("关闭自定义协议客户端失败", e);
                }
            }
            clientPool.clear();
            
            // 关闭服务端
            if (server != null) {
                server.shutdown();
            }
            
            started = false;
            logger.info("自定义协议处理器已关闭");
            
        } catch (Exception e) {
            logger.error("关闭自定义协议处理器失败", e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "关闭自定义协议处理器失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 调用远程服务（同步）
     * 
     * @param serviceInfo 服务信息
     * @param method 方法名称
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应对象
     */
    public <T> T call(ServiceInfo serviceInfo, String method, Object request, Class<T> responseType) {
        validateState();
        
        try {
            // 获取或创建客户端
            CustomProtocolClient client = getOrCreateClient(serviceInfo);
            
            // 序列化请求
            byte[] payload = serializeRequest(request);
            
            // 调用服务
            Map<String, String> metadata = new HashMap<>();
            CustomFrame responseFrame = client.call(
                    serviceInfo.getName(),
                    method,
                    payload,
                    metadata,
                    30000 // 默认超时 30 秒
            );
            
            // 解析响应
            DataFrame responseData = DataFrame.parseFrom(responseFrame.getBody());
            byte[] responsePayload = responseData.getData().toByteArray();
            
            // 反序列化响应
            return deserializeResponse(responsePayload, responseType);
            
        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            logger.error("自定义协议调用失败: service={}, method={}", 
                        serviceInfo.getName(), method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "自定义协议调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 调用远程服务（异步）
     * 
     * @param serviceInfo 服务信息
     * @param method 方法名称
     * @param request 请求对象
     * @param responseType 响应类型
     * @return 响应对象的 CompletableFuture
     */
    public <T> CompletableFuture<T> callAsync(ServiceInfo serviceInfo, String method, 
                                              Object request, Class<T> responseType) {
        validateState();
        
        try {
            // 获取或创建客户端
            CustomProtocolClient client = getOrCreateClient(serviceInfo);
            
            // 序列化请求
            byte[] payload = serializeRequest(request);
            
            // 异步调用服务
            Map<String, String> metadata = new HashMap<>();
            return client.callAsync(
                    serviceInfo.getName(),
                    method,
                    payload,
                    metadata,
                    30000 // 默认超时 30 秒
            ).thenApply(responseFrame -> {
                try {
                    // 解析响应
                    DataFrame responseData = DataFrame.parseFrom(responseFrame.getBody());
                    byte[] responsePayload = responseData.getData().toByteArray();
                    
                    // 反序列化响应
                    return deserializeResponse(responsePayload, responseType);
                } catch (Exception e) {
                    throw new FrameworkException(ErrorCode.SERIALIZATION_ERROR, 
                                                "响应反序列化失败: " + e.getMessage(), e);
                }
            });
            
        } catch (Exception e) {
            logger.error("自定义协议异步调用失败: service={}, method={}", 
                        serviceInfo.getName(), method, e);
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new FrameworkException(
                    ErrorCode.INTERNAL_ERROR,
                    "自定义协议异步调用失败: " + e.getMessage(),
                    e
            ));
            return future;
        }
    }
    
    /**
     * 注册服务处理器
     * 
     * @param serviceName 服务名称
     * @param handler 服务处理器
     */
    public void registerService(String serviceName, CustomProtocolServer.ServiceHandler handler) {
        if (server == null) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "自定义协议服务端未启动，无法注册服务");
        }
        
        server.registerService(serviceName, handler);
    }
    
    /**
     * 注销服务
     * 
     * @param serviceName 服务名称
     */
    public void unregisterService(String serviceName) {
        if (server != null) {
            server.unregisterService(serviceName);
        }
    }
    
    /**
     * 健康检查
     * 
     * @param serviceInfo 服务信息
     * @return 是否健康
     */
    public boolean healthCheck(ServiceInfo serviceInfo) {
        try {
            CustomProtocolClient client = getOrCreateClient(serviceInfo);
            CustomFrame pongFrame = client.ping().get();
            return pongFrame.getHeader().getType() == FrameType.PONG;
        } catch (Exception e) {
            logger.error("健康检查失败: service={}", serviceInfo.getName(), e);
            return false;
        }
    }
    
    /**
     * 获取或创建客户端
     */
    private CustomProtocolClient getOrCreateClient(ServiceInfo serviceInfo) {
        String key = serviceInfo.getAddress() + ":" + serviceInfo.getPort();
        
        return clientPool.computeIfAbsent(key, k -> {
            logger.info("创建自定义协议客户端: {}", key);
            CustomProtocolClient client = new CustomProtocolClient(
                    serviceInfo.getAddress(), 
                    serviceInfo.getPort()
            );
            client.start();
            return client;
        });
    }
    
    /**
     * 序列化请求
     */
    private byte[] serializeRequest(Object request) throws Exception {
        if (request == null) {
            return new byte[0];
        }
        
        if (request instanceof byte[]) {
            return (byte[]) request;
        }
        
        if (request instanceof String) {
            return ((String) request).getBytes();
        }
        
        // 使用 JSON 序列化
        return objectMapper.writeValueAsBytes(request);
    }
    
    /**
     * 反序列化响应
     */
    private <T> T deserializeResponse(byte[] payload, Class<T> responseType) throws Exception {
        if (payload == null || payload.length == 0) {
            return null;
        }
        
        if (responseType == byte[].class) {
            return responseType.cast(payload);
        }
        
        if (responseType == String.class) {
            return responseType.cast(new String(payload));
        }
        
        // 使用 JSON 反序列化
        return objectMapper.readValue(payload, responseType);
    }
    
    /**
     * 验证状态
     */
    private void validateState() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "自定义协议处理器未启动");
        }
    }
    
    /**
     * 获取服务端
     */
    public CustomProtocolServer getServer() {
        return server;
    }
}
