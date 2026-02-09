package com.framework.protocol.router;

import com.framework.protocol.model.InternalRequest;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 路由规则
 * 
 * 定义消息匹配条件和目标服务的映射关系。
 * 支持基于服务名称/方法名的精确匹配，以及基于内容的自定义匹配。
 * 
 * 需求: 4.3, 4.6
 */
public class RoutingRule {
    
    private String name;
    private Predicate<InternalRequest> matcher;
    private Function<InternalRequest, String> targetResolver;
    private int priority;  // 数值越大优先级越高
    
    public RoutingRule() {
        this.priority = 0;
    }
    
    public RoutingRule(String name, Predicate<InternalRequest> matcher, 
                       Function<InternalRequest, String> targetResolver, int priority) {
        this.name = name;
        this.matcher = matcher;
        this.targetResolver = targetResolver;
        this.priority = priority;
    }
    
    /**
     * 创建基于服务名称的精确匹配规则
     */
    public static RoutingRule forService(String serviceName, String targetServiceId) {
        return new RoutingRule(
            "service:" + serviceName,
            req -> serviceName.equals(req.getService()),
            req -> targetServiceId,
            10
        );
    }
    
    /**
     * 创建基于服务名称和方法名的精确匹配规则
     */
    public static RoutingRule forServiceMethod(String serviceName, String methodName, String targetServiceId) {
        return new RoutingRule(
            "service:" + serviceName + "/method:" + methodName,
            req -> serviceName.equals(req.getService()) && methodName.equals(req.getMethod()),
            req -> targetServiceId,
            20  // 更精确的规则优先级更高
        );
    }
    
    /**
     * 创建基于内容的自定义匹配规则
     */
    public static RoutingRule forContent(String name, Predicate<InternalRequest> matcher,
                                          Function<InternalRequest, String> targetResolver, int priority) {
        return new RoutingRule(name, matcher, targetResolver, priority);
    }
    
    /**
     * 检查请求是否匹配此规则
     */
    public boolean matches(InternalRequest request) {
        return matcher != null && matcher.test(request);
    }
    
    /**
     * 解析目标服务名称
     */
    public String resolveTarget(InternalRequest request) {
        return targetResolver != null ? targetResolver.apply(request) : null;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public Predicate<InternalRequest> getMatcher() { return matcher; }
    public void setMatcher(Predicate<InternalRequest> matcher) { this.matcher = matcher; }
    
    public Function<InternalRequest, String> getTargetResolver() { return targetResolver; }
    public void setTargetResolver(Function<InternalRequest, String> targetResolver) { this.targetResolver = targetResolver; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    @Override
    public String toString() {
        return "RoutingRule{name='" + name + "', priority=" + priority + '}';
    }
}
