package registry

import (
	"context"
	"testing"
	"time"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// TestServiceRegistrationDiscoverability 属性测试：服务注册可发现性
// Feature: multi-language-communication-framework, Property 13: 服务注册可发现性
// **Validates: Requirements 5.1**
//
// 对于任意服务，启动并注册后，应该能够通过服务注册中心查询到该服务的元数据
func TestServiceRegistrationDiscoverability(t *testing.T) {
	properties := gopter.NewProperties(nil)

	properties.Property("Registered service can be discovered", prop.ForAll(
		func(serviceName string, serviceID string, version string, address string, port uint16) bool {
			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			// 构造服务信息
			service := &ServiceInfo{
				ID:       serviceID,
				Name:     serviceName,
				Version:  version,
				Language: "golang",
				Address:  address,
				Port:     int(port),
				Protocols: []string{"gRPC", "JSON-RPC"},
				Metadata: map[string]string{
					"env": "test",
				},
				RegisteredAt: time.Now(),
			}

			// 注册服务
			ctx := context.Background()
			err := registry.Register(ctx, service)
			if err != nil {
				return false
			}

			// 查询服务
			services, err := registry.Discover(ctx, serviceName)
			if err != nil {
				return false
			}

			// 验证服务可以被发现
			if len(services) == 0 {
				return false
			}

			// 验证服务信息正确
			found := false
			for _, s := range services {
				if s.ID == serviceID && s.Name == serviceName {
					found = true
					// 验证元数据完整性
					if s.Version != version {
						return false
					}
					if s.Address != address {
						return false
					}
					if s.Port != int(port) {
						return false
					}
					if s.Language != "golang" {
						return false
					}
					break
				}
			}

			return found
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.RegexMatch("v[0-9]+\\.[0-9]+\\.[0-9]+"),
		gen.RegexMatch("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"),
		gen.UInt16Range(1024, 65535),
	))

	properties.Property("Multiple services with same name can be discovered", prop.ForAll(
		func(serviceName string, id1 string, id2 string, port1 uint16, port2 uint16) bool {
			// 确保 ID 不同
			if id1 == id2 {
				return true
			}

			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 注册第一个服务实例
			service1 := &ServiceInfo{
				ID:       id1,
				Name:     serviceName,
				Version:  "v1.0.0",
				Language: "golang",
				Address:  "127.0.0.1",
				Port:     int(port1),
				Protocols: []string{"gRPC"},
				RegisteredAt: time.Now(),
			}
			err := registry.Register(ctx, service1)
			if err != nil {
				return false
			}

			// 注册第二个服务实例
			service2 := &ServiceInfo{
				ID:       id2,
				Name:     serviceName,
				Version:  "v1.0.0",
				Language: "golang",
				Address:  "127.0.0.2",
				Port:     int(port2),
				Protocols: []string{"gRPC"},
				RegisteredAt: time.Now(),
			}
			err = registry.Register(ctx, service2)
			if err != nil {
				return false
			}

			// 查询服务
			services, err := registry.Discover(ctx, serviceName)
			if err != nil {
				return false
			}

			// 验证两个实例都可以被发现
			if len(services) != 2 {
				return false
			}

			// 验证两个实例的 ID 不同
			foundIds := make(map[string]bool)
			for _, s := range services {
				foundIds[s.ID] = true
			}

			return foundIds[id1] && foundIds[id2]
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.Identifier(),
		gen.UInt16Range(1024, 32767),
		gen.UInt16Range(32768, 65535),
	))

	properties.Property("Deregistered service cannot be discovered", prop.ForAll(
		func(serviceName string, serviceID string) bool {
			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 注册服务
			service := &ServiceInfo{
				ID:       serviceID,
				Name:     serviceName,
				Version:  "v1.0.0",
				Language: "golang",
				Address:  "127.0.0.1",
				Port:     8080,
				Protocols: []string{"gRPC"},
				RegisteredAt: time.Now(),
			}
			err := registry.Register(ctx, service)
			if err != nil {
				return false
			}

			// 验证服务可以被发现
			services, err := registry.Discover(ctx, serviceName)
			if err != nil || len(services) == 0 {
				return false
			}

			// 注销服务
			err = registry.Deregister(ctx, serviceID)
			if err != nil {
				return false
			}

			// 验证服务不能被发现
			services, err = registry.Discover(ctx, serviceName)
			if err != nil {
				return false
			}

			// 服务列表应该为空
			return len(services) == 0
		},
		gen.Identifier(),
		gen.Identifier(),
	))

	properties.Property("Service metadata is preserved", prop.ForAll(
		func(serviceName string, serviceID string, language string, protocols []string) bool {
			// 跳过空协议列表
			if len(protocols) == 0 {
				return true
			}

			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 构造服务信息
			metadata := map[string]string{
				"region": "us-west",
				"zone":   "zone-a",
			}

			service := &ServiceInfo{
				ID:       serviceID,
				Name:     serviceName,
				Version:  "v1.0.0",
				Language: language,
				Address:  "127.0.0.1",
				Port:     8080,
				Protocols: protocols,
				Metadata: metadata,
				RegisteredAt: time.Now(),
			}

			// 注册服务
			err := registry.Register(ctx, service)
			if err != nil {
				return false
			}

			// 查询服务
			services, err := registry.Discover(ctx, serviceName)
			if err != nil || len(services) == 0 {
				return false
			}

			// 验证元数据被保留
			found := services[0]
			if found.Language != language {
				return false
			}

			if len(found.Protocols) != len(protocols) {
				return false
			}

			if found.Metadata["region"] != "us-west" {
				return false
			}

			if found.Metadata["zone"] != "zone-a" {
				return false
			}

			return true
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.OneConstOf("java", "golang", "php"),
		gen.SliceOf(gen.OneConstOf("gRPC", "JSON-RPC", "REST")),
	))

	properties.Property("Service version is preserved", prop.ForAll(
		func(serviceName string, serviceID string, version string) bool {
			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 注册服务
			service := &ServiceInfo{
				ID:       serviceID,
				Name:     serviceName,
				Version:  version,
				Language: "golang",
				Address:  "127.0.0.1",
				Port:     8080,
				Protocols: []string{"gRPC"},
				RegisteredAt: time.Now(),
			}

			err := registry.Register(ctx, service)
			if err != nil {
				return false
			}

			// 查询服务
			services, err := registry.Discover(ctx, serviceName)
			if err != nil || len(services) == 0 {
				return false
			}

			// 验证版本信息正确
			return services[0].Version == version
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.RegexMatch("v[0-9]+\\.[0-9]+\\.[0-9]+"),
	))

	properties.Property("Healthy service returns healthy status", prop.ForAll(
		func(serviceName string, serviceID string) bool {
			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 注册服务
			service := &ServiceInfo{
				ID:       serviceID,
				Name:     serviceName,
				Version:  "v1.0.0",
				Language: "golang",
				Address:  "127.0.0.1",
				Port:     8080,
				Protocols: []string{"gRPC"},
				RegisteredAt: time.Now(),
			}

			err := registry.Register(ctx, service)
			if err != nil {
				return false
			}

			// 检查健康状态
			status, err := registry.HealthCheck(ctx, serviceID)
			if err != nil {
				return false
			}

			// 验证健康状态为 healthy
			return status == HealthStatusHealthy
		},
		gen.Identifier(),
		gen.Identifier(),
	))

	properties.Property("Non-existent service returns unknown status", prop.ForAll(
		func(serviceID string) bool {
			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 检查不存在的服务
			status, err := registry.HealthCheck(ctx, serviceID)

			// 应该返回错误或 unknown 状态
			return err != nil || status == HealthStatusUnknown
		},
		gen.Identifier(),
	))

	properties.Property("Service discovery returns empty list for non-existent service", prop.ForAll(
		func(serviceName string) bool {
			// 创建内存注册中心
			registry := NewMemoryRegistry(DefaultMemoryRegistryConfig())
			defer registry.Close()

			ctx := context.Background()

			// 查询不存在的服务
			services, err := registry.Discover(ctx, serviceName)
			if err != nil {
				return false
			}

			// 应该返回空列表
			return len(services) == 0
		},
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestServiceRegistrationWithHeartbeat 测试服务注册和心跳机制
func TestServiceRegistrationWithHeartbeat(t *testing.T) {
	properties := gopter.NewProperties(nil)

	properties.Property("Service with heartbeat remains discoverable", prop.ForAll(
		func(serviceName string, serviceID string) bool {
			// 创建内存注册中心，使用较短的 TTL
			config := &MemoryRegistryConfig{
				TTL:               1 * time.Second,
				HeartbeatInterval: 200 * time.Millisecond,
				CleanupInterval:   200 * time.Millisecond,
			}
			registry := NewMemoryRegistry(config)
			defer registry.Close()

			ctx := context.Background()

			// 注册服务
			service := &ServiceInfo{
				ID:       serviceID,
				Name:     serviceName,
				Version:  "v1.0.0",
				Language: "golang",
				Address:  "127.0.0.1",
				Port:     8080,
				Protocols: []string{"gRPC"},
				RegisteredAt: time.Now(),
			}

			err := registry.Register(ctx, service)
			if err != nil {
				return false
			}

			// 发送心跳
			err = registry.Heartbeat(ctx, serviceID)
			if err != nil {
				return false
			}

			// 等待一段时间（小于 TTL）
			time.Sleep(500 * time.Millisecond)

			// 再次发送心跳
			err = registry.Heartbeat(ctx, serviceID)
			if err != nil {
				return false
			}

			// 验证服务仍然可以被发现
			services, err := registry.Discover(ctx, serviceName)
			if err != nil {
				return false
			}

			return len(services) > 0
		},
		gen.Identifier(),
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}
