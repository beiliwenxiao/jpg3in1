package observability

import (
	"context"
	"encoding/json"
	"net/http"
	"sync"
	"time"
)

// HealthStatus 健康状态
type HealthStatus string

const (
	HealthStatusHealthy   HealthStatus = "healthy"
	HealthStatusUnhealthy HealthStatus = "unhealthy"
	HealthStatusDegraded  HealthStatus = "degraded"
)

// HealthCheck 健康检查接口
type HealthCheck interface {
	Name() string
	Check(ctx context.Context) error
}

// HealthChecker 健康检查器
type HealthChecker struct {
	checks      map[string]HealthCheck
	mu          sync.RWMutex
	serviceName string
}

// HealthResponse 健康检查响应
type HealthResponse struct {
	Status    HealthStatus           `json:"status"`
	Service   string                 `json:"service"`
	Timestamp time.Time              `json:"timestamp"`
	Checks    map[string]CheckResult `json:"checks"`
}

// CheckResult 单个检查结果
type CheckResult struct {
	Status  HealthStatus `json:"status"`
	Message string       `json:"message,omitempty"`
}

// NewHealthChecker 创建新的健康检查器
func NewHealthChecker(serviceName string) *HealthChecker {
	return &HealthChecker{
		checks:      make(map[string]HealthCheck),
		serviceName: serviceName,
	}
}

// RegisterCheck 注册健康检查
func (h *HealthChecker) RegisterCheck(check HealthCheck) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.checks[check.Name()] = check
}

// Check 执行所有健康检查
func (h *HealthChecker) Check(ctx context.Context) HealthResponse {
	h.mu.RLock()
	defer h.mu.RUnlock()

	response := HealthResponse{
		Status:    HealthStatusHealthy,
		Service:   h.serviceName,
		Timestamp: time.Now(),
		Checks:    make(map[string]CheckResult),
	}

	hasUnhealthy := false
	hasDegraded := false

	for name, check := range h.checks {
		err := check.Check(ctx)
		if err != nil {
			hasUnhealthy = true
			response.Checks[name] = CheckResult{
				Status:  HealthStatusUnhealthy,
				Message: err.Error(),
			}
		} else {
			response.Checks[name] = CheckResult{
				Status: HealthStatusHealthy,
			}
		}
	}

	if hasUnhealthy {
		response.Status = HealthStatusUnhealthy
	} else if hasDegraded {
		response.Status = HealthStatusDegraded
	}

	return response
}

// Handler 返回 HTTP 处理器
func (h *HealthChecker) Handler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ctx := r.Context()
		response := h.Check(ctx)

		w.Header().Set("Content-Type", "application/json")
		if response.Status == HealthStatusHealthy {
			w.WriteHeader(http.StatusOK)
		} else {
			w.WriteHeader(http.StatusServiceUnavailable)
		}

		json.NewEncoder(w).Encode(response)
	}
}

// SimpleHealthCheck 简单的健康检查实现
type SimpleHealthCheck struct {
	name      string
	checkFunc func(ctx context.Context) error
}

// NewSimpleHealthCheck 创建简单健康检查
func NewSimpleHealthCheck(name string, checkFunc func(ctx context.Context) error) HealthCheck {
	return &SimpleHealthCheck{
		name:      name,
		checkFunc: checkFunc,
	}
}

// Name 返回检查名称
func (s *SimpleHealthCheck) Name() string {
	return s.name
}

// Check 执行检查
func (s *SimpleHealthCheck) Check(ctx context.Context) error {
	return s.checkFunc(ctx)
}
