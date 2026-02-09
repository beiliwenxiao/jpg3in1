package com.framework.protocol.internal.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON-RPC 内部协议处理器
 * 
 * 管理 JSON-RPC 客户端连接池和服务端
 * 使用 jsonrpc4j 库实现服务间的 JSON-RPC 通信
 * 
 * **验证需求: 3.2, 3.5**
 */
public class JsonRpcInternalProtocolHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcInternalProtocolHandler.class);
    
    private final ObjectMapper objectMapper;
    private final Map<String, JsonRpcInternalClient> clientPool;
    private JsonRpcInternalServer server;
    private volatile boolean started;
    
    public JsonRpcInternalProtocolHandler() {
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
            logger.warn("JSON-RPC 内部协议处理器已经启动");
            return;
        }
        
        logger.info("启动 JSON-RPC 内部协议处理器");
        
        try {
            // 启动服务端（如果需要）
            if (port > 0) {
                server = new JsonRpcInternalServer(port);
                server.start();
            }
            
            started = true;
            logger.info("JSON-RPC 内部协议处理器启动成功");
            
        } catch (Exception e) {
            logger.error("JSON-RPC 内部协议处理器启动失败", e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "JSON-RPC 内部协议处理器启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 关闭协议处理器
     */
    public void shutdown() {
        if (!started) {
            logger.warn("JSON-RPC 内部协议处理器未启动");
            return;
        }
        
        logger.info("关闭 JSON-RPC 内部协议处理器");
        
        try {
            // 关闭所有客户端
            for (JsonRpcInternalClient client : clientPool.values()) {
                try {
                    client.shutdown();
                } catch (Exception e) {
                    logger.error("关闭 JSON-RPC 客户端失败", e);
                }
            }
            clientPool.clear();
            
            // 关闭服务端
            if (server != null) {
                server.shutdown();
            }
            
            started = false;
            logger.info("JSON-RPC 内部协议处理器已关闭");
            
        } catch (Exception e) {
            logger.error("关闭 JSON-RPC 内部协议处理器失败", e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "关闭 JSON-RPC 内部协议处理器失败: " + e.getMessage(), e);
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
            JsonRpcInternalClient client = getOrCreateClient(serviceInfo);
            
            // 序列化请求
            byte[] payload = serializeRequest(request);
            
            // 调用服务
            Map<String, String> headers = new ConcurrentHashMap<>();
            byte[] responsePayload = client.call(
                    serviceInfo.getName(),
                    method,
                    payload,
                    headers,
                    30000 // 默认超时 30 秒
            );
            
            // 反序列化响应
            return deserializeResponse(responsePayload, responseType);
            
        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            logger.error("JSON-RPC 调用失败: service={}, method={}", 
                        serviceInfo.getName(), method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "JSON-RPC 调用失败: " + e.getMessage(), e);
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
            JsonRpcInternalClient client = getOrCreateClient(serviceInfo);
            
            // 序列化请求
            byte[] payload = serializeRequest(request);
            
            // 异步调用服务
            Map<String, String> headers = new ConcurrentHashMap<>();
            return client.callAsync(
                    serviceInfo.getName(),
                    method,
                    payload,
                    headers,
                    30000 // 默认超时 30 秒
            ).thenApply(responsePayload -> {
                try {
                    return deserializeResponse(responsePayload, responseType);
                } catch (Exception e) {
                    throw new FrameworkException(ErrorCode.SERIALIZATION_ERROR, 
                                                "响应反序列化失败: " + e.getMessage(), e);
                }
            });
            
        } catch (Exception e) {
            logger.error("JSON-RPC 异步调用失败: service={}, method={}", 
                        serviceInfo.getName(), method, e);
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new FrameworkException(
                    ErrorCode.INTERNAL_ERROR,
                    "JSON-RPC 异步调用失败: " + e.getMessage(),
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
    public void registerService(String serviceName, JsonRpcInternalServer.ServiceHandler handler) {
        if (server == null) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "JSON-RPC 服务端未启动，无法注册服务");
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
     * 获取或创建客户端
     */
    private JsonRpcInternalClient getOrCreateClient(ServiceInfo serviceInfo) {
        String key = serviceInfo.getAddress() + ":" + serviceInfo.getPort();
        
        return clientPool.computeIfAbsent(key, k -> {
            logger.info("创建 JSON-RPC 内部客户端: {}", key);
            JsonRpcInternalClient client = new JsonRpcInternalClient(
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
                                        "JSON-RPC 内部协议处理器未启动");
        }
    }
    
    /**
     * 获取服务端
     */
    public JsonRpcInternalServer getServer() {
        return server;
    }
}
