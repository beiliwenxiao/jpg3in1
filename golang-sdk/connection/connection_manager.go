package connection

import (
	"context"
	"fmt"
	"sync"
	"sync/atomic"
	"time"
)

// ConnectionManager 连接管理器接口
//
// 管理到各服务端点的连接池，提供连接获取、释放和生命周期管理
//
// **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 12.2**
type ConnectionManager interface {
	// GetConnection 获取到指定端点的连接
	// 优先复用空闲连接，如果没有则创建新连接
	GetConnection(ctx context.Context, endpoint *ServiceEndpoint) (*ManagedConnection, error)

	// ReleaseConnection 释放连接回连接池
	ReleaseConnection(conn *ManagedConnection)

	// CloseConnections 关闭到指定端点的所有连接
	CloseConnections(endpoint *ServiceEndpoint) error

	// CloseAll 关闭所有连接并释放资源
	CloseAll() error

	// ShutdownGracefully 优雅关闭：等待所有活跃请求完成后关闭
	ShutdownGracefully(timeout time.Duration) error

	// GetPoolStats 获取到指定端点的连接池统计信息
	GetPoolStats(endpoint *ServiceEndpoint) *ConnectionPoolStats

	// GetTotalStats 获取全局连接池统计信息
	GetTotalStats() *ConnectionPoolStats

	// UpdateConfig 更新连接池配置
	UpdateConfig(config *ConnectionConfig)

	// IsClosed 检查是否已关闭
	IsClosed() bool
}

// DefaultConnectionManager 默认连接管理器实现
//
// 为每个 ServiceEndpoint 维护独立的连接池
//
// **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 12.2**
type DefaultConnectionManager struct {
	config *ConnectionConfig
	pools  sync.Map // map[string]*ConnectionPool
	closed atomic.Bool
	mu     sync.RWMutex
}

// NewConnectionManager 创建新的连接管理器
func NewConnectionManager(config *ConnectionConfig) ConnectionManager {
	if config == nil {
		config = DefaultConnectionConfig()
	}

	return &DefaultConnectionManager{
		config: config,
	}
}

// GetConnection 获取到指定端点的连接
func (m *DefaultConnectionManager) GetConnection(ctx context.Context, endpoint *ServiceEndpoint) (*ManagedConnection, error) {
	if m.closed.Load() {
		return nil, fmt.Errorf("connection manager is closed")
	}

	if endpoint == nil {
		return nil, fmt.Errorf("service endpoint cannot be nil")
	}

	// 获取或创建连接池
	key := endpointKey(endpoint)
	poolInterface, _ := m.pools.LoadOrStore(key, NewConnectionPool(endpoint, m.config))
	pool := poolInterface.(*ConnectionPool)

	// 从连接池获取连接
	return pool.Acquire(ctx)
}

// ReleaseConnection 释放连接回连接池
func (m *DefaultConnectionManager) ReleaseConnection(conn *ManagedConnection) {
	if conn == nil {
		return
	}

	key := endpointKey(conn.Endpoint())
	if poolInterface, ok := m.pools.Load(key); ok {
		pool := poolInterface.(*ConnectionPool)
		pool.Release(conn)
	} else {
		// 池已不存在，直接关闭连接
		_ = conn.Close()
	}
}

// CloseConnections 关闭到指定端点的所有连接
func (m *DefaultConnectionManager) CloseConnections(endpoint *ServiceEndpoint) error {
	key := endpointKey(endpoint)
	if poolInterface, ok := m.pools.LoadAndDelete(key); ok {
		pool := poolInterface.(*ConnectionPool)
		return pool.Close()
	}
	return nil
}

// CloseAll 关闭所有连接并释放资源
func (m *DefaultConnectionManager) CloseAll() error {
	if !m.closed.CompareAndSwap(false, true) {
		return nil
	}

	var lastErr error
	m.pools.Range(func(key, value interface{}) bool {
		pool := value.(*ConnectionPool)
		if err := pool.Close(); err != nil {
			lastErr = err
		}
		m.pools.Delete(key)
		return true
	})

	return lastErr
}

// ShutdownGracefully 优雅关闭：等待所有活跃请求完成后关闭
func (m *DefaultConnectionManager) ShutdownGracefully(timeout time.Duration) error {
	if !m.closed.CompareAndSwap(false, true) {
		return nil
	}

	var lastErr error
	m.pools.Range(func(key, value interface{}) bool {
		pool := value.(*ConnectionPool)
		if err := pool.ShutdownGracefully(timeout); err != nil {
			lastErr = err
		}
		m.pools.Delete(key)
		return true
	})

	return lastErr
}

// GetPoolStats 获取到指定端点的连接池统计信息
func (m *DefaultConnectionManager) GetPoolStats(endpoint *ServiceEndpoint) *ConnectionPoolStats {
	key := endpointKey(endpoint)
	if poolInterface, ok := m.pools.Load(key); ok {
		pool := poolInterface.(*ConnectionPool)
		return pool.GetStats()
	}
	return &ConnectionPoolStats{
		MaxConnections: m.config.MaxConnections,
	}
}

// GetTotalStats 获取全局连接池统计信息
func (m *DefaultConnectionManager) GetTotalStats() *ConnectionPoolStats {
	total := &ConnectionPoolStats{}

	m.pools.Range(func(key, value interface{}) bool {
		pool := value.(*ConnectionPool)
		stats := pool.GetStats()
		total.TotalConnections += stats.TotalConnections
		total.ActiveConnections += stats.ActiveConnections
		total.IdleConnections += stats.IdleConnections
		total.ClosedConnections += stats.ClosedConnections
		total.MaxConnections += stats.MaxConnections
		return true
	})

	return total
}

// UpdateConfig 更新连接池配置
func (m *DefaultConnectionManager) UpdateConfig(config *ConnectionConfig) {
	m.mu.Lock()
	defer m.mu.Unlock()

	m.config = config
	m.pools.Range(func(key, value interface{}) bool {
		pool := value.(*ConnectionPool)
		pool.UpdateConfig(config)
		return true
	})
}

// IsClosed 检查是否已关闭
func (m *DefaultConnectionManager) IsClosed() bool {
	return m.closed.Load()
}

// GetPoolCount 获取当前管理的连接池数量
func (m *DefaultConnectionManager) GetPoolCount() int {
	count := 0
	m.pools.Range(func(key, value interface{}) bool {
		count++
		return true
	})
	return count
}

// endpointKey 生成端点的唯一 key
func endpointKey(endpoint *ServiceEndpoint) string {
	return fmt.Sprintf("%s:%d", endpoint.Address, endpoint.Port)
}
