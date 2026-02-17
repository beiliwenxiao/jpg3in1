package connection

import (
	"context"
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// ConnectionPool 连接池
//
// 管理到单个服务端点的连接池
//
// **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 12.2**
type ConnectionPool struct {
	endpoint    *ServiceEndpoint
	config      *ConnectionConfig
	connections []*ManagedConnection
	mu          sync.RWMutex
	closed      atomic.Bool
	idCounter   atomic.Int64

	// 用于定期清理空闲连接
	cleanupTicker *time.Ticker
	cleanupDone   chan struct{}
}

// NewConnectionPool 创建新的连接池
func NewConnectionPool(endpoint *ServiceEndpoint, config *ConnectionConfig) *ConnectionPool {
	pool := &ConnectionPool{
		endpoint:      endpoint,
		config:        config,
		connections:   make([]*ManagedConnection, 0, config.MaxConnections),
		cleanupDone:   make(chan struct{}),
		cleanupTicker: time.NewTicker(config.HealthCheckInterval),
	}

	// 启动清理协程
	go pool.cleanupLoop()

	return pool
}

// Acquire 获取连接
func (p *ConnectionPool) Acquire(ctx context.Context) (*ManagedConnection, error) {
	if p.closed.Load() {
		return nil, fmt.Errorf("connection pool is closed")
	}

	// 首先尝试复用空闲连接
	if conn := p.findIdleConnection(); conn != nil {
		conn.SetState(StateActive)
		conn.UpdateLastUsed()
		return conn, nil
	}

	// 如果没有空闲连接，尝试创建新连接
	p.mu.Lock()
	defer p.mu.Unlock()

	// 再次检查（双重检查锁定）
	if conn := p.findIdleConnectionLocked(); conn != nil {
		conn.SetState(StateActive)
		conn.UpdateLastUsed()
		return conn, nil
	}

	// 检查是否达到最大连接数
	if len(p.connections) >= p.config.MaxConnections {
		return nil, fmt.Errorf("connection pool is full: %d/%d",
			len(p.connections), p.config.MaxConnections)
	}

	// 创建新连接
	conn, err := p.createConnection(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to create connection: %w", err)
	}

	p.connections = append(p.connections, conn)
	conn.SetState(StateActive)
	return conn, nil
}

// Release 释放连接回连接池
func (p *ConnectionPool) Release(conn *ManagedConnection) {
	if conn == nil {
		return
	}

	// 如果连接已关闭或不健康，从池中移除
	if conn.IsClosed() || !conn.IsHealthy() {
		p.removeConnection(conn)
		_ = conn.Close()
		return
	}

	// 将连接标记为空闲
	conn.SetState(StateIdle)
	conn.UpdateLastUsed()
}

// Close 关闭连接池
func (p *ConnectionPool) Close() error {
	if !p.closed.CompareAndSwap(false, true) {
		return nil
	}

	// 停止清理协程
	p.cleanupTicker.Stop()
	close(p.cleanupDone)

	p.mu.Lock()
	defer p.mu.Unlock()

	// 关闭所有连接
	var lastErr error
	for _, conn := range p.connections {
		if err := conn.Close(); err != nil {
			lastErr = err
		}
	}

	p.connections = nil
	return lastErr
}

// ShutdownGracefully 优雅关闭连接池
func (p *ConnectionPool) ShutdownGracefully(timeout time.Duration) error {
	if !p.closed.CompareAndSwap(false, true) {
		return nil
	}

	// 停止清理协程
	p.cleanupTicker.Stop()
	close(p.cleanupDone)

	// 等待所有活跃连接变为空闲
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if p.allConnectionsIdle() {
			break
		}
		time.Sleep(100 * time.Millisecond)
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	// 关闭所有连接
	var lastErr error
	for _, conn := range p.connections {
		if err := conn.Close(); err != nil {
			lastErr = err
		}
	}

	p.connections = nil
	return lastErr
}

// GetStats 获取连接池统计信息
func (p *ConnectionPool) GetStats() *ConnectionPoolStats {
	p.mu.RLock()
	defer p.mu.RUnlock()

	stats := &ConnectionPoolStats{
		MaxConnections: p.config.MaxConnections,
	}

	for _, conn := range p.connections {
		stats.TotalConnections++
		switch conn.State() {
		case StateActive:
			stats.ActiveConnections++
		case StateIdle:
			stats.IdleConnections++
		case StateClosed:
			stats.ClosedConnections++
		}
	}

	return stats
}

// UpdateConfig 更新连接池配置
func (p *ConnectionPool) UpdateConfig(config *ConnectionConfig) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.config = config
}

// createConnection 创建新连接
func (p *ConnectionPool) createConnection(ctx context.Context) (*ManagedConnection, error) {
	// 设置连接超时
	connectCtx, cancel := context.WithTimeout(ctx, p.config.ConnectTimeout)
	defer cancel()

	// 根据协议类型创建连接
	switch p.endpoint.Protocol {
	case "gRPC", "grpc":
		return p.createGrpcConnection(connectCtx)
	default:
		return nil, fmt.Errorf("unsupported protocol: %s", p.endpoint.Protocol)
	}
}

// createGrpcConnection 创建 gRPC 连接
func (p *ConnectionPool) createGrpcConnection(ctx context.Context) (*ManagedConnection, error) {
	target := fmt.Sprintf("%s:%d", p.endpoint.Address, p.endpoint.Port)

	opts := []grpc.DialOption{
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithBlock(),
	}

	conn, err := grpc.DialContext(ctx, target, opts...)
	if err != nil {
		return nil, fmt.Errorf("failed to dial gRPC: %w", err)
	}

	id := fmt.Sprintf("%s-%d", p.endpoint.Key(), p.idCounter.Add(1))
	return NewManagedConnection(id, p.endpoint, conn), nil
}

// findIdleConnection 查找空闲连接（无锁版本）
func (p *ConnectionPool) findIdleConnection() *ManagedConnection {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.findIdleConnectionLocked()
}

// findIdleConnectionLocked 查找空闲连接（需要持有锁）
func (p *ConnectionPool) findIdleConnectionLocked() *ManagedConnection {
	for _, conn := range p.connections {
		if conn.IsIdle() && conn.IsHealthy() {
			return conn
		}
	}
	return nil
}

// removeConnection 从池中移除连接
func (p *ConnectionPool) removeConnection(conn *ManagedConnection) {
	p.mu.Lock()
	defer p.mu.Unlock()

	for i, c := range p.connections {
		if c == conn {
			// 从切片中移除
			p.connections = append(p.connections[:i], p.connections[i+1:]...)
			break
		}
	}
}

// allConnectionsIdle 检查是否所有连接都是空闲的
func (p *ConnectionPool) allConnectionsIdle() bool {
	p.mu.RLock()
	defer p.mu.RUnlock()

	for _, conn := range p.connections {
		if conn.IsActive() {
			return false
		}
	}
	return true
}

// cleanupLoop 定期清理空闲和过期连接
func (p *ConnectionPool) cleanupLoop() {
	for {
		select {
		case <-p.cleanupDone:
			return
		case <-p.cleanupTicker.C:
			p.cleanup()
		}
	}
}

// cleanup 清理空闲和过期连接
func (p *ConnectionPool) cleanup() {
	p.mu.Lock()
	defer p.mu.Unlock()

	now := time.Now()
	toRemove := make([]*ManagedConnection, 0)

	for _, conn := range p.connections {
		// 检查连接是否已关闭
		if conn.IsClosed() {
			toRemove = append(toRemove, conn)
			continue
		}

		// 检查连接是否不健康
		if !conn.IsHealthy() {
			toRemove = append(toRemove, conn)
			continue
		}

		// 检查空闲超时
		if conn.IsIdle() && now.Sub(conn.LastUsedAt()) > p.config.IdleTimeout {
			toRemove = append(toRemove, conn)
			continue
		}

		// 检查最大生命周期
		if now.Sub(conn.CreatedAt()) > p.config.MaxLifetime {
			toRemove = append(toRemove, conn)
			continue
		}
	}

	// 移除并关闭连接
	for _, conn := range toRemove {
		for i, c := range p.connections {
			if c == conn {
				p.connections = append(p.connections[:i], p.connections[i+1:]...)
				break
			}
		}
		_ = conn.Close()
	}
}

// ConnectionPoolStats 连接池统计信息
type ConnectionPoolStats struct {
	TotalConnections  int
	ActiveConnections int
	IdleConnections   int
	ClosedConnections int
	MaxConnections    int
}
