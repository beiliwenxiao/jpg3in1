package com.framework.protocol.router;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import com.framework.protocol.model.InternalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 默认消息路由器实现
 * 
 * 根据服务名称和方法名路由消息，支持基于内容的路由规则，
 * 处理服务不可用的情况。
 * 
 * 路由优先级:
 * 1. 自定义路由规则（按优先级排序）
 * 2. 服务名称精确匹配
 * 
 * 需求: 4.3, 4.4, 4.6
 */
public class DefaultMessageRouter implements MessageRouter {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultMessageRouter.class);
    
    // 路由规则列表（线程安全）
    private final CopyOnWriteArrayList<RoutingRule> rules = new CopyOnWriteArrayList<>();
    
    // 服务端点注册表: serviceName -> List<ServiceEndpoint>
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ServiceEndpoint>> endpointRegistry = 
        new ConcurrentHashMap<>();
    
    // 服务端点索引: serviceId -> ServiceEndpoint
    private final ConcurrentHashMap<String, ServiceEndpoint> endpointIndex = new ConcurrentHashMap<>();
    
    @Override
    public ServiceEndpoint route(InternalRequest request) {
        if (request == null) {
            throw new FrameworkException(ErrorCode.ROUTING_ERROR, "请求不能为空");
        }
        
        String service = request.getService();
        String method = request.getMethod();
        
        logger.debug("路由请求: service={}, method={}", service, method);
        
        // 1. 先尝试自定义路由规则（按优先级从高到低）
        ServiceEndpoint ruleEndpoint = routeByRules(request);
        if (ruleEndpoint != null) {
            logger.debug("通过路由规则匹配: rule matched, endpoint={}", ruleEndpoint);
            return ruleEndpoint;
        }
        
        // 2. 按服务名称精确匹配
        if (service == null || service.isBlank()) {
            throw new FrameworkException(ErrorCode.ROUTING_ERROR, "服务名称不能为空");
        }
        
        ServiceEndpoint endpoint = routeByServiceName(service);
        if (endpoint != null) {
            logger.debug("通过服务名称匹配: service={}, endpoint={}", service, endpoint);
            return endpoint;
        }
        
        // 3. 服务不可用
        logger.warn("服务不可用: service={}, method={}", service, method);
        throw new FrameworkException(ErrorCode.SERVICE_UNAVAILABLE, 
            "服务不可用: " + service, 
            "没有找到服务 '" + service + "' 的可用实例");
    }
    
    @Override
    public void registerRule(RoutingRule rule) {
        if (rule == null || rule.getName() == null) {
            throw new IllegalArgumentException("路由规则和规则名称不能为空");
        }
        
        // 移除同名旧规则
        rules.removeIf(r -> rule.getName().equals(r.getName()));
        rules.add(rule);
        
        // 按优先级排序（高优先级在前）
        rules.sort(Comparator.comparingInt(RoutingRule::getPriority).reversed());
        
        logger.info("注册路由规则: name={}, priority={}", rule.getName(), rule.getPriority());
    }
    
    @Override
    public void removeRule(String ruleName) {
        if (ruleName == null) return;
        boolean removed = rules.removeIf(r -> ruleName.equals(r.getName()));
        if (removed) {
            logger.info("移除路由规则: name={}", ruleName);
        }
    }
    
    @Override
    public void updateRoutingTable(List<ServiceInfo> services) {
        if (services == null) return;
        
        logger.info("更新路由表: {} 个服务", services.size());
        
        // 清空现有端点
        endpointRegistry.clear();
        endpointIndex.clear();
        
        // 重新注册所有服务
        for (ServiceInfo service : services) {
            ServiceEndpoint endpoint = convertToEndpoint(service);
            registerEndpoint(endpoint);
        }
    }
    
    @Override
    public void registerEndpoint(ServiceEndpoint endpoint) {
        if (endpoint == null || endpoint.getServiceName() == null) {
            throw new IllegalArgumentException("服务端点和服务名称不能为空");
        }
        
        String serviceName = endpoint.getServiceName();
        
        endpointRegistry.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<ServiceEndpoint> endpoints = endpointRegistry.get(serviceName);
        
        // 避免重复注册
        if (endpoint.getServiceId() != null) {
            endpoints.removeIf(e -> endpoint.getServiceId().equals(e.getServiceId()));
            endpointIndex.put(endpoint.getServiceId(), endpoint);
        }
        
        endpoints.add(endpoint);
        
        logger.debug("注册服务端点: serviceName={}, serviceId={}, address={}:{}", 
                    serviceName, endpoint.getServiceId(), endpoint.getAddress(), endpoint.getPort());
    }
    
    @Override
    public void removeEndpoint(String serviceId) {
        if (serviceId == null) return;
        
        ServiceEndpoint removed = endpointIndex.remove(serviceId);
        if (removed != null) {
            String serviceName = removed.getServiceName();
            CopyOnWriteArrayList<ServiceEndpoint> endpoints = endpointRegistry.get(serviceName);
            if (endpoints != null) {
                endpoints.removeIf(e -> serviceId.equals(e.getServiceId()));
                if (endpoints.isEmpty()) {
                    endpointRegistry.remove(serviceName);
                }
            }
            logger.info("注销服务端点: serviceId={}, serviceName={}", serviceId, serviceName);
        }
    }
    
    @Override
    public List<RoutingRule> getRules() {
        return Collections.unmodifiableList(new ArrayList<>(rules));
    }
    
    @Override
    public List<ServiceEndpoint> getEndpoints(String serviceName) {
        if (serviceName == null) return Collections.emptyList();
        CopyOnWriteArrayList<ServiceEndpoint> endpoints = endpointRegistry.get(serviceName);
        if (endpoints == null || endpoints.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(endpoints));
    }
    
    // ========== 内部方法 ==========
    
    /**
     * 通过路由规则匹配
     */
    private ServiceEndpoint routeByRules(InternalRequest request) {
        for (RoutingRule rule : rules) {
            if (rule.matches(request)) {
                String targetServiceName = rule.resolveTarget(request);
                if (targetServiceName != null) {
                    // 先尝试按 serviceId 查找
                    ServiceEndpoint byId = endpointIndex.get(targetServiceName);
                    if (byId != null) return byId;
                    
                    // 再按 serviceName 查找
                    ServiceEndpoint byName = routeByServiceName(targetServiceName);
                    if (byName != null) return byName;
                    
                    logger.warn("路由规则 '{}' 匹配但目标服务不可用: target={}", 
                              rule.getName(), targetServiceName);
                }
            }
        }
        return null;
    }
    
    /**
     * 通过服务名称匹配（简单轮询选择）
     */
    private ServiceEndpoint routeByServiceName(String serviceName) {
        CopyOnWriteArrayList<ServiceEndpoint> endpoints = endpointRegistry.get(serviceName);
        if (endpoints == null || endpoints.isEmpty()) {
            return null;
        }
        
        // 简单轮询：使用 hashCode 取模选择（后续可由负载均衡器替代）
        if (endpoints.size() == 1) {
            return endpoints.get(0);
        }
        
        int index = Math.abs((int) (System.nanoTime() % endpoints.size()));
        return endpoints.get(index);
    }
    
    /**
     * 将 ServiceInfo 转换为 ServiceEndpoint
     */
    private ServiceEndpoint convertToEndpoint(ServiceInfo serviceInfo) {
        ServiceEndpoint endpoint = new ServiceEndpoint();
        endpoint.setServiceId(serviceInfo.getId());
        endpoint.setServiceName(serviceInfo.getName());
        endpoint.setAddress(serviceInfo.getAddress());
        endpoint.setPort(serviceInfo.getPort());
        
        // 选择第一个可用的内部协议
        if (serviceInfo.getProtocols() != null && !serviceInfo.getProtocols().isEmpty()) {
            endpoint.setProtocol(serviceInfo.getProtocols().get(0));
        } else {
            endpoint.setProtocol("gRPC"); // 默认使用 gRPC
        }
        
        if (serviceInfo.getMetadata() != null) {
            endpoint.setMetadata(new HashMap<>(serviceInfo.getMetadata()));
        }
        
        return endpoint;
    }
}
