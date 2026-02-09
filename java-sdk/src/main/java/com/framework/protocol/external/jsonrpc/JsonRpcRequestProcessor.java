package com.framework.protocol.external.jsonrpc;

import com.framework.protocol.adapter.ProtocolAdapter;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JSON-RPC 请求处理器
 * 
 * 负责处理 JSON-RPC 请求并协调协议转换
 */
@Component
public class JsonRpcRequestProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestProcessor.class);
    
    private final ProtocolAdapter protocolAdapter;
    
    public JsonRpcRequestProcessor(ProtocolAdapter protocolAdapter) {
        this.protocolAdapter = protocolAdapter;
    }
    
    /**
     * 处理外部请求
     */
    public ExternalResponse process(ExternalRequest externalRequest) {
        logger.debug("处理 JSON-RPC 请求: service={}, method={}", 
                    externalRequest.getService(), externalRequest.getMethod());
        
        // 转换为内部请求
        InternalRequest internalRequest = protocolAdapter.transformRequest(externalRequest);
        
        // 路由并执行请求（这里暂时返回模拟响应）
        InternalResponse internalResponse = executeInternalRequest(internalRequest);
        
        // 转换为外部响应
        ExternalResponse externalResponse = protocolAdapter.transformResponse(internalResponse);
        
        return externalResponse;
    }
    
    /**
     * 执行内部请求（暂时返回模拟响应）
     */
    private InternalResponse executeInternalRequest(InternalRequest request) {
        // TODO: 实际实现将通过消息路由器转发到目标服务
        InternalResponse response = new InternalResponse();
        response.setSuccess(true);
        response.setPayload("Mock JSON-RPC response".getBytes());
        return response;
    }
}
