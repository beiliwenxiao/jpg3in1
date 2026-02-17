package resilience

import (
	"context"
	"fmt"
	"time"

	"github.com/framework/golang-sdk/errors"
)

// RetryExecutor 重试执行器
type RetryExecutor struct {
	policy *RetryPolicy
}

// NewRetryExecutor 创建重试执行器
func NewRetryExecutor(policy *RetryPolicy) *RetryExecutor {
	if policy == nil {
		policy = DefaultRetryPolicy()
	}
	return &RetryExecutor{
		policy: policy,
	}
}

// Execute 同步执行带重试的操作
func (r *RetryExecutor) Execute(operation func() error) error {
	var lastErr error

	for attempt := 0; attempt < r.policy.MaxAttempts; attempt++ {
		err := operation()
		if err == nil {
			return nil
		}

		lastErr = err

		// 检查是否为可重试的错误
		if fe, ok := err.(*errors.FrameworkError); ok {
			if !r.policy.IsRetryable(fe.Code) || attempt >= r.policy.MaxAttempts-1 {
				return err
			}

			delay := r.policy.CalculateDelay(attempt)
			fmt.Printf("操作失败，第 %d 次重试，延迟 %v，错误: %s\n", attempt+1, delay, err.Error())
			time.Sleep(delay)
		} else {
			// 非框架错误，不重试
			return err
		}
	}

	return lastErr
}

// ExecuteWithResult 同步执行带重试的操作（带返回值）
func (r *RetryExecutor) ExecuteWithResult(operation func() (interface{}, error)) (interface{}, error) {
	var lastErr error

	for attempt := 0; attempt < r.policy.MaxAttempts; attempt++ {
		result, err := operation()
		if err == nil {
			return result, nil
		}

		lastErr = err

		// 检查是否为可重试的错误
		if fe, ok := err.(*errors.FrameworkError); ok {
			if !r.policy.IsRetryable(fe.Code) || attempt >= r.policy.MaxAttempts-1 {
				return nil, err
			}

			delay := r.policy.CalculateDelay(attempt)
			fmt.Printf("操作失败，第 %d 次重试，延迟 %v，错误: %s\n", attempt+1, delay, err.Error())
			time.Sleep(delay)
		} else {
			// 非框架错误，不重试
			return nil, err
		}
	}

	return nil, lastErr
}

// ExecuteAsync 异步执行带重试的操作
func (r *RetryExecutor) ExecuteAsync(ctx context.Context, operation func() error) <-chan error {
	resultChan := make(chan error, 1)

	go func() {
		defer close(resultChan)
		resultChan <- r.executeAsyncInternal(ctx, operation, 0)
	}()

	return resultChan
}

// ExecuteAsyncWithResult 异步执行带重试的操作（带返回值）
func (r *RetryExecutor) ExecuteAsyncWithResult(ctx context.Context, operation func() (interface{}, error)) <-chan AsyncResult {
	resultChan := make(chan AsyncResult, 1)

	go func() {
		defer close(resultChan)
		result, err := r.executeAsyncInternalWithResult(ctx, operation, 0)
		resultChan <- AsyncResult{Result: result, Error: err}
	}()

	return resultChan
}

// AsyncResult 异步操作结果
type AsyncResult struct {
	Result interface{}
	Error  error
}

func (r *RetryExecutor) executeAsyncInternal(ctx context.Context, operation func() error, attempt int) error {
	// 检查上下文是否已取消
	select {
	case <-ctx.Done():
		return ctx.Err()
	default:
	}

	err := operation()
	if err == nil {
		return nil
	}

	// 检查是否为可重试的错误
	if fe, ok := err.(*errors.FrameworkError); ok {
		if r.policy.IsRetryable(fe.Code) && attempt < r.policy.MaxAttempts-1 {
			delay := r.policy.CalculateDelay(attempt)
			fmt.Printf("异步操作失败，第 %d 次重试，延迟 %v，错误: %s\n", attempt+1, delay, err.Error())

			// 等待延迟或上下文取消
			timer := time.NewTimer(delay)
			select {
			case <-ctx.Done():
				timer.Stop()
				return ctx.Err()
			case <-timer.C:
				return r.executeAsyncInternal(ctx, operation, attempt+1)
			}
		}
	}

	return err
}

func (r *RetryExecutor) executeAsyncInternalWithResult(ctx context.Context, operation func() (interface{}, error), attempt int) (interface{}, error) {
	// 检查上下文是否已取消
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
	}

	result, err := operation()
	if err == nil {
		return result, nil
	}

	// 检查是否为可重试的错误
	if fe, ok := err.(*errors.FrameworkError); ok {
		if r.policy.IsRetryable(fe.Code) && attempt < r.policy.MaxAttempts-1 {
			delay := r.policy.CalculateDelay(attempt)
			fmt.Printf("异步操作失败，第 %d 次重试，延迟 %v，错误: %s\n", attempt+1, delay, err.Error())

			// 等待延迟或上下文取消
			timer := time.NewTimer(delay)
			select {
			case <-ctx.Done():
				timer.Stop()
				return nil, ctx.Err()
			case <-timer.C:
				return r.executeAsyncInternalWithResult(ctx, operation, attempt+1)
			}
		}
	}

	return nil, err
}
