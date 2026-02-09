# JSON-RPC 内部协议实现

## 概述

JSON-RPC 内部协议是多语言通信框架的内部通信机制之一，用于服务间的高效通信。本实现使用 **jsonrpc4j** 库作为客户端，服务端则自行实现 JSON-RPC 2.0 协议处理，提供了完整的客户端和服务端功能。

**验证需求**: 3.2, 3.5

## 实现说明

- **客户端**: 使用 jsonrpc4j 的 `JsonRpcHttpClient` 实现，简化 HTTP 通信和 JSON-RPC 协议处理
- **服务端**: 自行实现 JSON-RPC 2.0 协议解析和处理，基于 ServerSocket 提供 TCP 监听
- **序列化**: 使用 Jackson 进行 JSON 序列化和反序列化

这种混合方式既利用了 jsonrpc4j 的成熟客户端实现，又保持了服务端的灵活性和可控性。

## 特性

- ✅ 基于 jsonrpc4j 库实现
- ✅ 支持同步和异步调用
- ✅ 支持服务注册和发现
- ✅ 使用 JSON 序列化（需求 3.5）
- ✅ 连接池管理
- ✅ 错误处理和重试机制
- ✅ 健康检查支持

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│         JsonRpcInternalProtocolHandler                      │
│  (协议处理器 - 管理客户端池和服务端)                          │
└─────────────────┬───────────────────────┬───────────────────┘
                  │                       │
        ┌─────────▼─────────┐   ┌────────▼──────────┐
        │ JsonRpcInternal   │   │ JsonRpcInternal   │
        │     Client        │   │     Server        │
        │  (客户端连接池)    │   │  (服务端监听)      │
        └─────────┬─────────┘   └────────┬──────────┘
                  │                      │
        ┌─────────▼─────────┐   ┌────────▼──────────┐
        │  jsonrpc4j        │   │  jsonrpc4j        │
        │  JsonRpcHttpClient│   │  JsonRpcServer    │
        └───────────────────┘   └───────────────────┘
```

## 核心组件

### 1. JsonRpcInternalConfig

配置类，包含服务端和客户端的配置参数。

**服务端配置**:
- `port`: 监听端口（默认 9091）
- `maxConnections`: 最大连接数（默认 1000）
- `readTimeout`: 读取超时（默认 30 秒）
- `writeTimeout`: 写入超时（默认 30 秒）

**客户端配置**:
- `connectionTimeout`: 连接超时（默认 5 秒）
- `readTimeout`: 读取超时（默认 30 秒）
- `maxRetryAttempts`: 最大重试次数（默认 3）
- `backoffMultiplier`: 退避倍数（默认 2.0）

### 2. JsonRpcInternalClient

客户端实现，负责向远程服务发起 JSON-RPC 调用。

**主要方法**:
- `start()`: 启动客户端
- `shutdown()`: 关闭客户端
- `call()`: 同步调用
- `callAsync()`: 异步调用
- `healthCheck()`: 健康检查

### 3. JsonRpcInternalServer

服务端实现，负责接收和处理 JSON-RPC 请求。

**主要方法**:
- `start()`: 启动服务端
- `shutdown()`: 关闭服务端
- `registerService()`: 注册服务处理器
- `unregisterService()`: 注销服务

### 4. JsonRpcInternalProtocolHandler

协议处理器，统一管理客户端连接池和服务端。

**主要方法**:
- `start(port)`: 启动协议处理器
- `shutdown()`: 关闭协议处理器
- `call()`: 同步调用远程服务
- `callAsync()`: 异步调用远程服务
- `registerService()`: 注册本地服务
- `unregisterService()`: 注销本地服务

## 使用方法

### 1. 启动服务端

```java
// 创建协议处理器
JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();

// 启动服务端，监听 9091 端口
handler.start(9091);

// 注册服务
handler.registerService("UserService", new JsonRpcInternalServer.ServiceHandler() {
    @Override
    public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
        // 处理请求
        if ("getUser".equals(method)) {
            // 返回用户信息
            Map<String, Object> user = new HashMap<>();
            user.put("id", 1);
            user.put("name", "张三");
            
            return new ObjectMapper().writeValueAsBytes(user);
        }
        
        throw new RuntimeException("未知方法: " + method);
    }
});
```

### 2. 客户端同步调用

```java
// 创建协议处理器（仅客户端模式）
JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();
handler.start(0); // 端口为 0 表示不启动服务端

// 构建服务信息
ServiceInfo serviceInfo = new ServiceInfo();
serviceInfo.setName("UserService");
serviceInfo.setAddress("localhost");
serviceInfo.setPort(9091);

// 同步调用
Map<String, Object> request = new HashMap<>();
request.put("userId", 1);

Map<String, Object> response = handler.call(
    serviceInfo,
    "getUser",
    request,
    Map.class
);

System.out.println("用户信息: " + response);
```

### 3. 客户端异步调用

```java
// 异步调用
CompletableFuture<Map> future = handler.callAsync(
    serviceInfo,
    "getUser",
    request,
    Map.class
);

// 处理异步响应
future.thenAccept(response -> {
    System.out.println("用户信息: " + response);
}).exceptionally(e -> {
    System.err.println("调用失败: " + e.getMessage());
    return null;
});
```

## JSON-RPC 协议格式

### 请求格式

```json
{
  "jsonrpc": "2.0",
  "method": "UserService.getUser",
  "params": {
    "userId": 1
  },
  "id": 1
}
```

### 响应格式

**成功响应**:
```json
{
  "jsonrpc": "2.0",
  "result": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  },
  "id": 1
}
```

**错误响应**:
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32601,
    "message": "Method not found"
  },
  "id": 1
}
```

## 错误码映射

JSON-RPC 标准错误码映射到框架错误码：

| JSON-RPC 错误码 | 含义 | 框架错误码 |
|----------------|------|-----------|
| -32700 | Parse error | SERIALIZATION_ERROR |
| -32600 | Invalid Request | PROTOCOL_ERROR |
| -32601 | Method not found | NOT_FOUND |
| -32602 | Invalid params | BAD_REQUEST |
| -32603 | Internal error | INTERNAL_ERROR |

## 序列化

JSON-RPC 内部协议使用 **JSON** 作为序列化格式（需求 3.5），通过 Jackson 库实现：

- 请求和响应都使用 JSON 格式
- 支持基本数据类型和复合数据类型
- 自动处理 Java 对象和 JSON 之间的转换

## 性能优化

1. **连接池**: 客户端使用连接池复用 HTTP 连接
2. **异步处理**: 服务端使用线程池异步处理请求
3. **超时控制**: 支持连接超时和读取超时配置
4. **重试机制**: 客户端支持指数退避重试

## 与 gRPC 的对比

| 特性 | JSON-RPC | gRPC |
|-----|----------|------|
| 序列化格式 | JSON | Protocol Buffers |
| 传输协议 | HTTP | HTTP/2 |
| 性能 | 中等 | 高 |
| 可读性 | 高（文本格式） | 低（二进制格式） |
| 跨语言支持 | 优秀 | 优秀 |
| 流式支持 | 不支持 | 支持 |

## 使用场景

JSON-RPC 内部协议适用于：

1. **调试和开发**: JSON 格式易于阅读和调试
2. **跨语言通信**: JSON 是通用的数据交换格式
3. **中等性能要求**: 不需要极致性能的场景
4. **简单的请求/响应模式**: 不需要流式通信

## 注意事项

1. **端口配置**: 确保服务端端口不与其他服务冲突
2. **超时设置**: 根据实际业务调整超时时间
3. **错误处理**: 正确处理网络异常和业务异常
4. **资源释放**: 使用完毕后及时调用 `shutdown()` 释放资源
5. **线程安全**: 所有组件都是线程安全的，可以在多线程环境中使用

## 示例代码

完整的使用示例请参考 `JsonRpcInternalUsageExample.java`。

## 依赖

```xml
<dependency>
    <groupId>com.github.briandilley.jsonrpc4j</groupId>
    <artifactId>jsonrpc4j</artifactId>
    <version>1.6</version>
</dependency>
```

## 相关文档

- [JSON-RPC 2.0 规范](https://www.jsonrpc.org/specification)
- [jsonrpc4j 文档](https://github.com/briandilley/jsonrpc4j)
- [设计文档](../../../../../.kiro/specs/multi-language-communication-framework/design.md)
- [需求文档](../../../../../.kiro/specs/multi-language-communication-framework/requirements.md)
