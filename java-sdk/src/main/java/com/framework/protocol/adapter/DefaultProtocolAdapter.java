package com.framework.protocol.adapter;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 默认协议适配器实现
 * 
 * 支持 REST、WebSocket、JSON-RPC、MQTT 四种外部协议到内部协议的双向转换。
 * 保持消息语义一致性（请求/响应、发布/订阅等）。
 * 
 * 需求: 4.1, 4.2, 4.5
 */
public class DefaultProtocolAdapter implements ProtocolAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultProtocolAdapter.class);
    
    private static final List<String> SUPPORTED_PROTOCOLS = 
        List.of("REST", "WebSocket", "JSON-RPC", "MQTT");
    
    private final ObjectMapper objectMapper;
    
    public DefaultProtocolAdapter() {
        this.objectMapper = new ObjectMapper();
    }
    
    public DefaultProtocolAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public InternalRequest transformRequest(ExternalRequest externalRequest) {
        if (externalRequest == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "外部请求不能为空");
        }
        
        String protocol = externalRequest.getProtocol();
        if (protocol == null || protocol.isBlank()) {
            throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, "协议类型不能为空");
        }
        
        logger.debug("转换外部请求到内部请求: protocol={}, service={}, method={}", 
                    protocol, externalRequest.getService(), externalRequest.getMethod());
        
        return switch (protocol.toUpperCase()) {
            case "REST" -> transformRestRequest(externalRequest);
            case "WEBSOCKET" -> transformWebSocketRequest(externalRequest);
            case "JSON-RPC" -> transformJsonRpcRequest(externalRequest);
            case "MQTT" -> transformMqttRequest(externalRequest);
            default -> throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, 
                "不支持的协议类型: " + protocol);
        };
    }
    
    @Override
    public ExternalResponse transformResponse(InternalResponse internalResponse, ExternalRequest originalRequest) {
        if (internalResponse == null) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "内部响应不能为空");
        }
        
        String protocol = originalRequest != null ? originalRequest.getProtocol() : 
                          internalResponse.getSourceProtocol();
        
        if (protocol == null || protocol.isBlank()) {
            // 回退到通用转换
            return transformResponseGeneric(internalResponse);
        }
        
        logger.debug("转换内部响应到外部响应: protocol={}, success={}", protocol, internalResponse.isSuccess());
        
        return switch (protocol.toUpperCase()) {
            case "REST" -> transformRestResponse(internalResponse, originalRequest);
            case "WEBSOCKET" -> transformWebSocketResponse(internalResponse, originalRequest);
            case "JSON-RPC" -> transformJsonRpcResponse(internalResponse, originalRequest);
            case "MQTT" -> transformMqttResponse(internalResponse, originalRequest);
            default -> transformResponseGeneric(internalResponse);
        };
    }
    
    @Override
    public ExternalResponse transformResponse(InternalResponse internalResponse) {
        return transformResponse(internalResponse, null);
    }
    
    @Override
    public List<String> getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }
    
    @Override
    public boolean supportsProtocol(String protocol) {
        if (protocol == null) return false;
        return SUPPORTED_PROTOCOLS.stream()
            .anyMatch(p -> p.equalsIgnoreCase(protocol));
    }
    
    // ========== REST 协议转换 ==========
    
    private InternalRequest transformRestRequest(ExternalRequest ext) {
        InternalRequest req = createBaseInternalRequest(ext);
        req.setSourceProtocol("REST");
        req.setMessageType("request_response");
        
        // REST 特有: 保存 HTTP 方法到元数据
        if (ext.getHttpMethod() != null) {
            req.getMetadata().put("httpMethod", ext.getHttpMethod());
        }
        
        // 序列化请求体
        serializeBody(ext.getBody(), req);
        
        return req;
    }
    
    private ExternalResponse transformRestResponse(InternalResponse resp, ExternalRequest origReq) {
        ExternalResponse extResp = new ExternalResponse();
        extResp.setProtocol("REST");
        extResp.setMessageType("request_response");
        
        if (resp.isSuccess()) {
            extResp.setStatusCode(200);
            extResp.setBody(deserializePayload(resp.getPayload()));
        } else {
            extResp.setStatusCode(mapErrorCodeToHttpStatus(resp.getErrorCode()));
            extResp.setBody(buildRestErrorBody(resp));
        }
        
        copyHeaders(resp, extResp);
        copyMetadata(resp, extResp);
        return extResp;
    }
    
    // ========== WebSocket 协议转换 ==========
    
    private InternalRequest transformWebSocketRequest(ExternalRequest ext) {
        InternalRequest req = createBaseInternalRequest(ext);
        req.setSourceProtocol("WebSocket");
        
        // WebSocket 支持双向通信，根据元数据判断消息类型
        String msgType = (String) ext.getMetadata().getOrDefault("messageType", "text");
        req.getMetadata().put("wsMessageType", msgType);
        
        // WebSocket 默认是请求/响应，但也支持流式
        req.setMessageType(ext.getMessageType() != null ? ext.getMessageType() : "request_response");
        
        serializeBody(ext.getBody(), req);
        return req;
    }
    
    private ExternalResponse transformWebSocketResponse(InternalResponse resp, ExternalRequest origReq) {
        ExternalResponse extResp = new ExternalResponse();
        extResp.setProtocol("WebSocket");
        extResp.setMessageType(resp.getMessageType() != null ? resp.getMessageType() : "request_response");
        
        if (resp.isSuccess()) {
            extResp.setStatusCode(200);
            extResp.setBody(deserializePayload(resp.getPayload()));
        } else {
            extResp.setStatusCode(500);
            extResp.setBody(buildWebSocketErrorBody(resp));
        }
        
        copyHeaders(resp, extResp);
        copyMetadata(resp, extResp);
        return extResp;
    }
    
    // ========== JSON-RPC 协议转换 ==========
    
    private InternalRequest transformJsonRpcRequest(ExternalRequest ext) {
        InternalRequest req = createBaseInternalRequest(ext);
        req.setSourceProtocol("JSON-RPC");
        req.setMessageType("request_response");
        
        // JSON-RPC 特有: 保存请求 ID
        Object jsonRpcId = ext.getMetadata().get("jsonRpcId");
        if (jsonRpcId != null) {
            req.getMetadata().put("jsonRpcId", jsonRpcId);
        }
        
        serializeBody(ext.getBody(), req);
        return req;
    }
    
    private ExternalResponse transformJsonRpcResponse(InternalResponse resp, ExternalRequest origReq) {
        ExternalResponse extResp = new ExternalResponse();
        extResp.setProtocol("JSON-RPC");
        extResp.setMessageType("request_response");
        extResp.setStatusCode(200); // JSON-RPC 总是返回 200，错误在 body 中
        
        Map<String, Object> jsonRpcBody = new LinkedHashMap<>();
        jsonRpcBody.put("jsonrpc", "2.0");
        
        if (resp.isSuccess()) {
            jsonRpcBody.put("result", deserializePayload(resp.getPayload()));
        } else {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", mapErrorCodeToJsonRpcCode(resp.getErrorCode()));
            error.put("message", resp.getErrorMessage() != null ? resp.getErrorMessage() : "Internal error");
            jsonRpcBody.put("error", error);
        }
        
        // 恢复 JSON-RPC 请求 ID
        Object jsonRpcId = null;
        if (origReq != null && origReq.getMetadata() != null) {
            jsonRpcId = origReq.getMetadata().get("jsonRpcId");
        }
        if (jsonRpcId == null && resp.getMetadata() != null) {
            jsonRpcId = resp.getMetadata().get("jsonRpcId");
        }
        jsonRpcBody.put("id", jsonRpcId);
        
        extResp.setBody(jsonRpcBody);
        copyHeaders(resp, extResp);
        copyMetadata(resp, extResp);
        return extResp;
    }
    
    // ========== MQTT 协议转换 ==========
    
    private InternalRequest transformMqttRequest(ExternalRequest ext) {
        InternalRequest req = createBaseInternalRequest(ext);
        req.setSourceProtocol("MQTT");
        req.setMessageType("publish_subscribe"); // MQTT 默认是发布/订阅模式
        
        // MQTT 特有: 保存主题、QoS 等信息
        Object topic = ext.getMetadata().get("topic");
        if (topic != null) {
            req.getMetadata().put("mqttTopic", topic);
        }
        Object qos = ext.getMetadata().get("qos");
        if (qos != null) {
            req.getMetadata().put("mqttQos", qos);
        }
        Object retained = ext.getMetadata().get("retained");
        if (retained != null) {
            req.getMetadata().put("mqttRetained", retained);
        }
        Object responseTopic = ext.getMetadata().get("responseTopic");
        if (responseTopic != null) {
            req.getMetadata().put("mqttResponseTopic", responseTopic);
            // 如果有响应主题，则是请求/响应模式
            req.setMessageType("request_response");
        }
        
        serializeBody(ext.getBody(), req);
        return req;
    }
    
    private ExternalResponse transformMqttResponse(InternalResponse resp, ExternalRequest origReq) {
        ExternalResponse extResp = new ExternalResponse();
        extResp.setProtocol("MQTT");
        extResp.setMessageType(resp.getMessageType() != null ? resp.getMessageType() : "publish_subscribe");
        
        if (resp.isSuccess()) {
            extResp.setStatusCode(200);
            extResp.setBody(deserializePayload(resp.getPayload()));
        } else {
            extResp.setStatusCode(500);
            extResp.setBody(buildMqttErrorBody(resp));
        }
        
        // 恢复 MQTT 元数据
        if (origReq != null && origReq.getMetadata() != null) {
            Object responseTopic = origReq.getMetadata().get("responseTopic");
            if (responseTopic != null) {
                extResp.getMetadata().put("responseTopic", responseTopic);
            }
        }
        
        copyHeaders(resp, extResp);
        copyMetadata(resp, extResp);
        return extResp;
    }
    
    // ========== 通用转换（回退） ==========
    
    private ExternalResponse transformResponseGeneric(InternalResponse resp) {
        ExternalResponse extResp = new ExternalResponse();
        extResp.setMessageType(resp.getMessageType() != null ? resp.getMessageType() : "request_response");
        
        if (resp.isSuccess()) {
            extResp.setStatusCode(200);
            extResp.setBody(deserializePayload(resp.getPayload()));
        } else {
            extResp.setStatusCode(500);
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", true);
            errorBody.put("code", resp.getErrorCode());
            errorBody.put("message", resp.getErrorMessage());
            extResp.setBody(errorBody);
        }
        
        copyHeaders(resp, extResp);
        copyMetadata(resp, extResp);
        return extResp;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 创建基础内部请求（公共字段）
     */
    private InternalRequest createBaseInternalRequest(ExternalRequest ext) {
        InternalRequest req = new InternalRequest();
        req.setService(ext.getService());
        req.setMethod(ext.getMethod());
        req.setTraceId(generateTraceId());
        
        // 复制请求头
        if (ext.getHeaders() != null) {
            req.setHeaders(new HashMap<>(ext.getHeaders()));
        }
        
        // 复制元数据
        if (ext.getMetadata() != null) {
            req.setMetadata(new HashMap<>(ext.getMetadata()));
        }
        
        // 保存原始协议类型到元数据
        req.getMetadata().put("sourceProtocol", ext.getProtocol());
        
        return req;
    }
    
    /**
     * 序列化请求体到 payload 字节数组
     */
    private void serializeBody(Object body, InternalRequest req) {
        if (body == null) return;
        try {
            byte[] payload = objectMapper.writeValueAsBytes(body);
            req.setPayload(payload);
        } catch (Exception e) {
            logger.error("序列化请求体失败", e);
            throw new FrameworkException(ErrorCode.SERIALIZATION_ERROR, 
                "序列化请求体失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 反序列化 payload 字节数组到对象
     */
    private Object deserializePayload(byte[] payload) {
        if (payload == null || payload.length == 0) return null;
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception e) {
            // 如果 JSON 解析失败，返回原始字符串
            logger.debug("反序列化 payload 为 JSON 失败，返回原始字符串");
            return new String(payload);
        }
    }
    
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
    
    private void copyHeaders(InternalResponse resp, ExternalResponse extResp) {
        if (resp.getHeaders() != null) {
            extResp.setHeaders(new HashMap<>(resp.getHeaders()));
        }
    }
    
    private void copyMetadata(InternalResponse resp, ExternalResponse extResp) {
        if (resp.getMetadata() != null) {
            resp.getMetadata().forEach((k, v) -> extResp.getMetadata().put(k, v));
        }
    }
    
    // ========== 错误体构建 ==========
    
    private Map<String, Object> buildRestErrorBody(InternalResponse resp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("code", resp.getErrorCode());
        body.put("message", resp.getErrorMessage());
        body.put("timestamp", System.currentTimeMillis());
        return body;
    }
    
    private Map<String, Object> buildWebSocketErrorBody(InternalResponse resp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "error");
        body.put("error", true);
        body.put("code", resp.getErrorCode());
        body.put("message", resp.getErrorMessage());
        body.put("timestamp", System.currentTimeMillis());
        return body;
    }
    
    private Map<String, Object> buildMqttErrorBody(InternalResponse resp) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("code", resp.getErrorCode());
        body.put("message", resp.getErrorMessage());
        return body;
    }
    
    // ========== 错误码映射 ==========
    
    private int mapErrorCodeToHttpStatus(String errorCode) {
        if (errorCode == null) return 500;
        try {
            int code = Integer.parseInt(errorCode);
            if (code >= 400 && code < 600) return code;
            // 框架错误码 (6xx) 映射到 500
            return 500;
        } catch (NumberFormatException e) {
            // 尝试按名称匹配
            return switch (errorCode.toUpperCase()) {
                case "BAD_REQUEST" -> 400;
                case "UNAUTHORIZED" -> 401;
                case "FORBIDDEN" -> 403;
                case "NOT_FOUND" -> 404;
                case "TIMEOUT" -> 408;
                case "SERVICE_UNAVAILABLE" -> 503;
                case "NOT_IMPLEMENTED" -> 501;
                default -> 500;
            };
        }
    }
    
    private int mapErrorCodeToJsonRpcCode(String errorCode) {
        if (errorCode == null) return -32603;
        return switch (errorCode.toUpperCase()) {
            case "BAD_REQUEST", "400" -> -32602;      // Invalid params
            case "NOT_FOUND", "404" -> -32601;         // Method not found
            case "PROTOCOL_ERROR", "600" -> -32600;    // Invalid Request
            case "SERIALIZATION_ERROR", "601" -> -32700; // Parse error
            default -> -32603;                          // Internal error
        };
    }
}
