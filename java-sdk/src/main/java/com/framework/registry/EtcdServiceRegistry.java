package com.framework.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 基于 etcd 的服务注册中心实现
 * 
 * 使用 jetcd 客户端连接 etcd 集群，实现服务注册、注销、发现和健康检查。
 * 
 * 数据存储格式:
 * - 服务注册: {namespace}/{serviceName}/{serviceId} -> ServiceInfo JSON
 * - 健康状态: {namespace}/{serviceName}/{serviceId}/health -> HealthStatus
 * 
 * 需求: 5.1, 5.2, 5.3, 5.4, 5.5
 */
public class EtcdServiceRegistry implements ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(EtcdServiceRegistry.class);

    private final ServiceRegistryConfig config;
    private final ObjectMapper objectMapper;

    private Client etcdClient;
    private KV kvClient;
    private Lease leaseClient;
    private Watch watchClient;

    /** 已注册服务的租约 ID 映射: serviceId -> leaseId */
    private final ConcurrentHashMap<String, Long> serviceLeases;

    /** 已注册服务的信息缓存: serviceId -> ServiceInfo */
    private final ConcurrentHashMap<String, ServiceInfo> registeredServices;

    /** 服务健康状态: serviceId -> HealthStatus */
    private final ConcurrentHashMap<String, HealthStatus> healthStatuses;

    /** 服务变化监听器: serviceName -> listener */
    private final ConcurrentHashMap<String, Consumer<List<ServiceInfo>>> watchListeners;

    /** Watch 实例: serviceName -> Watch.Watcher */
    private final ConcurrentHashMap<String, Watch.Watcher> watchers;

    /** 心跳调度器 */
    private ScheduledExecutorService heartbeatScheduler;

    /** 健康检查调度器 */
    private ScheduledExecutorService healthCheckScheduler;

    /** 连续健康检查失败计数: serviceId -> failCount */
    private final ConcurrentHashMap<String, Integer> healthCheckFailCounts;

    private volatile boolean started;
    private volatile boolean closed;

    public EtcdServiceRegistry(ServiceRegistryConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.serviceLeases = new ConcurrentHashMap<>();
        this.registeredServices = new ConcurrentHashMap<>();
        this.healthStatuses = new ConcurrentHashMap<>();
        this.watchListeners = new ConcurrentHashMap<>();
        this.watchers = new ConcurrentHashMap<>();
        this.healthCheckFailCounts = new ConcurrentHashMap<>();
        this.started = false;
        this.closed = false;
    }

    @Override
    public void start() {
        if (started) {
            logger.warn("服务注册中心客户端已启动");
            return;
        }
        if (closed) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "服务注册中心客户端已关闭，无法重新启动");
        }

        logger.info("启动服务注册中心客户端, endpoints={}", config.getEndpoints());

        try {
            // 创建 etcd 客户端
            ClientBuilder builder = Client.builder()
                    .endpoints(config.getEndpoints().toArray(new String[0]));

            this.etcdClient = builder.build();
            this.kvClient = etcdClient.getKVClient();
            this.leaseClient = etcdClient.getLeaseClient();
            this.watchClient = etcdClient.getWatchClient();

            // 启动心跳调度器
            this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "registry-heartbeat");
                t.setDaemon(true);
                return t;
            });

            // 启动健康检查调度器
            if (config.isHealthCheckEnabled()) {
                this.healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "registry-health-check");
                    t.setDaemon(true);
                    return t;
                });
                healthCheckScheduler.scheduleAtFixedRate(
                        this::performHealthChecks,
                        config.getHealthCheckInterval(),
                        config.getHealthCheckInterval(),
                        TimeUnit.SECONDS
                );
            }

            started = true;
            logger.info("服务注册中心客户端启动成功");

        } catch (Exception e) {
            logger.error("启动服务注册中心客户端失败", e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "无法连接到 etcd: " + e.getMessage(), e);
        }
    }

    @Override
    public void register(ServiceInfo serviceInfo) {
        ensureStarted();
        validateServiceInfo(serviceInfo);

        String serviceId = serviceInfo.getId();
        String serviceName = serviceInfo.getName();
        String key = buildServiceKey(serviceName, serviceId);

        logger.info("注册服务: name={}, id={}, address={}:{}", 
                serviceName, serviceId, serviceInfo.getAddress(), serviceInfo.getPort());

        try {
            // 创建租约
            LeaseGrantResponse leaseResponse = leaseClient.grant(config.getServiceTtl())
                    .get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
            long leaseId = leaseResponse.getID();

            // 序列化服务信息
            serviceInfo.setRegisteredAt(new Date());
            String value = objectMapper.writeValueAsString(serviceInfo);

            // 写入 etcd（带租约）
            PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
            kvClient.put(
                    ByteSequence.from(key, StandardCharsets.UTF_8),
                    ByteSequence.from(value, StandardCharsets.UTF_8),
                    putOption
            ).get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            // 设置初始健康状态
            updateHealthStatus(serviceId, HealthStatus.HEALTHY);

            // 保存租约和服务信息
            serviceLeases.put(serviceId, leaseId);
            registeredServices.put(serviceId, serviceInfo);

            // 启动心跳保活
            startKeepAlive(serviceId, leaseId);

            logger.info("服务注册成功: name={}, id={}, leaseId={}", serviceName, serviceId, leaseId);

        } catch (JsonProcessingException e) {
            throw new FrameworkException(ErrorCode.SERIALIZATION_ERROR,
                    "序列化服务信息失败: " + e.getMessage(), e);
        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "服务注册失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deregister(String serviceId) {
        ensureStarted();
        if (serviceId == null || serviceId.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务 ID 不能为空");
        }

        ServiceInfo serviceInfo = registeredServices.get(serviceId);
        if (serviceInfo == null) {
            logger.warn("服务未注册，无法注销: id={}", serviceId);
            return;
        }

        String key = buildServiceKey(serviceInfo.getName(), serviceId);
        String healthKey = buildHealthKey(serviceInfo.getName(), serviceId);

        logger.info("注销服务: name={}, id={}", serviceInfo.getName(), serviceId);

        try {
            // 删除服务注册信息
            kvClient.delete(ByteSequence.from(key, StandardCharsets.UTF_8))
                    .get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            // 删除健康状态
            kvClient.delete(ByteSequence.from(healthKey, StandardCharsets.UTF_8))
                    .get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            // 撤销租约（会自动删除关联的 key）
            Long leaseId = serviceLeases.remove(serviceId);
            if (leaseId != null) {
                leaseClient.revoke(leaseId)
                        .get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);
            }

            // 清理本地缓存
            registeredServices.remove(serviceId);
            healthStatuses.remove(serviceId);
            healthCheckFailCounts.remove(serviceId);

            logger.info("服务注销成功: id={}", serviceId);

        } catch (Exception e) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "服务注销失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ServiceInfo> discover(String serviceName) {
        ensureStarted();
        if (serviceName == null || serviceName.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }

        String prefix = config.getNamespace() + "/" + serviceName + "/";

        try {
            GetOption option = GetOption.builder()
                    .isPrefix(true)
                    .build();

            GetResponse response = kvClient.get(
                    ByteSequence.from(prefix, StandardCharsets.UTF_8),
                    option
            ).get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            List<ServiceInfo> services = new ArrayList<>();
            for (var kv : response.getKvs()) {
                String key = kv.getKey().toString(StandardCharsets.UTF_8);
                // 跳过健康状态 key
                if (key.endsWith("/health")) {
                    continue;
                }
                try {
                    String value = kv.getValue().toString(StandardCharsets.UTF_8);
                    ServiceInfo info = objectMapper.readValue(value, ServiceInfo.class);
                    // 只返回健康的服务实例
                    HealthStatus status = getHealthStatus(info.getId());
                    if (status == HealthStatus.HEALTHY) {
                        services.add(info);
                    }
                } catch (JsonProcessingException e) {
                    logger.warn("反序列化服务信息失败: key={}", key, e);
                }
            }

            logger.debug("发现服务: name={}, count={}", serviceName, services.size());
            return services;

        } catch (Exception e) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR,
                    "服务发现失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ServiceInfo> discover(String serviceName, String version) {
        List<ServiceInfo> allServices = discover(serviceName);
        if (version == null || version.isEmpty()) {
            return allServices;
        }
        return allServices.stream()
                .filter(s -> version.equals(s.getVersion()))
                .toList();
    }

    @Override
    public HealthStatus getHealthStatus(String serviceId) {
        ensureStarted();
        if (serviceId == null || serviceId.isEmpty()) {
            return HealthStatus.UNKNOWN;
        }

        // 先查本地缓存
        HealthStatus cached = healthStatuses.get(serviceId);
        if (cached != null) {
            return cached;
        }

        // 从 etcd 查询
        ServiceInfo serviceInfo = registeredServices.get(serviceId);
        if (serviceInfo == null) {
            return HealthStatus.UNKNOWN;
        }

        String healthKey = buildHealthKey(serviceInfo.getName(), serviceId);
        try {
            GetResponse response = kvClient.get(
                    ByteSequence.from(healthKey, StandardCharsets.UTF_8)
            ).get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            if (response.getKvs().isEmpty()) {
                return HealthStatus.UNKNOWN;
            }

            String value = response.getKvs().get(0).getValue().toString(StandardCharsets.UTF_8);
            HealthStatus status = HealthStatus.fromValue(value);
            healthStatuses.put(serviceId, status);
            return status;

        } catch (Exception e) {
            logger.warn("获取健康状态失败: serviceId={}", serviceId, e);
            return HealthStatus.UNKNOWN;
        }
    }

    @Override
    public void updateHealthStatus(String serviceId, HealthStatus status) {
        ensureStarted();
        if (serviceId == null || serviceId.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务 ID 不能为空");
        }

        ServiceInfo serviceInfo = registeredServices.get(serviceId);
        if (serviceInfo == null) {
            logger.warn("服务未注册，无法更新健康状态: id={}", serviceId);
            return;
        }

        String healthKey = buildHealthKey(serviceInfo.getName(), serviceId);

        try {
            Long leaseId = serviceLeases.get(serviceId);
            PutOption.Builder putBuilder = PutOption.builder();
            if (leaseId != null) {
                putBuilder.withLeaseId(leaseId);
            }

            kvClient.put(
                    ByteSequence.from(healthKey, StandardCharsets.UTF_8),
                    ByteSequence.from(status.getValue(), StandardCharsets.UTF_8),
                    putBuilder.build()
            ).get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

            healthStatuses.put(serviceId, status);
            logger.debug("更新健康状态: serviceId={}, status={}", serviceId, status);

        } catch (Exception e) {
            logger.warn("更新健康状态失败: serviceId={}", serviceId, e);
        }
    }

    @Override
    public void watch(String serviceName, Consumer<List<ServiceInfo>> listener) {
        ensureStarted();
        if (serviceName == null || serviceName.isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }

        String prefix = config.getNamespace() + "/" + serviceName + "/";

        watchListeners.put(serviceName, listener);

        WatchOption option = WatchOption.builder()
                .isPrefix(true)
                .build();

        Watch.Watcher watcher = watchClient.watch(
                ByteSequence.from(prefix, StandardCharsets.UTF_8),
                option,
                watchResponse -> {
                    for (WatchEvent event : watchResponse.getEvents()) {
                        logger.debug("服务变化事件: type={}, key={}",
                                event.getEventType(),
                                event.getKeyValue().getKey().toString(StandardCharsets.UTF_8));
                    }
                    // 重新查询该服务的所有实例并通知监听器
                    try {
                        List<ServiceInfo> services = discover(serviceName);
                        Consumer<List<ServiceInfo>> l = watchListeners.get(serviceName);
                        if (l != null) {
                            l.accept(services);
                        }
                    } catch (Exception e) {
                        logger.error("处理服务变化事件失败: serviceName={}", serviceName, e);
                    }
                }
        );

        watchers.put(serviceName, watcher);
        logger.info("开始监听服务变化: serviceName={}", serviceName);
    }

    @Override
    public void unwatch(String serviceName) {
        Watch.Watcher watcher = watchers.remove(serviceName);
        if (watcher != null) {
            watcher.close();
        }
        watchListeners.remove(serviceName);
        logger.info("停止监听服务变化: serviceName={}", serviceName);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        logger.info("关闭服务注册中心客户端");

        // 注销所有已注册的服务
        for (String serviceId : new ArrayList<>(registeredServices.keySet())) {
            try {
                deregister(serviceId);
            } catch (Exception e) {
                logger.warn("注销服务失败: id={}", serviceId, e);
            }
        }

        // 关闭所有 watcher
        for (Watch.Watcher watcher : watchers.values()) {
            try {
                watcher.close();
            } catch (Exception e) {
                logger.warn("关闭 watcher 失败", e);
            }
        }
        watchers.clear();
        watchListeners.clear();

        // 关闭调度器
        shutdownScheduler(heartbeatScheduler);
        shutdownScheduler(healthCheckScheduler);

        // 关闭 etcd 客户端
        if (etcdClient != null) {
            etcdClient.close();
        }

        closed = true;
        started = false;
        logger.info("服务注册中心客户端已关闭");
    }

    @Override
    public boolean isAvailable() {
        if (!started || closed || etcdClient == null) {
            return false;
        }
        try {
            // 使用 KV get 操作作为健康检查探针
            etcdClient.getKVClient()
                    .get(io.etcd.jetcd.ByteSequence.from("/health-check".getBytes()))
                    .get(config.getConnectionTimeout(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 启动租约保活（心跳）
     */
    private void startKeepAlive(String serviceId, long leaseId) {
        leaseClient.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse response) {
                logger.trace("心跳保活成功: serviceId={}, leaseId={}, ttl={}",
                        serviceId, leaseId, response.getTTL());
            }

            @Override
            public void onError(Throwable t) {
                logger.error("心跳保活失败: serviceId={}, leaseId={}", serviceId, leaseId, t);
                // 尝试重新注册
                ServiceInfo info = registeredServices.get(serviceId);
                if (info != null && started && !closed) {
                    logger.info("尝试重新注册服务: serviceId={}", serviceId);
                    heartbeatScheduler.schedule(() -> {
                        try {
                            serviceLeases.remove(serviceId);
                            register(info);
                        } catch (Exception e) {
                            logger.error("重新注册服务失败: serviceId={}", serviceId, e);
                        }
                    }, config.getHeartbeatInterval(), TimeUnit.SECONDS);
                }
            }

            @Override
            public void onCompleted() {
                logger.debug("心跳保活完成: serviceId={}", serviceId);
            }
        });
    }

    /**
     * 执行健康检查
     */
    private void performHealthChecks() {
        for (Map.Entry<String, ServiceInfo> entry : registeredServices.entrySet()) {
            String serviceId = entry.getKey();
            ServiceInfo info = entry.getValue();

            try {
                // 检查租约是否仍然有效
                Long leaseId = serviceLeases.get(serviceId);
                if (leaseId == null) {
                    markUnhealthy(serviceId);
                    continue;
                }

                // 尝试获取租约信息来验证连接
                leaseClient.timeToLive(leaseId, io.etcd.jetcd.options.LeaseOption.DEFAULT)
                        .get(config.getRequestTimeout(), TimeUnit.MILLISECONDS);

                // 健康检查通过，重置失败计数
                healthCheckFailCounts.put(serviceId, 0);
                if (healthStatuses.get(serviceId) != HealthStatus.HEALTHY) {
                    updateHealthStatus(serviceId, HealthStatus.HEALTHY);
                    logger.info("服务恢复健康: serviceId={}", serviceId);
                }

            } catch (Exception e) {
                int failCount = healthCheckFailCounts.getOrDefault(serviceId, 0) + 1;
                healthCheckFailCounts.put(serviceId, failCount);

                if (failCount >= config.getHealthCheckFailureThreshold()) {
                    markUnhealthy(serviceId);
                }

                logger.warn("健康检查失败: serviceId={}, failCount={}/{}", 
                        serviceId, failCount, config.getHealthCheckFailureThreshold());
            }
        }
    }

    private void markUnhealthy(String serviceId) {
        if (healthStatuses.get(serviceId) != HealthStatus.UNHEALTHY) {
            healthStatuses.put(serviceId, HealthStatus.UNHEALTHY);
            logger.warn("服务标记为不健康: serviceId={}", serviceId);
        }
    }

    /**
     * 构建服务注册 key
     */
    private String buildServiceKey(String serviceName, String serviceId) {
        return config.getNamespace() + "/" + serviceName + "/" + serviceId;
    }

    /**
     * 构建健康状态 key
     */
    private String buildHealthKey(String serviceName, String serviceId) {
        return config.getNamespace() + "/" + serviceName + "/" + serviceId + "/health";
    }

    private void ensureStarted() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "服务注册中心客户端未启动");
        }
        if (closed) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "服务注册中心客户端已关闭");
        }
    }

    private void validateServiceInfo(ServiceInfo serviceInfo) {
        if (serviceInfo == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务信息不能为空");
        }
        if (serviceInfo.getId() == null || serviceInfo.getId().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务 ID 不能为空");
        }
        if (serviceInfo.getName() == null || serviceInfo.getName().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }
        if (serviceInfo.getAddress() == null || serviceInfo.getAddress().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务地址不能为空");
        }
        if (serviceInfo.getPort() <= 0) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务端口必须大于 0");
        }
    }

    private void shutdownScheduler(ScheduledExecutorService scheduler) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取所有已注册的服务（用于测试和调试）
     */
    public Map<String, ServiceInfo> getRegisteredServices() {
        return Collections.unmodifiableMap(registeredServices);
    }
}
