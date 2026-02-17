package connection

import (
	"context"
	"testing"
	"time"
)

// TestConnectionManagerCreation 测试连接管理器创建
func TestConnectionManagerCreation(t *testing.T) {
	config := DefaultConnectionConfig()
	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	if manager == nil {
		t.Fatal("Manager should not be nil")
	}

	if manager.IsClosed() {
		t.Error("Manager should not be closed initially")
	}
}

// TestConnectionManagerNilEndpoint 测试空端点处理
func TestConnectionManagerNilEndpoint(t *testing.T) {
	config := DefaultConnectionConfig()
	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	ctx := context.Background()

	// 测试空端点
	_, err := manager.GetConnection(ctx, nil)
	if err == nil {
		t.Error("Expected error for nil endpoint")
	}
}

// TestConnectionManagerBasic 测试连接管理器基本功能（使用 mock 连接）
func TestConnectionManagerBasic(t *testing.T) {
	t.Skip("Skipping test that requires actual gRPC server")

	config := DefaultConnectionConfig()
	config.MaxConnections = 5
	config.IdleTimeout = 1 * time.Second

	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	endpoint := &ServiceEndpoint{
		ServiceID: "test-service",
		Name:      "test",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	ctx := context.Background()

	// 测试获取连接
	conn, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		t.Fatalf("Failed to get connection: %v", err)
	}

	if conn == nil {
		t.Fatal("Connection is nil")
	}

	// 测试连接状态
	if !conn.IsActive() {
		t.Error("Connection should be active")
	}

	// 测试释放连接
	manager.ReleaseConnection(conn)

	if !conn.IsIdle() {
		t.Error("Connection should be idle after release")
	}

	// 测试连接复用
	conn2, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		t.Fatalf("Failed to get connection: %v", err)
	}

	if conn.ID() != conn2.ID() {
		t.Error("Should reuse the same connection")
	}

	manager.ReleaseConnection(conn2)
}

// TestConnectionManagerMultipleEndpoints 测试多个端点
func TestConnectionManagerMultipleEndpoints(t *testing.T) {
	t.Skip("Skipping test that requires actual gRPC server")

	config := DefaultConnectionConfig()
	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	endpoint1 := &ServiceEndpoint{
		ServiceID: "service-1",
		Name:      "test1",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	endpoint2 := &ServiceEndpoint{
		ServiceID: "service-2",
		Name:      "test2",
		Address:   "localhost",
		Port:      50052,
		Protocol:  "gRPC",
	}

	ctx := context.Background()

	// 获取到不同端点的连接
	conn1, err := manager.GetConnection(ctx, endpoint1)
	if err != nil {
		t.Fatalf("Failed to get connection to endpoint1: %v", err)
	}

	conn2, err := manager.GetConnection(ctx, endpoint2)
	if err != nil {
		t.Fatalf("Failed to get connection to endpoint2: %v", err)
	}

	// 验证连接到不同端点
	if conn1.Endpoint().Port == conn2.Endpoint().Port {
		t.Error("Connections should be to different endpoints")
	}

	manager.ReleaseConnection(conn1)
	manager.ReleaseConnection(conn2)

	// 验证统计信息
	stats := manager.GetTotalStats()
	if stats.TotalConnections != 2 {
		t.Errorf("Expected 2 total connections, got %d", stats.TotalConnections)
	}
}

// TestConnectionManagerMaxConnections 测试最大连接数限制
func TestConnectionManagerMaxConnections(t *testing.T) {
	t.Skip("Skipping test that requires actual gRPC server")

	config := DefaultConnectionConfig()
	config.MaxConnections = 2

	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	endpoint := &ServiceEndpoint{
		ServiceID: "test-service",
		Name:      "test",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	ctx := context.Background()

	// 获取最大数量的连接
	conn1, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		t.Fatalf("Failed to get connection 1: %v", err)
	}

	conn2, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		t.Fatalf("Failed to get connection 2: %v", err)
	}

	// 尝试获取超过最大数量的连接
	_, err = manager.GetConnection(ctx, endpoint)
	if err == nil {
		t.Error("Expected error when exceeding max connections")
	}

	// 释放一个连接后应该可以再次获取
	manager.ReleaseConnection(conn1)

	conn3, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		t.Fatalf("Failed to get connection after release: %v", err)
	}

	manager.ReleaseConnection(conn2)
	manager.ReleaseConnection(conn3)
}

// TestConnectionManagerGracefulShutdown 测试优雅关闭
func TestConnectionManagerGracefulShutdown(t *testing.T) {
	t.Skip("Skipping test that requires actual gRPC server")

	config := DefaultConnectionConfig()
	manager := NewConnectionManager(config)

	endpoint := &ServiceEndpoint{
		ServiceID: "test-service",
		Name:      "test",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	ctx := context.Background()

	// 获取连接
	conn, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		t.Fatalf("Failed to get connection: %v", err)
	}

	// 释放连接
	manager.ReleaseConnection(conn)

	// 优雅关闭
	err = manager.ShutdownGracefully(5 * time.Second)
	if err != nil {
		t.Errorf("Graceful shutdown failed: %v", err)
	}

	// 验证管理器已关闭
	if !manager.IsClosed() {
		t.Error("Manager should be closed")
	}

	// 关闭后不应该能获取新连接
	_, err = manager.GetConnection(ctx, endpoint)
	if err == nil {
		t.Error("Expected error when getting connection from closed manager")
	}
}

// TestConnectionManagerCloseConnections 测试关闭特定端点的连接
func TestConnectionManagerCloseConnections(t *testing.T) {
	t.Skip("Skipping test that requires actual gRPC server")

	config := DefaultConnectionConfig()
	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	endpoint1 := &ServiceEndpoint{
		ServiceID: "service-1",
		Name:      "test1",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	endpoint2 := &ServiceEndpoint{
		ServiceID: "service-2",
		Name:      "test2",
		Address:   "localhost",
		Port:      50052,
		Protocol:  "gRPC",
	}

	ctx := context.Background()

	// 获取到两个端点的连接
	conn1, _ := manager.GetConnection(ctx, endpoint1)
	conn2, _ := manager.GetConnection(ctx, endpoint2)

	manager.ReleaseConnection(conn1)
	manager.ReleaseConnection(conn2)

	// 关闭 endpoint1 的连接
	err := manager.CloseConnections(endpoint1)
	if err != nil {
		t.Errorf("Failed to close connections: %v", err)
	}

	// endpoint1 的连接应该被关闭
	stats1 := manager.GetPoolStats(endpoint1)
	if stats1.TotalConnections != 0 {
		t.Errorf("Expected 0 connections for endpoint1, got %d", stats1.TotalConnections)
	}

	// endpoint2 的连接应该还在
	stats2 := manager.GetPoolStats(endpoint2)
	if stats2.TotalConnections == 0 {
		t.Error("Expected connections for endpoint2 to still exist")
	}
}

// TestConnectionManagerUpdateConfig 测试更新配置
func TestConnectionManagerUpdateConfig(t *testing.T) {
	config := DefaultConnectionConfig()
	config.MaxConnections = 5

	manager := NewConnectionManager(config)
	defer manager.CloseAll()

	// 更新配置
	newConfig := DefaultConnectionConfig()
	newConfig.MaxConnections = 10
	newConfig.IdleTimeout = 10 * time.Second

	manager.UpdateConfig(newConfig)

	// 验证配置已更新（通过统计信息）
	endpoint := &ServiceEndpoint{
		ServiceID: "test-service",
		Name:      "test",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	stats := manager.GetPoolStats(endpoint)
	if stats.MaxConnections != 10 {
		t.Errorf("Expected max connections to be 10, got %d", stats.MaxConnections)
	}
}
