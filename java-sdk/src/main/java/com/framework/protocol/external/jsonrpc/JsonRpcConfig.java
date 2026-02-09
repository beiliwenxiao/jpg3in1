package com.framework.protocol.external.jsonrpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.framework.protocol.adapter.ProtocolAdapter;

/**
 * JSON-RPC 2.0 配置类
 * 
 * 配置 JSON-RPC 协议处理器相关的 Bean
 */
@Configuration
public class JsonRpcConfig {
    
    /**
     * 创建 JSON-RPC 请求处理器
     */
    @Bean
    public JsonRpcRequestProcessor jsonRpcRequestProcessor(ProtocolAdapter protocolAdapter) {
        return new JsonRpcRequestProcessor(protocolAdapter);
    }
    
    /**
     * 创建 JSON-RPC 协议处理器
     */
    @Bean
    public JsonRpcProtocolHandler jsonRpcProtocolHandler(JsonRpcRequestProcessor requestProcessor) {
        return new JsonRpcProtocolHandler(requestProcessor);
    }
}
