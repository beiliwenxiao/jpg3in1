package observability

import (
	"context"
	"fmt"
	"net/http"

	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// ObservabilityManager 可观测性管理器
type ObservabilityManager struct {
	logger        Logger
	metrics       *MetricsCollector
	tracer        *Tracer
	healthChecker *HealthChecker
	serviceName   string
	metricsPort   int
}

// Config 可观测性配置
type Config struct {
	ServiceName string
	MetricsPort int
	LogLevel    LogLevel
}

// NewObservabilityManager 创建可观测性管理器
func NewObservabilityManager(config Config) *ObservabilityManager {
	logger := NewLogger(config.ServiceName)
	logger.SetLevel(config.LogLevel)

	return &ObservabilityManager{
		logger:        logger,
		metrics:       NewMetricsCollector(config.ServiceName),
		tracer:        NewTracer(config.ServiceName),
		healthChecker: NewHealthChecker(config.ServiceName),
		serviceName:   config.ServiceName,
		metricsPort:   config.MetricsPort,
	}
}

// Logger 获取日志记录器
func (o *ObservabilityManager) Logger() Logger {
	return o.logger
}

// Metrics 获取指标收集器
func (o *ObservabilityManager) Metrics() *MetricsCollector {
	return o.metrics
}

// Tracer 获取追踪器
func (o *ObservabilityManager) Tracer() *Tracer {
	return o.tracer
}

// HealthChecker 获取健康检查器
func (o *ObservabilityManager) HealthChecker() *HealthChecker {
	return o.healthChecker
}

// StartMetricsServer 启动指标暴露服务器
func (o *ObservabilityManager) StartMetricsServer() error {
	mux := http.NewServeMux()

	// Prometheus 指标端点
	mux.Handle("/metrics", promhttp.Handler())

	// 健康检查端点
	mux.HandleFunc("/health", o.healthChecker.Handler())

	addr := fmt.Sprintf(":%d", o.metricsPort)
	o.logger.Info(context.Background(), "Starting metrics server",
		Field{Key: "address", Value: addr})

	go func() {
		if err := http.ListenAndServe(addr, mux); err != nil {
			o.logger.Error(context.Background(), "Metrics server error",
				Field{Key: "error", Value: err.Error()})
		}
	}()

	return nil
}

// SetLogLevel 动态设置日志级别
func (o *ObservabilityManager) SetLogLevel(level LogLevel) {
	o.logger.SetLevel(level)
	o.logger.Info(context.Background(), "Log level changed",
		Field{Key: "new_level", Value: string(level)})
}
