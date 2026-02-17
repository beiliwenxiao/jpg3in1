package resilience

import (
	"testing"
	"time"

	"github.com/framework/golang-sdk/errors"
)

func TestNewCircuitBreaker(t *testing.T) {
	cb := NewCircuitBreaker("test", 5, 3, 30*time.Second)

	if cb.GetName() != "test" {
		t.Errorf("Name = %v, want 'test'", cb.GetName())
	}
	if cb.GetFailureThreshold() != 5 {
		t.Errorf("FailureThreshold = %v, want 5", cb.GetFailureThreshold())
	}
	if cb.GetSuccessThreshold() != 3 {
		t.Errorf("SuccessThreshold = %v, want 3", cb.GetSuccessThreshold())
	}
	if cb.GetTimeout() != 30*time.Second {
		t.Errorf("Timeout = %v, want 30s", cb.GetTimeout())
	}
	if cb.GetState() != StateClosed {
		t.Errorf("Initial state = %v, want CLOSED", cb.GetState())
	}
}

func TestNewDefaultCircuitBreaker(t *testing.T) {
	cb := NewDefaultCircuitBreaker("default")

	if cb.GetFailureThreshold() != 5 {
		t.Errorf("FailureThreshold = %v, want 5", cb.GetFailureThreshold())
	}
	if cb.GetSuccessThreshold() != 3 {
		t.Errorf("SuccessThreshold = %v, want 3", cb.GetSuccessThreshold())
	}
	if cb.GetTimeout() != 30*time.Second {
		t.Errorf("Timeout = %v, want 30s", cb.GetTimeout())
	}
}

func TestCircuitBreaker_Execute_Success(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	callCount := 0
	err := cb.Execute(func() error {
		callCount++
		return nil
	})

	if err != nil {
		t.Errorf("Execute() error = %v, want nil", err)
	}
	if callCount != 1 {
		t.Errorf("callCount = %v, want 1", callCount)
	}
	if cb.GetState() != StateClosed {
		t.Errorf("State = %v, want CLOSED", cb.GetState())
	}
}

func TestCircuitBreaker_Execute_Failure(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	err := cb.Execute(func() error {
		return errors.NewFrameworkError(errors.InternalError, "错误")
	})

	if err == nil {
		t.Error("Execute() error = nil, want error")
	}
	if cb.GetFailureCount() != 1 {
		t.Errorf("FailureCount = %v, want 1", cb.GetFailureCount())
	}
}

func TestCircuitBreaker_StateTransition_ClosedToOpen(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	// 连续失败 3 次，应该转为 OPEN
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	if cb.GetState() != StateOpen {
		t.Errorf("State = %v, want OPEN after 3 failures", cb.GetState())
	}
}

func TestCircuitBreaker_StateTransition_OpenToHalfOpen(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 100*time.Millisecond)

	// 转为 OPEN 状态
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	if cb.GetState() != StateOpen {
		t.Fatalf("State = %v, want OPEN", cb.GetState())
	}

	// 等待超时，增加一些缓冲时间
	time.Sleep(200 * time.Millisecond)

	// 检查是否允许请求（应该转为 HALF_OPEN）
	if !cb.AllowRequest() {
		t.Error("AllowRequest() = false, want true after timeout")
	}

	if cb.GetState() != StateHalfOpen {
		t.Errorf("State = %v, want HALF_OPEN after timeout", cb.GetState())
	}
}

func TestCircuitBreaker_StateTransition_HalfOpenToClosed(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 100*time.Millisecond)

	// 转为 OPEN 状态
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	// 等待超时转为 HALF_OPEN
	time.Sleep(200 * time.Millisecond)
	cb.AllowRequest()

	if cb.GetState() != StateHalfOpen {
		t.Fatalf("State = %v, want HALF_OPEN", cb.GetState())
	}

	// 连续成功 2 次，应该转为 CLOSED
	cb.RecordSuccess()
	cb.RecordSuccess()

	if cb.GetState() != StateClosed {
		t.Errorf("State = %v, want CLOSED after 2 successes", cb.GetState())
	}
	if cb.GetFailureCount() != 0 {
		t.Errorf("FailureCount = %v, want 0 after transition to CLOSED", cb.GetFailureCount())
	}
}

func TestCircuitBreaker_StateTransition_HalfOpenToOpen(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 100*time.Millisecond)

	// 转为 OPEN 状态
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	// 等待超时转为 HALF_OPEN
	time.Sleep(200 * time.Millisecond)
	cb.AllowRequest()

	if cb.GetState() != StateHalfOpen {
		t.Fatalf("State = %v, want HALF_OPEN", cb.GetState())
	}

	// 失败一次，应该转回 OPEN
	cb.RecordFailure()

	if cb.GetState() != StateOpen {
		t.Errorf("State = %v, want OPEN after failure in HALF_OPEN", cb.GetState())
	}
}

func TestCircuitBreaker_AllowRequest_WhenOpen(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	// 转为 OPEN 状态
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	// OPEN 状态下应该拒绝请求
	if cb.AllowRequest() {
		t.Error("AllowRequest() = true, want false when OPEN")
	}
}

func TestCircuitBreaker_ExecuteWithResult_Success(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	result, err := cb.ExecuteWithResult(func() (interface{}, error) {
		return "success", nil
	})

	if err != nil {
		t.Errorf("ExecuteWithResult() error = %v, want nil", err)
	}
	if result != "success" {
		t.Errorf("result = %v, want 'success'", result)
	}
}

func TestCircuitBreaker_ExecuteWithResult_Rejected(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	// 转为 OPEN 状态
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	result, err := cb.ExecuteWithResult(func() (interface{}, error) {
		return "should not execute", nil
	})

	if err == nil {
		t.Error("ExecuteWithResult() error = nil, want error when circuit is OPEN")
	}
	if result != nil {
		t.Errorf("result = %v, want nil when circuit is OPEN", result)
	}

	// 验证错误类型
	if fe, ok := err.(*errors.FrameworkError); ok {
		if fe.Code != errors.ServiceUnavailable {
			t.Errorf("error code = %v, want ServiceUnavailable", fe.Code)
		}
	} else {
		t.Error("error should be FrameworkError")
	}
}

func TestCircuitBreaker_Reset(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	// 转为 OPEN 状态
	for i := 0; i < 3; i++ {
		cb.RecordFailure()
	}

	if cb.GetState() != StateOpen {
		t.Fatalf("State = %v, want OPEN", cb.GetState())
	}

	// 重置
	cb.Reset()

	if cb.GetState() != StateClosed {
		t.Errorf("State = %v, want CLOSED after reset", cb.GetState())
	}
	if cb.GetFailureCount() != 0 {
		t.Errorf("FailureCount = %v, want 0 after reset", cb.GetFailureCount())
	}
	if cb.GetSuccessCount() != 0 {
		t.Errorf("SuccessCount = %v, want 0 after reset", cb.GetSuccessCount())
	}
}

func TestCircuitBreaker_RecordSuccess_InClosedState(t *testing.T) {
	cb := NewCircuitBreaker("test", 3, 2, 1*time.Second)

	// 先记录一些失败
	cb.RecordFailure()
	cb.RecordFailure()

	if cb.GetFailureCount() != 2 {
		t.Fatalf("FailureCount = %v, want 2", cb.GetFailureCount())
	}

	// 记录成功应该重置失败计数
	cb.RecordSuccess()

	if cb.GetFailureCount() != 0 {
		t.Errorf("FailureCount = %v, want 0 after success in CLOSED state", cb.GetFailureCount())
	}
}

func TestCircuitBreaker_BoundaryValues(t *testing.T) {
	// 测试边界值处理
	cb := NewCircuitBreaker("test", 0, 0, 0)

	if cb.GetFailureThreshold() < 1 {
		t.Errorf("FailureThreshold = %v, should be >= 1", cb.GetFailureThreshold())
	}
	if cb.GetSuccessThreshold() < 1 {
		t.Errorf("SuccessThreshold = %v, should be >= 1", cb.GetSuccessThreshold())
	}
	if cb.GetTimeout() < time.Millisecond {
		t.Errorf("Timeout = %v, should be >= 1ms", cb.GetTimeout())
	}
}
