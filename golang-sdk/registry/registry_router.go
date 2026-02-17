package registry

import (
	"context"
	"fmt"
	"sync"

	"github.com/framework/golang-sdk/protocol/adapter"
	"github.com/framework/golang-sdk/protocol/router"
)

// RegistryRouter 集成服务注册的路由器
type RegistryRouter struct {
	registry     ServiceRegistry
	router       router.MessageRouter
	loadBalancer router.LoadBalancer
	mu           sync.RWMutex
	watchers     map[string]context.CancelFunc // serviceName -> cancel function
}

// NewRegistryRouter 创建集成服务注册的路由器
func NewRegistryRouter(registry ServiceRegistry, loadBalancer router.LoadBalancer) *RegistryRouter {
	if loadBalancer == nil {
		loadBalancer = router.NewRoundRobinLoadBalancer()
	}

	return &RegistryRouter{
		registry:     registry,
		router:       router.NewDefaultMessageRouter(loadBalancer),
		loadBalancer: loadBalancer,
		watchers:     make(map[string]context.CancelFunc),
	}
}

// Route 路由消息到目标服务
func (rr *RegistryRouter) Route(ctx context.Context, request *adapter.InternalRequest) (*router.ServiceEndpoint, error) {
	if request == nil {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "request is nil",
		}
	}

	// 从注册中心查询服务
	services, err := rr.registry.Discover(ctx, request.Service)
	if err != nil {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: fmt.Sprintf("failed to discover service %s: %v", request.Service, err),
			Cause:   err,
		}
	}

	if len(services) == 0 {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: fmt.Sprintf("no available instances for service: %s", request.Service),
		}
	}

	// 转换为 ServiceEndpoint
	endpoints := make([]*router.ServiceEndpoint, 0, len(services))
	for _, service := range services {
		endpoint := &router.ServiceEndpoint{
			ServiceId: service.ID,
			Address:   service.Address,
			Port:      service.Port,
			Protocol:  rr.selectProtocol(service.Protocols),
			Metadata:  service.Metadata,
		}
		endpoints = append(endpoints, endpoint)
	}

	// 使用负载均衡器选择端点
	endpoint, err := rr.loadBalancer.Select(endpoints)
	if err != nil {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorRouting,
			Message: fmt.Sprintf("failed to select endpoint for service %s", request.Service),
			Cause:   err,
		}
	}

	return endpoint, nil
}

// RegisterService 注册服务
func (rr *RegistryRouter) RegisterService(ctx context.Context, service *ServiceInfo) error {
	return rr.registry.Register(ctx, service)
}

// DeregisterService 注销服务
func (rr *RegistryRouter) DeregisterService(ctx context.Context, serviceID string) error {
	return rr.registry.Deregister(ctx, serviceID)
}

// WatchService 监听服务变化
func (rr *RegistryRouter) WatchService(ctx context.Context, serviceName string) error {
	rr.mu.Lock()
	defer rr.mu.Unlock()

	// 如果已经在监听，先取消
	if cancel, exists := rr.watchers[serviceName]; exists {
		cancel()
	}

	// 创建新的监听上下文
	watchCtx, cancel := context.WithCancel(ctx)
	rr.watchers[serviceName] = cancel

	// 启动监听
	return rr.registry.Watch(watchCtx, serviceName, func(services []*ServiceInfo) {
		// 更新路由表
		endpoints := make(map[string][]*router.ServiceEndpoint)
		serviceEndpoints := make([]*router.ServiceEndpoint, 0, len(services))

		for _, service := range services {
			endpoint := &router.ServiceEndpoint{
				ServiceId: service.ID,
				Address:   service.Address,
				Port:      service.Port,
				Protocol:  rr.selectProtocol(service.Protocols),
				Metadata:  service.Metadata,
			}
			serviceEndpoints = append(serviceEndpoints, endpoint)
		}

		endpoints[serviceName] = serviceEndpoints
		_ = rr.router.UpdateRoutingTable(endpoints)
	})
}

// StopWatchService 停止监听服务变化
func (rr *RegistryRouter) StopWatchService(serviceName string) {
	rr.mu.Lock()
	defer rr.mu.Unlock()

	if cancel, exists := rr.watchers[serviceName]; exists {
		cancel()
		delete(rr.watchers, serviceName)
	}
}

// Close 关闭路由器
func (rr *RegistryRouter) Close() error {
	// 停止所有监听
	rr.mu.Lock()
	for _, cancel := range rr.watchers {
		cancel()
	}
	rr.watchers = make(map[string]context.CancelFunc)
	rr.mu.Unlock()

	// 关闭注册中心连接
	return rr.registry.Close()
}

// selectProtocol 选择协议
func (rr *RegistryRouter) selectProtocol(protocols []string) adapter.ProtocolType {
	// 优先选择 gRPC
	for _, p := range protocols {
		if p == string(adapter.ProtocolGRPC) {
			return adapter.ProtocolGRPC
		}
	}

	// 其次选择内部 RPC
	for _, p := range protocols {
		if p == string(adapter.ProtocolInternalRPC) {
			return adapter.ProtocolInternalRPC
		}
	}

	// 默认使用自定义二进制协议
	if len(protocols) > 0 {
		return adapter.ProtocolType(protocols[0])
	}

	return adapter.ProtocolGRPC
}
