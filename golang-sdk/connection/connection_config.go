package connection

import "time"

// ConnectionConfig 连接池配置
//
// 提供连接池的各项配置参数
//
// **验证需求: 7.1, 12.2**
type ConnectionConfig struct {
	// MaxConnections 最大连接数
	MaxConnections int

	// MinConnections 最小连接数（核心连接数）
	MinConnections int

	// IdleTimeout 连接空闲超时时间
	IdleTimeout time.Duration

	// MaxLifetime 连接最大存活时间
	MaxLifetime time.Duration

	// ConnectionTimeout 获取连接超时时间
	ConnectionTimeout time.Duration

	// ConnectTimeout 连接建立超时时间
	ConnectTimeout time.Duration

	// HealthCheckInterval 健康检查间隔
	HealthCheckInterval time.Duration

	// ReconnectDelay 重连延迟
	ReconnectDelay time.Duration

	// MaxReconnectAttempts 最大重连次数
	MaxReconnectAttempts int

	// KeepAlive 是否启用 TCP KeepAlive
	KeepAlive bool

	// TCPNoDelay 是否启用 TCP NoDelay
	TCPNoDelay bool
}

// DefaultConnectionConfig 返回默认连接配置
func DefaultConnectionConfig() *ConnectionConfig {
	return &ConnectionConfig{
		MaxConnections:       100,
		MinConnections:       10,
		IdleTimeout:          5 * time.Minute,
		MaxLifetime:          30 * time.Minute,
		ConnectionTimeout:    5 * time.Second,
		ConnectTimeout:       5 * time.Second,
		HealthCheckInterval:  30 * time.Second,
		ReconnectDelay:       1 * time.Second,
		MaxReconnectAttempts: 3,
		KeepAlive:            true,
		TCPNoDelay:           true,
	}
}
