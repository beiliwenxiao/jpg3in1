package connection_test

import (
	"context"
	"fmt"
	"time"

	"github.com/framework/golang-sdk/connection"
)

// Example_basicUsage 演示基本使用
func Example_basicUsage() {
	// 创建配置
	config := connection.DefaultConnectionConfig()
	config.MaxConnections = 50
	config.IdleTimeout = 5 * time.Minute

	// 创建连接管理器
	manager := connection.NewConnectionManager(config)
	defer manager.CloseAll()

	// 定义服务端点
	endpoint := &connection.ServiceEndpoint{
		ServiceID: "user-service",
		Name:      "user",
		Address:   "localhost",
		Port:      50051,
		Protocol:  "gRPC",
	}

	// 获取连接
	ctx := context.Background()
	conn, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		fmt.Printf("Failed to get connection: %v\n", err)
		return
	}

	fmt.Printf("Got connection: %s\n", conn.ID())

	// 使用连接
	// ... 执行 RPC 调用 ...

	// 释放连接
	manager.ReleaseConnection(conn)

	fmt.Println("Connection released")
}

// Example_withRetry 演示带重连的使用
func Example_withRetry() {
	config := connection.DefaultConnectionConfig()
	config.MaxReconnectAttempts = 3
	config.ReconnectDelay = 1 * time.Second

	// 创建基础连接管理器
	baseManager := connection.NewConnectionManager(config)

	// 包装为生命周期管理器
	lifecycleManager := connection.NewConnectionLifecycleManager(baseManager, config)
	defer lifecycleManager.CloseAll()

	endpoint := &connection.ServiceEndpoint{
		ServiceID: "api-service",
		Name:      "api",
		Address:   "localhost",
		Port:      50052,
		Protocol:  "gRPC",
	}

	// 获取连接（带自动重连）
	ctx := context.Background()
	conn, err := lifecycleManager.GetConnectionWithRetry(ctx, endpoint)
	if err != nil {
		fmt.Printf("Failed to get connection after retries: %v\n", err)
		return
	}

	fmt.Printf("Got connection with retry: %s\n", conn.ID())

	// 使用连接
	// ...

	// 释放连接
	lifecycleManager.ReleaseConnection(conn)
}

// Example_stats 演示统计信息
func Example_stats() {
	config := connection.DefaultConnectionConfig()
	manager := connection.NewConnectionManager(config)
	defer manager.CloseAll()

	endpoint := &connection.ServiceEndpoint{
		ServiceID: "data-service",
		Name:      "data",
		Address:   "localhost",
		Port:      50053,
		Protocol:  "gRPC",
	}

	// 获取连接池统计
	stats := manager.GetPoolStats(endpoint)
	fmt.Printf("Pool Stats - Total: %d, Active: %d, Idle: %d, Max: %d\n",
		stats.TotalConnections,
		stats.ActiveConnections,
		stats.IdleConnections,
		stats.MaxConnections)

	// 获取全局统计
	totalStats := manager.GetTotalStats()
	fmt.Printf("Total Stats - Total: %d, Active: %d\n",
		totalStats.TotalConnections,
		totalStats.ActiveConnections)
}

// Example_gracefulShutdown 演示优雅关闭
func Example_gracefulShutdown() {
	config := connection.DefaultConnectionConfig()
	manager := connection.NewConnectionManager(config)

	endpoint := &connection.ServiceEndpoint{
		ServiceID: "worker-service",
		Name:      "worker",
		Address:   "localhost",
		Port:      50054,
		Protocol:  "gRPC",
	}

	ctx := context.Background()
	conn, err := manager.GetConnection(ctx, endpoint)
	if err != nil {
		fmt.Printf("Failed to get connection: %v\n", err)
		return
	}

	// 使用连接
	// ...

	// 释放连接
	manager.ReleaseConnection(conn)

	// 优雅关闭，等待最多 30 秒
	err = manager.ShutdownGracefully(30 * time.Second)
	if err != nil {
		fmt.Printf("Graceful shutdown error: %v\n", err)
		return
	}

	fmt.Println("Manager shutdown gracefully")
}

// Example_updateConfig 演示动态更新配置
func Example_updateConfig() {
	config := connection.DefaultConnectionConfig()
	config.MaxConnections = 50

	manager := connection.NewConnectionManager(config)
	defer manager.CloseAll()

	fmt.Printf("Initial max connections: %d\n", config.MaxConnections)

	// 更新配置
	newConfig := connection.DefaultConnectionConfig()
	newConfig.MaxConnections = 100
	newConfig.IdleTimeout = 10 * time.Minute

	manager.UpdateConfig(newConfig)

	fmt.Println("Configuration updated")
}
