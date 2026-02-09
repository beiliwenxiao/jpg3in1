package com.framework.registry.loadbalance;

import com.framework.model.ServiceInfo;

import java.util.List;

/**
 * 负载均衡器接口
 * 
 * 从可用服务实例列表中选择一个目标实例。
 * 
 * 需求: 5.6
 */
public interface LoadBalancer {

    /**
     * 从服务实例列表中选择一个实例
     * 
     * @param instances 可用服务实例列表
     * @return 选中的服务实例，列表为空时返回 null
     */
    ServiceInfo select(List<ServiceInfo> instances);

    /**
     * 获取负载均衡策略名称
     * 
     * @return 策略名称
     */
    String getStrategy();

    /**
     * 记录请求完成（用于最少连接等策略）
     * 
     * @param serviceId 服务实例 ID
     */
    default void recordCompletion(String serviceId) {
        // 默认空实现，只有需要跟踪连接数的策略才需要覆盖
    }
}
