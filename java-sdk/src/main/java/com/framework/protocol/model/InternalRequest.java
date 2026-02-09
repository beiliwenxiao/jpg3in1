package com.framework.protocol.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部请求模型
 * 
 * 表示框架内部使用的统一请求格式
 */
public class InternalRequest {
    
    private String service;         // 服务名称
    private String method;          // 方法名称
    private byte[] payload;         // 消息负载
    private Map<String, String> headers;  // 请求头
    private String traceId;         // 追踪 ID
    private int timeout;            // 超时时间（毫秒）
    private String sourceProtocol;  // 来源外部协议类型
    private String messageType;     // 消息语义类型: request_response, publish_subscribe, stream
    private Map<String, Object> metadata;  // 元数据
    
    public InternalRequest() {
        this.headers = new HashMap<>();
        this.metadata = new HashMap<>();
        this.timeout = 30000;  // 默认 30 秒
        this.messageType = "request_response";
    }
    
    public String getService() {
        return service;
    }
    
    public void setService(String service) {
        this.service = service;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public String getSourceProtocol() {
        return sourceProtocol;
    }
    
    public void setSourceProtocol(String sourceProtocol) {
        this.sourceProtocol = sourceProtocol;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
}
