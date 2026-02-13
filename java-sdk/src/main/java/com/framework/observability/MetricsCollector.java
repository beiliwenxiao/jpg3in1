package com.framework.observability;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标收集器
 * 
 * 集成 Prometheus 客户端，收集 RPC 调用次数、延迟、错误率等指标。
 * 
 * 需求: 10.2
 */
public class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final CollectorRegistry registry;
    private final String serviceName;

    // 请求计数器
    private final Counter requestTotal;
    // 请求延迟直方图
    private final Histogram requestDuration;
    // 错误计数器
    private final Counter errorTotal;
    // 活跃连接数
    private final Gauge activeConnections;
    // 熔断器状态
    private final Gauge circuitBreakerState;

    // 计时器缓存
    private final ConcurrentHashMap<String, Histogram.Timer> activeTimers = new ConcurrentHashMap<>();

    public MetricsCollector(String serviceName) {
        this(serviceName, CollectorRegistry.defaultRegistry);
    }

    public MetricsCollector(String serviceName, CollectorRegistry registry) {
        this.serviceName = serviceName;
        this.registry = registry;

        this.requestTotal = Counter.build()
                .name("framework_requests_total")
                .help("Total number of requests")
                .labelNames("service", "method", "protocol", "status")
                .register(registry);

        this.requestDuration = Histogram.build()
                .name("framework_request_duration_seconds")
                .help("Request duration in seconds")
                .labelNames("service", "method", "protocol")
                .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
                .register(registry);

        this.errorTotal = Counter.build()
                .name("framework_errors_total")
                .help("Total number of errors")
                .labelNames("service", "error_code", "protocol")
                .register(registry);

        this.activeConnections = Gauge.build()
                .name("framework_active_connections")
                .help("Number of active connections")
                .labelNames("service", "target")
                .register(registry);

        this.circuitBreakerState = Gauge.build()
                .name("framework_circuit_breaker_state")
                .help("Circuit breaker state (0=closed, 1=open, 2=half-open)")
                .labelNames("service", "target")
                .register(registry);

        logger.info("指标收集器已初始化: {}", serviceName);
    }

    /**
     * 记录请求开始
     */
    public String startRequest(String targetService, String method, String protocol) {
        String timerId = targetService + ":" + method + ":" + System.nanoTime();
        Histogram.Timer timer = requestDuration.labels(targetService, method, protocol).startTimer();
        activeTimers.put(timerId, timer);
        return timerId;
    }

    /**
     * 记录请求成功完成
     */
    public void recordSuccess(String timerId, String targetService, String method, String protocol) {
        requestTotal.labels(targetService, method, protocol, "success").inc();
        Histogram.Timer timer = activeTimers.remove(timerId);
        if (timer != null) {
            timer.observeDuration();
        }
    }

    /**
     * 记录请求失败
     */
    public void recordError(String timerId, String targetService, String method,
                            String protocol, String errorCode) {
        requestTotal.labels(targetService, method, protocol, "error").inc();
        errorTotal.labels(targetService, errorCode, protocol).inc();
        Histogram.Timer timer = activeTimers.remove(timerId);
        if (timer != null) {
            timer.observeDuration();
        }
    }

    /**
     * 记录请求延迟（手动方式）
     */
    public void recordLatency(String targetService, String method, String protocol, double durationSeconds) {
        requestDuration.labels(targetService, method, protocol).observe(durationSeconds);
    }

    /**
     * 更新活跃连接数
     */
    public void setActiveConnections(String targetService, double count) {
        activeConnections.labels(serviceName, targetService).set(count);
    }

    /**
     * 更新熔断器状态
     */
    public void setCircuitBreakerState(String targetService, int state) {
        circuitBreakerState.labels(serviceName, targetService).set(state);
    }

    /**
     * 获取请求总数
     */
    public double getRequestCount(String targetService, String method, String protocol, String status) {
        return requestTotal.labels(targetService, method, protocol, status).get();
    }

    /**
     * 获取错误总数
     */
    public double getErrorCount(String targetService, String errorCode, String protocol) {
        return errorTotal.labels(targetService, errorCode, protocol).get();
    }

    public CollectorRegistry getRegistry() {
        return registry;
    }

    public String getServiceName() {
        return serviceName;
    }
}
