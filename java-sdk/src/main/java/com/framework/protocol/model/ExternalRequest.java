package com.framework.protocol.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部请求模型
 * 
 * 表示来自外部协议的请求
 */
public class ExternalRequest {
    
    private String protocol;        // 协议类型: REST, WebSocket, JSON-RPC, MQTT
    private String service;         // 服务名称
    private String method;          // 方法名称
    private String httpMethod;      // HTTP 方法（仅 REST）
    private String messageType;     // 消息语义类型: request_response, publish_subscribe, stream
    private Map<String, String> headers;  // 请求头
    private Object body;            // 请求体
    private Map<String, Object> metadata;  // 元数据
    
    public ExternalRequest() {
        this.headers = new HashMap<>();
        this.metadata = new HashMap<>();
        this.messageType = "request_response"; // 默认请求/响应模式
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
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
    
    public String getHttpMethod() {
        return httpMethod;
    }
    
    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public Object getBody() {
        return body;
    }
    
    public void setBody(Object body) {
        this.body = body;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
