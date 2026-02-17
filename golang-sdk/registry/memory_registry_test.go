package registry

import (
	"context"
	"fmt"
	"testing"
	"time"
)

// TestMemoryRegistryServiceRegistration 测试内存注册中心的服务注册
func TestMemoryRegistryServiceRegistration(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	ctx := context.Background()

	// 创建测试服务
	service := &ServiceInfo{
		ID:       "test-service-1",
		Name:     "test-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8080,
		Protocols: []string{"gRPC", "HTTP"},
		Metadata: map[string]string{
			"region": "us-west",
		},
		RegisteredAt: time.Now(),
	}

	// 注册服务
	err := registry.Register(ctx, service)
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// 查询服务
	services, err := registry.Discover(ctx, "test-service")
	if err != nil {
		t.Fatalf("Failed to discover service: %v", err)
	}

	if len(services) != 1 {
		t.Fatalf("Expected 1 service, got %d", len(services))
	}

	if services[0].ID != service.ID {
		t.Errorf("Expected service ID %s, got %s", service.ID, services[0].ID)
	}

	// 健康检查
	status, err := registry.HealthCheck(ctx, service.ID)
	if err != nil {
		t.Fatalf("Failed to check health: %v", err)
	}

	if status != HealthStatusHealthy {
		t.Errorf("Expected healthy status, got %s", status)
	}

	// 注销服务
	err = registry.Deregister(ctx, service.ID)
	if err != nil {
		t.Fatalf("Failed to deregister service: %v", err)
	}

	// 验证服务已注销
	services, err = registry.Discover(ctx, "test-service")
	if err != nil {
		t.Fatalf("Failed to discover service after deregister: %v", err)
	}

	if len(services) != 0 {
		t.Errorf("Expected 0 services after deregister, got %d", len(services))
	}
}

// TestMemoryRegistryMultipleInstances 测试多个服务实例
func TestMemoryRegistryMultipleInstances(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	ctx := context.Background()

	// 注册多个服务实例
	for i := 1; i <= 3; i++ {
		service := &ServiceInfo{
			ID:       fmt.Sprintf("test-service-%d", i),
			Name:     "test-service",
			Version:  "1.0.0",
			Language: "golang",
			Address:  "localhost",
			Port:     8080 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err := registry.Register(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %d: %v", i, err)
		}
	}

	// 查询服务
	services, err := registry.Discover(ctx, "test-service")
	if err != nil {
		t.Fatalf("Failed to discover services: %v", err)
	}

	if len(services) != 3 {
		t.Fatalf("Expected 3 services, got %d", len(services))
	}

	// 清理
	for i := 1; i <= 3; i++ {
		serviceID := fmt.Sprintf("test-service-%d", i)
		err = registry.Deregister(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestMemoryRegistryServiceWatch 测试服务监听
func TestMemoryRegistryServiceWatch(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	ctx := context.Background()

	// 设置监听
	changeCount := 0
	err := registry.Watch(ctx, "watch-test-service", func(services []*ServiceInfo) {
		changeCount++
	})
	if err != nil {
		t.Fatalf("Failed to watch service: %v", err)
	}

	// 注册服务
	service := &ServiceInfo{
		ID:       "watch-test-1",
		Name:     "watch-test-service",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     9090,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err = registry.Register(ctx, service)
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// 等待监听触发
	time.Sleep(100 * time.Millisecond)

	if changeCount == 0 {
		t.Error("Expected service change notification, but got none")
	}

	// 清理
	err = registry.Deregister(ctx, service.ID)
	if err != nil {
		t.Errorf("Failed to deregister service: %v", err)
	}
}

// TestMemoryRegistryTTL 测试服务 TTL 过期
func TestMemoryRegistryTTL(t *testing.T) {
	// 使用较短的 TTL 和清理间隔进行测试
	config := &MemoryRegistryConfig{
		TTL:               1 * time.Second,
		HeartbeatInterval: 500 * time.Millisecond,
		CleanupInterval:   500 * time.Millisecond,
	}

	registry := NewMemoryRegistry(config)
	defer registry.Close()

	ctx := context.Background()

	// 注册服务
	service := &ServiceInfo{
		ID:       "ttl-test-service",
		Name:     "ttl-test",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8080,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err := registry.Register(ctx, service)
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// 立即查询，应该能找到服务
	services, err := registry.Discover(ctx, "ttl-test")
	if err != nil {
		t.Fatalf("Failed to discover service: %v", err)
	}

	if len(services) != 1 {
		t.Fatalf("Expected 1 service, got %d", len(services))
	}

	// 等待服务过期（TTL + 清理间隔）
	time.Sleep(2 * time.Second)

	// 再次查询，服务应该已经过期
	services, err = registry.Discover(ctx, "ttl-test")
	if err != nil {
		t.Fatalf("Failed to discover service after TTL: %v", err)
	}

	if len(services) != 0 {
		t.Errorf("Expected 0 services after TTL expiration, got %d", len(services))
	}
}

// TestMemoryRegistryHeartbeat 测试心跳机制
func TestMemoryRegistryHeartbeat(t *testing.T) {
	// 使用较短的 TTL 进行测试
	config := &MemoryRegistryConfig{
		TTL:               2 * time.Second,
		HeartbeatInterval: 500 * time.Millisecond,
		CleanupInterval:   500 * time.Millisecond,
	}

	registry := NewMemoryRegistry(config)
	defer registry.Close()

	ctx := context.Background()

	// 注册服务
	service := &ServiceInfo{
		ID:       "heartbeat-test-service",
		Name:     "heartbeat-test",
		Version:  "1.0.0",
		Language: "golang",
		Address:  "localhost",
		Port:     8080,
		Protocols: []string{"gRPC"},
		RegisteredAt: time.Now(),
	}

	err := registry.Register(ctx, service)
	if err != nil {
		t.Fatalf("Failed to register service: %v", err)
	}

	// 持续发送心跳
	stopHeartbeat := make(chan bool)
	go func() {
		ticker := time.NewTicker(1 * time.Second)
		defer ticker.Stop()

		for {
			select {
			case <-stopHeartbeat:
				return
			case <-ticker.C:
				_ = registry.Heartbeat(ctx, service.ID)
			}
		}
	}()

	// 等待超过 TTL 的时间
	time.Sleep(3 * time.Second)

	// 服务应该仍然存在（因为有心跳）
	services, err := registry.Discover(ctx, "heartbeat-test")
	if err != nil {
		t.Fatalf("Failed to discover service: %v", err)
	}

	if len(services) != 1 {
		t.Errorf("Expected 1 service with heartbeat, got %d", len(services))
	}

	// 停止心跳
	close(stopHeartbeat)

	// 等待服务过期
	time.Sleep(3 * time.Second)

	// 服务应该已经过期
	services, err = registry.Discover(ctx, "heartbeat-test")
	if err != nil {
		t.Fatalf("Failed to discover service after stopping heartbeat: %v", err)
	}

	if len(services) != 0 {
		t.Errorf("Expected 0 services after stopping heartbeat, got %d", len(services))
	}
}

// TestMemoryRegistryVersionManagement 测试服务版本管理
func TestMemoryRegistryVersionManagement(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	ctx := context.Background()

	// 注册同一服务的不同版本
	versions := []string{"1.0.0", "1.1.0", "2.0.0"}
	for i, version := range versions {
		service := &ServiceInfo{
			ID:       fmt.Sprintf("versioned-service-%d", i+1),
			Name:     "versioned-service",
			Version:  version,
			Language: "golang",
			Address:  "localhost",
			Port:     8080 + i,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		}

		err := registry.Register(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service version %s: %v", version, err)
		}
	}

	// 查询服务，应该返回所有版本
	services, err := registry.Discover(ctx, "versioned-service")
	if err != nil {
		t.Fatalf("Failed to discover services: %v", err)
	}

	if len(services) != 3 {
		t.Fatalf("Expected 3 service versions, got %d", len(services))
	}

	// 验证版本信息
	versionMap := make(map[string]bool)
	for _, service := range services {
		versionMap[service.Version] = true
	}

	for _, version := range versions {
		if !versionMap[version] {
			t.Errorf("Expected version %s not found", version)
		}
	}

	// 清理
	for i := range versions {
		serviceID := fmt.Sprintf("versioned-service-%d", i+1)
		err = registry.Deregister(ctx, serviceID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", serviceID, err)
		}
	}
}

// TestMemoryRegistryGetAllServices 测试获取所有服务
func TestMemoryRegistryGetAllServices(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	ctx := context.Background()

	// 注册多个不同的服务
	services := []*ServiceInfo{
		{
			ID:       "service-a-1",
			Name:     "service-a",
			Version:  "1.0.0",
			Language: "golang",
			Address:  "localhost",
			Port:     8080,
			Protocols: []string{"gRPC"},
			RegisteredAt: time.Now(),
		},
		{
			ID:       "service-b-1",
			Name:     "service-b",
			Version:  "1.0.0",
			Language: "java",
			Address:  "localhost",
			Port:     8081,
			Protocols: []string{"HTTP"},
			RegisteredAt: time.Now(),
		},
	}

	for _, service := range services {
		err := registry.Register(ctx, service)
		if err != nil {
			t.Fatalf("Failed to register service %s: %v", service.ID, err)
		}
	}

	// 获取所有服务
	allServices := registry.GetAllServices()

	if len(allServices) != 2 {
		t.Errorf("Expected 2 service types, got %d", len(allServices))
	}

	if len(allServices["service-a"]) != 1 {
		t.Errorf("Expected 1 instance of service-a, got %d", len(allServices["service-a"]))
	}

	if len(allServices["service-b"]) != 1 {
		t.Errorf("Expected 1 instance of service-b, got %d", len(allServices["service-b"]))
	}

	// 清理
	for _, service := range services {
		err := registry.Deregister(ctx, service.ID)
		if err != nil {
			t.Errorf("Failed to deregister service %s: %v", service.ID, err)
		}
	}
}

// TestMemoryRegistryErrorHandling 测试错误处理
func TestMemoryRegistryErrorHandling(t *testing.T) {
	registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
	defer registry.Close()

	ctx := context.Background()

	// 测试注册 nil 服务
	err := registry.Register(ctx, nil)
	if err == nil {
		t.Error("Expected error when registering nil service")
	}

	// 测试注册空 ID 的服务
	err = registry.Register(ctx, &ServiceInfo{Name: "test"})
	if err == nil {
		t.Error("Expected error when registering service with empty ID")
	}

	// 测试注册空名称的服务
	err = registry.Register(ctx, &ServiceInfo{ID: "test-1"})
	if err == nil {
		t.Error("Expected error when registering service with empty name")
	}

	// 测试注销不存在的服务
	err = registry.Deregister(ctx, "non-existent-service")
	if err == nil {
		t.Error("Expected error when deregistering non-existent service")
	}

	// 测试查询空名称的服务
	_, err = registry.Discover(ctx, "")
	if err == nil {
		t.Error("Expected error when discovering service with empty name")
	}

	// 测试健康检查不存在的服务
	_, err = registry.HealthCheck(ctx, "non-existent-service")
	if err == nil {
		t.Error("Expected error when checking health of non-existent service")
	}

	// 测试监听空名称的服务
	err = registry.Watch(ctx, "", func([]*ServiceInfo) {})
	if err == nil {
		t.Error("Expected error when watching service with empty name")
	}

	// 测试监听 nil 回调
	err = registry.Watch(ctx, "test", nil)
	if err == nil {
		t.Error("Expected error when watching with nil callback")
	}
}
