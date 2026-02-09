package com.framework.registry;

import com.framework.model.ServiceInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * 服务注册中心接口
 * 
 * 管理服务的注册、注销、发现和健康检查。
 * 
 * 需求: 5.1, 5.2, 5.3, 5.4, 5.5
 */
public interface ServiceRegistry {

    /**
     * 注册服务
     * 
     * @param serviceInfo 服务信息（名称、地址、协议、语言等）
     * @throws com.framework.exception.FrameworkException 注册失败时
     */
    void register(ServiceInfo serviceInfo);

    /**
     * 注销服务
     * 
     * @param serviceId 服务 ID
     * @throws com.framework.exception.FrameworkException 注销失败时
     */
    void deregister(String serviceId);

    /**
     * 查询服务实例列表
     * 
     * @param serviceName 服务名称
     * @return 可用服务实例列表
     */
    List<ServiceInfo> discover(String serviceName);

    /**
     * 按版本查询服务实例
     * 
     * @param serviceName 服务名称
     * @param version 服务版本
     * @return 匹配版本的服务实例列表
     */
    List<ServiceInfo> discover(String serviceName, String version);

    /**
     * 获取服务健康状态
     * 
     * @param serviceId 服务 ID
     * @return 健康状态
     */
    HealthStatus getHealthStatus(String serviceId);

    /**
     * 更新服务健康状态
     * 
     * @param serviceId 服务 ID
     * @param status 新的健康状态
     */
    void updateHealthStatus(String serviceId, HealthStatus status);

    /**
     * 监听服务变化
     * 
     * @param serviceName 服务名称
     * @param listener 变化回调
     */
    void watch(String serviceName, Consumer<List<ServiceInfo>> listener);

    /**
     * 取消监听
     * 
     * @param serviceName 服务名称
     */
    void unwatch(String serviceName);

    /**
     * 启动服务注册中心客户端（开始心跳等）
     */
    void start();

    /**
     * 关闭服务注册中心客户端
     */
    void close();

    /**
     * 检查注册中心是否可用
     * 
     * @return 是否可用
     */
    boolean isAvailable();
}
