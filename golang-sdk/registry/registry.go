package registry

import (
	"context"
	"time"
)

// ServiceInfo 服务信息
type ServiceInfo struct {
	ID           string            // 服务实例 ID
	Name         string            // 服务名称
	Version      string            // 服务版本
	Language     string            // 编程语言
	Address      string            // 服务地址
	Port         int               // 服务端口
	Protocols    []string          // 支持的协议
	Metadata     map[string]string // 元数据
	RegisteredAt time.Time         // 注册时间
}

// HealthStatus 健康状态
type HealthStatus string

const (
	HealthStatusHealthy   HealthStatus = "healthy"
	HealthStatusUnhealthy HealthStatus = "unhealthy"
	HealthStatusUnknown   HealthStatus = "unknown"
)

// ServiceRegistry 服务注册中心接口
type ServiceRegistry interface {
	// Register 注册服务
	Register(ctx context.Context, service *ServiceInfo) error

	// Deregister 注销服务
	Deregister(ctx context.Context, serviceID string) error

	// Discover 查询服务
	Discover(ctx context.Context, serviceName string) ([]*ServiceInfo, error)

	// HealthCheck 健康检查
	HealthCheck(ctx context.Context, serviceID string) (HealthStatus, error)

	// Watch 监听服务变化
	Watch(ctx context.Context, serviceName string, callback func([]*ServiceInfo)) error

	// Close 关闭注册中心连接
	Close() error
}
