package grpc

import (
	"context"
	"testing"
	"time"
)

// TestGrpcClientCreation 测试 gRPC 客户端创建
func TestGrpcClientCreation(t *testing.T) {
	config := &GrpcClientConfig{
		Address: "localhost",
		Port:    9001,
		Timeout: 5 * time.Second,
	}
	
	client := NewGrpcClient(config)
	if client == nil {
		t.Fatal("Failed to create gRPC client")
	}
	
	if client.config.Address != config.Address {
		t.Errorf("Expected address %s, got %s", config.Address, client.config.Address)
	}
}

// TestGrpcServerCreation 测试 gRPC 服务器创建
func TestGrpcServerCreation(t *testing.T) {
	config := &GrpcServerConfig{
		Host: "127.0.0.1",
		Port: 9002,
	}
	
	server := NewGrpcServer(config)
	if server == nil {
		t.Fatal("Failed to create gRPC server")
	}
	
	if server.config.Host != config.Host {
		t.Errorf("Expected host %s, got %s", config.Host, server.config.Host)
	}
}

// TestGrpcServerStartStop 测试 gRPC 服务器启动和停止
func TestGrpcServerStartStop(t *testing.T) {
	config := &GrpcServerConfig{
		Host: "127.0.0.1",
		Port: 9003,
	}
	
	server := NewGrpcServer(config)
	
	// 启动服务器
	err := server.Start()
	if err != nil {
		t.Fatalf("Failed to start gRPC server: %v", err)
	}
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 停止服务器
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	err = server.Stop(ctx)
	if err != nil {
		t.Fatalf("Failed to stop gRPC server: %v", err)
	}
}

// TestGrpcClientConnectWithoutServer 测试客户端连接（无服务器）
func TestGrpcClientConnectWithoutServer(t *testing.T) {
	config := &GrpcClientConfig{
		Address: "localhost",
		Port:    9999, // 不存在的端口
		Timeout: 1 * time.Second,
	}
	
	client := NewGrpcClient(config)
	
	ctx := context.Background()
	err := client.Connect(ctx)
	
	// 预期连接失败
	if err == nil {
		t.Error("Expected connection to fail without server")
		client.Close()
	}
}

// TestGrpcClientConnectToServer 测试客户端连接到服务器
func TestGrpcClientConnectToServer(t *testing.T) {
	// 启动服务器
	serverConfig := &GrpcServerConfig{
		Host: "127.0.0.1",
		Port: 9004,
	}
	
	server := NewGrpcServer(serverConfig)
	err := server.Start()
	if err != nil {
		t.Fatalf("Failed to start gRPC server: %v", err)
	}
	defer server.Stop(context.Background())
	
	// 等待服务器启动
	time.Sleep(500 * time.Millisecond)
	
	// 创建客户端并连接
	clientConfig := &GrpcClientConfig{
		Address: "127.0.0.1",
		Port:    9004,
		Timeout: 5 * time.Second,
	}
	
	client := NewGrpcClient(clientConfig)
	
	ctx := context.Background()
	err = client.Connect(ctx)
	if err != nil {
		t.Fatalf("Failed to connect to gRPC server: %v", err)
	}
	defer client.Close()
	
	// 验证连接状态
	if !client.IsConnected() {
		t.Error("Client should be connected")
	}
}

// TestGrpcClientClose 测试客户端关闭
func TestGrpcClientClose(t *testing.T) {
	// 启动服务器
	serverConfig := &GrpcServerConfig{
		Host: "127.0.0.1",
		Port: 9005,
	}
	
	server := NewGrpcServer(serverConfig)
	err := server.Start()
	if err != nil {
		t.Fatalf("Failed to start gRPC server: %v", err)
	}
	defer server.Stop(context.Background())
	
	time.Sleep(500 * time.Millisecond)
	
	// 创建客户端并连接
	clientConfig := &GrpcClientConfig{
		Address: "127.0.0.1",
		Port:    9005,
		Timeout: 5 * time.Second,
	}
	
	client := NewGrpcClient(clientConfig)
	
	ctx := context.Background()
	err = client.Connect(ctx)
	if err != nil {
		t.Fatalf("Failed to connect to gRPC server: %v", err)
	}
	
	// 关闭客户端
	err = client.Close()
	if err != nil {
		t.Fatalf("Failed to close client: %v", err)
	}
}

// TestGrpcClientCallWithoutConnection 测试未连接时调用
func TestGrpcClientCallWithoutConnection(t *testing.T) {
	config := &GrpcClientConfig{
		Address: "localhost",
		Port:    9006,
		Timeout: 5 * time.Second,
	}
	
	client := NewGrpcClient(config)
	
	// 尝试在未连接时调用
	ctx := context.Background()
	err := client.Call(ctx, "test-service", "test-method", nil, nil)
	
	// 预期失败
	if err == nil {
		t.Error("Expected error when calling without connection")
	}
}
