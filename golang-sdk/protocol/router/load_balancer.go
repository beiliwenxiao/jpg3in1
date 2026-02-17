package router

import (
	"fmt"
	"math/rand"
	"sync"
	"time"

	"github.com/framework/golang-sdk/protocol/adapter"
)

// RoundRobinLoadBalancer 轮询负载均衡器
type RoundRobinLoadBalancer struct {
	mu      sync.Mutex
	counter int
}

// NewRoundRobinLoadBalancer 创建轮询负载均衡器
func NewRoundRobinLoadBalancer() *RoundRobinLoadBalancer {
	return &RoundRobinLoadBalancer{
		counter: 0,
	}
}

// Select 选择端点
func (lb *RoundRobinLoadBalancer) Select(endpoints []*ServiceEndpoint) (*ServiceEndpoint, error) {
	if len(endpoints) == 0 {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: "no endpoints available",
		}
	}

	lb.mu.Lock()
	defer lb.mu.Unlock()

	// 轮询选择
	index := lb.counter % len(endpoints)
	lb.counter++

	return endpoints[index], nil
}

// RandomLoadBalancer 随机负载均衡器
type RandomLoadBalancer struct {
	rand *rand.Rand
	mu   sync.Mutex
}

// NewRandomLoadBalancer 创建随机负载均衡器
func NewRandomLoadBalancer() *RandomLoadBalancer {
	return &RandomLoadBalancer{
		rand: rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

// Select 选择端点
func (lb *RandomLoadBalancer) Select(endpoints []*ServiceEndpoint) (*ServiceEndpoint, error) {
	if len(endpoints) == 0 {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: "no endpoints available",
		}
	}

	lb.mu.Lock()
	defer lb.mu.Unlock()

	// 随机选择
	index := lb.rand.Intn(len(endpoints))
	return endpoints[index], nil
}

// WeightedRoundRobinLoadBalancer 加权轮询负载均衡器
type WeightedRoundRobinLoadBalancer struct {
	mu              sync.Mutex
	currentWeights  map[string]int
}

// NewWeightedRoundRobinLoadBalancer 创建加权轮询负载均衡器
func NewWeightedRoundRobinLoadBalancer() *WeightedRoundRobinLoadBalancer {
	return &WeightedRoundRobinLoadBalancer{
		currentWeights: make(map[string]int),
	}
}

// Select 选择端点
func (lb *WeightedRoundRobinLoadBalancer) Select(endpoints []*ServiceEndpoint) (*ServiceEndpoint, error) {
	if len(endpoints) == 0 {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: "no endpoints available",
		}
	}

	lb.mu.Lock()
	defer lb.mu.Unlock()

	// 计算总权重
	totalWeight := 0
	for _, endpoint := range endpoints {
		weight := lb.getWeight(endpoint)
		totalWeight += weight
	}

	if totalWeight == 0 {
		// 如果所有权重都是0，使用简单轮询
		return endpoints[0], nil
	}

	// 更新当前权重并选择最大的
	var selected *ServiceEndpoint
	maxWeight := -1

	for _, endpoint := range endpoints {
		weight := lb.getWeight(endpoint)
		currentWeight := lb.currentWeights[endpoint.ServiceId]
		currentWeight += weight
		lb.currentWeights[endpoint.ServiceId] = currentWeight

		if currentWeight > maxWeight {
			maxWeight = currentWeight
			selected = endpoint
		}
	}

	// 减少选中端点的当前权重
	if selected != nil {
		lb.currentWeights[selected.ServiceId] -= totalWeight
	}

	return selected, nil
}

// getWeight 获取端点权重
func (lb *WeightedRoundRobinLoadBalancer) getWeight(endpoint *ServiceEndpoint) int {
	if endpoint.Metadata == nil {
		return 1
	}

	weightStr, exists := endpoint.Metadata["weight"]
	if !exists {
		return 1
	}

	var weight int
	fmt.Sscanf(weightStr, "%d", &weight)
	if weight <= 0 {
		return 1
	}

	return weight
}

// LeastConnectionLoadBalancer 最少连接负载均衡器
type LeastConnectionLoadBalancer struct {
	mu          sync.Mutex
	connections map[string]int // 端点ID -> 连接数
}

// NewLeastConnectionLoadBalancer 创建最少连接负载均衡器
func NewLeastConnectionLoadBalancer() *LeastConnectionLoadBalancer {
	return &LeastConnectionLoadBalancer{
		connections: make(map[string]int),
	}
}

// Select 选择端点
func (lb *LeastConnectionLoadBalancer) Select(endpoints []*ServiceEndpoint) (*ServiceEndpoint, error) {
	if len(endpoints) == 0 {
		return nil, &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: "no endpoints available",
		}
	}

	lb.mu.Lock()
	defer lb.mu.Unlock()

	// 选择连接数最少的端点
	var selected *ServiceEndpoint
	minConnections := -1

	for _, endpoint := range endpoints {
		connections := lb.connections[endpoint.ServiceId]
		if minConnections == -1 || connections < minConnections {
			minConnections = connections
			selected = endpoint
		}
	}

	// 增加连接计数
	if selected != nil {
		lb.connections[selected.ServiceId]++
	}

	return selected, nil
}

// ReleaseConnection 释放连接
func (lb *LeastConnectionLoadBalancer) ReleaseConnection(endpointId string) {
	lb.mu.Lock()
	defer lb.mu.Unlock()

	if count, exists := lb.connections[endpointId]; exists && count > 0 {
		lb.connections[endpointId]--
	}
}
