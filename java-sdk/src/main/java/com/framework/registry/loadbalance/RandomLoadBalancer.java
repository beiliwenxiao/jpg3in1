package com.framework.registry.loadbalance;

import com.framework.model.ServiceInfo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡器
 * 
 * 随机选择一个服务实例。
 * 
 * 需求: 5.6
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public ServiceInfo select(List<ServiceInfo> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        return instances.get(index);
    }

    @Override
    public String getStrategy() {
        return "random";
    }
}
