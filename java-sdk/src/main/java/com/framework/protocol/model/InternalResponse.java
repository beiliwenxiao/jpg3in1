package com.framework.protocol.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部响应模型
 * 
 * 表示框架内部使用的统一响应格式
 */
public class InternalResponse {
    
    private boolean success;        // 是否成功
    private byte[] payload;         // 消息负载
    private Map<String, String> headers;  // 响应头
    private String errorCode;       // 错误码
    private String errorMessage;    // 错误消息
    private String sourceProtocol;  // 来源外部协议类型（用于反向转换）
    private String messageType;     // 消息语义类型
    private Map<String, Object> metadata;  // 元数据
    
    public InternalResponse() {
        this.success = true;
        this.headers = new HashMap<>();
        this.metadata = new HashMap<>();
        this.messageType = "request_response";
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
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
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
