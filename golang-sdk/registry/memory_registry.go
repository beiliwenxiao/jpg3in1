package registry

import (
	"context"
	"fmt"
	"sync"
	"time"
)

// MemoryRegistryConfig 内存注册中心配置
type MemoryRegistryConfig struct {
	TTL               time.Duration // 服务 TTL（生存时间）
	HeartbeatInterval time.Duration // 心跳间隔
	CleanupInterval   time.Duration // 清理过期服务的间隔
}

// DefaultMemoryRegistryConfig 默认配置
func DefaultMemoryRegistryConfig() *MemoryRegistryConfig {
	return &MemoryRegistryConfig{
		TTL:               30 * time.Second,
		HeartbeatInterval: 10 * time.Second,
		CleanupInterval:   5 * time.Second,
	}
}

// serviceEntry 服务条目（包含服务信息和过期时间）
type serviceEntry struct {
	info      *ServiceInfo
	expiresAt time.Time
	mu        sync.RWMutex
}

// MemoryRegistry 基于内存的服务注册中心
// 零依赖，适合开发测试环境
type MemoryRegistry struct {
	config    *MemoryRegistryConfig
	mu        sync.RWMutex
	services  map[string]map[string]*serviceEntry // serviceName -> serviceID -> entry
	watchers  map[string][]func([]*ServiceInfo)   // serviceName -> callbacks
	ctx       context.Context
	cancel    context.CancelFunc
	wg        sync.WaitGroup
}

// NewMemoryRegistry 创建内存注册中心
func NewMemoryRegistry(config *MemoryRegistryConfig) *MemoryRegistry {
	if config == nil {
		config = DefaultMemoryRegistryConfig()
	}

	ctx, cancel := context.WithCancel(context.Background())

	registry := &MemoryRegistry{
		config:   config,
		services: make(map[string]map[string]*serviceEntry),
		watchers: make(map[string][]func([]*ServiceInfo)),
		ctx:      ctx,
		cancel:   cancel,
	}

	// 启动定期清理过期服务的 goroutine
	registry.wg.Add(1)
	go registry.cleanupExpiredServices()

	return registry
}

// Register 注册服务
func (m *MemoryRegistry) Register(ctx context.Context, service *ServiceInfo) error {
	if service == nil {
		return fmt.Errorf("service is nil")
	}

	if service.ID == "" {
		return fmt.Errorf("service ID is empty")
	}

	if service.Name == "" {
		return fmt.Errorf("service name is empty")
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	// 确保服务名称的 map 存在
	if m.services[service.Name] == nil {
		m.services[service.Name] = make(map[string]*serviceEntry)
	}

	// 创建或更新服务条目
	entry := &serviceEntry{
		info:      service,
		expiresAt: time.Now().Add(m.config.TTL),
	}

	m.services[service.Name][service.ID] = entry

	// 通知监听者
	go m.notifyWatchers(service.Name)

	return nil
}

// Deregister 注销服务
func (m *MemoryRegistry) Deregister(ctx context.Context, serviceID string) error {
	if serviceID == "" {
		return fmt.Errorf("service ID is empty")
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	// 查找并删除服务
	var serviceName string
	var found bool

	for name, instances := range m.services {
		if _, exists := instances[serviceID]; exists {
			delete(instances, serviceID)
			serviceName = name
			found = true

			// 如果该服务名下没有实例了，删除整个 map
			if len(instances) == 0 {
				delete(m.services, name)
			}
			break
		}
	}

	if !found {
		return fmt.Errorf("service not found: %s", serviceID)
	}

	// 通知监听者
	go m.notifyWatchers(serviceName)

	return nil
}

// Discover 查询服务
func (m *MemoryRegistry) Discover(ctx context.Context, serviceName string) ([]*ServiceInfo, error) {
	if serviceName == "" {
		return nil, fmt.Errorf("service name is empty")
	}

	m.mu.RLock()
	defer m.mu.RUnlock()

	instances, exists := m.services[serviceName]
	if !exists || len(instances) == 0 {
		return []*ServiceInfo{}, nil
	}

	// 收集所有未过期的服务实例
	now := time.Now()
	services := make([]*ServiceInfo, 0, len(instances))

	// 先收集所有服务 ID 并排序，确保返回顺序一致
	serviceIDs := make([]string, 0, len(instances))
	for id := range instances {
		serviceIDs = append(serviceIDs, id)
	}
	
	// 简单排序以保证顺序一致性
	for i := 0; i < len(serviceIDs); i++ {
		for j := i + 1; j < len(serviceIDs); j++ {
			if serviceIDs[i] > serviceIDs[j] {
				serviceIDs[i], serviceIDs[j] = serviceIDs[j], serviceIDs[i]
			}
		}
	}

	// 按排序后的 ID 顺序添加服务
	for _, id := range serviceIDs {
		entry := instances[id]
		entry.mu.RLock()
		if entry.expiresAt.After(now) {
			services = append(services, entry.info)
		}
		entry.mu.RUnlock()
	}

	return services, nil
}

// HealthCheck 健康检查
func (m *MemoryRegistry) HealthCheck(ctx context.Context, serviceID string) (HealthStatus, error) {
	if serviceID == "" {
		return HealthStatusUnknown, fmt.Errorf("service ID is empty")
	}

	m.mu.RLock()
	defer m.mu.RUnlock()

	// 查找服务
	now := time.Now()
	for _, instances := range m.services {
		if entry, exists := instances[serviceID]; exists {
			entry.mu.RLock()
			defer entry.mu.RUnlock()

			if entry.expiresAt.After(now) {
				return HealthStatusHealthy, nil
			}
			return HealthStatusUnhealthy, nil
		}
	}

	return HealthStatusUnknown, fmt.Errorf("service not found: %s", serviceID)
}

// Watch 监听服务变化
func (m *MemoryRegistry) Watch(ctx context.Context, serviceName string, callback func([]*ServiceInfo)) error {
	if serviceName == "" {
		return fmt.Errorf("service name is empty")
	}

	if callback == nil {
		return fmt.Errorf("callback is nil")
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	// 注册回调
	m.watchers[serviceName] = append(m.watchers[serviceName], callback)

	return nil
}

// Close 关闭注册中心
func (m *MemoryRegistry) Close() error {
	m.cancel()
	m.wg.Wait()
	return nil
}

// Heartbeat 发送心跳，更新服务的过期时间
func (m *MemoryRegistry) Heartbeat(ctx context.Context, serviceID string) error {
	if serviceID == "" {
		return fmt.Errorf("service ID is empty")
	}

	m.mu.RLock()
	defer m.mu.RUnlock()

	// 查找服务并更新过期时间
	for _, instances := range m.services {
		if entry, exists := instances[serviceID]; exists {
			entry.mu.Lock()
			entry.expiresAt = time.Now().Add(m.config.TTL)
			entry.mu.Unlock()
			return nil
		}
	}

	return fmt.Errorf("service not found: %s", serviceID)
}

// cleanupExpiredServices 定期清理过期的服务
func (m *MemoryRegistry) cleanupExpiredServices() {
	defer m.wg.Done()

	ticker := time.NewTicker(m.config.CleanupInterval)
	defer ticker.Stop()

	for {
		select {
		case <-m.ctx.Done():
			return
		case <-ticker.C:
			m.performCleanup()
		}
	}
}

// performCleanup 执行清理操作
func (m *MemoryRegistry) performCleanup() {
	m.mu.Lock()
	defer m.mu.Unlock()

	now := time.Now()
	changedServices := make(map[string]bool)

	// 遍历所有服务，删除过期的实例
	for serviceName, instances := range m.services {
		for serviceID, entry := range instances {
			entry.mu.RLock()
			expired := entry.expiresAt.Before(now)
			entry.mu.RUnlock()

			if expired {
				delete(instances, serviceID)
				changedServices[serviceName] = true
			}
		}

		// 如果该服务名下没有实例了，删除整个 map
		if len(instances) == 0 {
			delete(m.services, serviceName)
		}
	}

	// 通知监听者
	for serviceName := range changedServices {
		go m.notifyWatchers(serviceName)
	}
}

// notifyWatchers 通知监听者服务变化
func (m *MemoryRegistry) notifyWatchers(serviceName string) {
	m.mu.RLock()
	callbacks := m.watchers[serviceName]
	m.mu.RUnlock()

	if len(callbacks) == 0 {
		return
	}

	// 获取最新的服务列表
	services, err := m.Discover(context.Background(), serviceName)
	if err != nil {
		return
	}

	// 调用所有回调
	for _, callback := range callbacks {
		callback(services)
	}
}

// GetAllServices 获取所有服务（用于调试和监控）
func (m *MemoryRegistry) GetAllServices() map[string][]*ServiceInfo {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := make(map[string][]*ServiceInfo)
	now := time.Now()

	for serviceName, instances := range m.services {
		services := make([]*ServiceInfo, 0, len(instances))
		for _, entry := range instances {
			entry.mu.RLock()
			if entry.expiresAt.After(now) {
				services = append(services, entry.info)
			}
			entry.mu.RUnlock()
		}
		if len(services) > 0 {
			result[serviceName] = services
		}
	}

	return result
}
