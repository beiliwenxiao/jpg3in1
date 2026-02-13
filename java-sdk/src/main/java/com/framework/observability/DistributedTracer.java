package com.framework.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式追踪器
 * 
 * 集成 OpenTelemetry SDK，生成和传播 trace ID 和 span ID。
 * 
 * 需求: 10.3
 */
public class DistributedTracer {

    private static final Logger logger = LoggerFactory.getLogger(DistributedTracer.class);

    private final Tracer tracer;
    private final String serviceName;
    private final ConcurrentHashMap<String, SpanContext> activeSpans = new ConcurrentHashMap<>();

    public DistributedTracer(String serviceName, OpenTelemetry openTelemetry) {
        this.serviceName = serviceName;
        this.tracer = openTelemetry.getTracer(serviceName, "1.0.0");
        logger.info("分布式追踪器已初始化: {}", serviceName);
    }

    /**
     * 创建新的根 Span
     */
    public SpanHandle startSpan(String operationName) {
        Span span = tracer.spanBuilder(operationName)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        return new SpanHandle(span);
    }

    /**
     * 创建子 Span
     */
    public SpanHandle startSpan(String operationName, SpanHandle parent) {
        Span span = tracer.spanBuilder(operationName)
                .setParent(Context.current().with(parent.getSpan()))
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        return new SpanHandle(span);
    }

    /**
     * 创建服务端 Span
     */
    public SpanHandle startServerSpan(String operationName) {
        Span span = tracer.spanBuilder(operationName)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        return new SpanHandle(span);
    }

    /**
     * 从传播的上下文中提取追踪信息
     */
    public Map<String, String> extractTraceContext(SpanHandle spanHandle) {
        SpanContext ctx = spanHandle.getSpan().getSpanContext();
        Map<String, String> context = new ConcurrentHashMap<>();
        context.put("traceId", ctx.getTraceId());
        context.put("spanId", ctx.getSpanId());
        context.put("traceFlags", ctx.getTraceFlags().asHex());
        return context;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * Span 句柄，封装 OpenTelemetry Span 的操作
     */
    public static class SpanHandle implements AutoCloseable {
        private final Span span;

        SpanHandle(Span span) {
            this.span = span;
        }

        public SpanHandle setAttribute(String key, String value) {
            span.setAttribute(key, value);
            return this;
        }

        public SpanHandle setAttribute(String key, long value) {
            span.setAttribute(key, value);
            return this;
        }

        public SpanHandle setAttribute(String key, boolean value) {
            span.setAttribute(key, value);
            return this;
        }

        public SpanHandle setStatus(StatusCode statusCode, String description) {
            span.setStatus(statusCode, description);
            return this;
        }

        public SpanHandle recordException(Throwable exception) {
            span.recordException(exception);
            return this;
        }

        public SpanHandle addEvent(String name) {
            span.addEvent(name);
            return this;
        }

        public SpanHandle addEvent(String name, io.opentelemetry.api.common.Attributes attributes) {
            span.addEvent(name, attributes);
            return this;
        }

        public String getTraceId() {
            return span.getSpanContext().getTraceId();
        }

        public String getSpanId() {
            return span.getSpanContext().getSpanId();
        }

        Span getSpan() {
            return span;
        }

        @Override
        public void close() {
            span.end();
        }
    }
}
