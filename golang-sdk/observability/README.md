# Observability 可观测性模块

## 概述

可观测性模块提供了完整的日志记录、指标收集、分布式追踪和健康检查功能，帮助监控和调试分布式系统。

## 功能特性

### 1. 日志记录 (Logger)
- 基于 GoFrame glog 实现
- 支持多个日志级别：Debug、Info、Warn、Error
- 自动包含上下文信息（请求ID、时间戳、服务名称）
- 支持运行时动态调整日志级别
- 结构化日志字段

### 2. 指标收集 (Metrics)
- 集成 Prometheus 客户端
- 收集关键性能指标：
  - 请求延迟（直方图）
  - 请求总数（计数器）
  - 错误率（计数器）
  - 吞吐量（字节数）
  - 活跃连接数（仪表盘）
- 通过 `/metrics` 端点暴露指标

### 3. 分布式追踪 (Tracer)
- 集成 OpenTelemetry SDK
- 自动生成和传播 trace ID 和 span ID
- 支持跨服务追踪
- 记录 span 事件和属性
- 错误追踪和状态记录

### 4. 健康检查 (Health)
- 提供 `/health` 端点
- 支持注册多个健康检查
- 返回详细的健康状态
- 支持三种状态：healthy、unhealthy、degraded

## 使用示例

### 基本使用

```go
package main

import (
    "context"
    "time"
    
    "github.com/framework/golang-sdk/observability"
)

func main() {
    // 创建可观测性管理器
    config := observability.Config{
        ServiceName: "my-service",
        MetricsPort: 9090,
        LogLevel:    observability.LogLevelInfo,
    }
    
    obs := observability.NewObservabilityManager(config)
    
    // 启动指标服务器
    obs.StartMetricsServer()
    
    // 使用日志
    ctx := context.Background()
    obs.Logger().Info(ctx, "Service started", 
        observability.Field{Key: "version", Value: "1.0.0"})
    
    // 记录指标
    start := time.Now()
    // ... 执行业务逻辑 ...
    duration := time.Since(start)
    obs.Metrics().RecordRequest("my-service", "handleRequest", "http", "success", duration)
    
    // 使用追踪
    ctx, span := obs.Tracer().StartSpan(ctx, "process-request")
    defer obs.Tracer().EndSpan(span, nil)
    
    // 注册健康检查
    obs.HealthChecker().RegisterCheck(
        observability.NewSimpleHealthCheck("database", func(ctx context.Context) error {
            // 检查数据库连接
            return nil
        }),
    )
}
```

### 日志记录

```go
logger := obs.Logger()

// 不同级别的日志
logger.Debug(ctx, "Debug message", 
    observability.Field{Key: "detail", Value: "some detail"})
logger.Info(ctx, "Info message")
logger.Warn(ctx, "Warning message")
logger.Error(ctx, "Error message", 
    observability.Field{Key: "error", Value: err.Error()})

// 动态调整日志级别
obs.SetLogLevel(observability.LogLevelDebug)
```

### 指标收集

```go
metrics := obs.Metrics()

// 记录请求
metrics.RecordRequest("service", "method", "grpc", "success", duration)

// 记录错误
metrics.RecordError("service", "method", "500")

// 记录吞吐量
metrics.RecordThroughput("service", "in", 1024)  // 接收字节
metrics.RecordThroughput("service", "out", 2048) // 发送字节

// 管理连接数
metrics.IncActiveConnections()
defer metrics.DecActiveConnections()
```

### 分布式追踪

```go
tracer := obs.Tracer()

// 开始 span
ctx, span := tracer.StartSpan(ctx, "operation-name",
    attribute.String("key", "value"))
defer tracer.EndSpan(span, err)

// 添加事件
tracer.AddEvent(ctx, "processing-started")

// 设置属性
tracer.SetAttributes(ctx,
    attribute.Int("count", 10),
    attribute.String("status", "processing"))

// 提取 trace ID 和 span ID
traceID := tracer.ExtractTraceID(ctx)
spanID := tracer.ExtractSpanID(ctx)
```

### 健康检查

```go
healthChecker := obs.HealthChecker()

// 注册健康检查
healthChecker.RegisterCheck(
    observability.NewSimpleHealthCheck("redis", func(ctx context.Context) error {
        // 检查 Redis 连接
        return redisClient.Ping(ctx).Err()
    }),
)

healthChecker.RegisterCheck(
    observability.NewSimpleHealthCheck("etcd", func(ctx context.Context) error {
        // 检查 etcd 连接
        _, err := etcdClient.Get(ctx, "health-check")
        return err
    }),
)

// 手动执行检查
response := healthChecker.Check(ctx)
fmt.Printf("Health status: %s\n", response.Status)
```

## 端点说明

### Metrics 端点
- **URL**: `http://localhost:9090/metrics`
- **格式**: Prometheus 文本格式
- **用途**: 供 Prometheus 抓取指标

### Health 端点
- **URL**: `http://localhost:9090/health`
- **格式**: JSON
- **响应示例**:
```json
{
  "status": "healthy",
  "service": "my-service",
  "timestamp": "2024-01-01T12:00:00Z",
  "checks": {
    "database": {
      "status": "healthy"
    },
    "redis": {
      "status": "healthy"
    }
  }
}
```

## 配置说明

### Config 结构

```go
type Config struct {
    ServiceName string   // 服务名称
    MetricsPort int      // 指标端口（默认 9090）
    LogLevel    LogLevel // 日志级别
}
```

### 日志级别

- `LogLevelDebug`: 调试级别，输出所有日志
- `LogLevelInfo`: 信息级别，输出 Info、Warn、Error
- `LogLevelWarn`: 警告级别，输出 Warn、Error
- `LogLevelError`: 错误级别，仅输出 Error

## 最佳实践

1. **日志记录**
   - 在关键操作点记录日志
   - 使用结构化字段而不是字符串拼接
   - 包含足够的上下文信息（请求ID、用户ID等）
   - 避免记录敏感信息

2. **指标收集**
   - 记录所有请求的延迟和状态
   - 监控错误率和错误类型
   - 跟踪资源使用情况（连接数、内存等）
   - 使用合适的指标类型（Counter、Gauge、Histogram）

3. **分布式追踪**
   - 在服务边界创建 span
   - 传播 trace context 到下游服务
   - 记录重要的业务事件
   - 在错误时记录详细信息

4. **健康检查**
   - 检查关键依赖（数据库、缓存、消息队列）
   - 设置合理的超时时间
   - 避免在健康检查中执行重操作
   - 返回有意义的错误信息

## 与其他模块集成

可观测性模块可以与框架的其他模块无缝集成：

```go
// 在连接管理器中使用
conn, err := connManager.GetConnection(endpoint)
if err != nil {
    obs.Logger().Error(ctx, "Failed to get connection", 
        observability.Field{Key: "error", Value: err.Error()})
    obs.Metrics().RecordError("connection", "get", "CONNECTION_ERROR")
}

// 在协议处理器中使用
ctx, span := obs.Tracer().StartSpan(ctx, "handle-request")
defer obs.Tracer().EndSpan(span, err)

start := time.Now()
response, err := handler.Handle(ctx, request)
duration := time.Since(start)

obs.Metrics().RecordRequest("handler", "handle", "http", status, duration)
```

## 依赖

- `github.com/gogf/gf/v2` - GoFrame 框架（日志）
- `github.com/prometheus/client_golang` - Prometheus 客户端
- `go.opentelemetry.io/otel` - OpenTelemetry SDK

## 验证需求

本模块实现并验证以下需求：

- **需求 10.1**: 记录所有请求和响应的日志
- **需求 10.2**: 收集性能指标（延迟、吞吐量、错误率）
- **需求 10.3**: 支持分布式追踪（OpenTelemetry）
- **需求 10.4**: 提供健康检查端点
- **需求 10.5**: 日志包含上下文信息（请求ID、时间戳、服务名称）
- **需求 10.6**: 支持日志级别的动态调整
