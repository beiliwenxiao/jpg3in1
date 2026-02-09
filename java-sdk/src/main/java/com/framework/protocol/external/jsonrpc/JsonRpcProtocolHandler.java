package com.framework.protocol.external.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.external.ExternalProtocolHandler;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * JSON-RPC 2.0 协议处理器
 * 
 * 按照 JSON-RPC 2.0 规范解析请求和生成响应
 * 参考: https://www.jsonrpc.org/specification
 * 
 * **验证需求: 2.3**
 */
@RestController
@RequestMapping("/jsonrpc")
public class JsonRpcProtocolHandler implements ExternalProtocolHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcProtocolHandler.class);
    private static final String JSONRPC_VERSION = "2.0";
    
    private final JsonRpcRequestProcessor requestProcessor;
    private final ObjectMapper objectMapper;
    
    public JsonRpcProtocolHandler(JsonRpcRequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 处理 JSON-RPC 2.0 请求
     * 支持单个请求和批量请求
     */
    @PostMapping
    public ResponseEntity<Object> handleJsonRpc(@RequestBody String requestBody) {
        logger.debug("收到 JSON-RPC 请求: {}", requestBody);
        
        try {
            JsonNode rootNode = objectMapper.readTree(requestBody);
            
            // 判断是单个请求还是批量请求
            if (rootNode.isArray()) {
                return handleBatchRequest(rootNode);
            } else {
                return handleSingleRequest(rootNode);
            }
            
        } catch (Exception e) {
            logger.error("解析 JSON-RPC 请求失败: {}", e.getMessage(), e);
            return buildErrorResponse(null, JsonRpcErrorCode.PARSE_ERROR, 
                "Parse error: " + e.getMessage(), null);
        }
    }
    
    /**
     * 处理单个 JSON-RPC 请求
     */
    private ResponseEntity<Object> handleSingleRequest(JsonNode requestNode) {
        Object requestId = null;
        
        try {
            // 验证 JSON-RPC 版本
            if (!requestNode.has("jsonrpc") || 
                !JSONRPC_VERSION.equals(requestNode.get("jsonrpc").asText())) {
                return buildErrorResponse(null, JsonRpcErrorCode.INVALID_REQUEST,
                    "Invalid Request: jsonrpc version must be '2.0'", null);
            }
            
            // 提取请求 ID
            if (requestNode.has("id")) {
                JsonNode idNode = requestNode.get("id");
                if (idNode.isTextual()) {
                    requestId = idNode.asText();
                } else if (idNode.isNumber()) {
                    requestId = idNode.asLong();
                } else if (idNode.isNull()) {
                    requestId = null;
                } else {
                    return buildErrorResponse(null, JsonRpcErrorCode.INVALID_REQUEST,
                        "Invalid Request: id must be a string, number, or null", null);
                }
            }
            
            // 验证方法名
            if (!requestNode.has("method") || !requestNode.get("method").isTextual()) {
                return buildErrorResponse(requestId, JsonRpcErrorCode.INVALID_REQUEST,
                    "Invalid Request: method is required and must be a string", null);
            }
            
            String method = requestNode.get("method").asText();
            
            // 提取参数
            Object params = null;
            if (requestNode.has("params")) {
                JsonNode paramsNode = requestNode.get("params");
                if (paramsNode.isArray() || paramsNode.isObject()) {
                    params = objectMapper.treeToValue(paramsNode, Object.class);
                } else {
                    return buildErrorResponse(requestId, JsonRpcErrorCode.INVALID_REQUEST,
                        "Invalid Request: params must be an array or object", null);
                }
            }
            
            // 提取追踪信息（扩展字段）
            String traceId = requestNode.has("trace_id") ? requestNode.get("trace_id").asText() : null;
            String spanId = requestNode.has("span_id") ? requestNode.get("span_id").asText() : null;
            
            // 构建外部请求
            ExternalRequest externalRequest = buildExternalRequest(method, params, traceId, spanId);
            
            // 如果没有 ID，这是一个通知（不需要响应）
            if (requestId == null) {
                logger.debug("处理 JSON-RPC 通知: method={}", method);
                handle(externalRequest);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }
            
            // 处理请求
            ExternalResponse externalResponse = handle(externalRequest);
            
            // 构建 JSON-RPC 响应
            return buildSuccessResponse(requestId, externalResponse.getBody(), traceId, spanId);
            
        } catch (FrameworkException e) {
            logger.error("处理 JSON-RPC 请求失败: {}", e.getMessage(), e);
            return buildErrorResponse(requestId, mapErrorCodeToJsonRpc(e.getErrorCode()),
                e.getMessage(), e.getDetails());
        } catch (Exception e) {
            logger.error("处理 JSON-RPC 请求异常: {}", e.getMessage(), e);
            return buildErrorResponse(requestId, JsonRpcErrorCode.INTERNAL_ERROR,
                "Internal error: " + e.getMessage(), null);
        }
    }
    
    /**
     * 处理批量 JSON-RPC 请求
     */
    private ResponseEntity<Object> handleBatchRequest(JsonNode batchNode) {
        logger.debug("处理批量 JSON-RPC 请求，数量: {}", batchNode.size());
        
        // 空批量请求是无效的
        if (batchNode.size() == 0) {
            return buildErrorResponse(null, JsonRpcErrorCode.INVALID_REQUEST,
                "Invalid Request: batch request cannot be empty", null);
        }
        
        List<Map<String, Object>> responses = new ArrayList<>();
        
        // 处理每个请求
        for (JsonNode requestNode : batchNode) {
            ResponseEntity<Object> response = handleSingleRequest(requestNode);
            
            // 只有非通知请求才会有响应
            if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                if (responseBody != null) {
                    responses.add(responseBody);
                }
            }
        }
        
        // 如果所有请求都是通知，返回 204 No Content
        if (responses.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        
        return ResponseEntity.ok(responses);
    }
    
    @Override
    public ExternalResponse handle(ExternalRequest request) {
        return requestProcessor.process(request);
    }
    
    /**
     * 构建外部请求对象
     */
    private ExternalRequest buildExternalRequest(String method, Object params, 
                                                 String traceId, String spanId) {
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("JSON-RPC");
        
        // 解析方法名，格式: service.method 或 service/method
        String[] parts = method.split("[./]");
        if (parts.length == 2) {
            request.setService(parts[0]);
            request.setMethod(parts[1]);
        } else {
            // 如果没有指定服务，使用默认服务
            request.setService("default");
            request.setMethod(method);
        }
        
        // 设置参数
        if (params != null) {
            request.setBody(params);
        }
        
        // 设置追踪信息
        if (traceId != null) {
            request.getMetadata().put("traceId", traceId);
        }
        if (spanId != null) {
            request.getMetadata().put("spanId", spanId);
        }
        
        request.getMetadata().put("timestamp", System.currentTimeMillis());
        
        return request;
    }
    
    /**
     * 构建成功响应
     */
    private ResponseEntity<Object> buildSuccessResponse(Object id, Object result, 
                                                        String traceId, String spanId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("result", result);
        response.put("id", id);
        
        // 添加追踪信息（如果存在）
        if (traceId != null) {
            response.put("trace_id", traceId);
        }
        if (spanId != null) {
            response.put("span_id", spanId);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 构建错误响应
     */
    private ResponseEntity<Object> buildErrorResponse(Object id, JsonRpcErrorCode errorCode,
                                                      String message, Object data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", errorCode.getCode());
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSONRPC_VERSION);
        response.put("error", error);
        response.put("id", id);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 映射框架错误码到 JSON-RPC 错误码
     */
    private JsonRpcErrorCode mapErrorCodeToJsonRpc(ErrorCode errorCode) {
        switch (errorCode) {
            case BAD_REQUEST:
                return JsonRpcErrorCode.INVALID_PARAMS;
            case NOT_FOUND:
                return JsonRpcErrorCode.METHOD_NOT_FOUND;
            case PROTOCOL_ERROR:
                return JsonRpcErrorCode.INVALID_REQUEST;
            case SERIALIZATION_ERROR:
                return JsonRpcErrorCode.PARSE_ERROR;
            default:
                return JsonRpcErrorCode.INTERNAL_ERROR;
        }
    }
    
    /**
     * JSON-RPC 2.0 标准错误码
     */
    public enum JsonRpcErrorCode {
        PARSE_ERROR(-32700, "Parse error"),
        INVALID_REQUEST(-32600, "Invalid Request"),
        METHOD_NOT_FOUND(-32601, "Method not found"),
        INVALID_PARAMS(-32602, "Invalid params"),
        INTERNAL_ERROR(-32603, "Internal error"),
        SERVER_ERROR(-32000, "Server error");
        
        private final int code;
        private final String message;
        
        JsonRpcErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
