package observability

import (
	"testing"
	"time"
)

func TestMetricsCollector(t *testing.T) {
	metrics := NewMetricsCollector("test-service")

	// 测试记录请求
	metrics.RecordRequest("test-service", "testMethod", "http", "success", 100*time.Millisecond)
	metrics.RecordRequest("test-service", "testMethod", "grpc", "error", 200*time.Millisecond)

	// 测试记录错误
	metrics.RecordError("test-service", "testMethod", "500")
	metrics.RecordError("test-service", "testMethod", "404")

	// 测试记录吞吐量
	metrics.RecordThroughput("test-service", "in", 1024)
	metrics.RecordThroughput("test-service", "out", 2048)

	// 测试活跃连接数
	metrics.SetActiveConnections(10)
	metrics.IncActiveConnections()
	metrics.DecActiveConnections()
}

func TestMetricsCollectorConcurrent(t *testing.T) {
	metrics := NewMetricsCollector("test-service")

	// 并发测试
	done := make(chan bool)
	for i := 0; i < 10; i++ {
		go func() {
			for j := 0; j < 100; j++ {
				metrics.RecordRequest("test-service", "method", "http", "success", time.Millisecond)
				metrics.IncActiveConnections()
				metrics.DecActiveConnections()
			}
			done <- true
		}()
	}

	for i := 0; i < 10; i++ {
		<-done
	}
}

func TestMetricsCollectorDifferentProtocols(t *testing.T) {
	metrics := NewMetricsCollector("test-service")

	protocols := []string{"http", "grpc", "websocket", "mqtt"}
	for _, protocol := range protocols {
		metrics.RecordRequest("test-service", "method", protocol, "success", 50*time.Millisecond)
	}
}

func TestMetricsCollectorErrorCodes(t *testing.T) {
	metrics := NewMetricsCollector("test-service")

	errorCodes := []string{"400", "401", "403", "404", "500", "503"}
	for _, code := range errorCodes {
		metrics.RecordError("test-service", "method", code)
	}
}
