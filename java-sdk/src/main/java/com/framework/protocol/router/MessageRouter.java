package com.framework.protocol.router;

import com.framework.model.ServiceInfo;
import com.framework.protocol.model.InternalRequest;

import java.util.List;

/**
 * 消息路由器接口
 * 
 * 根据服务名称、方法名和路由规则确定目标服务端点。
 * 
 * 需求: 4.3, 4.4, 4.6
 */
public interface MessageRouter {
    
    /**
     * 路由消息到目标服务端点
     * 
     * @param request 内部请求
     * @return 目标服务端点
     * @throws com.framework.exception.FrameworkException 当目标服务不可用时
     */
    ServiceEndpoint route(InternalRequest request);
    
    /**
     * 注册路由规则
     * 
     * @param rule 路由规则
     */
    void registerRule(RoutingRule rule);
    
    /**
     * 移除路由规则
     * 
     * @param ruleName 规则名称
     */
    void removeRule(String ruleName);
    
    /**
     * 更新路由表（从服务注册中心同步）
     * 
     * @param services 可用服务列表
     */
    void updateRoutingTable(List<ServiceInfo> services);
    
    /**
     * 注册服务端点
     * 
     * @param endpoint 服务端点
     */
    void registerEndpoint(ServiceEndpoint endpoint);
    
    /**
     * 注销服务端点
     * 
     * @param serviceId 服务 ID
     */
    void removeEndpoint(String serviceId);
    
    /**
     * 获取所有已注册的路由规则
     * 
     * @return 路由规则列表
     */
    List<RoutingRule> getRules();
    
    /**
     * 获取指定服务名称的所有可用端点
     * 
     * @param serviceName 服务名称
     * @return 端点列表
     */
    List<ServiceEndpoint> getEndpoints(String serviceName);
}
