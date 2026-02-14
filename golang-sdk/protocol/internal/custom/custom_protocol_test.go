package custom

import (
	"context"
	"testing"
	"time"
)

// TestCustomProtocolHandlerCreation 测试自定义协议处理器创建
func TestCustomProtocolHandlerCreation(t *testing.T) {
	config := &CustomProtocolConfig{
		Host: "127.0.0.1",
		Port: 11001,
	}
	
	handler := NewCustomProtocolHandler(config)
	if handler == nil {
		t.Fatal("Failed to create custom protocol handler")
	}
}

// TestCustomProtocolHandlerStartStop 测试启动和停止
func TestCustomProtocolHandlerStartStop(t *testing.T) {
	config := &CustomProtocolConfig{
		Host: "127.0.0.1",
		Port: 11002,
	}
	
	handler := NewCustomProtocolHandler(config)
	
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

// TestCustomProtocolClientCreation 测试客户端创建
func TestCustomProtocolClientCreation(t *testing.T) {
	config := &CustomProtocolConfig{
		Host: "127.0.0.1",
		Port: 11003,
	}
	
	client := NewCustomProtocolClient(config)
	if client == nil {
		t.Fatal("Failed to create custom protocol client")
	}
}

// TestCustomProtocolClientConnectWithoutServer 测试客户端连接（无服务器）
func TestCustomProtocolClientConnectWithoutServer(t *testing.T) {
	config := &CustomProtocolConfig{
		Host: "127.0.0.1",
		Port: 19998, // 不存在的端口
	}
	
	client := NewCustomProtocolClient(config)
	
	err := client.Connect()
	if err == nil {
		t.Error("Expected connection to fail without server")
		client.Close()
	}
}

// TestCustomFrameCreation 测试帧创建
func TestCustomFrameCreation(t *testing.T) {
	frame := &CustomFrame{
		Header: &FrameHeader{
			Magic:      MagicNumber,
			Version:    1,
			Type:       FrameTypeData,
			Flags:      0,
			StreamId:   1,
			BodyLength: 10,
			Sequence:   1,
			Timestamp:  time.Now().UnixMilli(),
		},
		Body: []byte("test data"),
	}
	
	if frame.Header.Magic != MagicNumber {
		t.Errorf("Expected magic number 0x%X, got 0x%X", MagicNumber, frame.Header.Magic)
	}
	
	if frame.Header.Type != FrameTypeData {
		t.Errorf("Expected frame type DATA, got %s", frame.Header.Type)
	}
}

// TestFrameTypeString 测试帧类型字符串转换
func TestFrameTypeString(t *testing.T) {
	tests := []struct {
		frameType FrameType
		expected  string
	}{
		{FrameTypeData, "DATA"},
		{FrameTypePing, "PING"},
		{FrameTypePong, "PONG"},
		{FrameTypeClose, "CLOSE"},
		{FrameTypeWindowUpdate, "WINDOW_UPDATE"},
		{FrameTypeSettings, "SETTINGS"},
		{FrameTypeError, "ERROR"},
		{FrameTypeMetadata, "METADATA"},
	}
	
	for _, test := range tests {
		if test.frameType.String() != test.expected {
			t.Errorf("Expected %s, got %s", test.expected, test.frameType.String())
		}
	}
}

// TestCustomProtocolHandlerRegistration 测试处理器注册
func TestCustomProtocolHandlerRegistration(t *testing.T) {
	config := &CustomProtocolConfig{
		Host: "127.0.0.1",
		Port: 11004,
	}
	
	handler := NewCustomProtocolHandler(config)
	
	// 注册处理器
	handler.RegisterHandler(FrameTypeData, func(ctx context.Context, frame *CustomFrame) (*CustomFrame, error) {
		return frame, nil
	})
	
	// 验证处理器已注册
	handler.mu.RLock()
	_, exists := handler.handlers[FrameTypeData.String()]
	handler.mu.RUnlock()
	
	if !exists {
		t.Error("Handler should be registered")
	}
}

// TestCustomProtocolClientServerCommunication 测试客户端和服务器通信
func TestCustomProtocolClientServerCommunication(t *testing.T) {
	config := &CustomProtocolConfig{
		Host: "127.0.0.1",
		Port: 11005,
	}
	
	// 启动服务器
	handler := NewCustomProtocolHandler(config)
	
	// 注册处理器（回显）
	handler.RegisterHandler(FrameTypeData, func(ctx context.Context, frame *CustomFrame) (*CustomFrame, error) {
		return frame, nil
	})
	
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 创建客户端并连接
	client := NewCustomProtocolClient(config)
	err = client.Connect()
	if err != nil {
		t.Fatalf("Failed to connect client: %v", err)
	}
	defer client.Close()
	
	// 发送帧
	sendFrame := &CustomFrame{
		Header: &FrameHeader{
			Magic:      MagicNumber,
			Version:    1,
			Type:       FrameTypeData,
			Flags:      0,
			StreamId:   1,
			BodyLength: 9,
			Sequence:   1,
			Timestamp:  time.Now().UnixMilli(),
		},
		Body: []byte("test data"),
	}
	
	err = client.SendFrame(sendFrame)
	if err != nil {
		t.Fatalf("Failed to send frame: %v", err)
	}
	
	// 接收响应
	recvFrame, err := client.ReceiveFrame()
	if err != nil {
		t.Fatalf("Failed to receive frame: %v", err)
	}
	
	if recvFrame == nil {
		t.Fatal("Expected non-nil frame")
	}
	
	if recvFrame.Header.Type != FrameTypeData {
		t.Errorf("Expected frame type DATA, got %s", recvFrame.Header.Type)
	}
}
