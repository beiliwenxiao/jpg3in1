package resilience

import (
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"github.com/framework/golang-sdk/errors"
)

// State 熔断器状态
type State int32

const (
	// StateClosed 关闭状态，正常允许请求
	StateClosed State = iota
	// StateOpen 打开状态，拒绝所有请求
	StateOpen
	// StateHalfOpen 半开状态，允许少量请求测试服务是否恢复
	StateHalfOpen
)

// String 返回状态的字符串表示
func (s State) String() string {
	switch s {
	case StateClosed:
		return "CLOSED"
	case StateOpen:
		return "OPEN"
	case StateHalfOpen:
		return "HALF_OPEN"
	default:
		return "UNKNOWN"
	}
}

// CircuitBreaker 熔断器实现
type CircuitBreaker struct {
	name             string
	failureThreshold int
	successThreshold int
	timeout          time.Duration

	state           atomic.Value // State
	failureCount    atomic.Int32
	successCount    atomic.Int32
	lastFailureTime atomic.Int64

	mu sync.RWMutex
}

// NewCircuitBreaker 创建新的熔断器
func NewCircuitBreaker(name string, failureThreshold, successThreshold int, timeout time.Duration) *CircuitBreaker {
	if failureThreshold < 1 {
		failureThreshold = 1
	}
	if successThreshold < 1 {
		successThreshold = 1
	}
	if timeout < 1*time.Millisecond {
		timeout = 1 * time.Millisecond
	}

	cb := &CircuitBreaker{
		name:             name,
		failureThreshold: failureThreshold,
		successThreshold: successThreshold,
		timeout:          timeout,
	}
	cb.state.Store(StateClosed)
	return cb
}

// NewDefaultCircuitBreaker 使用默认配置创建熔断器：失败阈值 5，成功阈值 3，超时 30 秒
func NewDefaultCircuitBreaker(name string) *CircuitBreaker {
	return NewCircuitBreaker(name, 5, 3, 30*time.Second)
}

// Execute 通过熔断器执行操作
func (cb *CircuitBreaker) Execute(operation func() error) error {
	if !cb.AllowRequest() {
		return errors.NewFrameworkError(
			errors.ServiceUnavailable,
			fmt.Sprintf("熔断器 [%s] 处于打开状态，请求被拒绝", cb.name),
		)
	}

	err := operation()
	if err != nil {
		cb.RecordFailure()
		return err
	}

	cb.RecordSuccess()
	return nil
}

// ExecuteWithResult 通过熔断器执行操作（带返回值）
func (cb *CircuitBreaker) ExecuteWithResult(operation func() (interface{}, error)) (interface{}, error) {
	if !cb.AllowRequest() {
		return nil, errors.NewFrameworkError(
			errors.ServiceUnavailable,
			fmt.Sprintf("熔断器 [%s] 处于打开状态，请求被拒绝", cb.name),
		)
	}

	result, err := operation()
	if err != nil {
		cb.RecordFailure()
		return nil, err
	}

	cb.RecordSuccess()
	return result, nil
}

// AllowRequest 检查是否允许请求通过
func (cb *CircuitBreaker) AllowRequest() bool {
	currentState := cb.GetState()

	switch currentState {
	case StateClosed:
		return true
	case StateOpen:
		// 检查是否超时，超时则转为半开状态
		lastFailure := time.UnixMilli(cb.lastFailureTime.Load())
		if time.Since(lastFailure) >= cb.timeout {
			cb.mu.Lock()
			// 双重检查
			if cb.GetState() == StateOpen {
				cb.state.Store(StateHalfOpen)
				cb.successCount.Store(0)
				fmt.Printf("熔断器 [%s] 从 OPEN 转为 HALF_OPEN\n", cb.name)
			}
			cb.mu.Unlock()
			return true
		}
		return false
	case StateHalfOpen:
		return true
	default:
		return false
	}
}

// RecordSuccess 记录成功调用
func (cb *CircuitBreaker) RecordSuccess() {
	currentState := cb.GetState()

	if currentState == StateHalfOpen {
		successes := cb.successCount.Add(1)
		if successes >= int32(cb.successThreshold) {
			cb.mu.Lock()
			// 双重检查
			if cb.GetState() == StateHalfOpen {
				cb.state.Store(StateClosed)
				cb.failureCount.Store(0)
				cb.successCount.Store(0)
				fmt.Printf("熔断器 [%s] 从 HALF_OPEN 转为 CLOSED\n", cb.name)
			}
			cb.mu.Unlock()
		}
	} else if currentState == StateClosed {
		// 成功时重置失败计数
		cb.failureCount.Store(0)
	}
}

// RecordFailure 记录失败调用
func (cb *CircuitBreaker) RecordFailure() {
	currentState := cb.GetState()
	cb.lastFailureTime.Store(time.Now().UnixMilli())

	if currentState == StateHalfOpen {
		// 半开状态下失败，立即转回 Open
		cb.mu.Lock()
		if cb.GetState() == StateHalfOpen {
			cb.state.Store(StateOpen)
			cb.successCount.Store(0)
			fmt.Printf("熔断器 [%s] 从 HALF_OPEN 转回 OPEN\n", cb.name)
		}
		cb.mu.Unlock()
	} else if currentState == StateClosed {
		failures := cb.failureCount.Add(1)
		if failures >= int32(cb.failureThreshold) {
			cb.mu.Lock()
			// 双重检查
			if cb.GetState() == StateClosed {
				cb.state.Store(StateOpen)
				fmt.Printf("熔断器 [%s] 从 CLOSED 转为 OPEN，连续失败 %d 次\n", cb.name, failures)
			}
			cb.mu.Unlock()
		}
	}
}

// Reset 重置熔断器到初始状态
func (cb *CircuitBreaker) Reset() {
	cb.mu.Lock()
	defer cb.mu.Unlock()

	cb.state.Store(StateClosed)
	cb.failureCount.Store(0)
	cb.successCount.Store(0)
	cb.lastFailureTime.Store(0)
	fmt.Printf("熔断器 [%s] 已重置\n", cb.name)
}

// GetState 获取当前状态
func (cb *CircuitBreaker) GetState() State {
	return cb.state.Load().(State)
}

// GetName 获取熔断器名称
func (cb *CircuitBreaker) GetName() string {
	return cb.name
}

// GetFailureCount 获取失败计数
func (cb *CircuitBreaker) GetFailureCount() int {
	return int(cb.failureCount.Load())
}

// GetSuccessCount 获取成功计数
func (cb *CircuitBreaker) GetSuccessCount() int {
	return int(cb.successCount.Load())
}

// GetFailureThreshold 获取失败阈值
func (cb *CircuitBreaker) GetFailureThreshold() int {
	return cb.failureThreshold
}

// GetSuccessThreshold 获取成功阈值
func (cb *CircuitBreaker) GetSuccessThreshold() int {
	return cb.successThreshold
}

// GetTimeout 获取超时时间
func (cb *CircuitBreaker) GetTimeout() time.Duration {
	return cb.timeout
}
