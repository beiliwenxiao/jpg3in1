package com.framework.resilience;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器实现
 * 
 * 实现熔断器状态机（Closed → Open → Half-Open → Closed），
 * 防止对不可用服务的持续调用导致级联失败。
 * 
 * 状态转换：
 * - Closed: 正常状态，允许所有请求。连续失败达到阈值后转为 Open。
 * - Open: 熔断状态，拒绝所有请求。超时后转为 Half-Open。
 * - Half-Open: 半开状态，允许少量请求测试服务是否恢复。
 *   成功达到阈值转为 Closed，失败则转回 Open。
 */
public class CircuitBreaker {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }
    
    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutMs;
    
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;

    public CircuitBreaker(String name, int failureThreshold, int successThreshold, long timeoutMs) {
        this.name = name;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.successThreshold = Math.max(1, successThreshold);
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
    }
    
    /**
     * 使用默认配置创建熔断器：失败阈值 5，成功阈值 3，超时 30 秒
     */
    public CircuitBreaker(String name) {
        this(name, 5, 3, 30000);
    }
    
    /**
     * 通过熔断器执行操作
     */
    public <T> T execute(Callable<T> operation) {
        if (!allowRequest()) {
            throw new FrameworkException(ErrorCode.SERVICE_UNAVAILABLE,
                    "熔断器 [" + name + "] 处于打开状态，请求被拒绝");
        }
        
        try {
            T result = operation.call();
            recordSuccess();
            return result;
        } catch (FrameworkException e) {
            recordFailure();
            throw e;
        } catch (Exception e) {
            recordFailure();
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                    "操作执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否允许请求通过
     */
    public boolean allowRequest() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否超时，超时则转为半开状态
                if (System.currentTimeMillis() - lastFailureTime.get() >= timeoutMs) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        successCount.set(0);
                        logger.info("熔断器 [{}] 从 OPEN 转为 HALF_OPEN", name);
                    }
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 记录成功调用
     */
    public void recordSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= successThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    failureCount.set(0);
                    successCount.set(0);
                    logger.info("熔断器 [{}] 从 HALF_OPEN 转为 CLOSED", name);
                }
            }
        } else if (currentState == State.CLOSED) {
            // 成功时重置失败计数
            failureCount.set(0);
        }
    }
    
    /**
     * 记录失败调用
     */
    public void recordFailure() {
        State currentState = state.get();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (currentState == State.HALF_OPEN) {
            // 半开状态下失败，立即转回 Open
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                successCount.set(0);
                logger.warn("熔断器 [{}] 从 HALF_OPEN 转回 OPEN", name);
            }
        } else if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    logger.warn("熔断器 [{}] 从 CLOSED 转为 OPEN，连续失败 {} 次", name, failures);
                }
            }
        }
    }
    
    /**
     * 重置熔断器到初始状态
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        logger.info("熔断器 [{}] 已重置", name);
    }
    
    public State getState() {
        return state.get();
    }
    
    public String getName() {
        return name;
    }
    
    public int getFailureCount() {
        return failureCount.get();
    }
    
    public int getSuccessCount() {
        return successCount.get();
    }
    
    public int getFailureThreshold() {
        return failureThreshold;
    }
    
    public int getSuccessThreshold() {
        return successThreshold;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
}
