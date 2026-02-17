package observability

import (
	"context"
	"testing"
)

func TestLogger(t *testing.T) {
	logger := NewLogger("test-service")

	ctx := context.WithValue(context.Background(), "request_id", "req-123")
	ctx = context.WithValue(ctx, "timestamp", "2024-01-01T12:00:00Z")

	// 测试不同级别的日志
	logger.Debug(ctx, "Debug message", Field{Key: "key1", Value: "value1"})
	logger.Info(ctx, "Info message", Field{Key: "key2", Value: "value2"})
	logger.Warn(ctx, "Warn message", Field{Key: "key3", Value: "value3"})
	logger.Error(ctx, "Error message", Field{Key: "key4", Value: "value4"})
}

func TestLoggerSetLevel(t *testing.T) {
	logger := NewLogger("test-service")

	// 测试设置不同日志级别
	levels := []LogLevel{
		LogLevelDebug,
		LogLevelInfo,
		LogLevelWarn,
		LogLevelError,
	}

	for _, level := range levels {
		logger.SetLevel(level)
		// 验证日志级别已设置（通过日志输出验证）
	}
}

func TestLoggerWithoutContext(t *testing.T) {
	logger := NewLogger("test-service")

	// 测试没有上下文的情况
	logger.Info(nil, "Message without context")
}

func TestLoggerWithFields(t *testing.T) {
	logger := NewLogger("test-service")
	ctx := context.Background()

	// 测试多个字段
	logger.Info(ctx, "Message with multiple fields",
		Field{Key: "field1", Value: "value1"},
		Field{Key: "field2", Value: 123},
		Field{Key: "field3", Value: true},
	)
}
