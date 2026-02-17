package registry_test

import (
	"context"
	"fmt"
	"time"

	"github.com/framework/golang-sdk/protocol/adapter"
	"github.com/framework/golang-sdk/protocol/router"
	"github.com/framework/golang-sdk/registry"
)

// Example_memoryRegistry 演示如何使用内存注册中心
func Example_memoryRegistry() {
	// 创建内存注册中心
	reg := registry.NewMemoryRegistry(registry.DefaultMemoryRegistryConfig())
	defer reg.Close()

	ctx := context.Background()

	// 注册服务
	service := &registry.ServiceInfo{
		ID:       "user-service-1",
		Name:     "user-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8080,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err := reg.Register(ctx, service)
	if err != nil {
		panic(err)
	}

	// 查询服务
	services, err := reg.Discover(ctx, "user-service")
	if err != nil {
		panic(err)
	}

	fmt.Printf("Found %d service instances\n", len(services))

	// 健康检查
	status, err := reg.HealthCheck(ctx, service.ID)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Service status: %s\n", status)

	// 注销服务
	err = reg.Deregister(ctx, service.ID)
	if err != nil {
		panic(err)
	}

	// Output:
	// Found 1 service instances
	// Service status: healthy
}

// Example_memoryRegistryWithHeartbeat 演示如何使用心跳机制
func Example_memoryRegistryWithHeartbeat() {
	// 使用较短的 TTL 进行演示
	config := &registry.MemoryRegistryConfig{
		TTL:               5 * time.Second,
		HeartbeatInterval: 2 * time.Second,
		CleanupInterval:   1 * time.Second,
	}

	reg := registry.NewMemoryRegistry(config)
	defer reg.Close()

	ctx := context.Background()

	// 注册服务
	service := &registry.ServiceInfo{
		ID:       "order-service-1",
		Name:     "order-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8081,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err := reg.Register(ctx, service)
	if err != nil {
		panic(err)
	}

	// 启动心跳 goroutine
	stopHeartbeat := make(chan bool)
	go func() {
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()

		for {
			select {
			case <-stopHeartbeat:
				return
			case <-ticker.C:
				err := reg.Heartbeat(ctx, service.ID)
				if err != nil {
					fmt.Printf("Heartbeat failed: %v\n", err)
				}
			}
		}
	}()

	// 等待一段时间
	time.Sleep(3 * time.Second)

	// 检查服务仍然健康
	status, err := reg.HealthCheck(ctx, service.ID)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Service status with heartbeat: %s\n", status)

	// 停止心跳
	close(stopHeartbeat)

	// 清理
	reg.Deregister(ctx, service.ID)

	// Output:
	// Service status with heartbeat: healthy
}

// Example_registryRouterWithLoadBalancer 演示如何使用注册路由器和负载均衡
func Example_registryRouterWithLoadBalancer() {
	// 创建内存注册中心
	reg := registry.NewMemoryRegistry(registry.DefaultMemoryRegistryConfig())
	defer reg.Close()

	// 创建轮询负载均衡器
	lb := router.NewRoundRobinLoadBalancer()

	// 创建注册路由器
	registryRouter := registry.NewRegistryRouter(reg, lb)
	defer registryRouter.Close()

	ctx := context.Background()

	// 注册多个服务实例
	for i := 1; i <= 3; i++ {
		service := &registry.ServiceInfo{
			ID:       fmt.Sprintf("payment-service-%d", i),
			Name:     "payment-service",
			Version:  "1.0.0",
			Language: "golang",
			Address:  "localhost",
			Port:     8090 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err := registryRouter.RegisterService(ctx, service)
		if err != nil {
			panic(err)
		}
	}

	// 路由请求到服务（轮询选择）
	for i := 0; i < 3; i++ {
		request := &adapter.InternalRequest{
			Service: "payment-service",
			Method:  "ProcessPayment",
		}

		endpoint, err := registryRouter.Route(ctx, request)
		if err != nil {
			panic(err)
		}

		fmt.Printf("Request %d routed to port: %d\n", i+1, endpoint.Port)
	}

	// Output:
	// Request 1 routed to port: 8091
	// Request 2 routed to port: 8092
	// Request 3 routed to port: 8093
}

// Example_serviceWatch 演示如何监听服务变化
func Example_serviceWatch() {
	reg := registry.NewMemoryRegistry(registry.DefaultMemoryRegistryConfig())
	defer reg.Close()

	ctx := context.Background()

	// 设置监听
	changeCount := 0
	err := reg.Watch(ctx, "notification-service", func(services []*registry.ServiceInfo) {
		changeCount++
		fmt.Printf("Service changed, now %d instances\n", len(services))
	})
	if err != nil {
		panic(err)
	}

	// 注册第一个服务实例
	service1 := &registry.ServiceInfo{
		ID:       "notification-service-1",
		Name:     "notification-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8100,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err = reg.Register(ctx, service1)
	if err != nil {
		panic(err)
	}

	// 等待监听触发
	time.Sleep(100 * time.Millisecond)

	// 注册第二个服务实例
	service2 := &registry.ServiceInfo{
		ID:       "notification-service-2",
		Name:     "notification-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8101,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err = reg.Register(ctx, service2)
	if err != nil {
		panic(err)
	}

	// 等待监听触发
	time.Sleep(100 * time.Millisecond)

	// 注销第一个服务
	err = reg.Deregister(ctx, service1.ID)
	if err != nil {
		panic(err)
	}

	// 等待监听触发
	time.Sleep(100 * time.Millisecond)

	fmt.Printf("Total changes detected: %d\n", changeCount)

	// Output:
	// Service changed, now 1 instances
	// Service changed, now 2 instances
	// Service changed, now 1 instances
	// Total changes detected: 3
}

// Example_versionManagement 演示如何管理服务版本
func Example_versionManagement() {
	reg := registry.NewMemoryRegistry(registry.DefaultMemoryRegistryConfig())
	defer reg.Close()

	ctx := context.Background()

	// 注册不同版本的服务
	versions := []string{"1.0.0", "1.1.0", "2.0.0"}
	for i, version := range versions {
		service := &registry.ServiceInfo{
			ID:       fmt.Sprintf("api-service-v%d", i+1),
			Name:     "api-service",
			Version:  version,
			Language: "golang",
			Address:  "localhost",
			Port:     9000 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err := reg.Register(ctx, service)
		if err != nil {
			panic(err)
		}
	}

	// 查询所有版本
	services, err := reg.Discover(ctx, "api-service")
	if err != nil {
		panic(err)
	}

	fmt.Printf("Found %d versions of api-service:\n", len(services))
	for _, svc := range services {
		fmt.Printf("- Version %s on port %d\n", svc.Version, svc.Port)
	}

	// Output:
	// Found 3 versions of api-service:
	// - Version 1.0.0 on port 9000
	// - Version 1.1.0 on port 9001
	// - Version 2.0.0 on port 9002
}
