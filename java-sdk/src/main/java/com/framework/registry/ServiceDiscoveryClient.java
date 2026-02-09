package com.framework.registry;

import com.framework.model.ServiceInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * 服务发现客户端接口
 * 
 * 在 ServiceRegistry 基础上提供更高级的服务发现功能：
 * - 本地缓存（减少 etcd 查询）
 * - 服务变化监听与自动刷新
 * - 服务版本管理
 * 
 * 需求: 5.3, 5.5
 */
public interface ServiceDiscoveryClient {

    /**
     * 查询可用服务实例（优先使用缓存）
     * 
     * @param serviceName 服务名称
     * @return 可用服务实例列表
     */
    List<ServiceInfo> getInstances(String serviceName);

    /**
     * 按版本查询服务实例
     * 
     * @param serviceName 服务名称
     * @param version 服务版本
     * @return 匹配版本的服务实例列表
     */
    List<ServiceInfo> getInstances(String serviceName, String version);

    /**
     * 获取所有已知的服务版本
     * 
     * @param serviceName 服务名称
     * @return 版本列表
     */
    List<String> getVersions(String serviceName);

    /**
     * 强制刷新缓存
     * 
     * @param serviceName 服务名称
     */
    void refreshCache(String serviceName);

    /**
     * 订阅服务变化通知
     * 
     * @param serviceName 服务名称
     * @param listener 变化回调
     */
    void subscribe(String serviceName, Consumer<List<ServiceInfo>> listener);

    /**
     * 取消订阅
     * 
     * @param serviceName 服务名称
     */
    void unsubscribe(String serviceName);

    /**
     * 启动服务发现客户端
     */
    void start();

    /**
     * 关闭服务发现客户端
     */
    void close();
}
