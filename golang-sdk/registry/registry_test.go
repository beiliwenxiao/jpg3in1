package registry

import (
	"context"
	"fmt"
	"testing"
	"time"
)

// TestServiceRegistration 测试服务注册
func TestServiceRegistration(t *testing.T) {
	// 跳过测试如果没有 etcd 运行
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
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
	err = registry.Register(ctx, service)
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

// TestMultipleServiceInstances 测试多个服务实例
func TestMultipleServiceInstances(t *testing.T) {
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
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

		err = registry.Register(ctx, service)
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

// TestServiceWatch 测试服务监听
func TestServiceWatch(t *testing.T) {
	registry, err := NewEtcdRegistry(&EtcdRegistryConfig{
		Endpoints:        []string{"localhost:2379"},
		Namespace:        "/test-services",
		TTL:              10,
		HeartbeatInterval: 3 * time.Second,
		DialTimeout:      2 * time.Second,
	})

	if err != nil {
		t.Skipf("Skipping test: etcd not available: %v", err)
		return
	}
	defer registry.Close()

	ctx := context.Background()

	// 设置监听
	changeCount := 0
	err = registry.Watch(ctx, "watch-test-service", func(services []*ServiceInfo) {
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
	time.Sleep(1 * time.Second)

	if changeCount == 0 {
		t.Error("Expected service change notification, but got none")
	}

	// 清理
	err = registry.Deregister(ctx, service.ID)
	if err != nil {
		t.Errorf("Failed to deregister service: %v", err)
	}
}
