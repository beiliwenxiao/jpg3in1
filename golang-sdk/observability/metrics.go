package observability

import (
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

var (
	// 用于防止重复注册的锁
	metricsOnce sync.Once
	// 全局指标实例
	globalRequestDuration   *prometheus.HistogramVec
	globalRequestTotal      *prometheus.CounterVec
	globalErrorTotal        *prometheus.CounterVec
	globalThroughput        *prometheus.CounterVec
	globalActiveConnections prometheus.Gauge
)

// MetricsCollector 指标收集器
type MetricsCollector struct {
	// 请求延迟直方图
	requestDuration *prometheus.HistogramVec
	// 请求计数器
	requestTotal *prometheus.CounterVec
	// 错误计数器
	errorTotal *prometheus.CounterVec
	// 吞吐量计数器
	throughput *prometheus.CounterVec
	// 活跃连接数
	activeConnections prometheus.Gauge
}

// NewMetricsCollector 创建新的指标收集器
func NewMetricsCollector(serviceName string) *MetricsCollector {
	// 使用 sync.Once 确保指标只注册一次
	metricsOnce.Do(func() {
		globalRequestDuration = promauto.NewHistogramVec(
			prometheus.HistogramOpts{
				Name:    "framework_request_duration_seconds",
				Help:    "Request duration in seconds",
				Buckets: prometheus.DefBuckets,
			},
			[]string{"service", "method", "protocol"},
		)
		globalRequestTotal = promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "framework_request_total",
				Help: "Total number of requests",
			},
			[]string{"service", "method", "protocol", "status"},
		)
		globalErrorTotal = promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "framework_error_total",
				Help: "Total number of errors",
			},
			[]string{"service", "method", "error_code"},
		)
		globalThroughput = promauto.NewCounterVec(
			prometheus.CounterOpts{
				Name: "framework_throughput_bytes_total",
				Help: "Total throughput in bytes",
			},
			[]string{"service", "direction"}, // direction: in/out
		)
		globalActiveConnections = promauto.NewGauge(
			prometheus.GaugeOpts{
				Name: "framework_active_connections",
				Help: "Number of active connections",
			},
		)
	})

	return &MetricsCollector{
		requestDuration:   globalRequestDuration,
		requestTotal:      globalRequestTotal,
		errorTotal:        globalErrorTotal,
		throughput:        globalThroughput,
		activeConnections: globalActiveConnections,
	}
}

// RecordRequest 记录请求指标
func (m *MetricsCollector) RecordRequest(service, method, protocol, status string, duration time.Duration) {
	m.requestDuration.WithLabelValues(service, method, protocol).Observe(duration.Seconds())
	m.requestTotal.WithLabelValues(service, method, protocol, status).Inc()
}

// RecordError 记录错误指标
func (m *MetricsCollector) RecordError(service, method, errorCode string) {
	m.errorTotal.WithLabelValues(service, method, errorCode).Inc()
}

// RecordThroughput 记录吞吐量
func (m *MetricsCollector) RecordThroughput(service, direction string, bytes int64) {
	m.throughput.WithLabelValues(service, direction).Add(float64(bytes))
}

// SetActiveConnections 设置活跃连接数
func (m *MetricsCollector) SetActiveConnections(count float64) {
	m.activeConnections.Set(count)
}

// IncActiveConnections 增加活跃连接数
func (m *MetricsCollector) IncActiveConnections() {
	m.activeConnections.Inc()
}

// DecActiveConnections 减少活跃连接数
func (m *MetricsCollector) DecActiveConnections() {
	m.activeConnections.Dec()
}
