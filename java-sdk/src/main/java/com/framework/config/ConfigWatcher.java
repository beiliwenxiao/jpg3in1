package com.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 配置文件监听器
 * 
 * 监听本地配置文件变更，支持运行时热更新。
 * 当配置文件发生变化时，自动重新加载并通知 FrameworkConfig。
 * 
 * 需求: 9.3
 */
public class ConfigWatcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConfigWatcher.class);

    private final FrameworkConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Consumer<String>> fileChangeListeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;
    private WatchService watchService;
    private final ConcurrentHashMap<String, Long> lastModifiedMap = new ConcurrentHashMap<>();

    public ConfigWatcher(FrameworkConfig config) {
        this.config = config;
    }

    /**
     * 开始监听指定配置文件
     * 
     * @param filePath 配置文件路径
     * @param pollIntervalMs 轮询间隔（毫秒）
     */
    public void watchFile(String filePath, long pollIntervalMs) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.warn("配置文件不存在，无法监听: {}", filePath);
            return;
        }

        lastModifiedMap.put(filePath, file.lastModified());

        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "config-watcher");
                t.setDaemon(true);
                return t;
            });
        }

        running.set(true);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkFileChange(filePath);
            } catch (Exception e) {
                logger.error("检查配置文件变更失败: {}", filePath, e);
            }
        }, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);

        logger.info("开始监听配置文件: {} (间隔: {}ms)", filePath, pollIntervalMs);
    }

    /**
     * 使用 NIO WatchService 监听目录变更
     */
    public void watchDirectory(String dirPath) {
        try {
            Path dir = Paths.get(dirPath);
            if (!Files.isDirectory(dir)) {
                logger.warn("路径不是目录: {}", dirPath);
                return;
            }

            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            running.set(true);

            Thread watchThread = new Thread(() -> {
                while (running.get()) {
                    try {
                        WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                        if (key == null) continue;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                            Path changed = dir.resolve((Path) event.context());
                            String fileName = changed.toString();
                            logger.info("检测到配置文件变更: {}", fileName);

                            notifyFileChangeListeners(fileName);

                            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                                config.loadFromYaml(fileName);
                            } else if (fileName.endsWith(".properties")) {
                                config.loadFromProperties(fileName);
                            }
                        }
                        key.reset();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("监听目录变更失败", e);
                    }
                }
            }, "config-dir-watcher");
            watchThread.setDaemon(true);
            watchThread.start();

            logger.info("开始监听配置目录: {}", dirPath);
        } catch (IOException e) {
            logger.error("创建目录监听器失败: {}", dirPath, e);
        }
    }

    /**
     * 添加文件变更监听器
     */
    public void addFileChangeListener(Consumer<String> listener) {
        fileChangeListeners.add(listener);
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        if (scheduler != null) {
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
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("关闭 WatchService 失败", e);
            }
        }
        logger.info("配置监听器已关闭");
    }

    private void checkFileChange(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return;

        long lastModified = file.lastModified();
        Long previousModified = lastModifiedMap.get(filePath);

        if (previousModified != null && lastModified > previousModified) {
            lastModifiedMap.put(filePath, lastModified);
            logger.info("检测到配置文件变更: {}", filePath);

            notifyFileChangeListeners(filePath);

            if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
                config.loadFromYaml(filePath);
            } else if (filePath.endsWith(".properties")) {
                config.loadFromProperties(filePath);
            }
        }
    }

    private void notifyFileChangeListeners(String filePath) {
        for (Consumer<String> listener : fileChangeListeners) {
            try {
                listener.accept(filePath);
            } catch (Exception e) {
                logger.error("文件变更监听器执行失败", e);
            }
        }
    }
}
