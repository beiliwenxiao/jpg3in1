package com.framework.registry.loadbalance;

import com.framework.model.ServiceInfo;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最少连接负载均衡器
 * 
 * 选择当前活跃连接数最少的服务实例。
 * 连接数相同时选择第一个匹配的实例。
 * 
 * 需求: 5.6
 */
public class LeastConnectionsLoadBalancer implements LoadBalancer {

    /** 活跃连接计数: serviceId -> 连接数 */
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    @Override
    public ServiceInfo select(List<ServiceInfo> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        ServiceInfo selected = null;
        int minConnections = Integer.MAX_VALUE;

        for (ServiceInfo instance : instances) {
            int connections = getConnectionCount(instance.getId());
            if (connections < minConnections) {
                minConnections = connections;
                selected = instance;
            }
        }

        // 增加选中实例的连接计数
        if (selected != null) {
            connectionCounts.computeIfAbsent(selected.getId(), k -> new AtomicInteger(0))
                    .incrementAndGet();
        }

        return selected;
    }

    @Override
    public void recordCompletion(String serviceId) {
        AtomicInteger count = connectionCounts.get(serviceId);
        if (count != null) {
            count.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    @Override
    public String getStrategy() {
        return "least_connections";
    }

    /**
     * 获取指定服务实例的当前连接数
     */
    public int getConnectionCount(String serviceId) {
        AtomicInteger count = connectionCounts.get(serviceId);
        return count != null ? count.get() : 0;
    }

    /**
     * 重置所有连接计数
     */
    public void reset() {
        connectionCounts.clear();
    }
}
