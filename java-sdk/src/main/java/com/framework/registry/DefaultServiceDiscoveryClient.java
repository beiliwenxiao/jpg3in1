package com.framework.registry;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 默认服务发现客户端实现
 * 
 * 基于 ServiceRegistry 提供带缓存的服务发现功能。
 * - 本地缓存减少对 etcd 的查询压力
 * - 通过 watch 机制自动刷新缓存
 * - 支持多版本服务查询
 * 
 * 需求: 5.3, 5.5
 */
public class DefaultServiceDiscoveryClient implements ServiceDiscoveryClient {

    private static final Logger logger = LoggerFactory.getLogger(DefaultServiceDiscoveryClient.class);

    private final ServiceRegistry registry;
    private final ServiceRegistryConfig config;

    /** 服务实例缓存: serviceName -> ServiceInfo 列表 */
    private final ConcurrentHashMap<String, List<ServiceInfo>> serviceCache;

    /** 缓存时间戳: serviceName -> 上次刷新时间 */
    private final ConcurrentHashMap<String, Long> cacheTimestamps;

    /** 订阅者: serviceName -> listener 列表 */
    private final ConcurrentHashMap<String, List<Consumer<List<ServiceInfo>>>> subscribers;

    /** 已 watch 的服务名称 */
    private final Set<String> watchedServices;

    /** 缓存 TTL（毫秒） */
    private final long cacheTtlMs;

    /** 缓存刷新调度器 */
    private ScheduledExecutorService cacheRefreshScheduler;

    private volatile boolean started;
    private volatile boolean closed;

    public DefaultServiceDiscoveryClient(ServiceRegistry registry, ServiceRegistryConfig config) {
        this.registry = registry;
        this.config = config;
        this.serviceCache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();
        this.watchedServices = ConcurrentHashMap.newKeySet();
        // 默认缓存 60 秒
        this.cacheTtlMs = 60_000;
        this.started = false;
        this.closed = false;
    }

    @Override
    public void start() {
        if (started) {
            logger.warn("服务发现客户端已启动");
            return;
        }
        if (closed) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "服务发现客户端已关闭");
        }

        logger.info("启动服务发现客户端");

        // 启动缓存定期刷新
        this.cacheRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "discovery-cache-refresh");
            t.setDaemon(true);
            return t;
        });

        cacheRefreshScheduler.scheduleAtFixedRate(
                this::refreshAllCaches,
                cacheTtlMs,
                cacheTtlMs,
                TimeUnit.MILLISECONDS
        );

        started = true;
        logger.info("服务发现客户端启动成功");
    }

    @Override
    public List<ServiceInfo> getInstances(String serviceName) {
        ensureStarted();
        if (serviceName == null || serviceName.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }

        // 检查缓存是否有效
        if (isCacheValid(serviceName)) {
            List<ServiceInfo> cached = serviceCache.get(serviceName);
            if (cached != null) {
                logger.debug("从缓存获取服务实例: name={}, count={}", serviceName, cached.size());
                return Collections.unmodifiableList(cached);
            }
        }

        // 缓存失效，从注册中心查询
        return refreshAndGet(serviceName);
    }

    @Override
    public List<ServiceInfo> getInstances(String serviceName, String version) {
        List<ServiceInfo> allInstances = getInstances(serviceName);
        if (version == null || version.isEmpty()) {
            return allInstances;
        }
        return allInstances.stream()
                .filter(s -> version.equals(s.getVersion()))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getVersions(String serviceName) {
        List<ServiceInfo> instances = getInstances(serviceName);
        return instances.stream()
                .map(ServiceInfo::getVersion)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public void refreshCache(String serviceName) {
        ensureStarted();
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }
        refreshAndGet(serviceName);
    }

    @Override
    public void subscribe(String serviceName, Consumer<List<ServiceInfo>> listener) {
        ensureStarted();
        if (serviceName == null || serviceName.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }
        if (listener == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "监听器不能为空");
        }

        subscribers.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>()).add(listener);

        // 如果还没有 watch 该服务，启动 watch
        if (watchedServices.add(serviceName)) {
            registry.watch(serviceName, services -> {
                // 更新缓存
                serviceCache.put(serviceName, new ArrayList<>(services));
                cacheTimestamps.put(serviceName, System.currentTimeMillis());

                // 通知所有订阅者
                notifySubscribers(serviceName, services);
            });
            logger.info("开始监听服务变化: serviceName={}", serviceName);
        }
    }

    @Override
    public void unsubscribe(String serviceName) {
        subscribers.remove(serviceName);

        // 如果没有订阅者了，停止 watch
        if (watchedServices.remove(serviceName)) {
            registry.unwatch(serviceName);
            logger.info("停止监听服务变化: serviceName={}", serviceName);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        logger.info("关闭服务发现客户端");

        // 停止所有 watch
        for (String serviceName : new ArrayList<>(watchedServices)) {
            unsubscribe(serviceName);
        }

        // 关闭调度器
        if (cacheRefreshScheduler != null && !cacheRefreshScheduler.isShutdown()) {
            cacheRefreshScheduler.shutdown();
            try {
                if (!cacheRefreshScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cacheRefreshScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cacheRefreshScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 清理缓存
        serviceCache.clear();
        cacheTimestamps.clear();
        subscribers.clear();

        closed = true;
        started = false;
        logger.info("服务发现客户端已关闭");
    }

    // ==================== 内部方法 ====================

    private List<ServiceInfo> refreshAndGet(String serviceName) {
        try {
            List<ServiceInfo> services = registry.discover(serviceName);
            serviceCache.put(serviceName, new ArrayList<>(services));
            cacheTimestamps.put(serviceName, System.currentTimeMillis());
            logger.debug("刷新服务缓存: name={}, count={}", serviceName, services.size());
            return Collections.unmodifiableList(services);
        } catch (Exception e) {
            // 如果查询失败但缓存中有数据，返回缓存数据
            List<ServiceInfo> cached = serviceCache.get(serviceName);
            if (cached != null) {
                logger.warn("服务发现失败，使用缓存数据: name={}", serviceName, e);
                return Collections.unmodifiableList(cached);
            }
            throw e;
        }
    }

    private boolean isCacheValid(String serviceName) {
        Long timestamp = cacheTimestamps.get(serviceName);
        if (timestamp == null) {
            return false;
        }
        return (System.currentTimeMillis() - timestamp) < cacheTtlMs;
    }

    private void refreshAllCaches() {
        for (String serviceName : serviceCache.keySet()) {
            try {
                refreshAndGet(serviceName);
            } catch (Exception e) {
                logger.warn("定期刷新缓存失败: serviceName={}", serviceName, e);
            }
        }
    }

    private void notifySubscribers(String serviceName, List<ServiceInfo> services) {
        List<Consumer<List<ServiceInfo>>> listeners = subscribers.get(serviceName);
        if (listeners != null) {
            for (Consumer<List<ServiceInfo>> listener : listeners) {
                try {
                    listener.accept(services);
                } catch (Exception e) {
                    logger.error("通知订阅者失败: serviceName={}", serviceName, e);
                }
            }
        }
    }

    private void ensureStarted() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "服务发现客户端未启动");
        }
        if (closed) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "服务发现客户端已关闭");
        }
    }
}
