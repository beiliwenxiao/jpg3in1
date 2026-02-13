package com.framework.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 健康检查端点
 * 
 * 提供服务健康状态查询，支持注册自定义健康检查项。
 * 
 * 需求: 10.4
 */
public class HealthEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(HealthEndpoint.class);

    private final String serviceName;
    private final ConcurrentHashMap<String, Supplier<HealthCheck>> checks = new ConcurrentHashMap<>();

    public HealthEndpoint(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * 注册健康检查项
     */
    public void registerCheck(String name, Supplier<HealthCheck> check) {
        checks.put(name, check);
        logger.debug("注册健康检查项: {}", name);
    }

    /**
     * 执行所有健康检查
     */
    public HealthStatus check() {
        Map<String, HealthCheck> results = new LinkedHashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, Supplier<HealthCheck>> entry : checks.entrySet()) {
            try {
                HealthCheck result = entry.getValue().get();
                results.put(entry.getKey(), result);
                if (result.status() != Status.UP) {
                    allHealthy = false;
                }
            } catch (Exception e) {
                results.put(entry.getKey(), new HealthCheck(Status.DOWN, 
                        Map.of("error", e.getMessage())));
                allHealthy = false;
            }
        }

        return new HealthStatus(
                serviceName,
                allHealthy ? Status.UP : Status.DOWN,
                Instant.now().toString(),
                results
        );
    }

    /**
     * 快速检查是否健康
     */
    public boolean isHealthy() {
        return check().status() == Status.UP;
    }

    public enum Status {
        UP, DOWN, DEGRADED
    }

    public record HealthCheck(Status status, Map<String, Object> details) {
        public static HealthCheck up() {
            return new HealthCheck(Status.UP, Map.of());
        }

        public static HealthCheck up(Map<String, Object> details) {
            return new HealthCheck(Status.UP, details);
        }

        public static HealthCheck down(String reason) {
            return new HealthCheck(Status.DOWN, Map.of("reason", reason));
        }

        public static HealthCheck degraded(String reason) {
            return new HealthCheck(Status.DEGRADED, Map.of("reason", reason));
        }
    }

    public record HealthStatus(
            String service,
            Status status,
            String timestamp,
            Map<String, HealthCheck> checks
    ) {}
}
