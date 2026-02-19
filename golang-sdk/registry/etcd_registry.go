package registry

import (
	"context"
	"encoding/json"
	"fmt"
	"path"
	"sync"
	"time"

	clientv3 "go.etcd.io/etcd/client/v3"
)

// EtcdRegistryConfig etcd 注册中心配置
type EtcdRegistryConfig struct {
	Endpoints        []string      // etcd 端点列表
	Namespace        string        // 命名空间
	TTL              int64         // 租约 TTL（秒）
	HeartbeatInterval time.Duration // 心跳间隔
	DialTimeout      time.Duration // 连接超时
}

// DefaultEtcdRegistryConfig 默认配置
func DefaultEtcdRegistryConfig() *EtcdRegistryConfig {
	return &EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      5 * time.Second,
	}
}

// EtcdRegistry 基于 etcd 的服务注册中心
type EtcdRegistry struct {
	client    *clientv3.Client
	config    *EtcdRegistryConfig
	leaseID   clientv3.LeaseID
	mu        sync.RWMutex
	services  map[string]*ServiceInfo // serviceID -> ServiceInfo
	watchers  map[string][]func([]*ServiceInfo) // serviceName -> callbacks
	ctx       context.Context
	cancel    context.CancelFunc
	wg        sync.WaitGroup
}

// NewEtcdRegistry 创建 etcd 注册中心
func NewEtcdRegistry(config *EtcdRegistryConfig) (*EtcdRegistry, error) {
	if config == nil {
		config = DefaultEtcdRegistryConfig()
	}

	client, err := clientv3.New(clientv3.Config{
		Endpoints:   config.Endpoints,
		DialTimeout: config.DialTimeout,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to create etcd client: %w", err)
	}

	// 立即做连通性检查，避免测试中懒连接导致的超时挂起
	pingCtx, pingCancel := context.WithTimeout(context.Background(), config.DialTimeout)
	defer pingCancel()
	_, err = client.Status(pingCtx, config.Endpoints[0])
	if err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("etcd not reachable at %s: %w", config.Endpoints[0], err)
	}

	ctx, cancel := context.WithCancel(context.Background())

	registry := &EtcdRegistry{
		client:   client,
		config:   config,
		services: make(map[string]*ServiceInfo),
		watchers: make(map[string][]func([]*ServiceInfo)),
		ctx:      ctx,
		cancel:   cancel,
	}

	return registry, nil
}

// Register 注册服务
func (r *EtcdRegistry) Register(ctx context.Context, service *ServiceInfo) error {
	if service == nil {
		return fmt.Errorf("service is nil")
	}

	if service.ID == "" {
		return fmt.Errorf("service ID is empty")
	}

	if service.Name == "" {
		return fmt.Errorf("service name is empty")
	}

	// 创建租约
	lease, err := r.client.Grant(ctx, r.config.TTL)
	if err != nil {
		return fmt.Errorf("failed to create lease: %w", err)
	}

	r.leaseID = lease.ID

	// 序列化服务信息
	data, err := json.Marshal(service)
	if err != nil {
		return fmt.Errorf("failed to marshal service info: %w", err)
	}

	// 注册服务到 etcd
	key := r.getServiceKey(service.Name, service.ID)
	_, err = r.client.Put(ctx, key, string(data), clientv3.WithLease(lease.ID))
	if err != nil {
		return fmt.Errorf("failed to register service: %w", err)
	}

	// 保存服务信息
	r.mu.Lock()
	r.services[service.ID] = service
	r.mu.Unlock()

	// 启动心跳保活
	r.wg.Add(1)
	go r.keepAlive(service.ID)

	return nil
}

// Deregister 注销服务
func (r *EtcdRegistry) Deregister(ctx context.Context, serviceID string) error {
	if serviceID == "" {
		return fmt.Errorf("service ID is empty")
	}

	r.mu.Lock()
	service, exists := r.services[serviceID]
	if !exists {
		r.mu.Unlock()
		return fmt.Errorf("service not found: %s", serviceID)
	}
	delete(r.services, serviceID)
	r.mu.Unlock()

	// 从 etcd 删除服务
	key := r.getServiceKey(service.Name, service.ID)
	_, err := r.client.Delete(ctx, key)
	if err != nil {
		return fmt.Errorf("failed to deregister service: %w", err)
	}

	// 撤销租约
	if r.leaseID != 0 {
		_, err = r.client.Revoke(ctx, r.leaseID)
		if err != nil {
			return fmt.Errorf("failed to revoke lease: %w", err)
		}
	}

	return nil
}

// Discover 查询服务
func (r *EtcdRegistry) Discover(ctx context.Context, serviceName string) ([]*ServiceInfo, error) {
	if serviceName == "" {
		return nil, fmt.Errorf("service name is empty")
	}

	// 查询服务前缀
	prefix := r.getServicePrefix(serviceName)
	resp, err := r.client.Get(ctx, prefix, clientv3.WithPrefix())
	if err != nil {
		return nil, fmt.Errorf("failed to discover services: %w", err)
	}

	// 解析服务信息
	services := make([]*ServiceInfo, 0, len(resp.Kvs))
	for _, kv := range resp.Kvs {
		var service ServiceInfo
		if err := json.Unmarshal(kv.Value, &service); err != nil {
			continue // 跳过无效的服务信息
		}
		services = append(services, &service)
	}

	return services, nil
}

// HealthCheck 健康检查
func (r *EtcdRegistry) HealthCheck(ctx context.Context, serviceID string) (HealthStatus, error) {
	r.mu.RLock()
	service, exists := r.services[serviceID]
	r.mu.RUnlock()

	if !exists {
		return HealthStatusUnknown, fmt.Errorf("service not found: %s", serviceID)
	}

	// 检查服务在 etcd 中是否存在
	key := r.getServiceKey(service.Name, service.ID)
	resp, err := r.client.Get(ctx, key)
	if err != nil {
		return HealthStatusUnknown, fmt.Errorf("failed to check service health: %w", err)
	}

	if len(resp.Kvs) == 0 {
		return HealthStatusUnhealthy, nil
	}

	return HealthStatusHealthy, nil
}

// Watch 监听服务变化
func (r *EtcdRegistry) Watch(ctx context.Context, serviceName string, callback func([]*ServiceInfo)) error {
	if serviceName == "" {
		return fmt.Errorf("service name is empty")
	}

	if callback == nil {
		return fmt.Errorf("callback is nil")
	}

	// 注册回调
	r.mu.Lock()
	r.watchers[serviceName] = append(r.watchers[serviceName], callback)
	r.mu.Unlock()

	// 启动监听
	r.wg.Add(1)
	go r.watchService(serviceName)

	return nil
}

// Close 关闭注册中心连接
func (r *EtcdRegistry) Close() error {
	r.cancel()
	r.wg.Wait()

	if r.client != nil {
		return r.client.Close()
	}

	return nil
}

// keepAlive 保持租约活跃
func (r *EtcdRegistry) keepAlive(serviceID string) {
	defer r.wg.Done()

	ticker := time.NewTicker(r.config.HeartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-r.ctx.Done():
			return
		case <-ticker.C:
			if r.leaseID == 0 {
				continue
			}

			// 续约
			_, err := r.client.KeepAliveOnce(r.ctx, r.leaseID)
			if err != nil {
				// 续约失败，尝试重新注册
				r.mu.RLock()
				service, exists := r.services[serviceID]
				r.mu.RUnlock()

				if exists {
					_ = r.Register(r.ctx, service)
				}
			}
		}
	}
}

// watchService 监听服务变化
func (r *EtcdRegistry) watchService(serviceName string) {
	defer r.wg.Done()

	prefix := r.getServicePrefix(serviceName)
	watchChan := r.client.Watch(r.ctx, prefix, clientv3.WithPrefix())

	for {
		select {
		case <-r.ctx.Done():
			return
		case watchResp := <-watchChan:
			if watchResp.Err() != nil {
				continue
			}

			// 查询最新的服务列表
			services, err := r.Discover(r.ctx, serviceName)
			if err != nil {
				continue
			}

			// 通知所有回调
			r.mu.RLock()
			callbacks := r.watchers[serviceName]
			r.mu.RUnlock()

			for _, callback := range callbacks {
				callback(services)
			}
		}
	}
}

// getServiceKey 获取服务的 etcd key
func (r *EtcdRegistry) getServiceKey(serviceName, serviceID string) string {
	return path.Join(r.config.Namespace, serviceName, serviceID)
}

// getServicePrefix 获取服务的 etcd 前缀
func (r *EtcdRegistry) getServicePrefix(serviceName string) string {
	return path.Join(r.config.Namespace, serviceName) + "/"
}
