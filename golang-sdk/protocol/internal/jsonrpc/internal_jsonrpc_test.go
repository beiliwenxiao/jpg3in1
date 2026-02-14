package jsonrpc

import (
	"context"
	"testing"
	"time"
)

// TestInternalJsonRpcHandlerCreation 测试内部 JSON-RPC 处理器创建
func TestInternalJsonRpcHandlerCreation(t *testing.T) {
	config := &InternalJsonRpcConfig{
		Host: "127.0.0.1",
		Port: 10001,
	}
	
	handler := NewInternalJsonRpcHandler(config)
	if handler == nil {
		t.Fatal("Failed to create internal JSON-RPC handler")
	}
}

// TestInternalJsonRpcHandlerStartStop 测试启动和停止
func TestInternalJsonRpcHandlerStartStop(t *testing.T) {
	config := &InternalJsonRpcConfig{
		Host: "127.0.0.1",
		Port: 10002,
	}
	
	handler := NewInternalJsonRpcHandler(config)
	
	// 启动服务器
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start handler: %v", err)
	}
	
	// 等待服务器启动
	time.Sleep(300 * time.Millisecond)
	
	// 停止服务器
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = handler.Stop(ctx)
	if err != nil {
		t.Fatalf("Failed to stop handler: %v", err)
	}
}

// TestInternalJsonRpcClientCreation 测试客户端创建
func TestInternalJsonRpcClientCreation(t *testing.T) {
	config := &InternalJsonRpcConfig{
		Host: "127.0.0.1",
		Port: 10003,
	}
	
	client := NewInternalJsonRpcClient(config)
	if client == nil {
		t.Fatal("Failed to create internal JSON-RPC client")
	}
}

// TestInternalJsonRpcClientConnectWithoutServer 测试客户端连接（无服务器）
func TestInternalJsonRpcClientConnectWithoutServer(t *testing.T) {
	config := &InternalJsonRpcConfig{
		Host: "127.0.0.1",
		Port: 19999, // 不存在的端口
	}
	
	client := NewInternalJsonRpcClient(config)
	
	err := client.Connect()
	if err == nil {
		t.Error("Expected connection to fail without server")
		client.Close()
	}
}

// TestInternalJsonRpcMethodRegistration 测试方法注册
func TestInternalJsonRpcMethodRegistration(t *testing.T) {
	config := &InternalJsonRpcConfig{
		Host: "127.0.0.1",
		Port: 10004,
	}
	
	handler := NewInternalJsonRpcHandler(config)
	
	// 注册方法
	handler.RegisterMethod("test.method", func(ctx context.Context, params interface{}) (interface{}, error) {
		return map[string]interface{}{"result": "success"}, nil
	})
	
	// 验证方法已注册
	handler.mu.RLock()
	_, exists := handler.handlers["test.method"]
	handler.mu.RUnlock()
	
	if !exists {
		t.Error("Method should be registered")
	}
}

// TestInternalJsonRpcClientServerCommunication 测试客户端和服务器通信
func TestInternalJsonRpcClientServerCommunication(t *testing.T) {
	config := &InternalJsonRpcConfig{
		Host: "127.0.0.1",
		Port: 10005,
	}
	
	// 启动服务器
	handler := NewInternalJsonRpcHandler(config)
	
	// 注册测试方法
	handler.RegisterMethod("echo", func(ctx context.Context, params interface{}) (interface{}, error) {
		return params, nil
	})
	
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 创建客户端并连接
	client := NewInternalJsonRpcClient(config)
	err = client.Connect()
	if err != nil {
		t.Fatalf("Failed to connect client: %v", err)
	}
	defer client.Close()
	
	// 调用方法
	params := map[string]interface{}{"message": "hello"}
	result, err := client.Call(context.Background(), "echo", params, 1)
	if err != nil {
		t.Fatalf("Failed to call method: %v", err)
	}
	
	if result == nil {
		t.Error("Expected non-nil result")
	}
}
