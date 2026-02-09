package com.framework.registry.loadbalance;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;

/**
 * 负载均衡器工厂
 * 
 * 根据策略名称创建对应的负载均衡器实例。
 * 
 * 需求: 5.6
 */
public class LoadBalancerFactory {

    public static final String ROUND_ROBIN = "round_robin";
    public static final String RANDOM = "random";
    public static final String LEAST_CONNECTIONS = "least_connections";

    /**
     * 根据策略名称创建负载均衡器
     * 
     * @param strategy 策略名称
     * @return 负载均衡器实例
     */
    public static LoadBalancer create(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            strategy = ROUND_ROBIN;
        }

        return switch (strategy.toLowerCase()) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer();
            case RANDOM -> new RandomLoadBalancer();
            case LEAST_CONNECTIONS -> new LeastConnectionsLoadBalancer();
            default -> throw new FrameworkException(ErrorCode.BAD_REQUEST,
                    "不支持的负载均衡策略: " + strategy +
                    "，支持的策略: round_robin, random, least_connections");
        };
    }
}
