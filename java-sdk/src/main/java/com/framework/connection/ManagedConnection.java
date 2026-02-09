package com.framework.connection;

import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;
import com.framework.protocol.router.ServiceEndpoint;
import io.netty.channel.Channel;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 受管连接
 * 
 * 封装 Netty Channel，提供连接状态管理和生命周期跟踪
 * 
 * **验证需求: 7.1, 7.2, 7.4**
 */
public class ManagedConnection {

    /**
     * 连接状态
     */
    public enum State {
        IDLE,       // 空闲
        ACTIVE,     // 活跃（正在使用）
        CLOSED      // 已关闭
    }

    private final String id;
    private final ServiceEndpoint endpoint;
    private final Channel channel;
    private final Instant createdAt;
    private volatile Instant lastUsedAt;
    private final AtomicReference<State> state;
    private final AtomicInteger activeRequests;

    public ManagedConnection(String id, ServiceEndpoint endpoint, Channel channel) {
        this.id = id;
        this.endpoint = endpoint;
        this.channel = channel;
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
        this.state = new AtomicReference<>(State.IDLE);
        this.activeRequests = new AtomicInteger(0);
    }

    /**
     * 标记连接为活跃状态
     * @return 是否成功标记（如果已关闭则返回 false）
     */
    public boolean acquire() {
        if (state.compareAndSet(State.IDLE, State.ACTIVE)) {
            activeRequests.incrementAndGet();
            lastUsedAt = Instant.now();
            return true;
        }
        // 已经是 ACTIVE 状态也允许（支持连接复用多个请求）
        if (state.get() == State.ACTIVE) {
            activeRequests.incrementAndGet();
            lastUsedAt = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 释放连接（一个请求完成）
     */
    public void release() {
        lastUsedAt = Instant.now();
        int remaining = activeRequests.decrementAndGet();
        if (remaining <= 0) {
            activeRequests.set(0);
            state.compareAndSet(State.ACTIVE, State.IDLE);
        }
    }

    /**
     * 关闭连接
     * @return 关闭的 Future
     */
    public CompletableFuture<Void> close() {
        state.set(State.CLOSED);
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (channel != null && channel.isOpen()) {
            channel.close().addListener(f -> {
                if (f.isSuccess()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(f.cause());
                }
            });
        } else {
            future.complete(null);
        }
        return future;
    }

    /**
     * 检查连接是否健康
     */
    public boolean isHealthy() {
        return state.get() != State.CLOSED
                && channel != null
                && channel.isActive();
    }

    /**
     * 检查连接是否空闲
     */
    public boolean isIdle() {
        return state.get() == State.IDLE && activeRequests.get() == 0;
    }

    /**
     * 检查连接是否已关闭
     */
    public boolean isClosed() {
        return state.get() == State.CLOSED || channel == null || !channel.isActive();
    }

    /**
     * 检查连接是否超过空闲超时
     */
    public boolean isIdleTimedOut(long idleTimeoutMs) {
        if (!isIdle()) return false;
        long idleDuration = Instant.now().toEpochMilli() - lastUsedAt.toEpochMilli();
        return idleDuration > idleTimeoutMs;
    }

    /**
     * 检查连接是否超过最大存活时间
     */
    public boolean isExpired(long maxLifetimeMs) {
        long lifetime = Instant.now().toEpochMilli() - createdAt.toEpochMilli();
        return lifetime > maxLifetimeMs;
    }

    /**
     * 获取活跃请求数
     */
    public int getActiveRequestCount() {
        return activeRequests.get();
    }

    // Getters

    public String getId() { return id; }
    public ServiceEndpoint getEndpoint() { return endpoint; }
    public Channel getChannel() { return channel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public State getState() { return state.get(); }

    @Override
    public String toString() {
        return "ManagedConnection{" +
                "id='" + id + '\'' +
                ", endpoint=" + endpoint +
                ", state=" + state.get() +
                ", activeRequests=" + activeRequests.get() +
                ", createdAt=" + createdAt +
                ", lastUsedAt=" + lastUsedAt +
                '}';
    }
}
