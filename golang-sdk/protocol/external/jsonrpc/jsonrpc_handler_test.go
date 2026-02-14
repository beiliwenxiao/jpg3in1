package jsonrpc

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"testing"
	"time"
)

// TestJsonRpcHandlerCreation 测试 JSON-RPC 处理器创建
func TestJsonRpcHandlerCreation(t *testing.T) {
	config := &JsonRpcConfig{
		Host: "127.0.0.1",
		Port: 8096,
		Path: "/jsonrpc",
	}
	
	handler := NewJsonRpcProtocolHandler(config)
	if handler == nil {
		t.Fatal("Failed to create JSON-RPC protocol handler")
	}
}

// TestJsonRpcHandlerStartStop 测试 JSON-RPC 处理器启动和停止
func TestJsonRpcHandlerStartStop(t *testing.T) {
	config := &JsonRpcConfig{
		Host: "127.0.0.1",
		Port: 8097,
		Path: "/jsonrpc",
	}
	
	handler := NewJsonRpcProtocolHandler(config)
	
	// 启动处理器
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start JSON-RPC handler: %v", err)
	}
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 停止处理器
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = handler.Stop(ctx)
	if err != nil {
		t.Fatalf("Failed to stop JSON-RPC handler: %v", err)
	}
}

// TestJsonRpcValidRequest 测试有效的 JSON-RPC 请求
func TestJsonRpcValidRequest(t *testing.T) {
	config := &JsonRpcConfig{
		Host: "127.0.0.1",
		Port: 8098,
		Path: "/jsonrpc",
	}
	
	handler := NewJsonRpcProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start JSON-RPC handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 构造 JSON-RPC 请求
	request := JsonRpcRequest{
		Jsonrpc: "2.0",
		Method:  "test.method",
		Params:  map[string]interface{}{"key": "value"},
		Id:      1,
	}
	
	requestBody, _ := json.Marshal(request)
	
	// 发送请求
	resp, err := http.Post("http://127.0.0.1:8098/jsonrpc", "application/json", bytes.NewBuffer(requestBody))
	if err != nil {
		t.Fatalf("Failed to send JSON-RPC request: %v", err)
	}
	defer resp.Body.Close()
	
	// 解析响应
	var response JsonRpcResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		t.Fatalf("Failed to decode response: %v", err)
	}
	
	// 验证响应
	if response.Jsonrpc != "2.0" {
		t.Errorf("Expected jsonrpc 2.0, got %s", response.Jsonrpc)
	}
	
	if response.Error != nil {
		t.Errorf("Expected no error, got: %v", response.Error)
	}
	
	if response.Result == nil {
		t.Error("Expected result, got nil")
	}
}

// TestJsonRpcInvalidVersion 测试无效的 JSON-RPC 版本
func TestJsonRpcInvalidVersion(t *testing.T) {
	config := &JsonRpcConfig{
		Host: "127.0.0.1",
		Port: 8099,
		Path: "/jsonrpc",
	}
	
	handler := NewJsonRpcProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start JSON-RPC handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 构造无效版本的请求
	request := JsonRpcRequest{
		Jsonrpc: "1.0",
		Method:  "test.method",
		Id:      1,
	}
	
	requestBody, _ := json.Marshal(request)
	
	// 发送请求
	resp, err := http.Post("http://127.0.0.1:8099/jsonrpc", "application/json", bytes.NewBuffer(requestBody))
	if err != nil {
		t.Fatalf("Failed to send JSON-RPC request: %v", err)
	}
	defer resp.Body.Close()
	
	// 解析响应
	var response JsonRpcResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		t.Fatalf("Failed to decode response: %v", err)
	}
	
	// 验证错误响应
	if response.Error == nil {
		t.Error("Expected error response for invalid version")
	}
	
	if response.Error.Code != -32600 {
		t.Errorf("Expected error code -32600, got %d", response.Error.Code)
	}
}

// TestJsonRpcMissingMethod 测试缺少方法名的请求
func TestJsonRpcMissingMethod(t *testing.T) {
	config := &JsonRpcConfig{
		Host: "127.0.0.1",
		Port: 8100,
		Path: "/jsonrpc",
	}
	
	handler := NewJsonRpcProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start JSON-RPC handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 构造缺少方法名的请求
	request := JsonRpcRequest{
		Jsonrpc: "2.0",
		Method:  "",
		Id:      1,
	}
	
	requestBody, _ := json.Marshal(request)
	
	// 发送请求
	resp, err := http.Post("http://127.0.0.1:8100/jsonrpc", "application/json", bytes.NewBuffer(requestBody))
	if err != nil {
		t.Fatalf("Failed to send JSON-RPC request: %v", err)
	}
	defer resp.Body.Close()
	
	// 解析响应
	var response JsonRpcResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		t.Fatalf("Failed to decode response: %v", err)
	}
	
	// 验证错误响应
	if response.Error == nil {
		t.Error("Expected error response for missing method")
	}
}

// TestJsonRpcInvalidJson 测试无效的 JSON
func TestJsonRpcInvalidJson(t *testing.T) {
	config := &JsonRpcConfig{
		Host: "127.0.0.1",
		Port: 8101,
		Path: "/jsonrpc",
	}
	
	handler := NewJsonRpcProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start JSON-RPC handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 发送无效的 JSON
	invalidJson := []byte(`{invalid json}`)
	
	resp, err := http.Post("http://127.0.0.1:8101/jsonrpc", "application/json", bytes.NewBuffer(invalidJson))
	if err != nil {
		t.Fatalf("Failed to send request: %v", err)
	}
	defer resp.Body.Close()
	
	// 解析响应
	var response JsonRpcResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		t.Fatalf("Failed to decode response: %v", err)
	}
	
	// 验证错误响应
	if response.Error == nil {
		t.Error("Expected error response for invalid JSON")
	}
	
	if response.Error.Code != -32700 {
		t.Errorf("Expected error code -32700, got %d", response.Error.Code)
	}
}
