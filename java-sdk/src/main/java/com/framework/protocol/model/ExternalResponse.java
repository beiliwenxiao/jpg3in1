package com.framework.protocol.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部响应模型
 * 
 * 表示返回给外部协议的响应
 */
public class ExternalResponse {
    
    private String protocol;        // 协议类型
    private String messageType;     // 消息语义类型: request_response, publish_subscribe, stream
    private int statusCode;         // 状态码
    private Map<String, String> headers;  // 响应头
    private Object body;            // 响应体
    private Map<String, Object> metadata;  // 元数据
    
    public ExternalResponse() {
        this.statusCode = 200;
        this.headers = new HashMap<>();
        this.metadata = new HashMap<>();
        this.messageType = "request_response";
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
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
