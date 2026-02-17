package resilience

import (
	"context"
	"testing"
	"time"

	"github.com/framework/golang-sdk/errors"
)

func TestRetryExecutor_Execute_Success(t *testing.T) {
	executor := NewRetryExecutor(DefaultRetryPolicy())

	callCount := 0
	err := executor.Execute(func() error {
		callCount++
		return nil
	})

	if err != nil {
		t.Errorf("Execute() error = %v, want nil", err)
	}
	if callCount != 1 {
		t.Errorf("callCount = %v, want 1", callCount)
	}
}

func TestRetryExecutor_Execute_RetryableError(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		MaxAttempts(3).
		InitialDelay(10 * time.Millisecond).
		Build()
	executor := NewRetryExecutor(policy)

	callCount := 0
	err := executor.Execute(func() error {
		callCount++
		if callCount < 3 {
			return errors.NewFrameworkError(errors.Timeout, "超时")
		}
		return nil
	})

	if err != nil {
		t.Errorf("Execute() error = %v, want nil", err)
	}
	if callCount != 3 {
		t.Errorf("callCount = %v, want 3", callCount)
	}
}

func TestRetryExecutor_Execute_NonRetryableError(t *testing.T) {
	executor := NewRetryExecutor(DefaultRetryPolicy())

	callCount := 0
	err := executor.Execute(func() error {
		callCount++
		return errors.NewFrameworkError(errors.BadRequest, "无效请求")
	})

	if err == nil {
		t.Error("Execute() error = nil, want error")
	}
	if callCount != 1 {
		t.Errorf("callCount = %v, want 1 (no retry for non-retryable error)", callCount)
	}
}

func TestRetryExecutor_Execute_MaxAttemptsExceeded(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		MaxAttempts(3).
		InitialDelay(10 * time.Millisecond).
		Build()
	executor := NewRetryExecutor(policy)

	callCount := 0
	err := executor.Execute(func() error {
		callCount++
		return errors.NewFrameworkError(errors.Timeout, "超时")
	})

	if err == nil {
		t.Error("Execute() error = nil, want error")
	}
	if callCount != 3 {
		t.Errorf("callCount = %v, want 3", callCount)
	}
}

func TestRetryExecutor_ExecuteWithResult_Success(t *testing.T) {
	executor := NewRetryExecutor(DefaultRetryPolicy())

	callCount := 0
	result, err := executor.ExecuteWithResult(func() (interface{}, error) {
		callCount++
		return "success", nil
	})

	if err != nil {
		t.Errorf("ExecuteWithResult() error = %v, want nil", err)
	}
	if result != "success" {
		t.Errorf("result = %v, want 'success'", result)
	}
	if callCount != 1 {
		t.Errorf("callCount = %v, want 1", callCount)
	}
}

func TestRetryExecutor_ExecuteWithResult_Retry(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		MaxAttempts(3).
		InitialDelay(10 * time.Millisecond).
		Build()
	executor := NewRetryExecutor(policy)

	callCount := 0
	result, err := executor.ExecuteWithResult(func() (interface{}, error) {
		callCount++
		if callCount < 2 {
			return nil, errors.NewFrameworkError(errors.ConnectionError, "连接失败")
		}
		return "success", nil
	})

	if err != nil {
		t.Errorf("ExecuteWithResult() error = %v, want nil", err)
	}
	if result != "success" {
		t.Errorf("result = %v, want 'success'", result)
	}
	if callCount != 2 {
		t.Errorf("callCount = %v, want 2", callCount)
	}
}

func TestRetryExecutor_ExecuteAsync_Success(t *testing.T) {
	executor := NewRetryExecutor(DefaultRetryPolicy())
	ctx := context.Background()

	callCount := 0
	resultChan := executor.ExecuteAsync(ctx, func() error {
		callCount++
		return nil
	})

	err := <-resultChan
	if err != nil {
		t.Errorf("ExecuteAsync() error = %v, want nil", err)
	}
	if callCount != 1 {
		t.Errorf("callCount = %v, want 1", callCount)
	}
}

func TestRetryExecutor_ExecuteAsync_Retry(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		MaxAttempts(3).
		InitialDelay(10 * time.Millisecond).
		Build()
	executor := NewRetryExecutor(policy)
	ctx := context.Background()

	callCount := 0
	resultChan := executor.ExecuteAsync(ctx, func() error {
		callCount++
		if callCount < 2 {
			return errors.NewFrameworkError(errors.ServiceUnavailable, "服务不可用")
		}
		return nil
	})

	err := <-resultChan
	if err != nil {
		t.Errorf("ExecuteAsync() error = %v, want nil", err)
	}
	if callCount != 2 {
		t.Errorf("callCount = %v, want 2", callCount)
	}
}

func TestRetryExecutor_ExecuteAsync_ContextCanceled(t *testing.T) {
	policy := NewRetryPolicyBuilder().
		MaxAttempts(5).
		InitialDelay(100 * time.Millisecond).
		Build()
	executor := NewRetryExecutor(policy)

	ctx, cancel := context.WithCancel(context.Background())

	callCount := 0
	resultChan := executor.ExecuteAsync(ctx, func() error {
		callCount++
		if callCount == 1 {
			// 第一次调用后取消上下文
			cancel()
		}
		return errors.NewFrameworkError(errors.Timeout, "超时")
	})

	err := <-resultChan
	if err == nil {
		t.Error("ExecuteAsync() error = nil, want context error")
	}
	// 应该只调用一次，因为上下文被取消
	if callCount > 2 {
		t.Errorf("callCount = %v, should be <= 2 (context canceled)", callCount)
	}
}

func TestRetryExecutor_ExecuteAsyncWithResult_Success(t *testing.T) {
	executor := NewRetryExecutor(DefaultRetryPolicy())
	ctx := context.Background()

	callCount := 0
	resultChan := executor.ExecuteAsyncWithResult(ctx, func() (interface{}, error) {
		callCount++
		return "async success", nil
	})

	asyncResult := <-resultChan
	if asyncResult.Error != nil {
		t.Errorf("ExecuteAsyncWithResult() error = %v, want nil", asyncResult.Error)
	}
	if asyncResult.Result != "async success" {
		t.Errorf("result = %v, want 'async success'", asyncResult.Result)
	}
	if callCount != 1 {
		t.Errorf("callCount = %v, want 1", callCount)
	}
}

func TestRetryExecutor_NilPolicy(t *testing.T) {
	// 测试 nil policy 应该使用默认策略
	executor := NewRetryExecutor(nil)

	err := executor.Execute(func() error {
		return nil
	})

	if err != nil {
		t.Errorf("Execute() with nil policy error = %v, want nil", err)
	}
}
