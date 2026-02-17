package connection

import (
	"context"
	"fmt"
	"net"
	"sync"
	"sync/atomic"
	"time"

	"google.golang.org/grpc"
)

// ConnectionState 连接状态
type ConnectionState int32

const (
	// StateIdle 空闲状态
	StateIdle ConnectionState = iota
	// StateActive 活跃状态
	StateActive
	// StateClosed 已关闭状态
	StateClosed
)

// String 返回状态的字符串表示
func (s ConnectionState) String() string {
	switch s {
	case StateIdle:
		return "idle"
	case StateActive:
		return "active"
	case StateClosed:
		return "closed"
	default:
		return "unknown"
	}
}

// ManagedConnection 受管连接
//
// 封装底层网络连接，提供状态管理和生命周期控制
//
// **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5**
type ManagedConnection struct {
	id         string
	endpoint   *ServiceEndpoint
	conn       interface{} // 底层连接（可以是 *grpc.ClientConn 或 net.Conn）
	grpcConn   *grpc.ClientConn
	state      int32 // 使用 atomic 操作
	createdAt  time.Time
	lastUsedAt time.Time
	mu         sync.RWMutex
	closeOnce  sync.Once
	closeErr   error
}

// NewManagedConnection 创建新的受管连接
func NewManagedConnection(id string, endpoint *ServiceEndpoint, conn interface{}) *ManagedConnection {
	mc := &ManagedConnection{
		id:         id,
		endpoint:   endpoint,
		conn:       conn,
		state:      int32(StateIdle),
		createdAt:  time.Now(),
		lastUsedAt: time.Now(),
	}

	// 如果是 gRPC 连接，保存引用
	if grpcConn, ok := conn.(*grpc.ClientConn); ok {
		mc.grpcConn = grpcConn
	}

	return mc
}

// ID 返回连接 ID
func (mc *ManagedConnection) ID() string {
	return mc.id
}

// Endpoint 返回服务端点
func (mc *ManagedConnection) Endpoint() *ServiceEndpoint {
	return mc.endpoint
}

// State 返回当前状态
func (mc *ManagedConnection) State() ConnectionState {
	return ConnectionState(atomic.LoadInt32(&mc.state))
}

// SetState 设置连接状态
func (mc *ManagedConnection) SetState(state ConnectionState) {
	atomic.StoreInt32(&mc.state, int32(state))
}

// CreatedAt 返回创建时间
func (mc *ManagedConnection) CreatedAt() time.Time {
	mc.mu.RLock()
	defer mc.mu.RUnlock()
	return mc.createdAt
}

// LastUsedAt 返回最后使用时间
func (mc *ManagedConnection) LastUsedAt() time.Time {
	mc.mu.RLock()
	defer mc.mu.RUnlock()
	return mc.lastUsedAt
}

// UpdateLastUsed 更新最后使用时间
func (mc *ManagedConnection) UpdateLastUsed() {
	mc.mu.Lock()
	defer mc.mu.Unlock()
	mc.lastUsedAt = time.Now()
}

// IsIdle 检查连接是否空闲
func (mc *ManagedConnection) IsIdle() bool {
	return mc.State() == StateIdle
}

// IsActive 检查连接是否活跃
func (mc *ManagedConnection) IsActive() bool {
	return mc.State() == StateActive
}

// IsClosed 检查连接是否已关闭
func (mc *ManagedConnection) IsClosed() bool {
	return mc.State() == StateClosed
}

// IsHealthy 检查连接是否健康
func (mc *ManagedConnection) IsHealthy() bool {
	if mc.IsClosed() {
		return false
	}

	// 检查 gRPC 连接状态
	if mc.grpcConn != nil {
		state := mc.grpcConn.GetState()
		// Ready 和 Idle 状态认为是健康的
		return state.String() == "READY" || state.String() == "IDLE"
	}

	// 检查普通 TCP 连接
	if netConn, ok := mc.conn.(net.Conn); ok {
		// 尝试设置读超时来检测连接是否有效
		_ = netConn.SetReadDeadline(time.Now().Add(1 * time.Millisecond))
		one := make([]byte, 1)
		_, err := netConn.Read(one)
		_ = netConn.SetReadDeadline(time.Time{}) // 清除超时

		// 如果是超时错误，说明连接是活的
		if netErr, ok := err.(net.Error); ok && netErr.Timeout() {
			return true
		}

		// 其他错误说明连接有问题
		return err == nil
	}

	return true
}

// GetGrpcConn 获取 gRPC 连接
func (mc *ManagedConnection) GetGrpcConn() *grpc.ClientConn {
	return mc.grpcConn
}

// GetConn 获取底层连接
func (mc *ManagedConnection) GetConn() interface{} {
	return mc.conn
}

// Send 发送请求（简化版，实际使用时需要根据协议类型处理）
func (mc *ManagedConnection) Send(ctx context.Context, request interface{}) (interface{}, error) {
	if mc.IsClosed() {
		return nil, fmt.Errorf("connection is closed")
	}

	mc.SetState(StateActive)
	defer mc.SetState(StateIdle)
	mc.UpdateLastUsed()

	// TODO: 根据协议类型实现实际的发送逻辑
	return nil, fmt.Errorf("not implemented")
}

// Close 关闭连接
func (mc *ManagedConnection) Close() error {
	mc.closeOnce.Do(func() {
		mc.SetState(StateClosed)

		// 关闭 gRPC 连接
		if mc.grpcConn != nil {
			mc.closeErr = mc.grpcConn.Close()
			return
		}

		// 关闭普通连接
		if closer, ok := mc.conn.(interface{ Close() error }); ok {
			mc.closeErr = closer.Close()
		}
	})

	return mc.closeErr
}

// String 返回连接的字符串表示
func (mc *ManagedConnection) String() string {
	return fmt.Sprintf("Connection{id=%s, endpoint=%s, state=%s, created=%s, lastUsed=%s}",
		mc.id,
		mc.endpoint.Address+":"+fmt.Sprint(mc.endpoint.Port),
		mc.State(),
		mc.createdAt.Format(time.RFC3339),
		mc.lastUsedAt.Format(time.RFC3339))
}
