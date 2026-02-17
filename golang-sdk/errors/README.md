# 错误处理模块

## 概述

错误处理模块提供统一的错误模型和错误码映射，支持跨协议的错误转换和错误链追踪。

## 核心组件

### ErrorCode

统一的错误码枚举，包括：

- **客户端错误 (4xx)**: BadRequest, Unauthorized, Forbidden, NotFound, Timeout
- **服务端错误 (5xx)**: InternalError, NotImplemented, ServiceUnavailable
- **框架错误 (6xx)**: ProtocolError, SerializationError, RoutingError, ConnectionError

### FrameworkError

框架统一错误类型，提供：

- 标准化的错误响应格式
- 错误链追踪
- 协议错误码映射

## 使用示例

### 创建错误

```go
import "github.com/framework/golang-sdk/errors"

// 创建简单错误
err := errors.NewFrameworkError(errors.BadRequest, "无效的请求参数")

// 创建带详情的错误
err := errors.NewFrameworkErrorWithDetails(
    errors.NotFound, 
    "用户未找到", 
    "用户ID: 12345",
)

// 创建带原因的错误
cause := someError()
err := errors.NewFrameworkErrorWithCause(
    errors.InternalError, 
    "操作失败", 
    cause,
)
```

### 错误码映射

```go
// 从 HTTP 状态码映射
code := errors.FromHTTPStatus(404) // NotFound

// 从 gRPC 状态码映射
code := errors.FromGRPCStatus(5) // NotFound

// 从 JSON-RPC 错误码映射
code := errors.FromJSONRPCCode(-32601) // NotFound

// 转换为 HTTP 状态码
httpStatus := errors.NotFound.ToHTTPStatus() // 404
```

### 错误响应

```go
err := errors.NewFrameworkError(errors.BadRequest, "请求错误")
response := err.ToErrorResponse()
// 返回 map[string]interface{} 包含:
// - code: 错误码
// - error: 错误名称
// - message: 错误消息
// - timestamp: 时间戳
// - errorChain: 错误链（如果有）
```

## 验证需求

- **需求 8.1**: 协议错误标准化响应
- **需求 8.2**: 序列化错误处理
- **需求 8.5**: 错误码统一映射
- **需求 8.6**: 错误链式追踪
