# 服务注册与发现

本模块提供了服务注册与发现的功能，支持多种注册中心实现。

## 支持的注册中心

### 1. 内存注册中心（MemoryRegistry）

**推荐用于开发和测试环境**

零依赖的内存注册中心，适合快速开发和测试。

#### 特性

- ✅ 零外部依赖
- ✅ 快速启动
- ✅ 支持 TTL 和心跳机制
- ✅ 支持服务版本管理
- ✅ 支持健康检查
- ✅ 线程安全
- ✅ 自动清理过期服务

#### 使用示例

```go
package main

import (
    "context"
    "fmt"
    "time"
    
    "github.com/framework/golang-sdk/registry"
)

func main() {
    // 创建内存注册中心
    config := &registry.MemoryRegistryConfig{
        TTL:               30 * time.Second,  // 服务 TTL
        HeartbeatInterval: 10 * time.Second,  // 心跳间隔
        CleanupInterval:   5 * time.Second,   // 清理间隔
    }
    
    reg := registry.NewMemoryRegistry(config)
    defer reg.Close()
    
    ctx := context.Background()
    
    // 注册服务
    service := &registry.ServiceInfo{
        ID:       "my-service-1",
        Name:     "my-service",
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
    
    err := reg.Register(ctx, service)
    if err != nil {
        panic(err)
    }
    
    // 发送心跳保持服务活跃
    go func() {
        ticker := time.NewTicker(10 * time.Second)
        defer ticker.Stop()
        
        for range ticker.C {
            err := reg.Heartbeat(ctx, service.ID)
            if err != nil {
                fmt.Printf("Heartbeat failed: %v\n", err)
            }
        }
    }()
    
    // 查询服务
    services, err := reg.Discover(ctx, "my-service")
    if err != nil {
        panic(err)
    }
    
    fmt.Printf("Found %d service instances\n", len(services))
    
    // 健康检查
    status, err := reg.HealthCheck(ctx, service.ID)
    if err != nil {
        panic(err)
    }
    
    fmt.Printf("Service health status: %s\n", status)
    
    // 监听服务变化
    err = reg.Watch(ctx, "my-service", func(services []*registry.ServiceInfo) {
        fmt.Printf("Service changed, now %d instances\n", len(services))
    })
    if err != nil {
        panic(err)
    }
    
    // 注销服务
    err = reg.Deregister(ctx, service.ID)
    if err != nil {
        panic(err)
    }
}
```

### 2. etcd 注册中心（EtcdRegistry）

**推荐用于生产环境**

基于 etcd 的分布式注册中心，适合生产环境。

#### 特性

- ✅ 分布式一致性
- ✅ 高可用
- ✅ 持久化存储
- ✅ 支持集群部署
- ✅ 自动故障转移

#### 使用示例

```go
package main

import (
    "context"
    "time"
    
    "github.com/framework/golang-sdk/registry"
)

func main() {
    // 创建 etcd 注册中心
    config := &registry.EtcdRegistryConfig{
        Endpoints:        []string{"localhost:2379"},
        Namespace:        "/services",
        TTL:              10,
        HeartbeatInterval: 3 * time.Second,
        DialTimeout:      5 * time.Second,
    }
    
    reg, err := registry.NewEtcdRegistry(config)
    if err != nil {
        panic(err)
    }
    defer reg.Close()
    
    ctx := context.Background()
    
    // 注册服务
    service := &registry.ServiceInfo{
        ID:       "my-service-1",
        Name:     "my-service",
        Version:  "1.0.0",
        Language: "golang",
        Address:  "localhost",
        Port:     8080,
        Protocols: []string{"gRPC"},
        RegisteredAt: time.Now(),
    }
    
    err = reg.Register(ctx, service)
    if err != nil {
        panic(err)
    }
    
    // 其他操作与内存注册中心相同...
}
```

## 集成负载均衡

使用 `RegistryRouter` 可以将注册中心与负载均衡器集成：

```go
package main

import (
    "context"
    
    "github.com/framework/golang-sdk/registry"
    "github.com/framework/golang-sdk/protocol/router"
    "github.com/framework/golang-sdk/protocol/adapter"
)

func main() {
    // 创建注册中心
    reg := registry.NewMemoryRegistry(nil)
    defer reg.Close()
    
    // 创建负载均衡器（轮询、随机、最少连接）
    lb := router.NewRoundRobinLoadBalancer()
    // lb := router.NewRandomLoadBalancer()
    // lb := router.NewLeastConnectionLoadBalancer()
    
    // 创建集成路由器
    registryRouter := registry.NewRegistryRouter(reg, lb)
    defer registryRouter.Close()
    
    ctx := context.Background()
    
    // 注册服务
    service := &registry.ServiceInfo{
        ID:       "my-service-1",
        Name:     "my-service",
        Version:  "1.0.0",
        Language: "golang",
        Address:  "localhost",
        Port:     8080,
        Protocols: []string{"gRPC"},
    }
    
    err := registryRouter.RegisterService(ctx, service)
    if err != nil {
        panic(err)
    }
    
    // 路由请求到服务
    request := &adapter.InternalRequest{
        Service: "my-service",
        Method:  "GetUser",
    }
    
    endpoint, err := registryRouter.Route(ctx, request)
    if err != nil {
        panic(err)
    }
    
    // 使用选中的端点进行调用
    // ... 调用 endpoint.Address:endpoint.Port
}
```

## 负载均衡策略

### 1. 轮询（Round Robin）

按顺序依次选择服务实例。

```go
lb := router.NewRoundRobinLoadBalancer()
```

### 2. 随机（Random）

随机选择服务实例。

```go
lb := router.NewRandomLoadBalancer()
```

### 3. 最少连接（Least Connection）

选择当前连接数最少的服务实例。

```go
lb := router.NewLeastConnectionLoadBalancer()
```

## 配置说明

### MemoryRegistryConfig

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| TTL | time.Duration | 30s | 服务生存时间 |
| HeartbeatInterval | time.Duration | 10s | 心跳间隔 |
| CleanupInterval | time.Duration | 5s | 清理过期服务的间隔 |

### EtcdRegistryConfig

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| Endpoints | []string | ["localhost:2379"] | etcd 端点列表 |
| Namespace | string | "/services" | 服务命名空间 |
| TTL | int64 | 10 | 租约 TTL（秒） |
| HeartbeatInterval | time.Duration | 3s | 心跳间隔 |
| DialTimeout | time.Duration | 5s | 连接超时 |

## 最佳实践

### 1. 开发环境

使用内存注册中心，快速启动，无需外部依赖：

```go
reg := registry.NewMemoryRegistry(registry.DefaultMemoryRegistryConfig())
```

### 2. 生产环境

使用 etcd 注册中心，提供高可用和持久化：

```go
config := &registry.EtcdRegistryConfig{
    Endpoints: []string{"etcd1:2379", "etcd2:2379", "etcd3:2379"},
    Namespace: "/prod/services",
    TTL:       10,
}
reg, err := registry.NewEtcdRegistry(config)
```

### 3. 心跳机制

定期发送心跳以保持服务注册：

```go
go func() {
    ticker := time.NewTicker(config.HeartbeatInterval)
    defer ticker.Stop()
    
    for range ticker.C {
        if err := reg.Heartbeat(ctx, serviceID); err != nil {
            log.Printf("Heartbeat failed: %v", err)
            // 重新注册服务
            reg.Register(ctx, service)
        }
    }
}()
```

### 4. 优雅关闭

在程序退出时注销服务：

```go
defer func() {
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    
    if err := reg.Deregister(ctx, serviceID); err != nil {
        log.Printf("Failed to deregister service: %v", err)
    }
    
    reg.Close()
}()
```

### 5. 服务版本管理

使用版本号管理服务的不同版本：

```go
serviceV1 := &registry.ServiceInfo{
    ID:      "my-service-v1-1",
    Name:    "my-service",
    Version: "1.0.0",
    // ...
}

serviceV2 := &registry.ServiceInfo{
    ID:      "my-service-v2-1",
    Name:    "my-service",
    Version: "2.0.0",
    // ...
}
```

## 接口定义

```go
type ServiceRegistry interface {
    // Register 注册服务
    Register(ctx context.Context, service *ServiceInfo) error
    
    // Deregister 注销服务
    Deregister(ctx context.Context, serviceID string) error
    
    // Discover 查询服务
    Discover(ctx context.Context, serviceName string) ([]*ServiceInfo, error)
    
    // HealthCheck 健康检查
    HealthCheck(ctx context.Context, serviceID string) (HealthStatus, error)
    
    // Watch 监听服务变化
    Watch(ctx context.Context, serviceName string, callback func([]*ServiceInfo)) error
    
    // Close 关闭注册中心连接
    Close() error
}
```

## 测试

运行所有测试：

```bash
# Windows
go test ./registry -v

# Linux
go test ./registry -v
```

运行特定测试：

```bash
# 测试内存注册中心
go test ./registry -run TestMemoryRegistry -v

# 测试 etcd 注册中心（需要 etcd 运行）
go test ./registry -run TestServiceRegistration -v

# 测试负载均衡
go test ./registry -run TestMemoryRegistryRouterWithRoundRobin -v
```

## 性能特性

### 内存注册中心

- 注册/注销：< 1ms
- 查询：< 1ms
- 心跳：< 1ms
- 内存占用：每个服务实例约 1KB

### etcd 注册中心

- 注册/注销：5-20ms（取决于网络延迟）
- 查询：5-20ms
- 心跳：5-10ms
- 支持数千个服务实例

## 故障处理

### 服务过期

服务在 TTL 时间内未发送心跳将被自动清理。

### 网络故障

- 内存注册中心：无网络依赖
- etcd 注册中心：自动重连，支持故障转移

### 注册中心不可用

建议实现降级策略，使用本地缓存的服务列表。

## 迁移指南

### 从 etcd 迁移到内存注册中心

只需更改注册中心的创建方式，接口完全兼容：

```go
// 之前
reg, err := registry.NewEtcdRegistry(etcdConfig)

// 之后
reg := registry.NewMemoryRegistry(memoryConfig)
```

### 从内存注册中心迁移到 etcd

同样只需更改创建方式：

```go
// 之前
reg := registry.NewMemoryRegistry(memoryConfig)

// 之后
reg, err := registry.NewEtcdRegistry(etcdConfig)
```
