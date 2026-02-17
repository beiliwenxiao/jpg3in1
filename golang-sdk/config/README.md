# 配置管理模块

## 概述

配置管理模块使用 GoFrame 的 `gcfg` 组件实现，提供灵活的配置加载、环境变量覆盖和配置验证功能。

## 特性

- ✅ 基于 GoFrame gcfg 的配置加载
- ✅ 支持 YAML 格式配置文件
- ✅ 环境变量覆盖配置
- ✅ 配置验证
- ✅ 类型安全的配置访问
- ✅ 结构化配置对象

## 使用方法

### 1. 创建配置管理器

```go
import "github.com/framework/golang-sdk/config"

// 加载配置文件
cm, err := config.NewConfigManager("config.yaml")
if err != nil {
    log.Fatalf("Failed to load config: %v", err)
}
```

### 2. 访问配置值

```go
// 获取字符串配置
name := cm.GetString("framework.name")

// 获取整数配置
port := cm.GetInt("framework.network.port")

// 获取布尔配置
keepAlive := cm.GetBool("framework.network.keepAlive")

// 获取时间间隔配置
timeout := cm.GetDuration("framework.network.readTimeout")

// 获取字符串切片配置
endpoints := cm.GetStringSlice("framework.registry.endpoints")

// 使用默认值
host := cm.GetString("framework.network.host", "0.0.0.0")
```

### 3. 加载结构化配置

```go
// 加载完整的框架配置
frameworkConfig, err := cm.LoadFrameworkConfig()
if err != nil {
    log.Fatalf("Failed to load framework config: %v", err)
}

// 访问配置
fmt.Printf("Service: %s v%s\n", frameworkConfig.Name, frameworkConfig.Version)
fmt.Printf("Port: %d\n", frameworkConfig.Network.Port)
fmt.Printf("Registry: %s\n", frameworkConfig.Registry.Type)
```

## 环境变量覆盖

配置管理器支持通过环境变量覆盖配置文件中的值。环境变量命名规则：

- 将配置路径中的点（`.`）替换为下划线（`_`）
- 转换为大写字母

### 示例

| 配置路径 | 环境变量名 | 示例值 |
|---------|-----------|--------|
| `framework.name` | `FRAMEWORK_NAME` | `my-service` |
| `framework.network.host` | `FRAMEWORK_NETWORK_HOST` | `127.0.0.1` |
| `framework.network.port` | `FRAMEWORK_NETWORK_PORT` | `8080` |
| `framework.registry.type` | `FRAMEWORK_REGISTRY_TYPE` | `etcd` |
| `framework.network.keepAlive` | `FRAMEWORK_NETWORK_KEEPALIVE` | `true` |

### 使用环境变量

```bash
# 设置环境变量
export FRAMEWORK_NAME=my-service
export FRAMEWORK_NETWORK_PORT=8080
export FRAMEWORK_REGISTRY_ENDPOINTS=http://etcd1:2379,http://etcd2:2379

# 运行应用
./my-app
```

在代码中，环境变量会自动覆盖配置文件中的值：

```go
cm, _ := config.NewConfigManager("config.yaml")

// 如果设置了 FRAMEWORK_NAME 环境变量，将使用环境变量的值
name := cm.GetString("framework.name")
```

## 配置验证

配置管理器在初始化时会自动验证配置的有效性，包括：

### 必需配置项

- `framework.name` - 服务名称
- `framework.version` - 服务版本
- `framework.language` - 编程语言
- `framework.network.host` - 网络主机地址
- `framework.registry.type` - 注册中心类型
- `framework.registry.endpoints` - 注册中心端点列表

### 值范围验证

- `framework.network.port` - 必须在 1-65535 之间
- `framework.network.maxConnections` - 必须为正数
- `framework.connectionPool.maxConnections` - 必须为正数
- `framework.connectionPool.minConnections` - 必须为非负数，且不能大于 maxConnections
- `framework.observability.logging.level` - 必须是 debug、info、warn、error 之一

### 验证失败处理

如果配置验证失败，`NewConfigManager` 会返回错误：

```go
cm, err := config.NewConfigManager("config.yaml")
if err != nil {
    // 处理配置错误
    log.Fatalf("Config validation failed: %v", err)
}
```

## 配置文件示例

```yaml
framework:
  name: golang-service
  version: 1.0.0
  language: golang
  
  network:
    host: 0.0.0.0
    port: 8081
    maxConnections: 1000
    readTimeout: 30s
    writeTimeout: 30s
    keepAlive: true
  
  registry:
    type: etcd
    endpoints:
      - http://localhost:2379
    namespace: /framework/services
    ttl: 30
    heartbeatInterval: 10
  
  connectionPool:
    maxConnections: 100
    minConnections: 10
    idleTimeout: 5m
    maxLifetime: 30m
    connectionTimeout: 5s
  
  observability:
    logging:
      level: info
      format: json
      output: stdout
```

## 配置结构

### FrameworkConfig

完整的框架配置结构，包含所有子配置：

- `Name` - 服务名称
- `Version` - 服务版本
- `Language` - 编程语言
- `Network` - 网络配置
- `Registry` - 注册中心配置
- `Protocols` - 协议配置
- `ConnectionPool` - 连接池配置
- `Security` - 安全配置
- `Observability` - 可观测性配置

详细的配置结构定义请参考 `framework_config.go`。

## 最佳实践

1. **使用环境变量管理敏感信息**
   ```bash
   export FRAMEWORK_SECURITY_TLS_CERTFILE=/path/to/cert.pem
   export FRAMEWORK_SECURITY_TLS_KEYFILE=/path/to/key.pem
   ```

2. **为不同环境使用不同的配置文件**
   ```go
   env := os.Getenv("ENV")
   configFile := fmt.Sprintf("config.%s.yaml", env)
   cm, _ := config.NewConfigManager(configFile)
   ```

3. **在应用启动时验证配置**
   ```go
   cm, err := config.NewConfigManager("config.yaml")
   if err != nil {
       log.Fatalf("Invalid configuration: %v", err)
   }
   ```

4. **使用结构化配置对象**
   ```go
   // 推荐：使用结构化配置
   frameworkConfig, _ := cm.LoadFrameworkConfig()
   port := frameworkConfig.Network.Port
   
   // 而不是：每次都查询配置
   port := cm.GetInt("framework.network.port")
   ```

## 测试

运行配置管理模块的测试：

```bash
cd golang-sdk/config
go test -v
```

测试覆盖：
- 配置加载
- 类型转换
- 环境变量覆盖
- 配置验证
- 默认值处理
- 结构化配置加载
