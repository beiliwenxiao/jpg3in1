package com.framework.resilience;

import com.framework.exception.ErrorCode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 重试策略配置
 * 
 * 定义重试的最大次数、延迟参数和可重试的错误类型。
 * 使用指数退避算法计算重试间隔。
 */
public class RetryPolicy {
    
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final Set<ErrorCode> retryableErrors;
    
    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.initialDelayMs = builder.initialDelayMs;
        this.maxDelayMs = builder.maxDelayMs;
        this.multiplier = builder.multiplier;
        this.retryableErrors = Collections.unmodifiableSet(builder.retryableErrors);
    }
    
    /**
     * 默认重试策略：最多 3 次，初始延迟 100ms，最大延迟 5000ms，倍数 2
     */
    public static RetryPolicy defaultPolicy() {
        return builder().build();
    }
    
    /**
     * 不重试策略
     */
    public static RetryPolicy noRetry() {
        return builder().maxAttempts(1).build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public long getInitialDelayMs() {
        return initialDelayMs;
    }
    
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public Set<ErrorCode> getRetryableErrors() {
        return retryableErrors;
    }

    /**
     * 判断给定的错误码是否可重试
     */
    public boolean isRetryable(ErrorCode errorCode) {
        return retryableErrors.contains(errorCode);
    }
    
    /**
     * 计算第 attempt 次重试的延迟时间（指数退避）
     * 
     * @param attempt 当前重试次数（从 0 开始）
     * @return 延迟时间（毫秒）
     */
    public long calculateDelay(int attempt) {
        if (attempt <= 0) {
            return initialDelayMs;
        }
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));
        return Math.min(delay, maxDelayMs);
    }
    
    public static class Builder {
        private int maxAttempts = 3;
        private long initialDelayMs = 100;
        private long maxDelayMs = 5000;
        private double multiplier = 2.0;
        private Set<ErrorCode> retryableErrors = EnumSet.of(
                ErrorCode.TIMEOUT,
                ErrorCode.SERVICE_UNAVAILABLE,
                ErrorCode.CONNECTION_ERROR
        );
        
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }
        
        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = Math.max(0, initialDelayMs);
            return this;
        }
        
        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = Math.max(0, maxDelayMs);
            return this;
        }
        
        public Builder multiplier(double multiplier) {
            this.multiplier = Math.max(1.0, multiplier);
            return this;
        }
        
        public Builder retryableErrors(Set<ErrorCode> retryableErrors) {
            this.retryableErrors = EnumSet.copyOf(retryableErrors);
            return this;
        }
        
        public Builder addRetryableError(ErrorCode errorCode) {
            this.retryableErrors.add(errorCode);
            return this;
        }
        
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
