package com.framework.protocol.external.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JSON-RPC 2.0 协议处理器单元测试
 * 
 * 测试 JSON-RPC 2.0 规范的请求解析和响应生成
 * 测试单个请求和批量请求
 * 测试错误处理
 * 
 * **验证需求: 2.3**
 */
@ExtendWith(MockitoExtension.class)
class JsonRpcProtocolHandlerTest {
    
    @Mock
    private JsonRpcRequestProcessor requestProcessor;
    
    private JsonRpcProtocolHandler handler;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        handler = new JsonRpcProtocolHandler(requestProcessor);
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testHandleValidJsonRpcRequest() throws Exception {
        // 准备：有效的 JSON-RPC 2.0 请求
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "userService.getUser");
        request.put("params", Map.of("userId", "123"));
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody(Map.of("userId", "123", "name", "Test User"));
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertEquals("2.0", responseBody.get("jsonrpc"));
        assertEquals(1, responseBody.get("id"));
        assertNotNull(responseBody.get("result"));
        assertNull(responseBody.get("error"));
        
        verify(requestProcessor, times(1)).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleJsonRpcRequestWithStringId() throws Exception {
        // 准备：使用字符串 ID 的请求
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "testService.testMethod");
        request.put("params", List.of("param1", "param2"));
        request.put("id", "request-123");
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("success");
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertEquals("request-123", responseBody.get("id"));
        assertEquals("success", responseBody.get("result"));
    }
    
    @Test
    void testHandleJsonRpcNotification() throws Exception {
        // 准备：通知（没有 ID 的请求）
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "notificationService.notify");
        request.put("params", Map.of("message", "Hello"));
        // 没有 id 字段
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：通知不应该有响应
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(requestProcessor, times(1)).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleJsonRpcRequestWithTraceInfo() throws Exception {
        // 准备：包含追踪信息的请求
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "service.method");
        request.put("params", Map.of());
        request.put("id", 1);
        request.put("trace_id", "trace-123");
        request.put("span_id", "span-456");
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("result");
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：响应应该包含追踪信息
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertEquals("trace-123", responseBody.get("trace_id"));
        assertEquals("span-456", responseBody.get("span_id"));
    }
    
    @Test
    void testHandleInvalidJsonRpcVersion() throws Exception {
        // 准备：错误的 JSON-RPC 版本
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "1.0");
        request.put("method", "test");
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回错误
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody.get("error"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertEquals(-32600, error.get("code")); // INVALID_REQUEST
        
        verify(requestProcessor, never()).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleMissingMethod() throws Exception {
        // 准备：缺少 method 字段
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("params", Map.of());
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回错误
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody.get("error"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertEquals(-32600, error.get("code")); // INVALID_REQUEST
    }
    
    @Test
    void testHandleInvalidParams() throws Exception {
        // 准备：params 不是数组或对象
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "test");
        request.put("params", "invalid");
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回错误
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody.get("error"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertEquals(-32600, error.get("code")); // INVALID_REQUEST
    }
    
    @Test
    void testHandleParseError() {
        // 准备：无效的 JSON
        String requestBody = "{invalid json}";
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回解析错误
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody.get("error"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertEquals(-32700, error.get("code")); // PARSE_ERROR
    }
    
    @Test
    void testHandleProcessingError() throws Exception {
        // 准备
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "service.method");
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        when(requestProcessor.process(any(ExternalRequest.class)))
            .thenThrow(new FrameworkException(ErrorCode.NOT_FOUND, "方法不存在"));
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回方法不存在错误
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody.get("error"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertEquals(-32601, error.get("code")); // METHOD_NOT_FOUND
    }
    
    @Test
    void testHandleBatchRequest() throws Exception {
        // 准备：批量请求
        Map<String, Object> request1 = new HashMap<>();
        request1.put("jsonrpc", "2.0");
        request1.put("method", "service1.method1");
        request1.put("id", 1);
        
        Map<String, Object> request2 = new HashMap<>();
        request2.put("jsonrpc", "2.0");
        request2.put("method", "service2.method2");
        request2.put("id", 2);
        
        String requestBody = objectMapper.writeValueAsString(List.of(request1, request2));
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("result");
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回批量响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responseBody = (List<Map<String, Object>>) response.getBody();
        assertNotNull(responseBody);
        assertEquals(2, responseBody.size());
        
        verify(requestProcessor, times(2)).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleEmptyBatchRequest() throws Exception {
        // 准备：空批量请求
        String requestBody = "[]";
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：应该返回错误
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertNotNull(responseBody.get("error"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) responseBody.get("error");
        assertEquals(-32600, error.get("code")); // INVALID_REQUEST
    }
    
    @Test
    void testHandleBatchRequestWithNotifications() throws Exception {
        // 准备：批量请求包含通知
        Map<String, Object> request1 = new HashMap<>();
        request1.put("jsonrpc", "2.0");
        request1.put("method", "service1.method1");
        request1.put("id", 1);
        
        Map<String, Object> notification = new HashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "service2.notify");
        // 没有 id
        
        String requestBody = objectMapper.writeValueAsString(List.of(request1, notification));
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("result");
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：只有非通知请求有响应
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responseBody = (List<Map<String, Object>>) response.getBody();
        assertNotNull(responseBody);
        assertEquals(1, responseBody.size()); // 只有一个响应
        
        verify(requestProcessor, times(2)).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleBatchRequestAllNotifications() throws Exception {
        // 准备：批量请求全是通知
        Map<String, Object> notification1 = new HashMap<>();
        notification1.put("jsonrpc", "2.0");
        notification1.put("method", "service1.notify");
        
        Map<String, Object> notification2 = new HashMap<>();
        notification2.put("jsonrpc", "2.0");
        notification2.put("method", "service2.notify");
        
        String requestBody = objectMapper.writeValueAsString(List.of(notification1, notification2));
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        ResponseEntity<Object> response = handler.handleJsonRpc(requestBody);
        
        // 验证：全是通知应该返回 204
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(requestProcessor, times(2)).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleExternalRequest() {
        // 准备
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("JSON-RPC");
        request.setService("testService");
        request.setMethod("testMethod");
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("test response");
        
        when(requestProcessor.process(request)).thenReturn(mockResponse);
        
        // 执行
        ExternalResponse response = handler.handle(request);
        
        // 验证
        assertNotNull(response);
        assertEquals(200, response.getStatusCode());
        assertEquals("test response", response.getBody());
        verify(requestProcessor, times(1)).process(request);
    }
    
    @Test
    void testMethodNameParsing() throws Exception {
        // 准备：测试不同格式的方法名
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "userService.getUser"); // service.method 格式
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("result");
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        handler.handleJsonRpc(requestBody);
        
        // 验证：应该正确解析服务名和方法名
        verify(requestProcessor, times(1)).process(argThat(req -> 
            "userService".equals(req.getService()) && "getUser".equals(req.getMethod())
        ));
    }
    
    @Test
    void testMethodNameWithoutService() throws Exception {
        // 准备：只有方法名，没有服务名
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "simpleMethod");
        request.put("id", 1);
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody("result");
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        handler.handleJsonRpc(requestBody);
        
        // 验证：应该使用默认服务名
        verify(requestProcessor, times(1)).process(argThat(req -> 
            "default".equals(req.getService()) && "simpleMethod".equals(req.getMethod())
        ));
    }
}
