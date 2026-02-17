package observability_test

import (
	"context"
	"fmt"
	"time"

	"github.com/framework/golang-sdk/observability"
	"go.opentelemetry.io/otel/attribute"
)

// Example_basicUsage 演示基本使用
func Example_basicUsage() {
	// 创建可观测性管理器
	config := observability.Config{
		ServiceName: "example-service",
		MetricsPort: 9090,
		LogLevel:    observability.LogLevelInfo,
	}

	obs := observability.NewObservabilityManager(config)

	// 启动指标服务器（提供 /metrics 和 /health 端点）
	obs.StartMetricsServer()

	ctx := context.Background()

	// 使用日志
	obs.Logger().Info(ctx, "Service started",
		observability.Field{Key: "version", Value: "1.0.0"},
		observability.Field{Key: "port", Value: 8080})

	fmt.Println("Observability manager initialized")
	// Output: Observability manager initialized
}

// Example_logging 演示日志记录
func Example_logging() {
	logger := observability.NewLogger("my-service")

	ctx := context.WithValue(context.Background(), "request_id", "req-12345")
	ctx = context.WithValue(ctx, "timestamp", "2024-01-01T12:00:00Z")

	// 不同级别的日志
	logger.Debug(ctx, "Debug information", observability.Field{Key: "detail", Value: "some detail"})
	logger.Info(ctx, "Processing request", observability.Field{Key: "user_id", Value: "user-001"})
	logger.Warn(ctx, "High memory usage", observability.Field{Key: "usage", Value: "85%"})
	logger.Error(ctx, "Failed to connect", observability.Field{Key: "error", Value: "connection timeout"})

	fmt.Println("Logging examples completed")
	// Output: Logging examples completed
}

// Example_metrics 演示指标收集
func Example_metrics() {
	metrics := observability.NewMetricsCollector("my-service")

	// 记录请求
	start := time.Now()
	// ... 执行业务逻辑 ...
	duration := time.Since(start)
	metrics.RecordRequest("my-service", "handleRequest", "http", "success", duration)

	// 记录错误
	metrics.RecordError("my-service", "handleRequest", "500")

	// 记录吞吐量
	metrics.RecordThroughput("my-service", "in", 1024)  // 接收 1KB
	metrics.RecordThroughput("my-service", "out", 2048) // 发送 2KB

	// 管理连接数
	metrics.IncActiveConnections()
	defer metrics.DecActiveConnections()

	fmt.Println("Metrics recorded")
	// Output: Metrics recorded
}

// Example_tracing 演示分布式追踪
func Example_tracing() {
	tracer := observability.NewTracer("my-service")
	ctx := context.Background()

	// 开始一个 span
	ctx, span := tracer.StartSpan(ctx, "process-order",
		attribute.String("order_id", "order-123"),
		attribute.Int("items", 5))

	// 添加事件
	tracer.AddEvent(ctx, "validation-started")

	// 设置额外属性
	tracer.SetAttributes(ctx,
		attribute.String("customer_id", "cust-456"),
		attribute.Float64("total", 99.99))

	// 提取 trace ID 和 span ID
	traceID := tracer.ExtractTraceID(ctx)
	spanID := tracer.ExtractSpanID(ctx)

	fmt.Printf("Trace ID exists: %v, Span ID exists: %v\n",
		len(traceID) > 0, len(spanID) > 0)

	// 结束 span
	tracer.EndSpan(span, nil)

	// Output: Trace ID exists: true, Span ID exists: true
}

// Example_healthCheck 演示健康检查
func Example_healthCheck() {
	healthChecker := observability.NewHealthChecker("my-service")

	// 注册健康检查
	healthChecker.RegisterCheck(
		observability.NewSimpleHealthCheck("database", func(ctx context.Context) error {
			// 检查数据库连接
			return nil // 假设连接正常
		}),
	)

	healthChecker.RegisterCheck(
		observability.NewSimpleHealthCheck("cache", func(ctx context.Context) error {
			// 检查缓存连接
			return nil // 假设连接正常
		}),
	)

	// 执行健康检查
	ctx := context.Background()
	response := healthChecker.Check(ctx)

	fmt.Printf("Health status: %s\n", response.Status)
	fmt.Printf("Number of checks: %d\n", len(response.Checks))

	// Output:
	// Health status: healthy
	// Number of checks: 2
}

// Example_integration 演示完整集成
func Example_integration() {
	// 创建可观测性管理器
	config := observability.Config{
		ServiceName: "order-service",
		MetricsPort: 9090,
		LogLevel:    observability.LogLevelInfo,
	}

	obs := observability.NewObservabilityManager(config)
	obs.StartMetricsServer()

	// 注册健康检查
	obs.HealthChecker().RegisterCheck(
		observability.NewSimpleHealthCheck("ready", func(ctx context.Context) error {
			return nil
		}),
	)

	ctx := context.Background()

	// 模拟处理请求
	obs.Logger().Info(ctx, "Received order request",
		observability.Field{Key: "order_id", Value: "order-789"})

	// 开始追踪
	ctx, span := obs.Tracer().StartSpan(ctx, "process-order")
	defer obs.Tracer().EndSpan(span, nil)

	// 记录指标
	start := time.Now()
	obs.Metrics().IncActiveConnections()
	defer obs.Metrics().DecActiveConnections()

	// ... 业务逻辑 ...
	time.Sleep(10 * time.Millisecond)

	duration := time.Since(start)
	obs.Metrics().RecordRequest("order-service", "processOrder", "http", "success", duration)

	obs.Logger().Info(ctx, "Order processed successfully")

	fmt.Println("Request processed with full observability")
	// Output: Request processed with full observability
}

// Example_dynamicLogLevel 演示动态调整日志级别
func Example_dynamicLogLevel() {
	config := observability.Config{
		ServiceName: "my-service",
		MetricsPort: 9090,
		LogLevel:    observability.LogLevelInfo,
	}

	obs := observability.NewObservabilityManager(config)

	// 运行时调整日志级别
	obs.SetLogLevel(observability.LogLevelDebug)
	fmt.Println("Log level set to Debug")

	obs.SetLogLevel(observability.LogLevelError)
	fmt.Println("Log level set to Error")

	// Output:
	// Log level set to Debug
	// Log level set to Error
}
