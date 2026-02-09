# JSON-RPC 2.0 协议处理器

## 概述

JSON-RPC 2.0 协议处理器实现了完整的 [JSON-RPC 2.0 规范](https://www.jsonrpc.org/specification)，支持单个请求、批量请求、通知和标准错误处理。

**验证需求: 2.3**

## 功能特性

### 1. 标准 JSON-RPC 2.0 支持
- ✅ 完全符合 JSON-RPC 2.0 规范
- ✅ 支持字符串和数字类型的请求 ID
- ✅ 支持数组和对象类型的参数
- ✅ 支持通知（无 ID 的请求）
- ✅ 支持批量请求

### 2. 错误处理
- ✅ 标准错误码（-32700 到 -32603）
- ✅ 解析错误（Parse error）
- ✅ 无效请求（Invalid Request）
- ✅ 方法不存在（Method not found）
- ✅ 无效参数（Invalid params）
- ✅ 内部错误（Internal error）

### 3. 扩展功能
- ✅ 分布式追踪支持（trace_id, span_id）
- ✅ 服务名称解析（支持 service.method 格式）
- ✅ 与框架协议适配器集成

## 使用示例

### 单个请求

**请求：**
```json
{
  "jsonrpc": "2.0",
  "method": "userService.getUser",
  "params": {"userId": "123"},
  "id": 1
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "userId": "123",
    "name": "张三",
    "email": "zhangsan@example.com"
  },
  "id": 1
}
```

### 通知（无响应）

**请求：**
```json
{
  "jsonrpc": "2.0",
  "method": "notificationService.notify",
  "params": {"message": "系统更新"}
}
```

**响应：** HTTP 204 No Content（无响应体）

### 批量请求

**请求：**
```json
[
  {
    "jsonrpc": "2.0",
    "method": "userService.getUser",
    "params": {"userId": "123"},
    "id": 1
  },
  {
    "jsonrpc": "2.0",
    "method": "orderService.getOrder",
    "params": {"orderId": "456"},
    "id": 2
  }
]
```

**响应：**
```json
[
  {
    "jsonrpc": "2.0",
    "result": {"userId": "123", "name": "张三"},
    "id": 1
  },
  {
    "jsonrpc": "2.0",
    "result": {"orderId": "456", "status": "completed"},
    "id": 2
  }
]
```

### 错误响应

**请求：**
```json
{
  "jsonrpc": "2.0",
  "method": "unknownService.unknownMethod",
  "id": 1
}
```

**响应：**
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

### 带追踪信息的请求

**请求：**
```json
{
  "jsonrpc": "2.0",
  "method": "userService.getUser",
  "params": {"userId": "123"},
  "id": 1,
  "trace_id": "trace-abc-123",
  "span_id": "span-def-456"
}
```

**响应：**
```json
{
  "jsonrpc": "2.0",
  "result": {"userId": "123", "name": "张三"},
  "id": 1,
  "trace_id": "trace-abc-123",
  "span_id": "span-def-456"
}
```

## 端点配置

默认端点：`POST /jsonrpc`

可以通过 Spring Boot 配置修改：
```yaml
server:
  port: 8080
```

访问地址：`http://localhost:8080/jsonrpc`

## 方法名格式

支持两种方法名格式：

1. **带服务名：** `service.method` 或 `service/method`
   - 示例：`userService.getUser`
   - 解析为：service=`userService`, method=`getUser`

2. **不带服务名：** `method`
   - 示例：`getUser`
   - 解析为：service=`default`, method=`getUser`

## 错误码映射

框架错误码到 JSON-RPC 错误码的映射：

| 框架错误码 | JSON-RPC 错误码 | 说明 |
|-----------|----------------|------|
| BAD_REQUEST | -32602 | 无效参数 |
| NOT_FOUND | -32601 | 方法不存在 |
| PROTOCOL_ERROR | -32600 | 无效请求 |
| SERIALIZATION_ERROR | -32700 | 解析错误 |
| 其他错误 | -32603 | 内部错误 |

## 集成说明

### 1. 添加依赖

JSON-RPC 处理器已集成到框架中，无需额外依赖。

### 2. 配置 Bean

通过 `JsonRpcConfig` 自动配置：
```java
@Configuration
public class JsonRpcConfig {
    @Bean
    public JsonRpcRequestProcessor jsonRpcRequestProcessor(ProtocolAdapter protocolAdapter) {
        return new JsonRpcRequestProcessor(protocolAdapter);
    }
    
    @Bean
    public JsonRpcProtocolHandler jsonRpcProtocolHandler(JsonRpcRequestProcessor requestProcessor) {
        return new JsonRpcProtocolHandler(requestProcessor);
    }
}
```

### 3. 使用处理器

处理器会自动注册到 Spring MVC，处理 `/jsonrpc` 端点的请求。

## 测试

运行单元测试：
```bash
# Windows
mvnw test -Dtest=JsonRpcProtocolHandlerTest

# Linux
./mvnw test -Dtest=JsonRpcProtocolHandlerTest
```

## 性能考虑

- 支持批量请求以减少网络往返
- 通知不需要响应，提高吞吐量
- 与框架的连接池和缓存机制集成
- 支持异步处理（通过协议适配器）

## 安全性

- 输入验证：严格验证 JSON-RPC 格式
- 错误处理：不泄露敏感信息
- 支持认证和授权（通过框架的安全机制）
- 支持 TLS/SSL 加密传输

## 参考资料

- [JSON-RPC 2.0 规范](https://www.jsonrpc.org/specification)
- [框架设计文档](../../../../.kiro/specs/multi-language-communication-framework/design.md)
- [需求文档](../../../../.kiro/specs/multi-language-communication-framework/requirements.md)
