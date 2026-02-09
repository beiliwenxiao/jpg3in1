package com.framework.protocol.adapter;

import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;

import java.util.List;

/**
 * 协议适配器接口
 * 
 * 负责外部协议和内部协议之间的转换，保持消息语义一致性。
 * 
 * 需求: 4.1, 4.2, 4.5
 */
public interface ProtocolAdapter {
    
    /**
     * 外部协议转内部协议
     * 
     * @param externalRequest 外部请求
     * @return 内部请求
     */
    InternalRequest transformRequest(ExternalRequest externalRequest);
    
    /**
     * 内部协议转外部协议
     * 
     * @param internalResponse 内部响应
     * @param originalRequest  原始外部请求（用于保持协议上下文）
     * @return 外部响应
     */
    ExternalResponse transformResponse(InternalResponse internalResponse, ExternalRequest originalRequest);
    
    /**
     * 内部协议转外部协议（无原始请求上下文时使用）
     * 
     * @param internalResponse 内部响应
     * @return 外部响应
     */
    ExternalResponse transformResponse(InternalResponse internalResponse);
    
    /**
     * 获取支持的外部协议类型列表
     * 
     * @return 协议类型列表
     */
    List<String> getSupportedProtocols();
    
    /**
     * 判断是否支持指定的外部协议
     * 
     * @param protocol 协议类型
     * @return 是否支持
     */
    boolean supportsProtocol(String protocol);
}
