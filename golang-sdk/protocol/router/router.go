package router

import (
	"context"
	"fmt"
	"sync"

	"github.com/framework/golang-sdk/protocol/adapter"
)

// ServiceEndpoint 服务端点
type ServiceEndpoint struct {
	ServiceId string            // 服务 ID
	Address   string            // 地址
	Port      int               // 端口
	Protocol  adapter.ProtocolType // 协议类型
	Metadata  map[string]string // 元数据
}

// RoutingRule 路由规则
type RoutingRule struct {
	Name     string                                      // 规则名称
	Priority int                                         // 优先级（数字越大优先级越高）
	Matcher  func(*adapter.InternalRequest) bool        // 匹配函数
	Target   func(*adapter.InternalRequest) string      // 目标服务函数
}

// MessageRouter 消息路由器接口
type MessageRouter interface {
	// Route 路由消息到目标服务
	Route(ctx context.Context, request *adapter.InternalRequest) (*ServiceEndpoint, error)

	// RegisterRule 注册路由规则
	RegisterRule(rule *RoutingRule) error

	// UpdateRoutingTable 更新路由表
	UpdateRoutingTable(services map[string][]*ServiceEndpoint) error

	// GetServiceEndpoints 获取服务的所有端点
	GetServiceEndpoints(serviceName string) ([]*ServiceEndpoint, error)
}

// DefaultMessageRouter 默认消息路由器实现
type DefaultMessageRouter struct {
	mu             sync.RWMutex
	routingTable   map[string][]*ServiceEndpoint // 服务名 -> 端点列表
	rules          []*RoutingRule                 // 路由规则列表（按优先级排序）
	loadBalancer   LoadBalancer                   // 负载均衡器
}

// LoadBalancer 负载均衡器接口
type LoadBalancer interface {
	// Select 从端点列表中选择一个端点
	Select(endpoints []*ServiceEndpoint) (*ServiceEndpoint, error)
}

// NewDefaultMessageRouter 创建默认消息路由器
func NewDefaultMessageRouter(loadBalancer LoadBalancer) *DefaultMessageRouter {
	if loadBalancer == nil {
		loadBalancer = NewRoundRobinLoadBalancer()
	}

	return &DefaultMessageRouter{
		routingTable: make(map[string][]*ServiceEndpoint),
		rules:        make([]*RoutingRule, 0),
		loadBalancer: loadBalancer,
	}
}

// Route 路由消息到目标服务
func (r *DefaultMessageRouter) Route(ctx context.Context, request *adapter.InternalRequest) (*ServiceEndpoint, error) {
	if request == nil {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "request is nil",
		}
	}

	// 应用路由规则
	targetService := r.applyRoutingRules(request)
	if targetService == "" {
		targetService = request.Service
	}

	// 查找服务端点
	endpoints, err := r.GetServiceEndpoints(targetService)
	if err != nil {
		return nil, err
	}

	if len(endpoints) == 0 {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: fmt.Sprintf("no available endpoints for service: %s", targetService),
		}
	}

	// 使用负载均衡器选择端点
	endpoint, err := r.loadBalancer.Select(endpoints)
	if err != nil {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorRouting,
			Message: fmt.Sprintf("failed to select endpoint for service %s", targetService),
			Cause:   err,
		}
	}

	return endpoint, nil
}

// RegisterRule 注册路由规则
func (r *DefaultMessageRouter) RegisterRule(rule *RoutingRule) error {
	if rule == nil {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "rule is nil",
		}
	}

	if rule.Matcher == nil {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "rule matcher is nil",
		}
	}

	if rule.Target == nil {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "rule target is nil",
		}
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	// 插入规则并按优先级排序
	r.rules = append(r.rules, rule)
	r.sortRules()

	return nil
}

// UpdateRoutingTable 更新路由表
func (r *DefaultMessageRouter) UpdateRoutingTable(services map[string][]*ServiceEndpoint) error {
	if services == nil {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "services map is nil",
		}
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	// 完全替换路由表
	r.routingTable = make(map[string][]*ServiceEndpoint)
	for serviceName, endpoints := range services {
		r.routingTable[serviceName] = endpoints
	}

	return nil
}

// GetServiceEndpoints 获取服务的所有端点
func (r *DefaultMessageRouter) GetServiceEndpoints(serviceName string) ([]*ServiceEndpoint, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	endpoints, exists := r.routingTable[serviceName]
	if !exists {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: fmt.Sprintf("service not found: %s", serviceName),
		}
	}

	// 返回副本以避免并发修改
	result := make([]*ServiceEndpoint, len(endpoints))
	copy(result, endpoints)

	return result, nil
}

// applyRoutingRules 应用路由规则
func (r *DefaultMessageRouter) applyRoutingRules(request *adapter.InternalRequest) string {
	r.mu.RLock()
	defer r.mu.RUnlock()

	// 按优先级顺序应用规则
	for _, rule := range r.rules {
		if rule.Matcher(request) {
			return rule.Target(request)
		}
	}

	// 没有匹配的规则，返回空字符串
	return ""
}

// sortRules 按优先级排序规则（优先级高的在前）
func (r *DefaultMessageRouter) sortRules() {
	// 简单的冒泡排序
	n := len(r.rules)
	for i := 0; i < n-1; i++ {
		for j := 0; j < n-i-1; j++ {
			if r.rules[j].Priority < r.rules[j+1].Priority {
				r.rules[j], r.rules[j+1] = r.rules[j+1], r.rules[j]
			}
		}
	}
}

// AddServiceEndpoint 添加服务端点
func (r *DefaultMessageRouter) AddServiceEndpoint(serviceName string, endpoint *ServiceEndpoint) error {
	if serviceName == "" {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "service name is empty",
		}
	}

	if endpoint == nil {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorBadRequest,
			Message: "endpoint is nil",
		}
	}

	r.mu.Lock()
	defer r.mu.Unlock()

	if r.routingTable[serviceName] == nil {
		r.routingTable[serviceName] = make([]*ServiceEndpoint, 0)
	}

	r.routingTable[serviceName] = append(r.routingTable[serviceName], endpoint)

	return nil
}

// RemoveServiceEndpoint 移除服务端点
func (r *DefaultMessageRouter) RemoveServiceEndpoint(serviceName string, endpointId string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	endpoints, exists := r.routingTable[serviceName]
	if !exists {
		return &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: fmt.Sprintf("service not found: %s", serviceName),
		}
	}

	// 查找并移除端点
	for i, endpoint := range endpoints {
		if endpoint.ServiceId == endpointId {
			r.routingTable[serviceName] = append(endpoints[:i], endpoints[i+1:]...)
			return nil
		}
	}

	return &adapter.FrameworkError{
		Code:    adapter.ErrorNotFound,
		Message: fmt.Sprintf("endpoint not found: %s", endpointId),
	}
}
