package com.framework.registry.loadbalance;

import com.framework.model.ServiceInfo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器
 * 
 * 按顺序依次选择服务实例，循环往复。
 * 
 * 需求: 5.6
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public ServiceInfo select(List<ServiceInfo> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        int index = Math.abs(counter.getAndIncrement() % instances.size());
        return instances.get(index);
    }

    @Override
    public String getStrategy() {
        return "round_robin";
    }
}
