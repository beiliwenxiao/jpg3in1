package com.framework.protocol.external.rest;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.external.ExternalProtocolHandler;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API 协议处理器
 * 
 * 支持 GET、POST、PUT、DELETE、PATCH 方法
 * 实现请求解析和响应生成
 * 
 * **验证需求: 2.1, 2.5**
 */
@RestController
@RequestMapping("/api")
public class RestProtocolHandler implements ExternalProtocolHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(RestProtocolHandler.class);
    
    private final RestRequestProcessor requestProcessor;
    
    public RestProtocolHandler(RestRequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
    }
    
    /**
     * 处理 GET 请求
     */
    @GetMapping("/{service}/{method}")
    public ResponseEntity<Object> handleGet(
            @PathVariable String service,
            @PathVariable String method,
            @RequestParam(required = false) Map<String, String> params,
            @RequestHeader Map<String, String> headers) {
        
        logger.debug("处理 GET 请求: service={}, method={}", service, method);
        
        try {
            ExternalRequest request = buildRequest(HttpMethod.GET, service, method, params, headers, null);
            ExternalResponse response = processRequest(request);
            return buildResponse(response);
            
        } catch (FrameworkException e) {
            logger.error("GET 请求处理失败: {}", e.getMessage(), e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            logger.error("GET 请求异常: {}", e.getMessage(), e);
            return buildErrorResponse(new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    /**
     * 处理 POST 请求
     */
    @PostMapping("/{service}/{method}")
    public ResponseEntity<Object> handlePost(
            @PathVariable String service,
            @PathVariable String method,
            @RequestBody(required = false) Object body,
            @RequestHeader Map<String, String> headers) {
        
        logger.debug("处理 POST 请求: service={}, method={}", service, method);
        
        try {
            ExternalRequest request = buildRequest(HttpMethod.POST, service, method, null, headers, body);
            ExternalResponse response = processRequest(request);
            return buildResponse(response);
            
        } catch (FrameworkException e) {
            logger.error("POST 请求处理失败: {}", e.getMessage(), e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            logger.error("POST 请求异常: {}", e.getMessage(), e);
            return buildErrorResponse(new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    /**
     * 处理 PUT 请求
     */
    @PutMapping("/{service}/{method}")
    public ResponseEntity<Object> handlePut(
            @PathVariable String service,
            @PathVariable String method,
            @RequestBody(required = false) Object body,
            @RequestHeader Map<String, String> headers) {
        
        logger.debug("处理 PUT 请求: service={}, method={}", service, method);
        
        try {
            ExternalRequest request = buildRequest(HttpMethod.PUT, service, method, null, headers, body);
            ExternalResponse response = processRequest(request);
            return buildResponse(response);
            
        } catch (FrameworkException e) {
            logger.error("PUT 请求处理失败: {}", e.getMessage(), e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            logger.error("PUT 请求异常: {}", e.getMessage(), e);
            return buildErrorResponse(new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    /**
     * 处理 DELETE 请求
     */
    @DeleteMapping("/{service}/{method}")
    public ResponseEntity<Object> handleDelete(
            @PathVariable String service,
            @PathVariable String method,
            @RequestParam(required = false) Map<String, String> params,
            @RequestHeader Map<String, String> headers) {
        
        logger.debug("处理 DELETE 请求: service={}, method={}", service, method);
        
        try {
            ExternalRequest request = buildRequest(HttpMethod.DELETE, service, method, params, headers, null);
            ExternalResponse response = processRequest(request);
            return buildResponse(response);
            
        } catch (FrameworkException e) {
            logger.error("DELETE 请求处理失败: {}", e.getMessage(), e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            logger.error("DELETE 请求异常: {}", e.getMessage(), e);
            return buildErrorResponse(new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    /**
     * 处理 PATCH 请求
     */
    @PatchMapping("/{service}/{method}")
    public ResponseEntity<Object> handlePatch(
            @PathVariable String service,
            @PathVariable String method,
            @RequestBody(required = false) Object body,
            @RequestHeader Map<String, String> headers) {
        
        logger.debug("处理 PATCH 请求: service={}, method={}", service, method);
        
        try {
            ExternalRequest request = buildRequest(HttpMethod.PATCH, service, method, null, headers, body);
            ExternalResponse response = processRequest(request);
            return buildResponse(response);
            
        } catch (FrameworkException e) {
            logger.error("PATCH 请求处理失败: {}", e.getMessage(), e);
            return buildErrorResponse(e);
        } catch (Exception e) {
            logger.error("PATCH 请求异常: {}", e.getMessage(), e);
            return buildErrorResponse(new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    @Override
    public ExternalResponse handle(ExternalRequest request) {
        return requestProcessor.process(request);
    }
    
    /**
     * 构建外部请求对象
     */
    private ExternalRequest buildRequest(HttpMethod method, String service, String methodName,
                                        Map<String, String> params, Map<String, String> headers, Object body) {
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("REST");
        request.setService(service);
        request.setMethod(methodName);
        request.setHttpMethod(method.name());
        request.setHeaders(headers != null ? headers : new HashMap<>());
        
        // 合并参数和body
        if (params != null && !params.isEmpty()) {
            request.setBody(params);
        } else if (body != null) {
            request.setBody(body);
        }
        
        return request;
    }
    
    /**
     * 处理请求
     */
    private ExternalResponse processRequest(ExternalRequest request) {
        return requestProcessor.process(request);
    }
    
    /**
     * 构建响应
     */
    private ResponseEntity<Object> buildResponse(ExternalResponse response) {
        HttpStatus status = HttpStatus.valueOf(response.getStatusCode());
        return ResponseEntity.status(status)
                .headers(headers -> {
                    if (response.getHeaders() != null) {
                        response.getHeaders().forEach(headers::add);
                    }
                })
                .body(response.getBody());
    }
    
    /**
     * 构建错误响应
     */
    private ResponseEntity<Object> buildErrorResponse(FrameworkException exception) {
        HttpStatus status = mapErrorCodeToHttpStatus(exception.getErrorCode());
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", true);
        errorBody.put("code", exception.getErrorCode().getCode());
        errorBody.put("message", exception.getMessage());
        errorBody.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(status).body(errorBody);
    }
    
    /**
     * 映射错误码到 HTTP 状态码
     */
    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
        switch (errorCode) {
            case BAD_REQUEST:
                return HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED:
                return HttpStatus.UNAUTHORIZED;
            case FORBIDDEN:
                return HttpStatus.FORBIDDEN;
            case NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case TIMEOUT:
                return HttpStatus.REQUEST_TIMEOUT;
            case SERVICE_UNAVAILABLE:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_IMPLEMENTED:
                return HttpStatus.NOT_IMPLEMENTED;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
