# 容错模块

## 概述

容错模块提供重试策略和熔断器模式，用于提高系统的可靠性和容错能力。

## 核心组件

### RetryPolicy

重试策略配置，支持：

- 指数退避算法
- 可配置的重试次数和延迟
- 可重试错误类型配置

### RetryExecutor

重试执行器，提供：

- 同步重试执行
- 异步重试执行
- 上下文取消支持

### CircuitBreaker

熔断器实现，支持：

- 三种状态：Closed（关闭）、Open（打开）、Half-Open（半开）
- 自动状态转换
- 失败阈值和成功阈值配置

## 使用示例

### 重试策略

```go
import (
    "time"
    "github.com/framework/golang-sdk/resilience"
)

// 使用默认策略
policy := resilience.DefaultRetryPolicy()

// 自定义策略
policy := resilience.NewRetryPolicyBuilder().
    MaxAttempts(5).
    InitialDelay(200 * time.Millisecond).
    MaxDelay(10 * time.Second).
    Multiplier(2.0).
    Build()
```

### 重试执行

```go
executor := resilience.NewRetryExecutor(policy)

// 同步执行
err := executor.Execute(func() error {
    return doSomething()
})

// 带返回值的执行
result, err := executor.ExecuteWithResult(func() (interface{}, error) {
    return fetchData()
})

// 异步执行
ctx := context.Background()
resultChan := executor.ExecuteAsync(ctx, func() error {
    return doSomething()
})
err := <-resultChan
```

### 熔断器

```go
// 创建熔断器
cb := resilience.NewCircuitBreaker(
    "my-service",
    5,  // 失败阈值
    3,  // 成功阈值
    30*time.Second, // 超时时间
)

// 使用默认配置
cb := resilience.NewDefaultCircuitBreaker("my-service")

// 执行操作
err := cb.Execute(func() error {
    return callService()
})

// 带返回值的执行
result, err := cb.ExecuteWithResult(func() (interface{}, error) {
    return callService()
})

// 检查状态
state := cb.GetState() // StateClosed, StateOpen, StateHalfOpen

// 重置熔断器
cb.Reset()
```

### 组合使用

```go
// 结合重试和熔断器
policy := resilience.DefaultRetryPolicy()
executor := resilience.NewRetryExecutor(policy)
cb := resilience.NewDefaultCircuitBreaker("my-service")

err := executor.Execute(func() error {
    return cb.Execute(func() error {
        return callRemoteService()
    })
})
```

## 熔断器状态转换

1. **Closed → Open**: 连续失败达到失败阈值
2. **Open → Half-Open**: 超时后自动转换
3. **Half-Open → Closed**: 连续成功达到成功阈值
4. **Half-Open → Open**: 任何失败立即转回

## 验证需求

- **需求 8.3**: 请求超时取消和重试
- **需求 8.4**: 网络中断处理和熔断器
