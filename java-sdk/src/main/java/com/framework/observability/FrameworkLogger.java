package com.framework.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 框架日志记录器
 * 
 * 封装 SLF4J/Logback，提供结构化日志记录，自动包含上下文信息
 * （请求 ID、服务名称、时间戳等）。支持日志级别动态调整。
 * 
 * 需求: 10.1, 10.5, 10.6
 */
public class FrameworkLogger {

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_SERVICE_NAME = "serviceName";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private final Logger logger;
    private final String serviceName;
    private static final AtomicReference<LogLevel> globalLevel = new AtomicReference<>(LogLevel.INFO);

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    public FrameworkLogger(Class<?> clazz, String serviceName) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.serviceName = serviceName;
    }

    public FrameworkLogger(String name, String serviceName) {
        this.logger = LoggerFactory.getLogger(name);
        this.serviceName = serviceName;
    }

    /**
     * 设置请求上下文（线程级别）
     */
    public static void setRequestContext(String requestId, String traceId, String spanId) {
        MDC.put(MDC_REQUEST_ID, requestId);
        if (traceId != null) MDC.put(MDC_TRACE_ID, traceId);
        if (spanId != null) MDC.put(MDC_SPAN_ID, spanId);
    }

    /**
     * 生成并设置新的请求上下文
     */
    public static String newRequestContext() {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);
        return requestId;
    }

    /**
     * 清除请求上下文
     */
    public static void clearRequestContext() {
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }

    /**
     * 动态调整全局日志级别
     */
    public static void setGlobalLevel(LogLevel level) {
        globalLevel.set(level);
        // 通过 Logback API 动态调整
        try {
            ch.qos.logback.classic.Logger rootLogger =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(toLogbackLevel(level));
        } catch (Exception e) {
            // 非 Logback 环境，忽略
        }
    }

    public static LogLevel getGlobalLevel() {
        return globalLevel.get();
    }

    // ==================== 日志方法 ====================

    public void trace(String message, Object... args) {
        withServiceContext(() -> logger.trace(message, args));
    }

    public void debug(String message, Object... args) {
        withServiceContext(() -> logger.debug(message, args));
    }

    public void info(String message, Object... args) {
        withServiceContext(() -> logger.info(message, args));
    }

    public void warn(String message, Object... args) {
        withServiceContext(() -> logger.warn(message, args));
    }

    public void error(String message, Object... args) {
        withServiceContext(() -> logger.error(message, args));
    }

    public void error(String message, Throwable throwable) {
        withServiceContext(() -> logger.error(message, throwable));
    }

    /**
     * 记录请求日志
     */
    public void logRequest(String method, String service, String target, Map<String, String> headers) {
        withServiceContext(() ->
                logger.info("REQUEST: {} {}/{} headers={}", method, service, target, headers));
    }

    /**
     * 记录响应日志
     */
    public void logResponse(String method, String service, String target, int statusCode, long durationMs) {
        withServiceContext(() ->
                logger.info("RESPONSE: {} {}/{} status={} duration={}ms",
                        method, service, target, statusCode, durationMs));
    }

    /**
     * 记录错误日志（带上下文）
     */
    public void logError(String operation, Throwable error, Map<String, Object> context) {
        withServiceContext(() ->
                logger.error("ERROR: {} context={}", operation, context, error));
    }

    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    private void withServiceContext(Runnable action) {
        MDC.put(MDC_SERVICE_NAME, serviceName);
        try {
            action.run();
        } finally {
            // 不清除 serviceName，保持上下文
        }
    }

    private static ch.qos.logback.classic.Level toLogbackLevel(LogLevel level) {
        return switch (level) {
            case TRACE -> ch.qos.logback.classic.Level.TRACE;
            case DEBUG -> ch.qos.logback.classic.Level.DEBUG;
            case INFO -> ch.qos.logback.classic.Level.INFO;
            case WARN -> ch.qos.logback.classic.Level.WARN;
            case ERROR -> ch.qos.logback.classic.Level.ERROR;
        };
    }
}
