package com.framework.protocol.external.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.protocol.external.ExternalProtocolHandler;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 协议处理器
 * 
 * 支持文本和二进制消息
 * 实现双向通信
 * 
 * **验证需求: 2.2, 2.6**
 */
@Component
public class WebSocketProtocolHandler extends AbstractWebSocketHandler implements ExternalProtocolHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketProtocolHandler.class);
    
    private final WebSocketRequestProcessor requestProcessor;
    private final ObjectMapper objectMapper;
    
    // 存储活跃的 WebSocket 会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    public WebSocketProtocolHandler(WebSocketRequestProcessor requestProcessor) {
        this.requestProcessor = requestProcessor;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 连接建立时调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        logger.info("WebSocket 连接建立: sessionId={}, remoteAddress={}", 
                   sessionId, session.getRemoteAddress());
        
        // 发送欢迎消息
        Map<String, Object> welcomeMsg = new HashMap<>();
        welcomeMsg.put("type", "connection");
        welcomeMsg.put("status", "connected");
        welcomeMsg.put("sessionId", sessionId);
        welcomeMsg.put("timestamp", System.currentTimeMillis());
        
        sendTextMessage(session, objectMapper.writeValueAsString(welcomeMsg));
    }
    
    /**
     * 处理文本消息
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        logger.debug("收到文本消息: sessionId={}, payload={}", sessionId, payload);
        
        try {
            // 解析消息
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            
            // 构建外部请求
            ExternalRequest request = buildRequestFromMessage(messageData, "text");
            
            // 处理请求
            ExternalResponse response = handle(request);
            
            // 发送响应
            sendTextResponse(session, response);
            
        } catch (FrameworkException e) {
            logger.error("处理文本消息失败: {}", e.getMessage(), e);
            sendErrorMessage(session, e);
        } catch (Exception e) {
            logger.error("处理文本消息异常: {}", e.getMessage(), e);
            sendErrorMessage(session, new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    /**
     * 处理二进制消息
     */
    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String sessionId = session.getId();
        ByteBuffer payload = message.getPayload();
        
        logger.debug("收到二进制消息: sessionId={}, size={} bytes", sessionId, payload.remaining());
        
        try {
            // 将二进制数据转换为字节数组
            byte[] data = new byte[payload.remaining()];
            payload.get(data);
            
            // 尝试解析为 JSON（如果是 JSON 格式的二进制数据）
            Map<String, Object> messageData = null;
            try {
                messageData = objectMapper.readValue(data, Map.class);
            } catch (Exception e) {
                // 如果不是 JSON，创建一个包含原始数据的消息
                messageData = new HashMap<>();
                messageData.put("type", "binary");
                messageData.put("data", data);
            }
            
            // 构建外部请求
            ExternalRequest request = buildRequestFromMessage(messageData, "binary");
            request.getMetadata().put("binaryData", data);
            
            // 处理请求
            ExternalResponse response = handle(request);
            
            // 发送响应（二进制格式）
            sendBinaryResponse(session, response);
            
        } catch (FrameworkException e) {
            logger.error("处理二进制消息失败: {}", e.getMessage(), e);
            sendErrorMessage(session, e);
        } catch (Exception e) {
            logger.error("处理二进制消息异常: {}", e.getMessage(), e);
            sendErrorMessage(session, new FrameworkException(ErrorCode.INTERNAL_ERROR, e.getMessage(), e));
        }
    }
    
    /**
     * 连接关闭时调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        logger.info("WebSocket 连接关闭: sessionId={}, status={}", sessionId, status);
    }
    
    /**
     * 传输错误时调用
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        logger.error("WebSocket 传输错误: sessionId={}, error={}", sessionId, exception.getMessage(), exception);
        
        // 发送错误消息
        sendErrorMessage(session, new FrameworkException(ErrorCode.CONNECTION_ERROR, exception.getMessage(), exception));
        
        // 关闭会话
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
    
    @Override
    public ExternalResponse handle(ExternalRequest request) {
        return requestProcessor.process(request);
    }
    
    /**
     * 从消息数据构建外部请求
     */
    private ExternalRequest buildRequestFromMessage(Map<String, Object> messageData, String messageType) {
        ExternalRequest request = new ExternalRequest();
        request.setProtocol("WebSocket");
        
        // 提取服务和方法信息
        String service = (String) messageData.get("service");
        String method = (String) messageData.get("method");
        
        if (service == null || method == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, 
                "消息必须包含 'service' 和 'method' 字段");
        }
        
        request.setService(service);
        request.setMethod(method);
        
        // 提取请求体
        Object body = messageData.get("body");
        if (body != null) {
            request.setBody(body);
        }
        
        // 提取头信息
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) messageData.get("headers");
        if (headers != null) {
            request.setHeaders(headers);
        }
        
        // 添加元数据
        request.getMetadata().put("messageType", messageType);
        request.getMetadata().put("timestamp", System.currentTimeMillis());
        
        return request;
    }
    
    /**
     * 发送文本响应
     */
    private void sendTextResponse(WebSocketSession session, ExternalResponse response) throws IOException {
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("type", "response");
        responseData.put("statusCode", response.getStatusCode());
        responseData.put("body", response.getBody());
        responseData.put("headers", response.getHeaders());
        responseData.put("timestamp", System.currentTimeMillis());
        
        String jsonResponse = objectMapper.writeValueAsString(responseData);
        sendTextMessage(session, jsonResponse);
    }
    
    /**
     * 发送二进制响应
     */
    private void sendBinaryResponse(WebSocketSession session, ExternalResponse response) throws IOException {
        // 将响应转换为 JSON 字节数组
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("type", "response");
        responseData.put("statusCode", response.getStatusCode());
        responseData.put("body", response.getBody());
        responseData.put("headers", response.getHeaders());
        responseData.put("timestamp", System.currentTimeMillis());
        
        byte[] jsonBytes = objectMapper.writeValueAsBytes(responseData);
        sendBinaryMessage(session, jsonBytes);
    }
    
    /**
     * 发送错误消息
     */
    private void sendErrorMessage(WebSocketSession session, FrameworkException exception) {
        try {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("type", "error");
            errorData.put("error", true);
            errorData.put("code", exception.getErrorCode().getCode());
            errorData.put("message", exception.getMessage());
            errorData.put("timestamp", System.currentTimeMillis());
            
            String jsonError = objectMapper.writeValueAsString(errorData);
            sendTextMessage(session, jsonError);
            
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送文本消息（线程安全）
     */
    private synchronized void sendTextMessage(WebSocketSession session, String message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }
    
    /**
     * 发送二进制消息（线程安全）
     */
    private synchronized void sendBinaryMessage(WebSocketSession session, byte[] data) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new BinaryMessage(data));
        }
    }
    
    /**
     * 向指定会话发送消息
     */
    public void sendMessageToSession(String sessionId, String message) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            sendTextMessage(session, message);
        } else {
            throw new FrameworkException(ErrorCode.NOT_FOUND, "会话不存在或已关闭: " + sessionId);
        }
    }
    
    /**
     * 广播消息到所有连接的会话
     */
    public void broadcastMessage(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    sendTextMessage(session, message);
                }
            } catch (IOException e) {
                logger.error("广播消息失败: sessionId={}, error={}", 
                           session.getId(), e.getMessage(), e);
            }
        });
    }
    
    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * 关闭指定会话
     */
    public void closeSession(String sessionId) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            session.close(CloseStatus.NORMAL);
            sessions.remove(sessionId);
        }
    }
}
