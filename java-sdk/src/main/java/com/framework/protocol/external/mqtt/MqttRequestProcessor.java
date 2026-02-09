package com.framework.protocol.external.mqtt;

import com.framework.protocol.adapter.ProtocolAdapter;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MQTT 请求处理器
 * 
 * 负责处理 MQTT 消息并协调协议转换
 */
@Component
public class MqttRequestProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MqttRequestProcessor.class);
    
    private final ProtocolAdapter protocolAdapter;
    
    public MqttRequestProcessor(ProtocolAdapter protocolAdapter) {
        this.protocolAdapter = protocolAdapter;
    }
    
    /**
     * 处理外部请求
     */
    public ExternalResponse process(ExternalRequest externalRequest) {
        logger.debug("处理 MQTT 请求: service={}, method={}, topic={}", 
                    externalRequest.getService(), 
                    externalRequest.getMethod(),
                    externalRequest.getMetadata().get("topic"));
        
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
        response.setPayload("MQTT message processed".getBytes());
        return response;
    }
}
