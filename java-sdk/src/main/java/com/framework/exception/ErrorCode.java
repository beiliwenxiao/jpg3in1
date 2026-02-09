package com.framework.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一错误码枚举
 * 
 * 定义框架所有错误码，并提供与 HTTP 状态码、gRPC 状态码、JSON-RPC 错误码之间的映射。
 */
public enum ErrorCode {
    // 客户端错误 (4xx)
    BAD_REQUEST(400, "Bad Request", true),
    UNAUTHORIZED(401, "Unauthorized", true),
    FORBIDDEN(403, "Forbidden", true),
    NOT_FOUND(404, "Not Found", true),
    TIMEOUT(408, "Timeout", true),
    
    // 服务端错误 (5xx)
    INTERNAL_ERROR(500, "Internal Error", false),
    NOT_IMPLEMENTED(501, "Not Implemented", false),
    SERVICE_UNAVAILABLE(503, "Service Unavailable", false),
    
    // 框架错误 (6xx)
    PROTOCOL_ERROR(600, "Protocol Error", false),
    SERIALIZATION_ERROR(601, "Serialization Error", false),
    ROUTING_ERROR(602, "Routing Error", false),
    CONNECTION_ERROR(603, "Connection Error", false);
    
    private final int code;
    private final String message;
    private final boolean clientError;

    // HTTP 状态码到 ErrorCode 的映射
    private static final Map<Integer, ErrorCode> HTTP_STATUS_MAP = new HashMap<>();
    // gRPC 状态码到 ErrorCode 的映射
    private static final Map<Integer, ErrorCode> GRPC_STATUS_MAP = new HashMap<>();
    // JSON-RPC 错误码到 ErrorCode 的映射
    private static final Map<Integer, ErrorCode> JSONRPC_CODE_MAP = new HashMap<>();
    // 框架错误码到 ErrorCode 的映射
    private static final Map<Integer, ErrorCode> CODE_MAP = new HashMap<>();
    
    static {
        for (ErrorCode ec : values()) {
            CODE_MAP.put(ec.code, ec);
        }
        
        // HTTP 状态码映射
        HTTP_STATUS_MAP.put(400, BAD_REQUEST);
        HTTP_STATUS_MAP.put(401, UNAUTHORIZED);
        HTTP_STATUS_MAP.put(403, FORBIDDEN);
        HTTP_STATUS_MAP.put(404, NOT_FOUND);
        HTTP_STATUS_MAP.put(408, TIMEOUT);
        HTTP_STATUS_MAP.put(500, INTERNAL_ERROR);
        HTTP_STATUS_MAP.put(501, NOT_IMPLEMENTED);
        HTTP_STATUS_MAP.put(503, SERVICE_UNAVAILABLE);
        
        // gRPC 状态码映射 (io.grpc.Status.Code ordinal values)
        GRPC_STATUS_MAP.put(0, null);  // OK - 无错误
        GRPC_STATUS_MAP.put(1, INTERNAL_ERROR);      // CANCELLED
        GRPC_STATUS_MAP.put(2, INTERNAL_ERROR);      // UNKNOWN
        GRPC_STATUS_MAP.put(3, BAD_REQUEST);          // INVALID_ARGUMENT
        GRPC_STATUS_MAP.put(4, TIMEOUT);              // DEADLINE_EXCEEDED
        GRPC_STATUS_MAP.put(5, NOT_FOUND);            // NOT_FOUND
        GRPC_STATUS_MAP.put(7, FORBIDDEN);            // PERMISSION_DENIED
        GRPC_STATUS_MAP.put(12, NOT_IMPLEMENTED);     // UNIMPLEMENTED
        GRPC_STATUS_MAP.put(13, INTERNAL_ERROR);      // INTERNAL
        GRPC_STATUS_MAP.put(14, SERVICE_UNAVAILABLE); // UNAVAILABLE
        GRPC_STATUS_MAP.put(16, UNAUTHORIZED);        // UNAUTHENTICATED
        
        // JSON-RPC 错误码映射
        JSONRPC_CODE_MAP.put(-32700, BAD_REQUEST);        // Parse error
        JSONRPC_CODE_MAP.put(-32600, BAD_REQUEST);        // Invalid Request
        JSONRPC_CODE_MAP.put(-32601, NOT_FOUND);          // Method not found
        JSONRPC_CODE_MAP.put(-32602, BAD_REQUEST);        // Invalid params
        JSONRPC_CODE_MAP.put(-32603, INTERNAL_ERROR);     // Internal error
    }
    
    ErrorCode(int code, String message, boolean clientError) {
        this.code = code;
        this.message = message;
        this.clientError = clientError;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isClientError() {
        return clientError;
    }
    
    public boolean isServerError() {
        return code >= 500 && code < 600;
    }
    
    public boolean isFrameworkError() {
        return code >= 600;
    }
    
    /**
     * 是否为可重试的错误
     */
    public boolean isRetryable() {
        return this == TIMEOUT || this == SERVICE_UNAVAILABLE || this == CONNECTION_ERROR;
    }
    
    /**
     * 根据框架错误码查找 ErrorCode
     */
    public static ErrorCode fromCode(int code) {
        return CODE_MAP.getOrDefault(code, INTERNAL_ERROR);
    }
    
    /**
     * 从 HTTP 状态码映射到 ErrorCode
     */
    public static ErrorCode fromHttpStatus(int httpStatus) {
        ErrorCode mapped = HTTP_STATUS_MAP.get(httpStatus);
        if (mapped != null) {
            return mapped;
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return BAD_REQUEST;
        }
        if (httpStatus >= 500) {
            return INTERNAL_ERROR;
        }
        return INTERNAL_ERROR;
    }
    
    /**
     * 从 gRPC 状态码映射到 ErrorCode
     */
    public static ErrorCode fromGrpcStatus(int grpcStatusCode) {
        ErrorCode mapped = GRPC_STATUS_MAP.get(grpcStatusCode);
        return mapped != null ? mapped : INTERNAL_ERROR;
    }
    
    /**
     * 从 JSON-RPC 错误码映射到 ErrorCode
     */
    public static ErrorCode fromJsonRpcCode(int jsonRpcCode) {
        ErrorCode mapped = JSONRPC_CODE_MAP.get(jsonRpcCode);
        return mapped != null ? mapped : INTERNAL_ERROR;
    }
    
    /**
     * 将 ErrorCode 映射到 HTTP 状态码
     */
    public int toHttpStatus() {
        return switch (this) {
            case BAD_REQUEST -> 400;
            case UNAUTHORIZED -> 401;
            case FORBIDDEN -> 403;
            case NOT_FOUND -> 404;
            case TIMEOUT -> 408;
            case INTERNAL_ERROR -> 500;
            case NOT_IMPLEMENTED -> 501;
            case SERVICE_UNAVAILABLE -> 503;
            case PROTOCOL_ERROR -> 502;
            case SERIALIZATION_ERROR -> 400;
            case ROUTING_ERROR -> 502;
            case CONNECTION_ERROR -> 503;
        };
    }
    
    /**
     * 将 ErrorCode 映射到 JSON-RPC 错误码
     */
    public int toJsonRpcCode() {
        return switch (this) {
            case BAD_REQUEST -> -32600;
            case NOT_FOUND -> -32601;
            case INTERNAL_ERROR, TIMEOUT, SERVICE_UNAVAILABLE -> -32603;
            case SERIALIZATION_ERROR -> -32700;
            default -> -32603;
        };
    }
}
