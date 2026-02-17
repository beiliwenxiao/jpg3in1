package main

import (
	"context"
	"fmt"
	"math/rand"
	"time"

	"github.com/framework/golang-sdk/errors"
	"github.com/framework/golang-sdk/resilience"
)

// 模拟一个不稳定的服务调用
func unstableServiceCall(failureRate float64) error {
	if rand.Float64() < failureRate {
		return errors.NewFrameworkError(errors.ServiceUnavailable, "服务暂时不可用")
	}
	return nil
}

// 示例1: 基本错误处理
func example1_BasicErrorHandling() {
	fmt.Println("=== 示例1: 基本错误处理 ===")

	// 创建不同类型的错误
	err1 := errors.NewFrameworkError(errors.BadRequest, "无效的请求参数")
	fmt.Printf("错误1: %v\n", err1)

	err2 := errors.NewFrameworkErrorWithDetails(
		errors.NotFound,
		"资源未找到",
		"用户ID: 12345",
	)
	fmt.Printf("错误2: %v\n", err2)

	// 错误响应
	response := err2.ToErrorResponse()
	fmt.Printf("错误响应: %+v\n\n", response)
}

// 示例2: 错误码映射
func example2_ErrorCodeMapping() {
	fmt.Println("=== 示例2: 错误码映射 ===")

	// HTTP 状态码映射
	code1 := errors.FromHTTPStatus(404)
	fmt.Printf("HTTP 404 映射为: %s (代码: %d)\n", code1.String(), code1.Code())

	// gRPC 状态码映射
	code2 := errors.FromGRPCStatus(5)
	fmt.Printf("gRPC 5 映射为: %s (代码: %d)\n", code2.String(), code2.Code())

	// 转换为 HTTP 状态码
	httpStatus := errors.ServiceUnavailable.ToHTTPStatus()
	fmt.Printf("ServiceUnavailable 转换为 HTTP: %d\n\n", httpStatus)
}

// 示例3: 重试策略
func example3_RetryPolicy() {
	fmt.Println("=== 示例3: 重试策略 ===")

	// 创建重试策略
	policy := resilience.NewRetryPolicyBuilder().
		MaxAttempts(3).
		InitialDelay(100 * time.Millisecond).
		MaxDelay(1 * time.Second).
		Multiplier(2.0).
		Build()

	executor := resilience.NewRetryExecutor(policy)

	// 模拟一个可能失败的操作
	attemptCount := 0
	err := executor.Execute(func() error {
		attemptCount++
		fmt.Printf("尝试 #%d\n", attemptCount)
		if attemptCount < 3 {
			return errors.NewFrameworkError(errors.Timeout, "请求超时")
		}
		return nil
	})

	if err != nil {
		fmt.Printf("操作失败: %v\n", err)
	} else {
		fmt.Printf("操作成功，共尝试 %d 次\n", attemptCount)
	}
	fmt.Println()
}

// 示例4: 熔断器
func example4_CircuitBreaker() {
	fmt.Println("=== 示例4: 熔断器 ===")

	// 创建熔断器
	cb := resilience.NewCircuitBreaker(
		"example-service",
		3,                  // 失败阈值
		2,                  // 成功阈值
		500*time.Millisecond, // 超时时间
	)

	// 模拟多次调用
	for i := 1; i <= 10; i++ {
		fmt.Printf("调用 #%d - 状态: %s - ", i, cb.GetState())

		err := cb.Execute(func() error {
			// 前5次调用失败，后5次成功
			if i <= 5 {
				return errors.NewFrameworkError(errors.InternalError, "服务错误")
			}
			return nil
		})

		if err != nil {
			fmt.Printf("失败: %v\n", err)
		} else {
			fmt.Printf("成功\n")
		}

		// 在第6次调用前等待熔断器超时
		if i == 5 {
			fmt.Println("等待熔断器超时...")
			time.Sleep(600 * time.Millisecond)
		}
	}
	fmt.Println()
}

// 示例5: 组合使用重试和熔断器
func example5_CombinedUsage() {
	fmt.Println("=== 示例5: 组合使用重试和熔断器 ===")

	policy := resilience.NewRetryPolicyBuilder().
		MaxAttempts(2).
		InitialDelay(50 * time.Millisecond).
		Build()

	executor := resilience.NewRetryExecutor(policy)
	cb := resilience.NewCircuitBreaker("combined-service", 3, 2, 500*time.Millisecond)

	// 模拟调用
	for i := 1; i <= 5; i++ {
		fmt.Printf("请求 #%d - ", i)

		err := executor.Execute(func() error {
			return cb.Execute(func() error {
				// 70% 失败率
				return unstableServiceCall(0.7)
			})
		})

		if err != nil {
			fmt.Printf("失败 - 熔断器状态: %s\n", cb.GetState())
		} else {
			fmt.Printf("成功 - 熔断器状态: %s\n", cb.GetState())
		}

		time.Sleep(100 * time.Millisecond)
	}
	fmt.Println()
}

// 示例6: 异步重试
func example6_AsyncRetry() {
	fmt.Println("=== 示例6: 异步重试 ===")

	policy := resilience.DefaultRetryPolicy()
	executor := resilience.NewRetryExecutor(policy)
	ctx := context.Background()

	// 异步执行
	resultChan := executor.ExecuteAsyncWithResult(ctx, func() (interface{}, error) {
		fmt.Println("执行异步操作...")
		time.Sleep(100 * time.Millisecond)
		return "异步结果", nil
	})

	// 等待结果
	result := <-resultChan
	if result.Error != nil {
		fmt.Printf("异步操作失败: %v\n", result.Error)
	} else {
		fmt.Printf("异步操作成功: %v\n", result.Result)
	}
	fmt.Println()
}

func main() {
	rand.Seed(time.Now().UnixNano())

	example1_BasicErrorHandling()
	example2_ErrorCodeMapping()
	example3_RetryPolicy()
	example4_CircuitBreaker()
	example5_CombinedUsage()
	example6_AsyncRetry()

	fmt.Println("所有示例执行完成！")
}
