package com.framework.protocol.external.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * 
 * 配置 WebSocket 端点和处理器
 * 
 * **验证需求: 2.2, 2.6**
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final WebSocketProtocolHandler webSocketProtocolHandler;
    
    public WebSocketConfig(WebSocketProtocolHandler webSocketProtocolHandler) {
        this.webSocketProtocolHandler = webSocketProtocolHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 WebSocket 处理器，支持所有来源（生产环境应该限制）
        registry.addHandler(webSocketProtocolHandler, "/ws/**")
                .setAllowedOrigins("*");
    }
}
