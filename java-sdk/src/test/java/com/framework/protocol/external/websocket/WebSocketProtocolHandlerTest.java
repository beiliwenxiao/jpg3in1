package com.framework.protocol.external.websocket;

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
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WebSocket 协议处理器单元测试
 * 
 * 测试文本和二进制消息处理
 * 测试双向通信
 * 测试错误处理
 * 
 * **验证需求: 2.2, 2.6**
 */
@ExtendWith(MockitoExtension.class)
class WebSocketProtocolHandlerTest {
    
    @Mock
    private WebSocketRequestProcessor requestProcessor;
    
    @Mock
    private WebSocketSession session;
    
    private WebSocketProtocolHandler handler;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        handler = new WebSocketProtocolHandler(requestProcessor);
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testConnectionEstablished() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        // 执行
        handler.afterConnectionEstablished(session);
        
        // 验证：应该发送欢迎消息
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        assertEquals(1, handler.getActiveSessionCount());
    }
    
    @Test
    void testHandleTextMessage() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("service", "userService");
        messageData.put("method", "getUser");
        messageData.put("body", Map.of("userId", "123"));
        
        String jsonMessage = objectMapper.writeValueAsString(messageData);
        TextMessage textMessage = new TextMessage(jsonMessage);
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody(Map.of("userId", "123", "name", "Test User"));
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        handler.handleTextMessage(session, textMessage);
        
        // 验证：应该处理请求并发送响应
        verify(requestProcessor, times(1)).process(any(ExternalRequest.class));
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
    
    @Test
    void testHandleBinaryMessage() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("service", "dataService");
        messageData.put("method", "processData");
        messageData.put("body", Map.of("data", "binary data"));
        
        byte[] jsonBytes = objectMapper.writeValueAsBytes(messageData);
        BinaryMessage binaryMessage = new BinaryMessage(ByteBuffer.wrap(jsonBytes));
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody(Map.of("result", "processed"));
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        handler.handleBinaryMessage(session, binaryMessage);
        
        // 验证：应该处理请求并发送响应
        verify(requestProcessor, times(1)).process(any(ExternalRequest.class));
        verify(session, times(1)).sendMessage(any(BinaryMessage.class));
    }
    
    @Test
    void testHandleTextMessageWithMissingFields() throws Exception {
        // 准备：消息缺少必需字段
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("service", "userService");
        // 缺少 method 字段
        
        String jsonMessage = objectMapper.writeValueAsString(messageData);
        TextMessage textMessage = new TextMessage(jsonMessage);
        
        // 执行
        handler.handleTextMessage(session, textMessage);
        
        // 验证：应该发送错误消息
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        verify(requestProcessor, never()).process(any(ExternalRequest.class));
    }
    
    @Test
    void testHandleTextMessageWithProcessingError() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("service", "userService");
        messageData.put("method", "getUser");
        messageData.put("body", Map.of("userId", "123"));
        
        String jsonMessage = objectMapper.writeValueAsString(messageData);
        TextMessage textMessage = new TextMessage(jsonMessage);
        
        when(requestProcessor.process(any(ExternalRequest.class)))
            .thenThrow(new FrameworkException(ErrorCode.INTERNAL_ERROR, "处理失败"));
        
        // 执行
        handler.handleTextMessage(session, textMessage);
        
        // 验证：应该发送错误消息
        verify(requestProcessor, times(1)).process(any(ExternalRequest.class));
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
    
    @Test
    void testConnectionClosed() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.getActiveSessionCount());
        
        // 执行
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        
        // 验证：会话应该被移除
        assertEquals(0, handler.getActiveSessionCount());
    }
    
    @Test
    void testTransportError() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        Exception transportError = new RuntimeException("网络错误");
        
        // 执行
        handler.handleTransportError(session, transportError);
        
        // 验证：应该发送错误消息并关闭会话
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        verify(session, times(1)).close(CloseStatus.SERVER_ERROR);
    }
    
    @Test
    void testSendMessageToSession() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        handler.afterConnectionEstablished(session);
        
        // 执行
        handler.sendMessageToSession("test-session-123", "Hello, WebSocket!");
        
        // 验证：应该发送消息（包括欢迎消息）
        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }
    
    @Test
    void testSendMessageToNonExistentSession() {
        // 执行并验证：应该抛出异常
        assertThrows(FrameworkException.class, () -> {
            handler.sendMessageToSession("non-existent-session", "Hello");
        });
    }
    
    @Test
    void testBroadcastMessage() throws Exception {
        // 准备：创建多个会话
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);
        
        when(session1.getId()).thenReturn("session-1");
        when(session1.isOpen()).thenReturn(true);
        when(session2.getId()).thenReturn("session-2");
        when(session2.isOpen()).thenReturn(true);
        
        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);
        
        // 执行
        handler.broadcastMessage("Broadcast message");
        
        // 验证：所有会话都应该收到消息（包括欢迎消息）
        verify(session1, times(2)).sendMessage(any(TextMessage.class));
        verify(session2, times(2)).sendMessage(any(TextMessage.class));
    }
    
    @Test
    void testCloseSession() throws Exception {
        // 准备
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.getActiveSessionCount());
        
        // 执行
        handler.closeSession("test-session-123");
        
        // 验证：会话应该被关闭并移除
        verify(session, times(1)).close(CloseStatus.NORMAL);
        assertEquals(0, handler.getActiveSessionCount());
    }
    
    @Test
    void testHandleBinaryMessageWithRawData() throws Exception {
        // 准备：发送非 JSON 格式的二进制数据
        when(session.getId()).thenReturn("test-session-123");
        when(session.isOpen()).thenReturn(true);
        
        byte[] rawData = new byte[]{0x01, 0x02, 0x03, 0x04};
        BinaryMessage binaryMessage = new BinaryMessage(ByteBuffer.wrap(rawData));
        
        ExternalResponse mockResponse = new ExternalResponse();
        mockResponse.setStatusCode(200);
        mockResponse.setBody(Map.of("result", "processed"));
        
        when(requestProcessor.process(any(ExternalRequest.class))).thenReturn(mockResponse);
        
        // 执行
        handler.handleBinaryMessage(session, binaryMessage);
        
        // 验证：应该处理请求（即使不是 JSON 格式）
        verify(requestProcessor, times(1)).process(any(ExternalRequest.class));
        verify(session, times(1)).sendMessage(any(BinaryMessage.class));
    }
    
    @Test
    void testHandleExternalRequest() {
        // 准备
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("WebSocket");
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
}
