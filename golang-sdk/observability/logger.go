package observability

import (
	"context"
	"fmt"

	"github.com/gogf/gf/v2/os/glog"
)

// Logger 日志记录器接口
type Logger interface {
	Debug(ctx context.Context, msg string, fields ...Field)
	Info(ctx context.Context, msg string, fields ...Field)
	Warn(ctx context.Context, msg string, fields ...Field)
	Error(ctx context.Context, msg string, fields ...Field)
	SetLevel(level LogLevel)
}

// LogLevel 日志级别
type LogLevel string

const (
	LogLevelDebug LogLevel = "debug"
	LogLevelInfo  LogLevel = "info"
	LogLevelWarn  LogLevel = "warn"
	LogLevelError LogLevel = "error"
)

// Field 日志字段
type Field struct {
	Key   string
	Value interface{}
}

// FrameworkLogger 基于 GoFrame glog 的日志记录器
type FrameworkLogger struct {
	logger      *glog.Logger
	serviceName string
}

// NewLogger 创建新的日志记录器
func NewLogger(serviceName string) Logger {
	logger := glog.New()
	logger.SetPrefix(fmt.Sprintf("[%s]", serviceName))
	return &FrameworkLogger{
		logger:      logger,
		serviceName: serviceName,
	}
}

// Debug 记录调试级别日志
func (l *FrameworkLogger) Debug(ctx context.Context, msg string, fields ...Field) {
	l.logWithFields(ctx, l.logger.Debug, msg, fields...)
}

// Info 记录信息级别日志
func (l *FrameworkLogger) Info(ctx context.Context, msg string, fields ...Field) {
	l.logWithFields(ctx, l.logger.Info, msg, fields...)
}

// Warn 记录警告级别日志
func (l *FrameworkLogger) Warn(ctx context.Context, msg string, fields ...Field) {
	l.logWithFields(ctx, l.logger.Warning, msg, fields...)
}

// Error 记录错误级别日志
func (l *FrameworkLogger) Error(ctx context.Context, msg string, fields ...Field) {
	l.logWithFields(ctx, l.logger.Error, msg, fields...)
}

// SetLevel 设置日志级别
func (l *FrameworkLogger) SetLevel(level LogLevel) {
	switch level {
	case LogLevelDebug:
		l.logger.SetLevel(glog.LEVEL_ALL)
	case LogLevelInfo:
		l.logger.SetLevel(glog.LEVEL_INFO | glog.LEVEL_WARN | glog.LEVEL_ERRO | glog.LEVEL_CRIT)
	case LogLevelWarn:
		l.logger.SetLevel(glog.LEVEL_WARN | glog.LEVEL_ERRO | glog.LEVEL_CRIT)
	case LogLevelError:
		l.logger.SetLevel(glog.LEVEL_ERRO | glog.LEVEL_CRIT)
	}
}

// logWithFields 记录带字段的日志
func (l *FrameworkLogger) logWithFields(ctx context.Context, logFunc func(ctx context.Context, v ...interface{}), msg string, fields ...Field) {
	// 提取上下文信息
	requestID := extractRequestID(ctx)
	timestamp := extractTimestamp(ctx)

	// 构建日志消息
	logMsg := fmt.Sprintf("[RequestID: %s] [Timestamp: %s] [Service: %s] %s",
		requestID, timestamp, l.serviceName, msg)

	// 添加字段
	if len(fields) > 0 {
		logMsg += " |"
		for _, field := range fields {
			logMsg += fmt.Sprintf(" %s=%v", field.Key, field.Value)
		}
	}

	logFunc(ctx, logMsg)
}

// extractRequestID 从上下文提取请求ID
func extractRequestID(ctx context.Context) string {
	if ctx == nil {
		return "unknown"
	}
	if requestID := ctx.Value("request_id"); requestID != nil {
		return fmt.Sprintf("%v", requestID)
	}
	return "unknown"
}

// extractTimestamp 从上下文提取时间戳
func extractTimestamp(ctx context.Context) string {
	if ctx == nil {
		return "unknown"
	}
	if timestamp := ctx.Value("timestamp"); timestamp != nil {
		return fmt.Sprintf("%v", timestamp)
	}
	return "unknown"
}
