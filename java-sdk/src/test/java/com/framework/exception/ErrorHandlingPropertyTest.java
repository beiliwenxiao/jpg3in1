package com.framework.exception;

import com.framework.resilience.CircuitBreaker;
import com.framework.resilience.RetryExecutor;
import com.framework.resilience.RetryPolicy;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 错误处理属性测试
 * 
 * Feature: multi-language-communication-framework
 * 验证需求: 8.1, 8.5
 */
class ErrorHandlingPropertyTest {

    // ==================== 属性 27: 协议错误标准化响应 ====================

    /**
     * 属性 27: 协议错误标准化响应
     * 
     * 对于任意协议错误，框架应该返回符合标准格式的错误响应，
     * 包含错误码和错误消息。
     * 
     * **Validates: Requirements 8.1**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 27: 协议错误标准化响应")
    void protocolErrorStandardizedResponse(
            @ForAll("errorCodes") ErrorCode errorCode,
            @ForAll @NotBlank String message) {
        
        FrameworkException exception = new FrameworkException(errorCode, message);
        Map<String, Object> response = exception.toErrorResponse();
        
        // 标准化响应必须包含 code、error、message、timestamp
        assertNotNull(response.get("code"), "响应必须包含错误码");
        assertNotNull(response.get("error"), "响应必须包含错误名称");
        assertNotNull(response.get("message"), "响应必须包含错误消息");
        assertNotNull(response.get("timestamp"), "响应必须包含时间戳");
        
        assertEquals(errorCode.getCode(), response.get("code"), "错误码应与 ErrorCode 一致");
        assertEquals(errorCode.getMessage(), response.get("error"), "错误名称应与 ErrorCode 一致");
        assertEquals(message, response.get("message"), "错误消息应与传入的消息一致");
        assertTrue((long) response.get("timestamp") > 0, "时间戳应为正数");
    }

    /**
     * 属性 27 补充: 带详情和服务 ID 的标准化响应
     * 
     * **Validates: Requirements 8.1**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 27: 协议错误标准化响应 - 带详情")
    void protocolErrorStandardizedResponseWithDetails(
            @ForAll("errorCodes") ErrorCode errorCode,
            @ForAll @NotBlank String message,
            @ForAll @NotBlank String details,
            @ForAll @NotBlank String serviceId) {
        
        FrameworkException exception = new FrameworkException(
                errorCode, message, details, serviceId, null);
        Map<String, Object> response = exception.toErrorResponse();
        
        assertEquals(details, response.get("details"), "响应应包含详情");
        assertEquals(serviceId, response.get("serviceId"), "响应应包含服务 ID");
    }

    /**
     * 属性 27 补充: 错误链追踪在标准化响应中的体现
     * 
     * **Validates: Requirements 8.1, 8.6**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 27: 协议错误标准化响应 - 错误链")
    void protocolErrorResponseContainsErrorChain(
            @ForAll("errorCodes") ErrorCode outerCode,
            @ForAll("errorCodes") ErrorCode innerCode,
            @ForAll @NotBlank String outerMsg,
            @ForAll @NotBlank String innerMsg) {
        
        FrameworkException inner = new FrameworkException(innerCode, innerMsg);
        FrameworkException outer = new FrameworkException(outerCode, outerMsg, inner);
        
        Map<String, Object> response = outer.toErrorResponse();
        
        assertNotNull(response.get("errorChain"), "响应应包含错误链");
        @SuppressWarnings("unchecked")
        var chain = (java.util.List<String>) response.get("errorChain");
        assertTrue(chain.size() >= 2, "错误链应至少包含两层");
        assertTrue(chain.get(0).contains(outerMsg), "错误链第一层应包含外层错误消息");
        assertTrue(chain.get(1).contains(innerMsg), "错误链第二层应包含内层错误消息");
    }

    // ==================== 属性 31: 错误码统一映射 ====================

    /**
     * 属性 31: 错误码统一映射
     * 
     * 对于任意协议特定的错误，应该能够映射到框架统一的错误码。
     * 
     * **Validates: Requirements 8.5**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 31: 错误码统一映射 - HTTP")
    void httpStatusMapsToErrorCode(@ForAll("httpStatusCodes") int httpStatus) {
        ErrorCode errorCode = ErrorCode.fromHttpStatus(httpStatus);
        
        assertNotNull(errorCode, "HTTP 状态码应能映射到 ErrorCode");
        // 4xx 应映射为客户端错误
        if (httpStatus >= 400 && httpStatus < 500) {
            assertTrue(errorCode.isClientError(), 
                    "HTTP " + httpStatus + " 应映射为客户端错误");
        }
        // 5xx 应映射为服务端错误或框架错误
        if (httpStatus >= 500) {
            assertTrue(errorCode.isServerError() || errorCode.isFrameworkError(),
                    "HTTP " + httpStatus + " 应映射为服务端或框架错误");
        }
    }

    /**
     * 属性 31 补充: gRPC 状态码映射
     * 
     * **Validates: Requirements 8.5**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 31: 错误码统一映射 - gRPC")
    void grpcStatusMapsToErrorCode(@ForAll @IntRange(min = 1, max = 16) int grpcStatus) {
        ErrorCode errorCode = ErrorCode.fromGrpcStatus(grpcStatus);
        assertNotNull(errorCode, "gRPC 状态码应能映射到 ErrorCode");
    }

    /**
     * 属性 31 补充: JSON-RPC 错误码映射
     * 
     * **Validates: Requirements 8.5**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 31: 错误码统一映射 - JSON-RPC")
    void jsonRpcCodeMapsToErrorCode(@ForAll("jsonRpcCodes") int jsonRpcCode) {
        ErrorCode errorCode = ErrorCode.fromJsonRpcCode(jsonRpcCode);
        assertNotNull(errorCode, "JSON-RPC 错误码应能映射到 ErrorCode");
    }

    /**
     * 属性 31 补充: ErrorCode 到 HTTP 状态码的往返映射一致性
     * 
     * **Validates: Requirements 8.5**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 31: 错误码统一映射 - HTTP 往返")
    void errorCodeToHttpRoundTrip(@ForAll("errorCodes") ErrorCode errorCode) {
        int httpStatus = errorCode.toHttpStatus();
        assertTrue(httpStatus >= 400, "映射的 HTTP 状态码应 >= 400");
        
        // 对于标准 HTTP 错误码（4xx/5xx），往返映射应保持一致
        if (errorCode.getCode() >= 400 && errorCode.getCode() < 600) {
            ErrorCode mapped = ErrorCode.fromHttpStatus(httpStatus);
            assertEquals(errorCode, mapped, 
                    "标准 HTTP 错误码的往返映射应保持一致");
        }
    }

    /**
     * 属性 31 补充: 所有 ErrorCode 都有有效的 JSON-RPC 映射
     * 
     * **Validates: Requirements 8.5**
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 31: 错误码统一映射 - JSON-RPC 输出")
    void errorCodeToJsonRpcMapping(@ForAll("errorCodes") ErrorCode errorCode) {
        int jsonRpcCode = errorCode.toJsonRpcCode();
        assertTrue(jsonRpcCode < 0, "JSON-RPC 错误码应为负数");
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<ErrorCode> errorCodes() {
        return Arbitraries.of(ErrorCode.values());
    }

    @Provide
    Arbitrary<Integer> httpStatusCodes() {
        return Arbitraries.of(400, 401, 403, 404, 408, 500, 501, 503);
    }

    @Provide
    Arbitrary<Integer> jsonRpcCodes() {
        return Arbitraries.of(-32700, -32600, -32601, -32602, -32603);
    }
}
