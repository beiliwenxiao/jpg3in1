package com.framework.protocol.internal.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JSON-RPC 内部协议客户端实现
 * 
 * 使用 jsonrpc4j 库实现服务间的 JSON-RPC 调用
 * 
 * **验证需求: 3.2, 3.5**
 */
public class JsonRpcInternalClient {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcInternalClient.class);
    
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private JsonRpcHttpClient jsonRpcClient;
    private volatile boolean started;
    
    public JsonRpcInternalClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.started = false;
    }
    
    /**
     * 启动 JSON-RPC 客户端
     */
    public void start() {
        if (started) {
            logger.warn("JSON-RPC 内部客户端已经启动");
            return;
        }
        
        logger.info("启动 JSON-RPC 内部客户端: {}:{}", host, port);
        
        try {
            // 创建 JSON-RPC HTTP 客户端
            URL serviceUrl = new URL(String.format("http://%s:%d/jsonrpc", host, port));
            
            // 配置请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");
            
            jsonRpcClient = new JsonRpcHttpClient(objectMapper, serviceUrl, headers);
            
            started = true;
            logger.info("JSON-RPC 内部客户端启动成功");
            
        } catch (Exception e) {
            logger.error("JSON-RPC 内部客户端启动失败", e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                                        "JSON-RPC 内部客户端启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 关闭 JSON-RPC 客户端
     */
    public void shutdown() {
        if (!started) {
            logger.warn("JSON-RPC 内部客户端未启动");
            return;
        }
        
        logger.info("关闭 JSON-RPC 内部客户端");
        
        try {
            // jsonrpc4j 的 HTTP 客户端不需要显式关闭
            jsonRpcClient = null;
            
            started = false;
            logger.info("JSON-RPC 内部客户端已关闭");
            
        } catch (Exception e) {
            logger.error("关闭 JSON-RPC 内部客户端失败", e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "关闭 JSON-RPC 内部客户端失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 同步调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param payload 请求负载
     * @param headers 请求头
     * @param timeout 超时时间（毫秒）
     * @return 响应负载
     */
    public byte[] call(String service, String method, byte[] payload, 
                      Map<String, String> headers, long timeout) {
        validateState();
        
        logger.debug("JSON-RPC 内部同步调用: service={}, method={}", service, method);
        
        try {
            // 构建 JSON-RPC 方法名：service.method
            String rpcMethod = service + "." + method;
            
            // 反序列化请求负载为对象
            Object requestObject = deserializePayload(payload);
            
            // 调用 JSON-RPC 服务
            Object result = jsonRpcClient.invoke(rpcMethod, requestObject, Object.class);
            
            // 序列化响应
            return serializeResult(result);
            
        } catch (com.googlecode.jsonrpc4j.JsonRpcClientException e) {
            logger.error("JSON-RPC 调用失败: service={}, method={}, code={}, message={}", 
                        service, method, e.getCode(), e.getMessage());
            throw new FrameworkException(
                    mapJsonRpcErrorCode(e.getCode()),
                    e.getMessage()
            );
        } catch (Throwable e) {
            logger.error("JSON-RPC 调用异常: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "JSON-RPC 调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 异步调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param payload 请求负载
     * @param headers 请求头
     * @param timeout 超时时间（毫秒）
     * @return 响应负载的 CompletableFuture
     */
    public CompletableFuture<byte[]> callAsync(String service, String method, byte[] payload,
                                               Map<String, String> headers, long timeout) {
        validateState();
        
        logger.debug("JSON-RPC 内部异步调用: service={}, method={}", service, method);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(service, method, payload, headers, timeout);
            } catch (Exception e) {
                throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                            "JSON-RPC 异步调用失败: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 健康检查
     * 
     * @return 健康状态
     */
    public boolean healthCheck() {
        validateState();
        
        try {
            // 调用健康检查方法
            Object result = jsonRpcClient.invoke("health.check", null, Object.class);
            return result != null;
            
        } catch (Throwable e) {
            logger.error("健康检查失败", e);
            return false;
        }
    }
    
    /**
     * 验证客户端状态
     */
    private void validateState() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "JSON-RPC 内部客户端未启动");
        }
    }
    
    /**
     * 反序列化请求负载
     */
    private Object deserializePayload(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) {
            return null;
        }
        
        // 尝试解析为 JSON 对象
        return objectMapper.readValue(payload, Object.class);
    }
    
    /**
     * 序列化响应结果
     */
    private byte[] serializeResult(Object result) throws Exception {
        if (result == null) {
            return new byte[0];
        }
        
        return objectMapper.writeValueAsBytes(result);
    }
    
    /**
     * 映射 JSON-RPC 错误码到框架错误码
     */
    private ErrorCode mapJsonRpcErrorCode(int jsonRpcCode) {
        return switch (jsonRpcCode) {
            case -32700 -> ErrorCode.SERIALIZATION_ERROR; // Parse error
            case -32600 -> ErrorCode.PROTOCOL_ERROR;      // Invalid Request
            case -32601 -> ErrorCode.NOT_FOUND;           // Method not found
            case -32602 -> ErrorCode.BAD_REQUEST;         // Invalid params
            case -32603 -> ErrorCode.INTERNAL_ERROR;      // Internal error
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
