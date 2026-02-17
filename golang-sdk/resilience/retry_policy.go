package resilience

import (
	"math"
	"time"

	"github.com/framework/golang-sdk/errors"
)

// RetryPolicy 重试策略配置
type RetryPolicy struct {
	MaxAttempts    int
	InitialDelay   time.Duration
	MaxDelay       time.Duration
	Multiplier     float64
	RetryableErrors map[errors.ErrorCode]bool
}

// NewRetryPolicy 创建新的重试策略
func NewRetryPolicy(maxAttempts int, initialDelay, maxDelay time.Duration, multiplier float64) *RetryPolicy {
	return &RetryPolicy{
		MaxAttempts:  maxAttempts,
		InitialDelay: initialDelay,
		MaxDelay:     maxDelay,
		Multiplier:   multiplier,
		RetryableErrors: map[errors.ErrorCode]bool{
			errors.Timeout:            true,
			errors.ServiceUnavailable: true,
			errors.ConnectionError:    true,
		},
	}
}

// DefaultRetryPolicy 返回默认重试策略：最多 3 次，初始延迟 100ms，最大延迟 5s，倍数 2
func DefaultRetryPolicy() *RetryPolicy {
	return NewRetryPolicy(3, 100*time.Millisecond, 5*time.Second, 2.0)
}

// NoRetryPolicy 返回不重试策略
func NoRetryPolicy() *RetryPolicy {
	return NewRetryPolicy(1, 0, 0, 1.0)
}

// IsRetryable 判断给定的错误码是否可重试
func (p *RetryPolicy) IsRetryable(code errors.ErrorCode) bool {
	return p.RetryableErrors[code]
}

// CalculateDelay 计算第 attempt 次重试的延迟时间（指数退避）
// attempt 从 0 开始
func (p *RetryPolicy) CalculateDelay(attempt int) time.Duration {
	if attempt <= 0 {
		return p.InitialDelay
	}

	delay := time.Duration(float64(p.InitialDelay) * math.Pow(p.Multiplier, float64(attempt)))
	if delay > p.MaxDelay {
		return p.MaxDelay
	}
	return delay
}

// AddRetryableError 添加可重试的错误码
func (p *RetryPolicy) AddRetryableError(code errors.ErrorCode) {
	p.RetryableErrors[code] = true
}

// RemoveRetryableError 移除可重试的错误码
func (p *RetryPolicy) RemoveRetryableError(code errors.ErrorCode) {
	delete(p.RetryableErrors, code)
}

// RetryPolicyBuilder 重试策略构建器
type RetryPolicyBuilder struct {
	policy *RetryPolicy
}

// NewRetryPolicyBuilder 创建重试策略构建器
func NewRetryPolicyBuilder() *RetryPolicyBuilder {
	return &RetryPolicyBuilder{
		policy: DefaultRetryPolicy(),
	}
}

// MaxAttempts 设置最大重试次数
func (b *RetryPolicyBuilder) MaxAttempts(maxAttempts int) *RetryPolicyBuilder {
	if maxAttempts < 1 {
		maxAttempts = 1
	}
	b.policy.MaxAttempts = maxAttempts
	return b
}

// InitialDelay 设置初始延迟
func (b *RetryPolicyBuilder) InitialDelay(delay time.Duration) *RetryPolicyBuilder {
	if delay < 0 {
		delay = 0
	}
	b.policy.InitialDelay = delay
	return b
}

// MaxDelay 设置最大延迟
func (b *RetryPolicyBuilder) MaxDelay(delay time.Duration) *RetryPolicyBuilder {
	if delay < 0 {
		delay = 0
	}
	b.policy.MaxDelay = delay
	return b
}

// Multiplier 设置延迟倍数
func (b *RetryPolicyBuilder) Multiplier(multiplier float64) *RetryPolicyBuilder {
	if multiplier < 1.0 {
		multiplier = 1.0
	}
	b.policy.Multiplier = multiplier
	return b
}

// RetryableErrors 设置可重试的错误码
func (b *RetryPolicyBuilder) RetryableErrors(codes ...errors.ErrorCode) *RetryPolicyBuilder {
	b.policy.RetryableErrors = make(map[errors.ErrorCode]bool)
	for _, code := range codes {
		b.policy.RetryableErrors[code] = true
	}
	return b
}

// AddRetryableError 添加可重试的错误码
func (b *RetryPolicyBuilder) AddRetryableError(code errors.ErrorCode) *RetryPolicyBuilder {
	b.policy.RetryableErrors[code] = true
	return b
}

// Build 构建重试策略
func (b *RetryPolicyBuilder) Build() *RetryPolicy {
	return b.policy
}
