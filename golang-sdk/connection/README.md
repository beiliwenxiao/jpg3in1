# 连接管理器 (Connection Manager)

Golang SDK 的连接管理器模块，提供高效的连接池管理和生命周期控制。

## 功能特性

- ✅ **连接池管理**: 为每个服务端点维护独立的连接池
- ✅ **连接复用**: 自动复用空闲连接，提高性能
- ✅ **空闲超时**: 自动关闭超时的空闲连接
- ✅ **连接失败重连**: 支持指数退避的重连策略
- ✅ **优雅关闭**: 等待活跃请求完成后关闭连接
- ✅ **健康检查**: 定期检查连接健康状态
- ✅ **统计信息**: 提供详细的连接池统计

## 验证需求

- **需求 7.1**: 连接池复用
- **需求 7.2**: 空闲超时关闭
- **需求 7.3**: 连接失败重连
- **需求 7.4**: 连接健康监控
- **需求 7.5**: 优雅关闭
- **需求 12.2**: 连接复用优化

## 核心组件

### 1. ConnectionManager

连接管理器接口，管理多个服务端点的连接池。

```go
type ConnectionManager interface {
    GetConnection(ctx context.Context, endpoint *ServiceEndpoint) (*ManagedConnection, error)
    ReleaseConnection(conn *ManagedConnection)
    CloseConnections(endpoint *ServiceEndpoint) error
    CloseAll() error
    ShutdownGracefully(timeout time.Duration) error
    GetPoolStats(endpoint *ServiceEndpoint) *ConnectionPoolStats
    GetTotalStats() *ConnectionPoolStats
    UpdateConfig(config *ConnectionConfig)
    IsClosed() bool
}
```

### 2. ConnectionPool

单个服务端点的连接池实现。

- 管理到单个服务的所有连接
- 自动清理空闲和过期连接
- 支持最大连接数限制

### 3. ManagedConnection

受管连接，封装底层网络连接。

- 连接状态管理（Idle、Active、Closed）
- 生命周期跟踪（创建时间、最后使用时间）
- 健康检查

### 4. ConnectionLifecycleManager

连接生命周期管理器，提供重连功能。

- 自动重连失败的连接
- 指数退避策略
- 异步重连支持

## 使用示例

### 基本使用

```go
package main

import (
    "context"
    "fmt"
    "time"
    
    "github.com/framework/golang-sdk/connection"
)

func main() {
    // 创建配置
    config := connection.DefaultConnectionConfig()
    config.MaxConnections = 100
    config.IdleTimeout = 5 * time.Minute
    
    // 创建连接管理器
    manager := connection.NewConnectionManager(config)
    defer manager.CloseAll()
    
    // 定义服务端点
    endpoint := &connection.ServiceEndpoint{
        ServiceID: "user-service",
        Name:      "user",
        Address:   "localhost",
        Port:      50051,
        Protocol:  "gRPC",
    }
    
    // 获取连接
    ctx := context.Background()
    conn, err := manager.GetConnection(ctx, endpoint)
    if err != nil {
        panic(err)
    }
    
    // 使用连接
    // ... 执行 RPC 调用 ...
    
    // 释放连接
    manager.ReleaseConnection(conn)
}
```

### 使用生命周期管理器（带重连）

```go
// 创建基础连接管理器
baseManager := connection.NewConnectionManager(config)

// 包装为生命周期管理器
lifecycleManager := connection.NewConnectionLifecycleManager(baseManager, config)
defer lifecycleManager.CloseAll()

// 获取连接（带自动重连）
conn, err := lifecycleManager.GetConnectionWithRetry(ctx, endpoint)
if err != nil {
    panic(err)
}

// 使用连接
// ...

// 释放连接
lifecycleManager.ReleaseConnection(conn)
```

### 查看统计信息

```go
// 获取特定端点的统计
stats := manager.GetPoolStats(endpoint)
fmt.Printf("Total: %d, Active: %d, Idle: %d\n", 
    stats.TotalConnections, 
    stats.ActiveConnections, 
    stats.IdleConnections)

// 获取全局统计
totalStats := manager.GetTotalStats()
fmt.Printf("Global - Total: %d, Active: %d\n", 
    totalStats.TotalConnections, 
    totalStats.ActiveConnections)
```

### 优雅关闭

```go
// 等待最多 30 秒让所有活跃连接完成
err := manager.ShutdownGracefully(30 * time.Second)
if err != nil {
    fmt.Printf("Graceful shutdown error: %v\n", err)
}
```

## 配置选项

```go
type ConnectionConfig struct {
    MaxConnections       int           // 最大连接数，默认 100
    MinConnections       int           // 最小连接数，默认 10
    IdleTimeout          time.Duration // 空闲超时，默认 5 分钟
    MaxLifetime          time.Duration // 最大生命周期，默认 30 分钟
    ConnectionTimeout    time.Duration // 获取连接超时，默认 5 秒
    ConnectTimeout       time.Duration // 建立连接超时，默认 5 秒
    HealthCheckInterval  time.Duration // 健康检查间隔，默认 30 秒
    ReconnectDelay       time.Duration // 重连延迟，默认 1 秒
    MaxReconnectAttempts int           // 最大重连次数，默认 3
    KeepAlive            bool          // TCP KeepAlive，默认 true
    TCPNoDelay           bool          // TCP NoDelay，默认 true
}
```

## 连接状态

连接有三种状态：

- **Idle**: 空闲状态，可以被复用
- **Active**: 活跃状态，正在使用中
- **Closed**: 已关闭状态

## 自动清理机制

连接池会定期（根据 `HealthCheckInterval`）清理以下连接：

1. 已关闭的连接
2. 不健康的连接
3. 空闲超时的连接（超过 `IdleTimeout`）
4. 超过最大生命周期的连接（超过 `MaxLifetime`）

## 重连策略

使用指数退避算法：

- 第 1 次重试：延迟 1 秒
- 第 2 次重试：延迟 2 秒
- 第 3 次重试：延迟 4 秒
- 最大延迟：30 秒

## 注意事项

1. **连接复用**: 使用完连接后必须调用 `ReleaseConnection`，否则连接无法被复用
2. **优雅关闭**: 应用退出时建议使用 `ShutdownGracefully` 而不是 `CloseAll`
3. **超时设置**: 根据实际网络环境调整超时参数
4. **最大连接数**: 根据服务器负载能力设置合理的最大连接数

## 测试

运行单元测试：

```bash
# Windows
cd golang-sdk
go test -v ./connection/

# Linux
cd golang-sdk
go test -v ./connection/
```

注意：部分测试需要实际的 gRPC 服务器，这些测试会被跳过。

## 与 Java SDK 的对应关系

| Golang | Java |
|--------|------|
| `ConnectionManager` | `ConnectionManager` |
| `DefaultConnectionManager` | `DefaultConnectionManager` |
| `ConnectionPool` | `NettyConnectionPool` |
| `ManagedConnection` | `ManagedConnection` |
| `ConnectionLifecycleManager` | `ConnectionLifecycleManager` |
| `ConnectionConfig` | `ConnectionConfig` |

## 后续改进

- [ ] 支持更多协议（TCP、WebSocket 等）
- [ ] 添加连接预热功能
- [ ] 支持连接池监控指标导出
- [ ] 添加更详细的性能测试
