# 协议适配器和消息路由器

## 概述

本模块实现了多语言通信框架的协议适配器和消息路由器，负责：
- 外部协议（REST、WebSocket、JSON-RPC、MQTT）到内部协议的转换
- 内部协议响应到外部协议的转换
- 基于服务名称和方法名的消息路由
- 多种负载均衡策略（轮询、随机、加权轮询、最少连接）

## 目录结构

```
protocol/
├── adapter/              # 协议适配器
│   ├── adapter.go       # 接口定义和数据结构
│   ├── default_adapter.go  # 默认实现
│   ├── adapter_test.go  # 单元测试
│   └── example_test.go  # 使用示例
├── router/              # 消息路由器
│   ├── router.go        # 路由器接口和实现
│   ├── load_balancer.go # 负载均衡器实现
│   ├── router_test.go   # 单元测试
│   ├── load_balancer_test.go  # 负载均衡测试
│   └── example_test.go  # 使用示例
├── external/            # 外部协议处理器
│   ├── rest/
│   ├── websocket/
│   ├── jsonrpc/
│   └── mqtt/
└── internal/            # 内部协议处理器
    ├── grpc/
    ├── jsonrpc/
    └── custom/
```

## 协议适配器

### 功能

协议适配器负责在外部协议和内部协议之间进行转换，保持消息语义的一致性。

### 支持的协议

**外部协议：**
- REST API
- WebSocket
- JSON-RPC 2.0
- MQTT

**内部协议：**
- gRPC
- Internal JSON-RPC
- Custom Binary Protocol

### 使用示例

#### 1. REST 请求转换

```go
import (
    "context"
    "github.com/framework/golang-sdk/protocol/adapter"
)

// 创建适配器
adapterInstance := adapter.NewDefaultProtocolAdapter()

// 创建 REST 外部请求
externalReq := &adapter.ExternalRequest{
    Protocol: adapter.ProtocolREST,
    Headers: map[string]string{
        "X-Service-Name": "user-service",
        "X-Method-Name":  "getUser",
    },
    Body: map[string]interface{}{
        "userId": "12345",
    },
}

// 转换为内部请求
internalReq, err := adapterInstance.TransformRequest(context.Background(), externalReq)
if err != nil {
    // 处理错误
}

// internalReq.Service = "user-service"
// internalReq.Method = "getUser"
// internalReq.TraceId = "自动生成的追踪ID"
```

#### 2. JSON-RPC 请求转换

```go
// JSON-RPC 请求
externalReq := &adapter.ExternalRequest{
    Protocol: adapter.ProtocolJSONRPC,
    Body: map[string]interface{}{
        "jsonrpc": "2.0",
        "method":  "Calculator.add",
        "params": map[string]interface{}{
            "a": 10,
            "b": 20,
        },
        "id": 1,
    },
}

internalReq, err := adapterInstance.TransformRequest(context.Background(), externalReq)
// internalReq.Service = "Calculator"
// internalReq.Method = "add"
```

#### 3. 响应转换

```go
// 内部响应
internalResp := &adapter.InternalResponse{
    Payload: []byte(`{"result":"success"}`),
    Headers: map[string]string{
        "Content-Type": "application/json",
    },
}

// 转换为外部响应
externalResp, err := adapterInstance.TransformResponse(
    context.Background(), 
    internalResp, 
    adapter.ProtocolREST,
)

// externalResp.StatusCode = 200
// externalResp.Body = {"result":"success"}
```

#### 4. 错误处理

```go
// 包含错误的内部响应
internalResp := &adapter.InternalResponse{
    Error: &adapter.FrameworkError{
        Code:    adapter.ErrorNotFound,
        Message: "User not found",
    },
}

externalResp, err := adapterInstance.TransformResponse(
    context.Background(), 
    internalResp, 
    adapter.ProtocolREST,
)

// externalResp.StatusCode = 404
// externalResp.Error.Code = 404
```

## 消息路由器

### 功能

消息路由器负责根据服务名称、方法名和路由规则将请求路由到正确的服务端点。

### 负载均衡策略

1. **轮询（Round Robin）** - 默认策略，依次选择端点
2. **随机（Random）** - 随机选择端点
3. **加权轮询（Weighted Round Robin）** - 根据权重分配请求
4. **最少连接（Least Connection）** - 选择连接数最少的端点

### 使用示例

#### 1. 基本路由

```go
import (
    "context"
    "github.com/framework/golang-sdk/protocol/router"
    "github.com/framework/golang-sdk/protocol/adapter"
)

// 创建路由器（使用默认轮询负载均衡）
messageRouter := router.NewDefaultMessageRouter(nil)

// 添加服务端点
endpoint := &router.ServiceEndpoint{
    ServiceId: "user-service-1",
    Address:   "localhost",
    Port:      8080,
    Protocol:  adapter.ProtocolGRPC,
}
messageRouter.AddServiceEndpoint("user-service", endpoint)

// 创建请求
request := &adapter.InternalRequest{
    Service: "user-service",
    Method:  "getUser",
}

// 路由请求
selectedEndpoint, err := messageRouter.Route(context.Background(), request)
// selectedEndpoint.ServiceId = "user-service-1"
// selectedEndpoint.Address = "localhost"
// selectedEndpoint.Port = 8080
```

#### 2. 使用路由规则

```go
// 注册路由规则
rule := &router.RoutingRule{
    Name:     "special-method-rule",
    Priority: 10,
    Matcher: func(req *adapter.InternalRequest) bool {
        return req.Method == "specialMethod"
    },
    Target: func(req *adapter.InternalRequest) string {
        return "special-service"
    },
}
messageRouter.RegisterRule(rule)

// 匹配规则的请求会被路由到 special-service
```

#### 3. 使用不同的负载均衡策略

```go
// 随机负载均衡
randomLB := router.NewRandomLoadBalancer()
router1 := router.NewDefaultMessageRouter(randomLB)

// 加权轮询负载均衡
weightedLB := router.NewWeightedRoundRobinLoadBalancer()
router2 := router.NewDefaultMessageRouter(weightedLB)

// 最少连接负载均衡
leastConnLB := router.NewLeastConnectionLoadBalancer()
router3 := router.NewDefaultMessageRouter(leastConnLB)
```

#### 4. 批量更新路由表

```go
// 批量更新服务端点
services := map[string][]*router.ServiceEndpoint{
    "user-service": {
        {ServiceId: "user-1", Address: "localhost", Port: 8080},
        {ServiceId: "user-2", Address: "localhost", Port: 8081},
    },
    "order-service": {
        {ServiceId: "order-1", Address: "localhost", Port: 9090},
    },
}

messageRouter.UpdateRoutingTable(services)
```

## 错误码

框架定义了统一的错误码：

| 错误码 | 说明 |
|-------|------|
| 400 | 错误的请求 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 未找到 |
| 408 | 请求超时 |
| 500 | 内部错误 |
| 501 | 未实现 |
| 503 | 服务不可用 |
| 600 | 协议错误 |
| 601 | 序列化错误 |
| 602 | 路由错误 |
| 603 | 连接错误 |

## 运行测试

```bash
# 测试协议适配器
go test -v ./protocol/adapter/

# 测试消息路由器
go test -v ./protocol/router/

# 运行示例
go test -v -run Example ./protocol/adapter/
go test -v -run Example ./protocol/router/

# 测试所有协议模块
go test -v ./protocol/...
```

## 验证需求

本实现验证了以下需求：

- **需求 4.1**: 外部协议消息转换为内部协议格式
- **需求 4.2**: 内部服务响应转换回外部协议格式
- **需求 4.3**: 根据服务名称和方法名路由消息

## 设计属性

本实现支持以下设计属性：

- **属性 9**: 协议转换往返一致性
- **属性 10**: 消息路由正确性
- **属性 11**: 协议语义一致性
- **属性 12**: 基于内容的路由规则

## 扩展

### 自定义协议适配器

```go
type CustomAdapter struct {
    // 自定义字段
}

func (a *CustomAdapter) TransformRequest(ctx context.Context, external *adapter.ExternalRequest) (*adapter.InternalRequest, error) {
    // 自定义转换逻辑
}

func (a *CustomAdapter) TransformResponse(ctx context.Context, internal *adapter.InternalResponse, originalProtocol adapter.ProtocolType) (*adapter.ExternalResponse, error) {
    // 自定义转换逻辑
}

func (a *CustomAdapter) GetSupportedProtocols() []adapter.ProtocolType {
    return []adapter.ProtocolType{adapter.ProtocolCustomBinary}
}
```

### 自定义负载均衡器

```go
type CustomLoadBalancer struct {
    // 自定义字段
}

func (lb *CustomLoadBalancer) Select(endpoints []*router.ServiceEndpoint) (*router.ServiceEndpoint, error) {
    // 自定义选择逻辑
}
```

## 注意事项

1. **追踪 ID**: 适配器会自动生成或传递追踪 ID，用于分布式追踪
2. **错误处理**: 所有错误都使用统一的 FrameworkError 结构
3. **并发安全**: 路由器和负载均衡器都是并发安全的
4. **协议语义**: 适配器保持请求/响应、发布/订阅等消息语义的一致性
5. **路由规则优先级**: 数字越大优先级越高，高优先级规则先匹配
