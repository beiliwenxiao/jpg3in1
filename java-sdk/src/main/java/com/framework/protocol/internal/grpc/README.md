# gRPC 协议实现

## 概述

本模块实现了多语言通信框架的 gRPC 内部协议支持，提供高性能的服务间通信能力。

## 核心组件

### 1. GrpcClient - gRPC 客户端

负责与远程 gRPC 服务端建立连接并发起调用。

**主要功能：**
- 同步调用：`call(service, method, payload, headers, timeout)`
- 异步调用：`callAsync(service, method, payload, headers, timeout)`
- 流式调用：`stream(service, method, payload, headers, timeout)`
- 健康检查：`healthCheck()`

**使用示例：**
```java
// 创建客户端
GrpcClient client = new GrpcClient("localhost", 9090);
client.start();

// 同步调用
byte[] request = "{\"id\":1}".getBytes();
byte[] response = client.call("UserService", "getUser", request, null, 5000);

// 关闭客户端
client.shutdown();
```

### 2. GrpcServer - gRPC 服务端

负责监听端口并处理来自客户端的请求。

**主要功能：**
- 服务注册：`registerService(serviceName, handler)`
- 服务注销：`unregisterService(serviceName)`
- 启动服务：`start()`
- 关闭服务：`shutdown()`

**使用示例：**
```java
// 创建服务端
GrpcServer server = new GrpcServer(9090);

// 注册服务处理器
server.registerService("UserService", new GrpcServer.ServiceHandler() {
    @Override
    public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
        // 处理业务逻辑
        return responseData;
    }
});

// 启动服务端
server.start();
```

### 3. GrpcProtocolHandler - gRPC 协议处理器

提供统一的 gRPC 协议管理，包括客户端连接池和服务端管理。

**主要功能：**
- 客户端连接池管理
- 自动序列化/反序列化
- 服务注册和调用
- 统一的错误处理

**使用示例：**
```java
// 创建协议处理器
GrpcProtocolHandler handler = new GrpcProtocolHandler();
handler.start(9090);

// 注册服务
handler.registerService("OrderService", serviceHandler);

// 调用远程服务
ServiceInfo serviceInfo = new ServiceInfo();
serviceInfo.setName("OrderService");
serviceInfo.setAddress("localhost");
serviceInfo.setPort(9090);

String response = handler.call(serviceInfo, "createOrder", request, String.class);

// 关闭处理器
handler.shutdown();
```

### 4. GrpcConfig - gRPC 配置

提供服务端和客户端的配置选项。

**服务端配置：**
- `port`: 监听端口（默认 9090）
- `maxInboundMessageSize`: 最大入站消息大小（默认 4MB）
- `maxConnectionIdle`: 最大连接空闲时间（默认 5 分钟）
- `maxConnectionAge`: 最大连接存活时间（默认 1 小时）
- `useTls`: 是否启用 TLS（默认 false）

**客户端配置：**
- `maxRetryAttempts`: 最大重试次数（默认 3）
- `initialBackoffMs`: 初始退避时间（默认 100ms）
- `maxBackoffMs`: 最大退避时间（默认 5000ms）
- `keepAliveTimeMs`: 保活时间（默认 30 秒）
- `maxInboundMessageSize`: 最大入站消息大小（默认 4MB）

## 特性

### 1. 多种调用模式

- **同步调用**：阻塞等待响应，适合简单的请求-响应场景
- **异步调用**：返回 CompletableFuture，适合需要并发处理的场景
- **流式调用**：支持服务端流式响应，适合大数据传输

### 2. 连接池管理

- 自动管理客户端连接池
- 连接复用，提高性能
- 自动重连机制

### 3. 错误处理

- 统一的错误码映射
- 详细的错误信息
- 异常链追踪

### 4. 序列化支持

- 自动 JSON 序列化/反序列化
- 支持 byte[] 和 String 类型
- 可扩展的序列化机制

### 5. 健康检查

- 内置健康检查端点
- 支持服务可用性监控

## 协议定义

gRPC 服务基于 Protocol Buffers 定义，主要包括：

### FrameworkService

```protobuf
service FrameworkService {
  // 同步调用
  rpc Call(CallRequest) returns (CallResponse);
  
  // 流式调用
  rpc Stream(CallRequest) returns (stream StreamData);
  
  // 双向流
  rpc BiStream(stream CallRequest) returns (stream CallResponse);
  
  // 健康检查
  rpc HealthCheck(HealthCheckResponse) returns (HealthCheckResponse);
}
```

### 消息格式

- **CallRequest**: 包含服务名、方法名、负载、请求头、超时时间
- **CallResponse**: 包含响应负载、响应头、错误信息
- **StreamData**: 包含流数据和结束标志

## 集成到框架

gRPC 协议处理器可以集成到 `DefaultFrameworkClient` 中：

```java
public class DefaultFrameworkClient implements FrameworkClient {
    private GrpcProtocolHandler grpcHandler;
    
    public void start() {
        // 启动 gRPC 协议处理器
        grpcHandler = new GrpcProtocolHandler();
        grpcHandler.start(9090);
    }
    
    public <T> T call(String service, String method, Object request, Class<T> responseType) {
        // 查询服务注册中心获取服务信息
        ServiceInfo serviceInfo = serviceRegistry.discover(service);
        
        // 使用 gRPC 调用
        return grpcHandler.call(serviceInfo, method, request, responseType);
    }
}
```

## 性能优化

1. **连接复用**：客户端连接池自动复用连接，减少连接建立开销
2. **异步 I/O**：基于 Netty 的异步非阻塞 I/O
3. **Protocol Buffers**：高效的二进制序列化格式
4. **流式传输**：支持大数据流式传输，避免内存溢出

## 安全性

### TLS/SSL 支持

```java
GrpcConfig.ServerConfig serverConfig = new GrpcConfig.ServerConfig();
serverConfig.setUseTls(true);
serverConfig.setCertChainFile("server.crt");
serverConfig.setPrivateKeyFile("server.key");
```

### 认证和授权

可以通过请求头传递认证信息：

```java
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer " + token);

client.call(service, method, payload, headers, timeout);
```

## 跨平台兼容性

- **Windows 10**: 完全支持
- **Linux**: 完全支持
- **macOS**: 完全支持

## 依赖

```xml
<!-- gRPC -->
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty</artifactId>
    <version>1.60.0</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-protobuf</artifactId>
    <version>1.60.0</version>
</dependency>
<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-stub</artifactId>
    <version>1.60.0</version>
</dependency>
```

## 验证需求

本实现验证以下需求：

- **需求 3.1**: 服务间支持 gRPC 协议通信
- **需求 3.4**: 为 gRPC 提供 Protocol Buffers 序列化支持

## 下一步

1. 实现 JSON-RPC 内部协议（任务 7.2）
2. 实现自定义二进制协议（任务 7.3）
3. 编写属性测试验证内部协议通信（任务 7.4）

## 参考资料

- [gRPC 官方文档](https://grpc.io/docs/)
- [Protocol Buffers 文档](https://protobuf.dev/)
- [Netty 文档](https://netty.io/wiki/)
