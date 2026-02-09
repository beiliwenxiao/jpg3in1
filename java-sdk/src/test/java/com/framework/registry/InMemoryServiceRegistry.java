package com.framework.registry;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 内存实现的服务注册中心（用于属性测试）
 * 
 * 模拟 EtcdServiceRegistry 的核心逻辑，不依赖外部 etcd 实例。
 */
public class InMemoryServiceRegistry implements ServiceRegistry {

    /** 已注册服务: key(namespace/name/id) -> ServiceInfo */
    private final ConcurrentHashMap<String, ServiceInfo> services = new ConcurrentHashMap<>();

    /** 健康状态: serviceId -> HealthStatus */
    private final ConcurrentHashMap<String, HealthStatus> healthStatuses = new ConcurrentHashMap<>();

    /** 监听器: serviceName -> listeners */
    private final ConcurrentHashMap<String, List<Consumer<List<ServiceInfo>>>> watchers = new ConcurrentHashMap<>();

    private final String namespace;
    private volatile boolean started = false;
    private volatile boolean closed = false;

    public InMemoryServiceRegistry() {
        this("/framework/services");
    }

    public InMemoryServiceRegistry(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void start() {
        if (closed) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "已关闭");
        }
        started = true;
    }

    @Override
    public void register(ServiceInfo serviceInfo) {
        ensureStarted();
        validate(serviceInfo);

        String key = buildKey(serviceInfo.getName(), serviceInfo.getId());
        serviceInfo.setRegisteredAt(new Date());
        services.put(key, serviceInfo);
        healthStatuses.put(serviceInfo.getId(), HealthStatus.HEALTHY);

        notifyWatchers(serviceInfo.getName());
    }

    @Override
    public void deregister(String serviceId) {
        ensureStarted();
        if (serviceId == null || serviceId.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务 ID 不能为空");
        }

        String removedServiceName = null;
        Iterator<Map.Entry<String, ServiceInfo>> it = services.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ServiceInfo> entry = it.next();
            if (entry.getValue().getId().equals(serviceId)) {
                removedServiceName = entry.getValue().getName();
                it.remove();
                break;
            }
        }

        healthStatuses.remove(serviceId);

        if (removedServiceName != null) {
            notifyWatchers(removedServiceName);
        }
    }

    @Override
    public List<ServiceInfo> discover(String serviceName) {
        ensureStarted();
        if (serviceName == null || serviceName.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }

        String prefix = buildKey(serviceName, "");
        return services.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .filter(s -> {
                    HealthStatus status = healthStatuses.getOrDefault(s.getId(), HealthStatus.UNKNOWN);
                    return status == HealthStatus.HEALTHY;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceInfo> discover(String serviceName, String version) {
        List<ServiceInfo> all = discover(serviceName);
        if (version == null || version.isEmpty()) {
            return all;
        }
        return all.stream()
                .filter(s -> version.equals(s.getVersion()))
                .collect(Collectors.toList());
    }

    @Override
    public HealthStatus getHealthStatus(String serviceId) {
        return healthStatuses.getOrDefault(serviceId, HealthStatus.UNKNOWN);
    }

    @Override
    public void updateHealthStatus(String serviceId, HealthStatus status) {
        ensureStarted();
        healthStatuses.put(serviceId, status);
    }

    @Override
    public void watch(String serviceName, Consumer<List<ServiceInfo>> listener) {
        ensureStarted();
        watchers.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void unwatch(String serviceName) {
        watchers.remove(serviceName);
    }

    @Override
    public void close() {
        services.clear();
        healthStatuses.clear();
        watchers.clear();
        closed = true;
        started = false;
    }

    @Override
    public boolean isAvailable() {
        return started && !closed;
    }

    private String buildKey(String serviceName, String serviceId) {
        return namespace + "/" + serviceName + "/" + serviceId;
    }

    private void ensureStarted() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "未启动");
        }
        if (closed) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "已关闭");
        }
    }

    private void validate(ServiceInfo info) {
        if (info == null) throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务信息不能为空");
        if (info.getId() == null || info.getId().isEmpty())
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务 ID 不能为空");
        if (info.getName() == null || info.getName().isEmpty())
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        if (info.getAddress() == null || info.getAddress().isEmpty())
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务地址不能为空");
        if (info.getPort() <= 0)
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务端口必须大于 0");
    }

    private void notifyWatchers(String serviceName) {
        List<Consumer<List<ServiceInfo>>> listeners = watchers.get(serviceName);
        if (listeners != null) {
            List<ServiceInfo> current = discover(serviceName);
            for (Consumer<List<ServiceInfo>> listener : listeners) {
                listener.accept(current);
            }
        }
    }
}
