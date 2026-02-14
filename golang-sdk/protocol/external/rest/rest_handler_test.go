package rest

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"testing"
	"time"
)

// TestRestHandlerCreation 测试 REST 处理器创建
func TestRestHandlerCreation(t *testing.T) {
	config := &RestConfig{
		Host: "127.0.0.1",
		Port: 8081,
		Path: "/api",
	}
	
	handler := NewRestProtocolHandler(config)
	if handler == nil {
		t.Fatal("Failed to create REST protocol handler")
	}
}

// TestRestHandlerStartStop 测试 REST 处理器启动和停止
func TestRestHandlerStartStop(t *testing.T) {
	config := &RestConfig{
		Host: "127.0.0.1",
		Port: 8082,
		Path: "/api",
	}
	
	handler := NewRestProtocolHandler(config)
	
	// 启动处理器
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start REST handler: %v", err)
	}
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 停止处理器
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = handler.Stop(ctx)
	if err != nil {
		t.Fatalf("Failed to stop REST handler: %v", err)
	}
}

// TestRestHandlerGET 测试 GET 请求
func TestRestHandlerGET(t *testing.T) {
	config := &RestConfig{
		Host: "127.0.0.1",
		Port: 8083,
		Path: "/api",
	}
	
	handler := NewRestProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start REST handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 发送 GET 请求
	resp, err := http.Get("http://127.0.0.1:8083/api/test")
	if err != nil {
		t.Fatalf("Failed to send GET request: %v", err)
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		t.Errorf("Expected status code %d, got %d", http.StatusOK, resp.StatusCode)
	}
}

// TestRestHandlerPOST 测试 POST 请求
func TestRestHandlerPOST(t *testing.T) {
	config := &RestConfig{
		Host: "127.0.0.1",
		Port: 8084,
		Path: "/api",
	}
	
	handler := NewRestProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start REST handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 准备请求体
	requestBody := map[string]interface{}{
		"name": "test",
		"value": 123,
	}
	bodyBytes, _ := json.Marshal(requestBody)
	
	// 发送 POST 请求
	resp, err := http.Post("http://127.0.0.1:8084/api/test", "application/json", bytes.NewBuffer(bodyBytes))
	if err != nil {
		t.Fatalf("Failed to send POST request: %v", err)
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		t.Errorf("Expected status code %d, got %d", http.StatusOK, resp.StatusCode)
	}
}

// TestRestHandlerAllMethods 测试所有 HTTP 方法
func TestRestHandlerAllMethods(t *testing.T) {
	config := &RestConfig{
		Host: "127.0.0.1",
		Port: 8085,
		Path: "/api",
	}
	
	handler := NewRestProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start REST handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	methods := []string{
		http.MethodGet,
		http.MethodPost,
		http.MethodPut,
		http.MethodDelete,
		http.MethodPatch,
	}
	
	client := &http.Client{}
	
	for _, method := range methods {
		req, err := http.NewRequest(method, "http://127.0.0.1:8085/api/test", nil)
		if err != nil {
			t.Fatalf("Failed to create %s request: %v", method, err)
		}
		
		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("Failed to send %s request: %v", method, err)
		}
		
		if resp.StatusCode != http.StatusOK {
			t.Errorf("Method %s: expected status code %d, got %d", method, http.StatusOK, resp.StatusCode)
		}
		
		resp.Body.Close()
	}
}
