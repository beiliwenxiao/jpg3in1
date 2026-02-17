package resilience

import (
	"testing"
	"time"

	"github.com/framework/golang-sdk/errors"
)

func TestDefaultRetryPolicy(t *testing.T) {
	policy := DefaultRetryPolicy()

	if policy.MaxAttempts != 3 {
		t.Errorf("MaxAttempts = %v, want 3", policy.MaxAttempts)
	}
	if policy.InitialDelay != 100*time.Millisecond {
		t.Errorf("InitialDelay = %v, want 100ms", policy.InitialDelay)
	}
	if policy.MaxDelay != 5*time.Second {
		t.Errorf("MaxDelay = %v, want 5s", policy.MaxDelay)
	}
	if policy.Multiplier != 2.0 {
		t.Errorf("Multiplier = %v, want 2.0", policy.Multiplier)
	}
}

func TestNoRetryPolicy(t *testing.T) {
	policy := NoRetryPolicy()

	if policy.MaxAttempts != 1 {
		t.Errorf("MaxAttempts = %v, want 1", policy.MaxAttempts)
	}
}

func TestRetryPolicy_IsRetryable(t *testing.T) {
	policy := DefaultRetryPolicy()

	tests := []struct {
		code     errors.ErrorCode
		expected bool
	}{
		{errors.Timeout, true},
		{errors.ServiceUnavailable, true},
		{errors.ConnectionError, true},
		{errors.BadRequest, false},
		{errors.InternalError, false},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := policy.IsRetryable(tt.code); got != tt.expected {
				t.Errorf("IsRetryable(%v) = %v, want %v", tt.code, got, tt.expected)
			}
		})
	}
}

func TestRetryPolicy_CalculateDelay(t *testing.T) {
	policy := DefaultRetryPolicy()

	tests := []struct {
		attempt  int
		expected time.Duration
	}{
		{0, 100 * time.Millisecond},
		{1, 200 * time.Millisecond},
		{2, 400 * time.Millisecond},
		{3, 800 * time.Millisecond},
		{10, 5 * time.Second}, // 应该被限制在 MaxDelay
	}

	for _, tt := range tests {
		t.Run("", func(t *testing.T) {
			got := policy.CalculateDelay(tt.attempt)
			if got != tt.expected {
				t.Errorf("CalculateDelay(%d) = %v, want %v", tt.attempt, got, tt.expected)
			}
		})
	}
}

func TestRetryPolicy_AddRemoveRetryableError(t *testing.T) {
	policy := DefaultRetryPolicy()

	// 添加新的可重试错误
	policy.AddRetryableError(errors.NotFound)
	if !policy.IsRetryable(errors.NotFound) {
		t.Error("NotFound should be retryable after adding")
	}

	// 移除可重试错误
	policy.RemoveRetryableError(errors.NotFound)
	if policy.IsRetryable(errors.NotFound) {
		t.Error("NotFound should not be retryable after removing")
	}
}

func TestRetryPolicyBuilder(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		MaxAttempts(5).
		InitialDelay(200 * time.Millisecond).
		MaxDelay(10 * time.Second).
		Multiplier(3.0).
		RetryableErrors(errors.Timeout, errors.ConnectionError).
		Build()

	if policy.MaxAttempts != 5 {
		t.Errorf("MaxAttempts = %v, want 5", policy.MaxAttempts)
	}
	if policy.InitialDelay != 200*time.Millisecond {
		t.Errorf("InitialDelay = %v, want 200ms", policy.InitialDelay)
	}
	if policy.MaxDelay != 10*time.Second {
		t.Errorf("MaxDelay = %v, want 10s", policy.MaxDelay)
	}
	if policy.Multiplier != 3.0 {
		t.Errorf("Multiplier = %v, want 3.0", policy.Multiplier)
	}

	// 验证只有指定的错误可重试
	if !policy.IsRetryable(errors.Timeout) {
		t.Error("Timeout should be retryable")
	}
	if !policy.IsRetryable(errors.ConnectionError) {
		t.Error("ConnectionError should be retryable")
	}
	if policy.IsRetryable(errors.ServiceUnavailable) {
		t.Error("ServiceUnavailable should not be retryable")
	}
}

func TestRetryPolicyBuilder_AddRetryableError(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		RetryableErrors(errors.Timeout).
		AddRetryableError(errors.ConnectionError).
		Build()

	if !policy.IsRetryable(errors.Timeout) {
		t.Error("Timeout should be retryable")
	}
	if !policy.IsRetryable(errors.ConnectionError) {
		t.Error("ConnectionError should be retryable")
	}
}

func TestRetryPolicyBuilder_BoundaryValues(t *testing.T) {
	// 测试边界值处理
	policy := NewRetryPolicyBuilder().
		MaxAttempts(0).      // 应该被设置为 1
		InitialDelay(-100).  // 应该被设置为 0
		MaxDelay(-1000).     // 应该被设置为 0
		Multiplier(0.5).     // 应该被设置为 1.0
		Build()

	if policy.MaxAttempts < 1 {
		t.Errorf("MaxAttempts = %v, should be >= 1", policy.MaxAttempts)
	}
	if policy.InitialDelay < 0 {
		t.Errorf("InitialDelay = %v, should be >= 0", policy.InitialDelay)
	}
	if policy.MaxDelay < 0 {
		t.Errorf("MaxDelay = %v, should be >= 0", policy.MaxDelay)
	}
	if policy.Multiplier < 1.0 {
		t.Errorf("Multiplier = %v, should be >= 1.0", policy.Multiplier)
	}
}
