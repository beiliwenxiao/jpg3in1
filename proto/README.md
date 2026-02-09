# Protocol Buffers 定义文件

本目录包含多语言通信框架的 Protocol Buffers 定义文件，用于定义跨语言的协议规范。

## 文件说明

### 1. common.proto
定义了框架的通用数据结构和类型：

- **消息类型** (`MessageType`): REQUEST, RESPONSE, ERROR, EVENT, STREAM_START, STREAM_DATA, STREAM_END
- **序列化格式** (`SerializationFormat`): JSON, PROTOBUF, MSGPACK, CUSTOM
- **内部协议类型** (`InternalProtocol`): GRPC, JSON_RPC, CUSTOM_PROTOCOL
- **统一消息格式** (`Message`): 包含 ID、类型、时间戳、追踪信息、源/目标服务、负载、头部、元数据
- **服务信息** (`ServiceInfo`): 服务的注册信息，包括 ID、名称、版本、语言、地址、端口、协议、元数据
- **错误模型** (`FrameworkError`, `ErrorCode`): 统一的错误表示，支持错误链追踪
- **健康检查** (`HealthCheckResponse`, `HealthStatus`): 服务健康状态

**验证需求**: 3.4, 3.5, 3.6

### 2. service.proto
定义了 gRPC 服务接口：

- **CallRequest/CallResponse**: 同步服务调用的请求和响应
- **StreamData**: 流式数据传输
- **FrameworkService**: gRPC 服务接口
  - `Call`: 同步调用
  - `Stream`: 服务端流式调用
  - `BiStream`: 双向流式调用
  - `HealthCheck`: 健康检查

**验证需求**: 3.1, 3.4（gRPC 的 Protocol Buffers 序列化支持）

### 3. jsonrpc.proto
定义了 JSON-RPC 2.0 协议格式：

- **JsonRpcRequest**: JSON-RPC 2.0 请求格式
  - jsonrpc: 版本号（必须是 "2.0"）
  - method: 方法名称
  - params: 参数（JSON 编码）
  - id: 请求 ID（可选，不存在则为通知）
  - trace_id/span_id: 分布式追踪信息
  
- **JsonRpcResponse**: JSON-RPC 2.0 响应格式
  - jsonrpc: 版本号
  - result: 成功结果
  - error: 错误对象
  - id: 请求 ID
  
- **JsonRpcError**: 错误对象
  - code: 错误码
  - message: 错误消息
  - data: 额外数据
  
- **JsonRpcBatchRequest/JsonRpcBatchResponse**: 批量请求/响应
- **JsonRpcNotification**: 通知（无需响应的请求）
- **JsonRpcErrorCode**: 标准错误码
  - PARSE_ERROR (-32700): 解析错误
  - INVALID_REQUEST (-32600): 无效请求
  - METHOD_NOT_FOUND (-32601): 方法不存在
  - INVALID_PARAMS (-32602): 无效参数
  - INTERNAL_ERROR (-32603): 内部错误

**参考规范**: [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)

**验证需求**: 3.2, 3.5（JSON-RPC 的 JSON 序列化支持）

### 4. custom_protocol.proto
定义了自定义二进制协议格式：

#### 核心概念
- **高性能**: 紧凑的二进制格式，低延迟
- **多路复用**: 支持多个流在同一连接上并发传输
- **流控制**: 窗口更新机制防止接收方过载
- **可扩展**: 支持压缩、加密、优先级等特性

#### 主要组件

**CustomFrame**: 协议帧
- header: 帧头（魔数、版本、类型、标志、流 ID、长度、序列号、时间戳）
- body: 帧体（实际数据）
- trailer: 帧尾（CRC32 校验和）

**FrameType**: 帧类型
- DATA: 数据帧
- PING/PONG: 心跳
- CLOSE: 关闭连接
- WINDOW_UPDATE: 流控制
- SETTINGS: 设置
- ERROR: 错误
- METADATA: 元数据

**FrameFlags**: 帧标志（位标志）
- END_STREAM: 结束流
- END_HEADERS: 结束头部
- COMPRESSED: 压缩
- ENCRYPTED: 加密
- PRIORITY: 优先级

**DataFrame**: 数据帧体
- service/method: 服务和方法名称
- data: 请求/响应数据
- metadata: 元数据
- trace_id/span_id: 追踪信息

**HandshakeRequest/HandshakeResponse**: 连接握手
- 协议版本协商
- 特性协商
- 认证

**CompressionType**: 压缩类型
- NONE, GZIP, SNAPPY, LZ4, ZSTD

**验证需求**: 3.3, 3.6（自定义协议的可扩展序列化机制）

## 代码生成

### 前置条件

1. 安装 Protocol Buffers 编译器（protoc）
   - 下载地址: https://github.com/protocolbuffers/protobuf/releases
   - 建议版本: 3.20.0 或更高

2. 安装各语言的 protoc 插件：

   **Java**:
   ```xml
   <!-- Maven 依赖 -->
   <dependency>
       <groupId>io.grpc</groupId>
       <artifactId>grpc-protobuf</artifactId>
       <version>1.50.0</version>
   </dependency>
   <dependency>
       <groupId>io.grpc</groupId>
       <artifactId>grpc-stub</artifactId>
       <version>1.50.0</version>
   </dependency>
   ```

   **Golang**:
   ```bash
   go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
   go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
   ```

   **PHP**:
   ```bash
   # 安装 grpc_php_plugin
   # 参考: https://grpc.io/docs/languages/php/quickstart/
   ```

### 生成代码

#### Windows (PowerShell)
```powershell
cd scripts
./generate-proto.ps1
```

#### Linux/macOS (Bash)
```bash
cd scripts
./generate-proto.sh
```

#### 手动生成

**Java**:
```bash
cd proto
protoc --java_out=../java-sdk/src/main/java \
       --grpc-java_out=../java-sdk/src/main/java \
       common.proto service.proto jsonrpc.proto custom_protocol.proto
```

**Golang**:
```bash
cd proto
protoc --go_out=../golang-sdk/proto \
       --go-grpc_out=../golang-sdk/proto \
       --go_opt=paths=source_relative \
       --go-grpc_opt=paths=source_relative \
       common.proto service.proto jsonrpc.proto custom_protocol.proto
```

**PHP**:
```bash
cd proto
protoc --php_out=../php-sdk/src/Proto \
       --grpc_out=../php-sdk/src/Proto \
       --plugin=protoc-gen-grpc=grpc_php_plugin \
       common.proto service.proto jsonrpc.proto custom_protocol.proto
```

## 生成的代码位置

- **Java**: `java-sdk/src/main/java/com/framework/proto/`
- **Golang**: `golang-sdk/proto/`
- **PHP**: `php-sdk/src/Proto/`

## 协议规范完整性检查

### ✅ Protocol Buffers 定义（需求 3.4）
- [x] 定义了统一的消息格式（Message）
- [x] 定义了服务接口（FrameworkService）
- [x] 支持同步调用、流式调用、双向流
- [x] 定义了服务信息和元数据
- [x] 定义了错误模型和错误码

### ✅ JSON-RPC 2.0 格式（需求 3.5）
- [x] 完整实现 JSON-RPC 2.0 规范
- [x] 支持请求/响应模式
- [x] 支持批量请求
- [x] 支持通知（无需响应）
- [x] 定义了标准错误码
- [x] 扩展了分布式追踪支持

### ✅ 自定义二进制协议（需求 3.6）
- [x] 定义了帧结构（帧头、帧体、帧尾）
- [x] 支持多路复用（流 ID）
- [x] 支持流控制（窗口更新）
- [x] 支持压缩和加密
- [x] 支持优先级和设置
- [x] 定义了握手协议
- [x] 支持完整性校验（CRC32）

## 使用示例

### gRPC 调用示例（Java）
```java
// 创建 gRPC 通道
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 50051)
    .usePlaintext()
    .build();

// 创建客户端存根
FrameworkServiceGrpc.FrameworkServiceBlockingStub stub = 
    FrameworkServiceGrpc.newBlockingStub(channel);

// 构建请求
CallRequest request = CallRequest.newBuilder()
    .setService("user-service")
    .setMethod("getUser")
    .setPayload(ByteString.copyFromUtf8("{\"id\":123}"))
    .setTimeout(5000)
    .build();

// 发起调用
CallResponse response = stub.call(request);
```

### JSON-RPC 2.0 示例
```json
// 请求
{
  "jsonrpc": "2.0",
  "method": "user.getUser",
  "params": {"id": 123},
  "id": "req-001",
  "trace_id": "trace-123",
  "span_id": "span-456"
}

// 响应
{
  "jsonrpc": "2.0",
  "result": {"id": 123, "name": "John Doe"},
  "id": "req-001",
  "trace_id": "trace-123",
  "span_id": "span-456"
}

// 错误响应
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32601,
    "message": "Method not found",
    "data": {"method": "user.invalidMethod"}
  },
  "id": "req-001"
}
```

### 自定义协议帧结构
```
+------------------+
|   Frame Header   |  (固定大小)
|   - Magic        |  4 bytes (0x46524D57)
|   - Version      |  4 bytes
|   - Type         |  4 bytes
|   - Flags        |  4 bytes
|   - Stream ID    |  4 bytes
|   - Body Length  |  4 bytes
|   - Sequence     |  8 bytes
|   - Timestamp    |  8 bytes
+------------------+
|   Frame Body     |  (可变大小)
|   - Data         |
+------------------+
|   Frame Trailer  |  (可选)
|   - Checksum     |  4 bytes (CRC32)
+------------------+
```

## 协议选择指南

### gRPC (Protocol Buffers)
**适用场景**:
- 服务间高性能通信
- 需要强类型定义
- 需要流式传输
- 跨语言互操作

**优点**:
- 高性能（二进制编码）
- 强类型安全
- 自动生成代码
- 内置流式支持

**缺点**:
- 不易调试（二进制格式）
- 需要 .proto 文件

### JSON-RPC 2.0
**适用场景**:
- 需要人类可读的协议
- 简单的请求/响应模式
- 与 Web 前端集成
- 调试和测试

**优点**:
- 人类可读（JSON 格式）
- 简单易用
- 广泛支持
- 易于调试

**缺点**:
- 性能较低（文本编码）
- 数据体积较大
- 无强类型检查

### 自定义二进制协议
**适用场景**:
- 极致性能要求
- 需要自定义特性
- 复杂的流控制
- 特殊的网络环境

**优点**:
- 最高性能
- 完全可控
- 紧凑的数据格式
- 支持高级特性（多路复用、流控制）

**缺点**:
- 实现复杂
- 调试困难
- 需要自定义编解码器

## 扩展性

### 添加新的消息类型
1. 在 `common.proto` 中添加新的 enum 值
2. 重新生成代码
3. 在各语言 SDK 中实现处理逻辑

### 添加新的服务方法
1. 在 `service.proto` 中添加新的 rpc 方法
2. 重新生成代码
3. 实现服务端和客户端逻辑

### 自定义序列化格式
1. 在 `SerializationFormat` enum 中添加新值
2. 实现对应的序列化器
3. 在协议适配器中注册

## 版本兼容性

### 向后兼容规则
1. 不要更改已有字段的编号
2. 不要删除已有字段
3. 新增字段使用新的编号
4. 使用 `reserved` 关键字保留已删除的字段编号

### 版本升级策略
1. 在 `FrameHeader.version` 中标识协议版本
2. 握手时协商支持的版本
3. 保持多版本兼容性

## 参考资料

- [Protocol Buffers 官方文档](https://developers.google.com/protocol-buffers)
- [gRPC 官方文档](https://grpc.io/docs/)
- [JSON-RPC 2.0 规范](https://www.jsonrpc.org/specification)
- [HTTP/2 规范](https://httpwg.org/specs/rfc7540.html) (自定义协议参考)

## 维护者

多语言通信框架开发团队

## 许可证

[根据项目许可证]
