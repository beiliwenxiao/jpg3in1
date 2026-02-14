package websocket

import (
	"context"
	"testing"
	"time"

	"github.com/gogf/gf/v2/net/gclient"
)

// TestWebSocketHandlerCreation 测试 WebSocket 处理器创建
func TestWebSocketHandlerCreation(t *testing.T) {
	config := &WebSocketConfig{
		Host: "127.0.0.1",
		Port: 8091,
		Path: "/ws",
	}
	
	handler := NewWebSocketProtocolHandler(config)
	if handler == nil {
		t.Fatal("Failed to create WebSocket protocol handler")
	}
}

// TestWebSocketHandlerStartStop 测试 WebSocket 处理器启动和停止
func TestWebSocketHandlerStartStop(t *testing.T) {
	config := &WebSocketConfig{
		Host: "127.0.0.1",
		Port: 8092,
		Path: "/ws",
	}
	
	handler := NewWebSocketProtocolHandler(config)
	
	// 启动处理器
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start WebSocket handler: %v", err)
	}
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 停止处理器
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = handler.Stop(ctx)
	if err != nil {
		t.Fatalf("Failed to stop WebSocket handler: %v", err)
	}
}

// TestWebSocketTextMessage 测试文本消息
func TestWebSocketTextMessage(t *testing.T) {
	config := &WebSocketConfig{
		Host: "127.0.0.1",
		Port: 8093,
		Path: "/ws",
	}
	
	handler := NewWebSocketProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start WebSocket handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 创建 WebSocket 客户端
	client := gclient.NewWebSocket()
	conn, _, err := client.Dial("ws://127.0.0.1:8093/ws", nil)
	if err != nil {
		t.Fatalf("Failed to connect to WebSocket: %v", err)
	}
	defer conn.Close()
	
	// 发送文本消息
	testMessage := []byte(`{"type":"test","message":"hello"}`)
	err = conn.WriteMessage(1, testMessage)
	if err != nil {
		t.Fatalf("Failed to send text message: %v", err)
	}
	
	// 读取响应
	msgType, message, err := conn.ReadMessage()
	if err != nil {
		t.Fatalf("Failed to read response: %v", err)
	}
	
	if msgType != 1 {
		t.Errorf("Expected text message type (1), got %d", msgType)
	}
	
	if len(message) == 0 {
		t.Error("Expected non-empty response")
	}
}

// TestWebSocketBinaryMessage 测试二进制消息
func TestWebSocketBinaryMessage(t *testing.T) {
	config := &WebSocketConfig{
		Host: "127.0.0.1",
		Port: 8094,
		Path: "/ws",
	}
	
	handler := NewWebSocketProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start WebSocket handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 创建 WebSocket 客户端
	client := gclient.NewWebSocket()
	conn, _, err := client.Dial("ws://127.0.0.1:8094/ws", nil)
	if err != nil {
		t.Fatalf("Failed to connect to WebSocket: %v", err)
	}
	defer conn.Close()
	
	// 发送二进制消息
	testMessage := []byte{0x01, 0x02, 0x03, 0x04, 0x05}
	err = conn.WriteMessage(2, testMessage)
	if err != nil {
		t.Fatalf("Failed to send binary message: %v", err)
	}
	
	// 读取响应
	msgType, message, err := conn.ReadMessage()
	if err != nil {
		t.Fatalf("Failed to read response: %v", err)
	}
	
	if msgType != 2 {
		t.Errorf("Expected binary message type (2), got %d", msgType)
	}
	
	if len(message) == 0 {
		t.Error("Expected non-empty response")
	}
}

// TestWebSocketMultipleMessages 测试多条消息
func TestWebSocketMultipleMessages(t *testing.T) {
	config := &WebSocketConfig{
		Host: "127.0.0.1",
		Port: 8095,
		Path: "/ws",
	}
	
	handler := NewWebSocketProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start WebSocket handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 创建 WebSocket 客户端
	client := gclient.NewWebSocket()
	conn, _, err := client.Dial("ws://127.0.0.1:8095/ws", nil)
	if err != nil {
		t.Fatalf("Failed to connect to WebSocket: %v", err)
	}
	defer conn.Close()
	
	// 发送多条消息
	for i := 0; i < 5; i++ {
		testMessage := []byte(`{"index":` + string(rune(i+'0')) + `}`)
		err = conn.WriteMessage(1, testMessage)
		if err != nil {
			t.Fatalf("Failed to send message %d: %v", i, err)
		}
		
		// 读取响应
		_, message, err := conn.ReadMessage()
		if err != nil {
			t.Fatalf("Failed to read response %d: %v", i, err)
		}
		
		if len(message) == 0 {
			t.Errorf("Expected non-empty response for message %d", i)
		}
	}
}
