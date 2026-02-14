package client

import (
	"context"
	"testing"
	"time"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// TestClientCreation 测试客户端创建
func TestClientCreation(t *testing.T) {
	config := &Config{
		ServiceRegistry: "http://localhost:2379",
	}

	client := NewFrameworkClient(config)
	if client == nil {
		t.Fatal("Failed to create framework client")
	}
}

// TestServiceRegistration 测试服务注册
func TestServiceRegistration(t *testing.T) {
	config := &Config{
		ServiceRegistry: "http://localhost:2379",
	}

	client := NewFrameworkClient(config)
	
	err := client.RegisterService("test-service", nil)
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// 重复注册应该失败
	err = client.RegisterService("test-service", nil)
	if err == nil {
		t.Fatal("Expected error when registering duplicate service")
	}
}

// TestClientLifecycle 测试客户端生命周期
func TestClientLifecycle(t *testing.T) {
	config := &Config{
		ServiceRegistry: "http://localhost:2379",
	}

	client := NewFrameworkClient(config)

	// 启动客户端
	err := client.Start()
	if err != nil {
		t.Fatalf("Failed to start client: %v", err)
	}

	// 重复启动应该失败
	err = client.Start()
	if err == nil {
		t.Fatal("Expected error when starting already started client")
	}

	// 关闭客户端
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err = client.Shutdown(ctx)
	if err != nil {
		t.Fatalf("Failed to shutdown client: %v", err)
	}
}

// TestCrossLanguageAPIConsistency 属性测试：跨语言 API 一致性
// Feature: multi-language-communication-framework, Property 1: 跨语言 API 一致性
// 验证需求: 1.4
func TestCrossLanguageAPIConsistency(t *testing.T) {
	properties := gopter.NewProperties(nil)

	properties.Property("Golang SDK provides consistent API interface", prop.ForAll(
		func(serviceName string, methodName string) bool {
			config := &Config{
				ServiceRegistry: "http://localhost:2379",
			}

			client := NewFrameworkClient(config)

			// 验证 API 方法存在且签名一致
			// 1. Call 方法应该接受 context, service, method, request, response
			ctx := context.Background()
			err := client.Call(ctx, serviceName, methodName, nil, nil)
			
			// 客户端未启动时应该返回错误
			if err == nil {
				return false
			}

			// 2. CallAsync 方法应该返回 channel
			_, err = client.CallAsync(ctx, serviceName, methodName, nil)
			if err == nil {
				return false
			}

			// 3. Stream 方法应该返回 channel
			_, err = client.Stream(ctx, serviceName, methodName, nil)
			if err == nil {
				return false
			}

			// 4. RegisterService 方法应该能注册服务
			err = client.RegisterService(serviceName, nil)
			if err != nil {
				return false
			}

			return true
		},
		gen.Identifier(),
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestAsyncCallReturnsChannel 测试异步调用返回 channel
func TestAsyncCallReturnsChannel(t *testing.T) {
	config := &Config{
		ServiceRegistry: "http://localhost:2379",
	}

	client := NewFrameworkClient(config)
	err := client.Start()
	if err != nil {
		t.Fatalf("Failed to start client: %v", err)
	}
	defer client.Shutdown(context.Background())

	ctx := context.Background()
	responseChan, err := client.CallAsync(ctx, "test-service", "test-method", nil)
	
	if err != nil {
		t.Fatalf("CallAsync failed: %v", err)
	}

	if responseChan == nil {
		t.Fatal("CallAsync should return a channel")
	}

	// 等待响应或超时
	select {
	case response := <-responseChan:
		// 应该收到响应（即使是错误）
		if response.Error == nil {
			t.Log("Received response (expected error due to not implemented)")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Timeout waiting for async response")
	}
}

// TestStreamReturnsChannel 测试流式调用返回 channel
func TestStreamReturnsChannel(t *testing.T) {
	config := &Config{
		ServiceRegistry: "http://localhost:2379",
	}

	client := NewFrameworkClient(config)
	err := client.Start()
	if err != nil {
		t.Fatalf("Failed to start client: %v", err)
	}
	defer client.Shutdown(context.Background())

	ctx := context.Background()
	streamChan, err := client.Stream(ctx, "test-service", "test-method", nil)
	
	if err != nil {
		t.Fatalf("Stream failed: %v", err)
	}

	if streamChan == nil {
		t.Fatal("Stream should return a channel")
	}
}
