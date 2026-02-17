package main

import (
	"context"
	"fmt"
	"time"

	"github.com/framework/golang-sdk/observability"
	"go.opentelemetry.io/otel/attribute"
)

func main() {
	// 创建可观测性管理器
	config := observability.Config{
		ServiceName: "demo-service",
		MetricsPort: 9090,
		LogLevel:    observability.LogLevelInfo,
	}

	obs := observability.NewObservabilityManager(config)

	// 启动指标服务器
	// 提供以下端点：
	// - http://localhost:9090/metrics (Prometheus 指标)
	// - http://localhost:9090/health (健康检查)
	if err := obs.StartMetricsServer(); err != nil {
		fmt.Printf("Failed to start metrics server: %v\n", err)
		return
	}

	fmt.Println("=== 可观测性示例 ===")
	fmt.Println("指标端点: http://localhost:9090/metrics")
	fmt.Println("健康检查端点: http://localhost:9090/health")
	fmt.Println()

	// 注册健康检查
	obs.HealthChecker().RegisterCheck(
		observability.NewSimpleHealthCheck("database", func(ctx context.Context) error {
			// 模拟数据库健康检查
			fmt.Println("[健康检查] 检查数据库连接...")
			return nil // 假设连接正常
		}),
	)

	obs.HealthChecker().RegisterCheck(
		observability.NewSimpleHealthCheck("cache", func(ctx context.Context) error {
			// 模拟缓存健康检查
			fmt.Println("[健康检查] 检查缓存连接...")
			return nil // 假设连接正常
		}),
	)

	// 模拟处理多个请求
	for i := 1; i <= 3; i++ {
		fmt.Printf("\n--- 处理请求 #%d ---\n", i)
		processRequest(obs, i)
		time.Sleep(500 * time.Millisecond)
	}

	// 执行健康检查
	fmt.Println("\n--- 执行健康检查 ---")
	ctx := context.Background()
	healthResponse := obs.HealthChecker().Check(ctx)
	fmt.Printf("服务状态: %s\n", healthResponse.Status)
	fmt.Printf("检查项数量: %d\n", len(healthResponse.Checks))
	for name, result := range healthResponse.Checks {
		fmt.Printf("  - %s: %s\n", name, result.Status)
	}

	// 演示动态调整日志级别
	fmt.Println("\n--- 动态调整日志级别 ---")
	obs.SetLogLevel(observability.LogLevelDebug)
	obs.Logger().Debug(ctx, "这是调试日志，现在可以看到了")

	obs.SetLogLevel(observability.LogLevelError)
	obs.Logger().Info(ctx, "这是信息日志，现在看不到了")
	obs.Logger().Error(ctx, "这是错误日志，仍然可以看到")

	fmt.Println("\n=== 示例完成 ===")
	fmt.Println("提示：可以访问 http://localhost:9090/metrics 查看 Prometheus 指标")
	fmt.Println("提示：可以访问 http://localhost:9090/health 查看健康状态")

	// 保持服务运行一段时间
	time.Sleep(5 * time.Second)
}

func processRequest(obs *observability.ObservabilityManager, requestID int) {
	// 创建带请求ID的上下文
	ctx := context.WithValue(context.Background(), "request_id", fmt.Sprintf("req-%d", requestID))
	ctx = context.WithValue(ctx, "timestamp", time.Now().Format(time.RFC3339))

	// 记录请求开始
	obs.Logger().Info(ctx, "收到请求",
		observability.Field{Key: "request_id", Value: requestID},
		observability.Field{Key: "method", Value: "GET"},
		observability.Field{Key: "path", Value: "/api/orders"})

	// 开始分布式追踪
	ctx, span := obs.Tracer().StartSpan(ctx, "handle-request",
		attribute.Int("request_id", requestID),
		attribute.String("method", "GET"))
	defer func() {
		obs.Tracer().EndSpan(span, nil)
	}()

	// 增加活跃连接数
	obs.Metrics().IncActiveConnections()
	defer obs.Metrics().DecActiveConnections()

	// 模拟业务处理
	start := time.Now()

	// 步骤1：验证请求
	obs.Tracer().AddEvent(ctx, "validation-started")
	obs.Logger().Debug(ctx, "验证请求参数")
	time.Sleep(50 * time.Millisecond)

	// 步骤2：查询数据库
	obs.Tracer().AddEvent(ctx, "database-query-started")
	obs.Logger().Debug(ctx, "查询数据库")
	time.Sleep(100 * time.Millisecond)

	// 步骤3：处理业务逻辑
	obs.Tracer().AddEvent(ctx, "business-logic-started")
	obs.Logger().Debug(ctx, "执行业务逻辑")
	time.Sleep(80 * time.Millisecond)

	// 记录吞吐量
	obs.Metrics().RecordThroughput("demo-service", "in", 512)  // 接收 512 字节
	obs.Metrics().RecordThroughput("demo-service", "out", 1024) // 发送 1KB

	// 计算处理时间
	duration := time.Since(start)

	// 记录请求指标
	obs.Metrics().RecordRequest("demo-service", "handleRequest", "http", "success", duration)

	// 记录请求完成
	obs.Logger().Info(ctx, "请求处理完成",
		observability.Field{Key: "duration_ms", Value: duration.Milliseconds()},
		observability.Field{Key: "status", Value: "success"})

	// 提取并显示追踪信息
	traceID := obs.Tracer().ExtractTraceID(ctx)
	spanID := obs.Tracer().ExtractSpanID(ctx)
	fmt.Printf("[追踪] TraceID: %s, SpanID: %s\n", traceID[:16], spanID[:16])
}
