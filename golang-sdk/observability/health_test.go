package observability

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHealthChecker(t *testing.T) {
	checker := NewHealthChecker("test-service")
	ctx := context.Background()

	// 测试没有检查的情况
	response := checker.Check(ctx)
	if response.Status != HealthStatusHealthy {
		t.Errorf("Expected healthy status, got %s", response.Status)
	}
	if response.Service != "test-service" {
		t.Errorf("Expected service name 'test-service', got %s", response.Service)
	}
}

func TestHealthCheckerWithChecks(t *testing.T) {
	checker := NewHealthChecker("test-service")
	ctx := context.Background()

	// 注册健康检查
	checker.RegisterCheck(NewSimpleHealthCheck("check1", func(ctx context.Context) error {
		return nil
	}))

	checker.RegisterCheck(NewSimpleHealthCheck("check2", func(ctx context.Context) error {
		return nil
	}))

	response := checker.Check(ctx)
	if response.Status != HealthStatusHealthy {
		t.Errorf("Expected healthy status, got %s", response.Status)
	}

	if len(response.Checks) != 2 {
		t.Errorf("Expected 2 checks, got %d", len(response.Checks))
	}
}

func TestHealthCheckerWithFailure(t *testing.T) {
	checker := NewHealthChecker("test-service")
	ctx := context.Background()

	// 注册一个失败的检查
	checker.RegisterCheck(NewSimpleHealthCheck("failing-check", func(ctx context.Context) error {
		return errors.New("check failed")
	}))

	response := checker.Check(ctx)
	if response.Status != HealthStatusUnhealthy {
		t.Errorf("Expected unhealthy status, got %s", response.Status)
	}

	checkResult, exists := response.Checks["failing-check"]
	if !exists {
		t.Error("Expected failing-check to exist in results")
	}
	if checkResult.Status != HealthStatusUnhealthy {
		t.Errorf("Expected unhealthy check status, got %s", checkResult.Status)
	}
	if checkResult.Message != "check failed" {
		t.Errorf("Expected error message 'check failed', got %s", checkResult.Message)
	}
}

func TestHealthCheckerMixedResults(t *testing.T) {
	checker := NewHealthChecker("test-service")
	ctx := context.Background()

	// 注册混合的检查
	checker.RegisterCheck(NewSimpleHealthCheck("healthy-check", func(ctx context.Context) error {
		return nil
	}))

	checker.RegisterCheck(NewSimpleHealthCheck("unhealthy-check", func(ctx context.Context) error {
		return errors.New("service unavailable")
	}))

	response := checker.Check(ctx)
	if response.Status != HealthStatusUnhealthy {
		t.Errorf("Expected unhealthy status when any check fails, got %s", response.Status)
	}
}

func TestHealthCheckerHandler(t *testing.T) {
	checker := NewHealthChecker("test-service")

	// 注册健康检查
	checker.RegisterCheck(NewSimpleHealthCheck("test-check", func(ctx context.Context) error {
		return nil
	}))

	// 创建测试请求
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()

	// 调用处理器
	handler := checker.Handler()
	handler(w, req)

	// 验证响应
	if w.Code != http.StatusOK {
		t.Errorf("Expected status code 200, got %d", w.Code)
	}

	contentType := w.Header().Get("Content-Type")
	if contentType != "application/json" {
		t.Errorf("Expected Content-Type application/json, got %s", contentType)
	}
}

func TestHealthCheckerHandlerUnhealthy(t *testing.T) {
	checker := NewHealthChecker("test-service")

	// 注册失败的检查
	checker.RegisterCheck(NewSimpleHealthCheck("failing-check", func(ctx context.Context) error {
		return errors.New("service down")
	}))

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()

	handler := checker.Handler()
	handler(w, req)

	// 验证响应状态码
	if w.Code != http.StatusServiceUnavailable {
		t.Errorf("Expected status code 503, got %d", w.Code)
	}
}

func TestSimpleHealthCheck(t *testing.T) {
	check := NewSimpleHealthCheck("test", func(ctx context.Context) error {
		return nil
	})

	if check.Name() != "test" {
		t.Errorf("Expected name 'test', got %s", check.Name())
	}

	err := check.Check(context.Background())
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}
}
