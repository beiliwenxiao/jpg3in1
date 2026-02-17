package observability

import (
	"context"
	"testing"
	"time"
)

func TestObservabilityManager(t *testing.T) {
	config := Config{
		ServiceName: "test-service",
		MetricsPort: 9091,
		LogLevel:    LogLevelInfo,
	}

	obs := NewObservabilityManager(config)

	// 验证组件已创建
	if obs.Logger() == nil {
		t.Error("Expected logger to be created")
	}
	if obs.Metrics() == nil {
		t.Error("Expected metrics collector to be created")
	}
	if obs.Tracer() == nil {
		t.Error("Expected tracer to be created")
	}
	if obs.HealthChecker() == nil {
		t.Error("Expected health checker to be created")
	}
}

func TestObservabilityManagerSetLogLevel(t *testing.T) {
	config := Config{
		ServiceName: "test-service",
		MetricsPort: 9092,
		LogLevel:    LogLevelInfo,
	}

	obs := NewObservabilityManager(config)

	// 测试动态设置日志级别
	levels := []LogLevel{
		LogLevelDebug,
		LogLevelInfo,
		LogLevelWarn,
		LogLevelError,
	}

	for _, level := range levels {
		obs.SetLogLevel(level)
	}
}

func TestObservabilityManagerIntegration(t *testing.T) {
	config := Config{
		ServiceName: "test-service",
		MetricsPort: 9093,
		LogLevel:    LogLevelDebug,
	}

	obs := NewObservabilityManager(config)
	ctx := context.Background()

	// 集成测试：使用所有组件

	// 1. 日志记录
	obs.Logger().Info(ctx, "Starting operation")

	// 2. 开始追踪
	ctx, span := obs.Tracer().StartSpan(ctx, "test-operation")
	defer obs.Tracer().EndSpan(span, nil)

	// 3. 记录指标
	start := time.Now()
	time.Sleep(10 * time.Millisecond) // 模拟操作
	duration := time.Since(start)

	obs.Metrics().RecordRequest("test-service", "testMethod", "http", "success", duration)
	obs.Metrics().IncActiveConnections()
	defer obs.Metrics().DecActiveConnections()

	// 4. 健康检查
	obs.HealthChecker().RegisterCheck(
		NewSimpleHealthCheck("test", func(ctx context.Context) error {
			return nil
		}),
	)

	response := obs.HealthChecker().Check(ctx)
	if response.Status != HealthStatusHealthy {
		t.Errorf("Expected healthy status, got %s", response.Status)
	}

	obs.Logger().Info(ctx, "Operation completed")
}

func TestObservabilityManagerStartMetricsServer(t *testing.T) {
	config := Config{
		ServiceName: "test-service",
		MetricsPort: 9094,
		LogLevel:    LogLevelInfo,
	}

	obs := NewObservabilityManager(config)

	// 启动指标服务器
	err := obs.StartMetricsServer()
	if err != nil {
		t.Errorf("Failed to start metrics server: %v", err)
	}

	// 等待服务器启动
	time.Sleep(100 * time.Millisecond)

	// 注意：实际的 HTTP 请求测试需要在集成测试中进行
}

func TestObservabilityManagerConcurrent(t *testing.T) {
	config := Config{
		ServiceName: "test-service",
		MetricsPort: 9095,
		LogLevel:    LogLevelInfo,
	}

	obs := NewObservabilityManager(config)
	ctx := context.Background()

	// 并发测试
	done := make(chan bool)
	for i := 0; i < 10; i++ {
		go func(id int) {
			for j := 0; j < 50; j++ {
				// 日志
				obs.Logger().Info(ctx, "Concurrent operation")

				// 追踪
				_, span := obs.Tracer().StartSpan(ctx, "concurrent-op")
				obs.Tracer().EndSpan(span, nil)

				// 指标
				obs.Metrics().RecordRequest("test-service", "method", "http", "success", time.Millisecond)
			}
			done <- true
		}(i)
	}

	for i := 0; i < 10; i++ {
		<-done
	}
}
