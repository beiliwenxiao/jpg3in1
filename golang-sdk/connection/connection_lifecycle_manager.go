package connection

import (
	"context"
	"fmt"
	"sync"
	"sync/atomic"
	"time"
)

// ConnectionLifecycleManager 连接生命周期管理器
//
// 包装 DefaultConnectionManager，增加重连和生命周期管理能力
//
// **验证需求: 7.2, 7.3, 7.5**
type ConnectionLifecycleManager struct {
	manager      ConnectionManager
	config       *ConnectionConfig
	closed       atomic.Bool
	reconnecting sync.Map // map[string]*atomic.Bool
}

// NewConnectionLifecycleManager 创建连接生命周期管理器
func NewConnectionLifecycleManager(manager ConnectionManager, config *ConnectionConfig) *ConnectionLifecycleManager {
	if config == nil {
		config = DefaultConnectionConfig()
	}

	return &ConnectionLifecycleManager{
		manager: manager,
		config:  config,
	}
}

// GetConnectionWithRetry 获取连接（带重试）
//
// 如果连接失败，会根据配置的重连策略进行重试
//
// **验证需求: 7.3**
func (m *ConnectionLifecycleManager) GetConnectionWithRetry(ctx context.Context, endpoint *ServiceEndpoint) (*ManagedConnection, error) {
	if m.closed.Load() {
		return nil, fmt.Errorf("connection lifecycle manager is closed")
	}

	key := endpointKey(endpoint)

	// 检查是否正在重连
	if reconnecting, ok := m.reconnecting.Load(key); ok {
		if reconnecting.(*atomic.Bool).Load() {
			return nil, fmt.Errorf("endpoint %s is reconnecting", key)
		}
	}

	var lastErr error
	attempts := 0
	maxAttempts := m.config.MaxReconnectAttempts

	for attempts <= maxAttempts {
		attempts++

		// 尝试获取连接
		conn, err := m.manager.GetConnection(ctx, endpoint)
		if err == nil {
			return conn, nil
		}

		lastErr = err

		// 如果是最后一次尝试，不再等待
		if attempts > maxAttempts {
			break
		}

		// 计算退避延迟（指数退避）
		delay := m.config.ReconnectDelay * time.Duration(1<<uint(attempts-1))
		if delay > 30*time.Second {
			delay = 30 * time.Second
		}

		// 等待后重试
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-time.After(delay):
			// 继续重试
		}
	}

	return nil, fmt.Errorf("failed to connect after %d attempts: %w", attempts, lastErr)
}

// ReconnectAsync 异步重连到指定端点
//
// 在后台尝试重新建立连接
//
// **验证需求: 7.3**
func (m *ConnectionLifecycleManager) ReconnectAsync(endpoint *ServiceEndpoint) {
	key := endpointKey(endpoint)

	// 创建或获取重连标志
	reconnectingInterface, _ := m.reconnecting.LoadOrStore(key, &atomic.Bool{})
	reconnecting := reconnectingInterface.(*atomic.Bool)

	// 如果已经在重连，直接返回
	if !reconnecting.CompareAndSwap(false, true) {
		return
	}

	go func() {
		defer reconnecting.Store(false)

		ctx := context.Background()
		_, _ = m.GetConnectionWithRetry(ctx, endpoint)
	}()
}

// ReleaseConnection 释放连接
func (m *ConnectionLifecycleManager) ReleaseConnection(conn *ManagedConnection) {
	m.manager.ReleaseConnection(conn)
}

// CloseConnections 关闭到指定端点的所有连接
func (m *ConnectionLifecycleManager) CloseConnections(endpoint *ServiceEndpoint) error {
	return m.manager.CloseConnections(endpoint)
}

// ShutdownGracefully 优雅关闭
//
// 等待所有活跃连接完成后关闭
//
// **验证需求: 7.5**
func (m *ConnectionLifecycleManager) ShutdownGracefully(timeout time.Duration) error {
	if !m.closed.CompareAndSwap(false, true) {
		return nil
	}

	return m.manager.ShutdownGracefully(timeout)
}

// CloseAll 关闭所有连接
func (m *ConnectionLifecycleManager) CloseAll() error {
	if !m.closed.CompareAndSwap(false, true) {
		return nil
	}

	return m.manager.CloseAll()
}

// GetPoolStats 获取连接池统计
func (m *ConnectionLifecycleManager) GetPoolStats(endpoint *ServiceEndpoint) *ConnectionPoolStats {
	return m.manager.GetPoolStats(endpoint)
}

// GetTotalStats 获取全局统计
func (m *ConnectionLifecycleManager) GetTotalStats() *ConnectionPoolStats {
	return m.manager.GetTotalStats()
}

// UpdateConfig 更新配置
func (m *ConnectionLifecycleManager) UpdateConfig(config *ConnectionConfig) {
	m.config = config
	m.manager.UpdateConfig(config)
}

// IsClosed 检查是否已关闭
func (m *ConnectionLifecycleManager) IsClosed() bool {
	return m.closed.Load()
}

// GetManager 获取底层连接管理器
func (m *ConnectionLifecycleManager) GetManager() ConnectionManager {
	return m.manager
}
