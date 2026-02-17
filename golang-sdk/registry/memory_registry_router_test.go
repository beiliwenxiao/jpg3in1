package registry

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/framework/golang-sdk/protocol/adapter"
	"github.com/framework/golang-sdk/protocol/router"
)

// TestMemoryRegistryRouterWithRoundRobin 测试内存注册中心与轮询负载均衡
func TestMemoryRegistryRouterWithRoundRobin(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	// 创建新的轮询负载均衡器（确保计数器从0开始）
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

		err := registryRouter.RegisterService(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

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
			t.Errorf("Expected port %d to be selected 3 times, got %d times. All selections: %v", port, count, selectedPorts)
		}
	}

	// 清理
	for i := 1; i <= 3; i++ {
		serviceID := fmt.Sprintf("lb-test-service-%d", i)
		err := registryRouter.DeregisterService(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestMemoryRegistryRouterWithRandom 测试内存注册中心与随机负载均衡
func TestMemoryRegistryRouterWithRandom(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
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

		err := registryRouter.RegisterService(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

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
		err := registryRouter.DeregisterService(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestMemoryRegistryRouterWithLeastConnection 测试内存注册中心与最少连接负载均衡
func TestMemoryRegistryRouterWithLeastConnection(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
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

		err := registryRouter.RegisterService(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

	// 测试最少连接负载均衡
	request := &adapter.InternalRequest{
		Service: "lc-test-service",
		Method:  "test",
	}

	// 第一次请求
	endpoint1, err := registryRouter.Route(ctx, request)
	if err != nil {
		t.Fatalf("Failed to route first request: %v", err)
	}

	// 第二次请求
	endpoint2, err := registryRouter.Route(ctx, request)
	if err != nil {
		t.Fatalf("Failed to route second request: %v", err)
	}

	// 验证选择了端点
	if endpoint1 == nil || endpoint2 == nil {
		t.Error("Expected valid endpoints")
	}

	// 清理
	for i := 1; i <= 3; i++ {
		serviceID := fmt.Sprintf("lc-test-service-%d", i)
		err := registryRouter.DeregisterService(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestMemoryRegistryRouterServiceNotFound 测试服务不存在的情况
func TestMemoryRegistryRouterServiceNotFound(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	registryRouter := NewRegistryRouter(registry, nil)
	defer registryRouter.Close()

	ctx := context.Background()

	// 尝试路由到不存在的服务
	request := &adapter.InternalRequest{
		Service: "non-existent-service",
		Method:  "test",
	}

	_, err := registryRouter.Route(ctx, request)
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

// TestMemoryRegistryRouterServiceWatch 测试服务监听和动态更新
func TestMemoryRegistryRouterServiceWatch(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	lb := router.NewRoundRobinLoadBalancer()
	registryRouter := NewRegistryRouter(registry, lb)
	defer registryRouter.Close()

	ctx := context.Background()

	// 启动服务监听
	err := registryRouter.WatchService(ctx, "watch-service")
	if err != nil {
		t.Fatalf("Failed to watch service: %v", err)
	}

	// 注册第一个服务实例
	service1 := &ServiceInfo{
		ID:       "watch-service-1",
		Name:     "watch-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     9300,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err = registryRouter.RegisterService(ctx, service1)
	if err != nil {
		t.Fatalf("Failed to register service 1: %v", err)
	}

	// 等待监听触发
	time.Sleep(100 * time.Millisecond)

	// 路由请求，应该成功
	request := &adapter.InternalRequest{
		Service: "watch-service",
		Method:  "test",
	}

	endpoint, err := registryRouter.Route(ctx, request)
	if err != nil {
		t.Fatalf("Failed to route request: %v", err)
	}

	if endpoint.Port != 9300 {
		t.Errorf("Expected port 9300, got %d", endpoint.Port)
	}

	// 注册第二个服务实例
	service2 := &ServiceInfo{
		ID:       "watch-service-2",
		Name:     "watch-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     9301,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err = registryRouter.RegisterService(ctx, service2)
	if err != nil {
		t.Fatalf("Failed to register service 2: %v", err)
	}

	// 等待监听触发
	time.Sleep(100 * time.Millisecond)

	// 路由多个请求，应该在两个实例之间轮询
	selectedPorts := make(map[int]bool)
	for i := 0; i < 4; i++ {
		endpoint, err := registryRouter.Route(ctx, request)
		if err != nil {
			t.Fatalf("Failed to route request %d: %v", i, err)
		}
		selectedPorts[endpoint.Port] = true
	}

	// 应该选中了两个不同的端口
	if len(selectedPorts) != 2 {
		t.Errorf("Expected 2 different ports, got %d", len(selectedPorts))
	}

	// 停止监听
	registryRouter.StopWatchService("watch-service")

	// 清理
	err = registryRouter.DeregisterService(ctx, service1.ID)
	if err != nil {
		t.Errorf("Failed to deregister service 1: %v", err)
	}

	err = registryRouter.DeregisterService(ctx, service2.ID)
	if err != nil {
		t.Errorf("Failed to deregister service 2: %v", err)
	}
}

// TestMemoryRegistryRouterTTLExpiration 测试服务 TTL 过期后的路由
func TestMemoryRegistryRouterTTLExpiration(t *testing.T) {
	// 使用较短的 TTL 进行测试
	config := &MemoryRegistryConfig{
		TTL:               1 * time.Second,
		HeartbeatInterval: 500 * time.Millisecond,
		CleanupInterval:   500 * time.Millisecond,
	}

	registry := NewMemoryRegistry(config)
	defer registry.Close()

	registryRouter := NewRegistryRouter(registry, nil)
	defer registryRouter.Close()

	ctx := context.Background()

	// 注册服务
	service := &ServiceInfo{
		ID:       "ttl-service-1",
		Name:     "ttl-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     9400,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err := registryRouter.RegisterService(ctx, service)
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// 立即路由，应该成功
	request := &adapter.InternalRequest{
		Service: "ttl-service",
		Method:  "test",
	}

	_, err = registryRouter.Route(ctx, request)
	if err != nil {
		t.Fatalf("Failed to route request before TTL: %v", err)
	}

	// 等待服务过期
	time.Sleep(2 * time.Second)

	// 再次路由，应该失败（服务已过期）
	_, err = registryRouter.Route(ctx, request)
	if err == nil {
		t.Error("Expected error after TTL expiration, got nil")
	}
}
