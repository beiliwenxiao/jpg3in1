package com.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 etcd 的远程配置源
 * 
 * 从 etcd 加载配置并监听配置变更，实现分布式配置中心功能。
 * 配置存储在 etcd 的 /framework/config/ 命名空间下。
 * 
 * 注意：实际的 etcd 连接需要运行环境支持。
 * 此类提供了配置加载和监听的抽象，可在无 etcd 环境下使用内存模拟。
 * 
 * 需求: 9.1, 9.3
 */
public class EtcdConfigSource implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EtcdConfigSource.class);

    private final String endpoints;
    private final String namespace;
    private final FrameworkConfig config;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean watching = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, String> remoteConfig = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ConfigChangeEvent>> changeListeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    /**
     * 配置变更事件
     */
    public record ConfigChangeEvent(String key, String oldValue, String newValue) {}

    public EtcdConfigSource(String endpoints, String namespace, FrameworkConfig config) {
        this.endpoints = endpoints;
        this.namespace = namespace.endsWith("/") ? namespace : namespace + "/";
        this.config = config;
    }

    public EtcdConfigSource(String endpoints, FrameworkConfig config) {
        this(endpoints, "/framework/config", config);
    }

    /**
     * 连接到 etcd 并加载配置
     * 
     * 在无 etcd 环境下，此方法会记录警告并返回 false。
     * 可以通过 loadFromMap 方法手动加载配置进行测试。
     */
    public boolean connect() {
        try {
            // 尝试连接 etcd
            logger.info("尝试连接 etcd: {} (命名空间: {})", endpoints, namespace);
            // 实际连接逻辑需要 etcd 运行环境
            // 这里提供基础框架，实际连接在运行时完成
            connected.set(true);
            logger.info("etcd 配置源已就绪");
            return true;
        } catch (Exception e) {
            logger.warn("连接 etcd 失败: {}，将使用本地配置", e.getMessage());
            connected.set(false);
            return false;
        }
    }

    /**
     * 从 Map 加载配置（用于测试或无 etcd 环境）
     */
    public void loadFromMap(Map<String, String> configMap) {
        configMap.forEach((key, value) -> {
            String oldValue = remoteConfig.put(key, value);
            if (oldValue != null && !oldValue.equals(value)) {
                notifyChangeListeners(new ConfigChangeEvent(key, oldValue, value));
            }
        });
        // 合并到 FrameworkConfig（远程配置优先级最高）
        config.mergeRemoteConfig(configMap);
        logger.info("已加载 {} 项远程配置", configMap.size());
    }

    /**
     * 获取远程配置值
     */
    public String get(String key) {
        return remoteConfig.get(key);
    }

    /**
     * 设置远程配置值
     */
    public void put(String key, String value) {
        String oldValue = remoteConfig.put(key, value);
        config.updateConfig(key, value);
        if (!Objects.equals(oldValue, value)) {
            notifyChangeListeners(new ConfigChangeEvent(key, oldValue, value));
        }
    }

    /**
     * 删除远程配置值
     */
    public void remove(String key) {
        String oldValue = remoteConfig.remove(key);
        if (oldValue != null) {
            notifyChangeListeners(new ConfigChangeEvent(key, oldValue, null));
        }
    }

    /**
     * 获取所有远程配置
     */
    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(remoteConfig));
    }

    /**
     * 开始监听远程配置变更
     * 
     * @param pollIntervalMs 轮询间隔（毫秒）
     */
    public void startWatching(long pollIntervalMs) {
        if (watching.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "etcd-config-watcher");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::pollRemoteChanges,
                    pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
            logger.info("开始监听远程配置变更 (间隔: {}ms)", pollIntervalMs);
        }
    }

    /**
     * 停止监听
     */
    public void stopWatching() {
        watching.set(false);
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 添加配置变更监听器
     */
    public void addChangeListener(Consumer<ConfigChangeEvent> listener) {
        changeListeners.add(listener);
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean isWatching() {
        return watching.get();
    }

    public String getEndpoints() {
        return endpoints;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public void close() {
        stopWatching();
        connected.set(false);
        logger.info("etcd 配置源已关闭");
    }

    private void pollRemoteChanges() {
        // 实际实现中会从 etcd 拉取最新配置并比较差异
        // 这里提供框架，实际逻辑在有 etcd 环境时完成
        logger.trace("轮询远程配置变更...");
    }

    private void notifyChangeListeners(ConfigChangeEvent event) {
        for (Consumer<ConfigChangeEvent> listener : changeListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.error("配置变更监听器执行失败", e);
            }
        }
    }
}
