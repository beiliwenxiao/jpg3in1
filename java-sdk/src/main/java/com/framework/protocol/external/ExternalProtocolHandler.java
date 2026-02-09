package com.framework.protocol.external;

import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;

/**
 * 外部协议处理器接口
 * 
 * 定义外部协议处理的统一接口
 */
public interface ExternalProtocolHandler {
    
    /**
     * 处理外部请求
     * 
     * @param request 外部请求
     * @return 外部响应
     */
    ExternalResponse handle(ExternalRequest request);
}
