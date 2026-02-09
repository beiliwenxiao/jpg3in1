package com.framework.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 框架统一异常类
 * 
 * 提供标准化的错误响应、错误码映射和错误链追踪功能。
 * 所有框架内部的异常都应使用此类或其子类。
 */
public class FrameworkException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final String details;
    private final String serviceId;
    private final long timestamp;
    private final List<String> errorChain;
    
    public FrameworkException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
        this.serviceId = null;
        this.timestamp = Instant.now().toEpochMilli();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(formatChainEntry(errorCode, message, null));
    }
    
    public FrameworkException(ErrorCode errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
        this.serviceId = null;
        this.timestamp = Instant.now().toEpochMilli();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(formatChainEntry(errorCode, message, null));
    }
    
    public FrameworkException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
        this.serviceId = null;
        this.timestamp = Instant.now().toEpochMilli();
        this.errorChain = buildErrorChain(errorCode, message, cause);
    }

    public FrameworkException(ErrorCode errorCode, String message, String details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
        this.serviceId = null;
        this.timestamp = Instant.now().toEpochMilli();
        this.errorChain = buildErrorChain(errorCode, message, cause);
    }
    
    public FrameworkException(ErrorCode errorCode, String message, String details, 
                              String serviceId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
        this.serviceId = serviceId;
        this.timestamp = Instant.now().toEpochMilli();
        this.errorChain = buildErrorChain(errorCode, message, cause);
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public String getDetails() {
        return details;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 获取错误链（从最外层到最内层的错误追踪）
     */
    public List<String> getErrorChain() {
        return Collections.unmodifiableList(errorChain);
    }
    
    /**
     * 将异常转换为标准化的错误响应 Map
     */
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", errorCode.getCode());
        response.put("error", errorCode.getMessage());
        response.put("message", getMessage());
        response.put("timestamp", timestamp);
        
        if (details != null) {
            response.put("details", details);
        }
        if (serviceId != null) {
            response.put("serviceId", serviceId);
        }
        if (!errorChain.isEmpty()) {
            response.put("errorChain", errorChain);
        }
        return response;
    }
    
    /**
     * 从 HTTP 状态码创建 FrameworkException
     */
    public static FrameworkException fromHttpStatus(int httpStatus, String message) {
        return new FrameworkException(ErrorCode.fromHttpStatus(httpStatus), message);
    }
    
    /**
     * 从 gRPC 状态码创建 FrameworkException
     */
    public static FrameworkException fromGrpcStatus(int grpcStatusCode, String message) {
        return new FrameworkException(ErrorCode.fromGrpcStatus(grpcStatusCode), message);
    }
    
    /**
     * 从 JSON-RPC 错误码创建 FrameworkException
     */
    public static FrameworkException fromJsonRpcCode(int jsonRpcCode, String message) {
        return new FrameworkException(ErrorCode.fromJsonRpcCode(jsonRpcCode), message);
    }
    
    /**
     * 包装一个已有异常，添加服务上下文信息
     */
    public FrameworkException withServiceId(String serviceId) {
        return new FrameworkException(this.errorCode, this.getMessage(), 
                                     this.details, serviceId, this.getCause());
    }
    
    /**
     * 构建错误链
     */
    private static List<String> buildErrorChain(ErrorCode errorCode, String message, Throwable cause) {
        List<String> chain = new ArrayList<>();
        chain.add(formatChainEntry(errorCode, message, null));
        
        Throwable current = cause;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof FrameworkException fe) {
                chain.add(formatChainEntry(fe.getErrorCode(), fe.getMessage(), fe.getServiceId()));
            } else {
                chain.add(current.getClass().getSimpleName() + ": " + current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        return chain;
    }
    
    private static String formatChainEntry(ErrorCode code, String message, String serviceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(code.getCode()).append(" ").append(code.getMessage()).append("] ");
        sb.append(message);
        if (serviceId != null) {
            sb.append(" (service: ").append(serviceId).append(")");
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FrameworkException{");
        sb.append("code=").append(errorCode.getCode());
        sb.append(", error='").append(errorCode.getMessage()).append('\'');
        sb.append(", message='").append(getMessage()).append('\'');
        if (details != null) {
            sb.append(", details='").append(details).append('\'');
        }
        if (serviceId != null) {
            sb.append(", serviceId='").append(serviceId).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
