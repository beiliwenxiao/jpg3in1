package com.framework.resilience;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 重试执行器
 * 
 * 根据 RetryPolicy 配置，对可重试的操作执行指数退避重试。
 * 支持同步和异步两种执行模式。
 */
public class RetryExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryExecutor.class);
    
    private final RetryPolicy policy;
    
    public RetryExecutor(RetryPolicy policy) {
        this.policy = policy != null ? policy : RetryPolicy.defaultPolicy();
    }
    
    /**
     * 同步执行带重试的操作
     */
    public <T> T execute(Callable<T> operation) {
        FrameworkException lastException = null;
        
        for (int attempt = 0; attempt < policy.getMaxAttempts(); attempt++) {
            try {
                return operation.call();
            } catch (FrameworkException e) {
                lastException = e;
                if (!policy.isRetryable(e.getErrorCode()) || attempt >= policy.getMaxAttempts() - 1) {
                    throw e;
                }
                long delay = policy.calculateDelay(attempt);
                logger.warn("操作失败，第 {} 次重试，延迟 {}ms，错误: {}", 
                           attempt + 1, delay, e.getMessage());
                sleep(delay);
            } catch (Exception e) {
                throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                            "操作执行失败: " + e.getMessage(), e);
            }
        }
        
        // 不应到达此处，但作为安全保障
        throw lastException != null ? lastException 
                : new FrameworkException(ErrorCode.INTERNAL_ERROR, "重试耗尽");
    }

    /**
     * 异步执行带重试的操作
     */
    public <T> CompletableFuture<T> executeAsync(Callable<T> operation, 
                                                  ScheduledExecutorService scheduler) {
        return executeAsyncInternal(operation, scheduler, 0);
    }
    
    private <T> CompletableFuture<T> executeAsyncInternal(Callable<T> operation,
                                                           ScheduledExecutorService scheduler,
                                                           int attempt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).exceptionallyCompose(throwable -> {
            Throwable cause = throwable instanceof CompletionException 
                    ? throwable.getCause() : throwable;
            
            if (cause instanceof FrameworkException fe 
                    && policy.isRetryable(fe.getErrorCode()) 
                    && attempt < policy.getMaxAttempts() - 1) {
                
                long delay = policy.calculateDelay(attempt);
                logger.warn("异步操作失败，第 {} 次重试，延迟 {}ms，错误: {}", 
                           attempt + 1, delay, fe.getMessage());
                
                CompletableFuture<T> delayed = new CompletableFuture<>();
                scheduler.schedule(() -> {
                    executeAsyncInternal(operation, scheduler, attempt + 1)
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    delayed.completeExceptionally(ex);
                                } else {
                                    delayed.complete(result);
                                }
                            });
                }, delay, TimeUnit.MILLISECONDS);
                return delayed;
            }
            
            return CompletableFuture.failedFuture(cause);
        });
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "重试被中断", e);
        }
    }
}
