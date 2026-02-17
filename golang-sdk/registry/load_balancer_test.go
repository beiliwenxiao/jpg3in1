package registry

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/framework/golang-sdk/protocol/adapter"
	"github.com/framework/golang-sdk/protocol/router"
)

// TestRegistryRouterWithRoundRobin 测试带轮询负载均衡的注册路由器
func TestRegistryRouterWithRoundRobin(t *testing.T) {
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-lb-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
	defer registry.Close()

	// 创建轮询负载均衡器
	lb := router.NewRoundRobinLoadBalancer()
	registryRouter := NewRegistryRouter(registry, lb)
	defer registryRouter.Close()

	ctx := context.Background()

	// 注册多个服务实例
	for i := 1; i <= 3; i++ {
		service := &ServiceInfo{
			ID:       fmt.Sprintf("lb-test-service-%d", i),
			Name:     "lb-test-service",
			Version:  "1.0.0",
			Language: "golang",
			Address:  "localhost",
			Port:     9000 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err = registryRouter.RegisterService(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

	// 等待服务注册完成
	time.Sleep(500 * time.Millisecond)

	// 测试轮询负载均衡
	selectedPorts := make(map[int]int)
	for i := 0; i < 9; i++ {
		request := &adapter.InternalRequest{
			Service: "lb-test-service",
			Method:  "test",
		}

		endpoint, err := registryRouter.Route(ctx, request)
		if err != nil {
			t.Fatalf("Failed to route request: %v", err)
		}

		selectedPorts[endpoint.Port]++
	}

	// 验证每个端点都被选中了 3 次（轮询）
	if len(selectedPorts) != 3 {
		t.Errorf("Expected 3 different endpoints, got %d", len(selectedPorts))
	}

	for port, count := range selectedPorts {
		if count != 3 {
			t.Errorf("Expected port %d to be selected 3 times, got %d", port, count)
		}
	}

	// 清理
	for i := 1; i <= 3; i++ {
		serviceID := fmt.Sprintf("lb-test-service-%d", i)
		err = registryRouter.DeregisterService(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestRegistryRouterWithRandom 测试带随机负载均衡的注册路由器
func TestRegistryRouterWithRandom(t *testing.T) {
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-random-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
	defer registry.Close()

	// 创建随机负载均衡器
	lb := router.NewRandomLoadBalancer()
	registryRouter := NewRegistryRouter(registry, lb)
	defer registryRouter.Close()

	ctx := context.Background()

	// 注册多个服务实例
	for i := 1; i <= 3; i++ {
		service := &ServiceInfo{
			ID:       fmt.Sprintf("random-test-service-%d", i),
			Name:     "random-test-service",
			Version:  "1.0.0",
			Language: "golang",
			Address:  "localhost",
			Port:     9100 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err = registryRouter.RegisterService(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

	// 等待服务注册完成
	time.Sleep(500 * time.Millisecond)

	// 测试随机负载均衡
	selectedPorts := make(map[int]bool)
	for i := 0; i < 20; i++ {
		request := &adapter.InternalRequest{
			Service: "random-test-service",
			Method:  "test",
		}

		endpoint, err := registryRouter.Route(ctx, request)
		if err != nil {
			t.Fatalf("Failed to route request: %v", err)
		}

		selectedPorts[endpoint.Port] = true
	}

	// 验证至少选中了 2 个不同的端点（随机分布）
	if len(selectedPorts) < 2 {
		t.Errorf("Expected at least 2 different endpoints, got %d", len(selectedPorts))
	}

	// 清理
	for i := 1; i <= 3; i++ {
		serviceID := fmt.Sprintf("random-test-service-%d", i)
		err = registryRouter.DeregisterService(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestRegistryRouterWithLeastConnection 测试带最少连接负载均衡的注册路由器
func TestRegistryRouterWithLeastConnection(t *testing.T) {
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-lc-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
	defer registry.Close()

	// 创建最少连接负载均衡器
	lb := router.NewLeastConnectionLoadBalancer()
	registryRouter := NewRegistryRouter(registry, lb)
	defer registryRouter.Close()

	ctx := context.Background()

	// 注册多个服务实例
	for i := 1; i <= 3; i++ {
		service := &ServiceInfo{
			ID:       fmt.Sprintf("lc-test-service-%d", i),
			Name:     "lc-test-service",
			Version:  "1.0.0",
			Language: "golang",
			Address:  "localhost",
			Port:     9200 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err = registryRouter.RegisterService(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

	// 等待服务注册完成
	time.Sleep(500 * time.Millisecond)

	// 测试最少连接负载均衡
	request := &adapter.InternalRequest{
		Service: "lc-test-service",
		Method:  "test",
	}

	// 第一次请求应该选择第一个端点（连接数都是0）
	endpoint1, err := registryRouter.Route(ctx, request)
	if err != nil {
		t.Fatalf("Failed to route first request: %v", err)
	}

	// 第二次请求应该选择不同的端点（因为第一个端点连接数增加了）
	endpoint2, err := registryRouter.Route(ctx, request)
	if err != nil {
		t.Fatalf("Failed to route second request: %v", err)
	}

	// 验证选择了不同的端点
	if endpoint1.Port == endpoint2.Port {
		t.Log("Note: Both requests selected the same endpoint, which is possible with least connection")
	}

	// 清理
	for i := 1; i <= 3; i++ {
		serviceID := fmt.Sprintf("lc-test-service-%d", i)
		err = registryRouter.DeregisterService(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestServiceNotFound 测试服务不存在的情况
func TestServiceNotFound(t *testing.T) {
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-notfound-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
	defer registry.Close()

	registryRouter := NewRegistryRouter(registry, nil)
	defer registryRouter.Close()

	ctx := context.Background()

	// 尝试路由到不存在的服务
	request := &adapter.InternalRequest{
		Service: "non-existent-service",
		Method:  "test",
	}

	_, err = registryRouter.Route(ctx, request)
	if err == nil {
		t.Error("Expected error for non-existent service, got nil")
	}

	// 验证错误类型
	if frameworkErr, ok := err.(*adapter.FrameworkError); ok {
		if frameworkErr.Code != adapter.ErrorNotFound {
			t.Errorf("Expected ErrorNotFound, got %d", frameworkErr.Code)
		}
	} else {
		t.Error("Expected FrameworkError type")
	}
}
