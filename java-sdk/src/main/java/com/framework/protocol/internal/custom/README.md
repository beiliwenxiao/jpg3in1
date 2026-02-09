# 自定义二进制协议实现

## 概述

自定义二进制协议是一个高性能、低延迟的内部通信协议，专为服务间通信设计。它使用紧凑的二进制格式，支持多路复用、流控制和完整性校验。

**验证需求: 3.3, 3.6**

## 特性

- ✅ **高性能**: 基于 Netty 的异步非阻塞 I/O
- ✅ **紧凑格式**: 二进制编码，减少网络传输开销
- ✅ **完整性校验**: CRC32 校验和确保数据完整性
- ✅ **多路复用**: 支持在单个连接上并发多个请求
- ✅ **流控制**: 支持窗口更新和背压机制
- ✅ **可扩展**: 支持自定义帧类型和元数据

## 协议格式

### 帧结构

每个帧由三部分组成：

```
+----------------+----------------+----------------+
|   帧头 (40B)   |   帧体 (变长)   |   帧尾 (4B)    |
+----------------+----------------+----------------+
```

### 帧头格式 (40 字节)

| 字段 | 类型 | 长度 | 说明 |
|------|------|------|------|
| 魔数 | fixed32 | 4B | 0x46524D57 ("FRMW") |
| 版本 | uint32 | 4B | 协议版本号 |
| 帧类型 | uint32 | 4B | 帧类型枚举值 |
| 帧标志 | uint32 | 4B | 帧标志位 |
| 流 ID | uint32 | 4B | 流标识符 |
| 帧体长度 | uint32 | 4B | 帧体字节数 |
| 序列号 | uint64 | 8B | 请求序列号 |
| 时间戳 | int64 | 8B | 毫秒时间戳 |

### 帧类型

- `DATA`: 数据帧，携带业务数据
- `PING`: 心跳请求
- `PONG`: 心跳响应
- `CLOSE`: 关闭连接
- `WINDOW_UPDATE`: 窗口更新
- `SETTINGS`: 设置参数
- `ERROR`: 错误信息
- `METADATA`: 元数据

### 帧尾格式 (4 字节)

| 字段 | 类型 | 长度 | 说明 |
|------|------|------|------|
| 校验和 | fixed32 | 4B | CRC32 校验和 |

## 核心组件

### 1. CustomProtocolEncoder

编码器，将 `CustomFrame` 对象编码为二进制数据。

**功能**:
- 写入帧头（魔数、版本、类型等）
- 写入帧体数据
- 计算并写入 CRC32 校验和

### 2. CustomProtocolDecoder

解码器，将二进制数据解码为 `CustomFrame` 对象。

**功能**:
- 验证魔数和协议版本
- 读取帧头信息
- 读取帧体数据
- 验证 CRC32 校验和

### 3. CustomProtocolClient

客户端实现，提供同步和异步调用功能。

**功能**:
- 连接服务端
- 发送数据帧
- 接收响应帧
- 健康检查（Ping/Pong）
- 请求超时处理

### 4. CustomProtocolServer

服务端实现，处理客户端请求。

**功能**:
- 监听端口
- 接收客户端连接
- 处理数据帧
- 服务注册和路由
- 错误处理

### 5. CustomProtocolHandler

协议处理器，统一管理客户端和服务端。

**功能**:
- 启动/关闭协议处理器
- 管理客户端连接池
- 服务注册和调用
- 序列化/反序列化

### 6. CustomProtocolConfig

配置类，提供协议配置选项。

**配置项**:
- 服务端端口和启用状态
- 连接超时和请求超时
- 连接池参数
- 协议参数（最大帧大小、并发流数等）
- 性能参数（工作线程数等）

## 使用方法

### 1. 启动服务端

```java
// 创建协议处理器
CustomProtocolHandler handler = new CustomProtocolHandler();

// 启动服务端（监听 9090 端口）
handler.start(9090);

// 注册服务处理器
handler.registerService("UserService", new CustomProtocolServer.ServiceHandler() {
    @Override
    public byte[] handle(String method, byte[] data, Map<String, String> metadata) {
        // 处理业务逻辑
        return processRequest(method, data);
    }
});
```

### 2. 客户端同步调用

```java
// 创建协议处理器（仅客户端模式）
CustomProtocolHandler handler = new CustomProtocolHandler();
handler.start(0); // 端口为 0 表示不启动服务端

// 创建服务信息
ServiceInfo serviceInfo = new ServiceInfo();
serviceInfo.setName("UserService");
serviceInfo.setAddress("localhost");
serviceInfo.setPort(9090);

// 同步调用
String response = handler.call(
    serviceInfo,
    "getUser",
    "user123",
    String.class
);
```

### 3. 客户端异步调用

```java
// 异步调用
CompletableFuture<String> future = handler.callAsync(
    serviceInfo,
    "getUser",
    "user123",
    String.class
);

// 处理响应
future.thenAccept(response -> {
    System.out.println("响应: " + response);
}).exceptionally(throwable -> {
    System.err.println("调用失败: " + throwable.getMessage());
    return null;
});
```

### 4. 健康检查

```java
// 执行健康检查
boolean healthy = handler.healthCheck(serviceInfo);
System.out.println("服务健康状态: " + (healthy ? "健康" : "不健康"));
```

## 性能特性

### 1. 异步非阻塞 I/O

使用 Netty 的事件驱动模型，支持高并发连接和请求。

### 2. 连接复用

客户端维护连接池，复用 TCP 连接，减少连接建立开销。

### 3. 零拷贝

使用 Netty 的 `ByteBuf`，支持零拷贝操作，提高性能。

### 4. 紧凑编码

二进制格式比 JSON 更紧凑，减少网络传输量。

### 5. 批量处理

支持批量发送和接收，提高吞吐量。

## 安全性

### 1. 完整性校验

使用 CRC32 校验和验证数据完整性，防止数据损坏。

### 2. 协议验证

验证魔数和协议版本，防止协议混淆。

### 3. 帧大小限制

限制最大帧大小（默认 16MB），防止内存溢出攻击。

### 4. 超时保护

设置连接超时和请求超时，防止资源耗尽。

## 扩展性

### 1. 自定义帧类型

可以扩展新的帧类型，支持特定业务需求。

### 2. 自定义元数据

数据帧支持元数据字段，可以传递额外信息。

### 3. 压缩支持

可以集成压缩算法（如 Gzip、Snappy），减少传输量。

### 4. 加密支持

可以集成加密算法（如 TLS），保护数据安全。

## 与其他协议对比

| 特性 | 自定义协议 | gRPC | JSON-RPC |
|------|-----------|------|----------|
| 编码格式 | 二进制 | Protobuf | JSON |
| 性能 | 高 | 高 | 中 |
| 可读性 | 低 | 低 | 高 |
| 跨语言 | 需实现 | 原生支持 | 原生支持 |
| 流式支持 | 支持 | 支持 | 不支持 |
| 学习曲线 | 陡峭 | 中等 | 平缓 |

## 最佳实践

### 1. 连接管理

- 使用连接池复用连接
- 设置合理的空闲超时
- 实现连接健康检查

### 2. 错误处理

- 捕获并处理所有异常
- 返回明确的错误信息
- 实现重试机制

### 3. 性能优化

- 调整工作线程数
- 启用 TCP_NODELAY
- 使用批量操作

### 4. 监控和日志

- 记录关键操作日志
- 收集性能指标
- 实现分布式追踪

## 故障排查

### 1. 连接失败

- 检查服务端是否启动
- 检查网络连接
- 检查防火墙设置

### 2. 请求超时

- 增加超时时间
- 检查服务端性能
- 检查网络延迟

### 3. 校验和错误

- 检查网络质量
- 检查编解码器实现
- 检查数据传输过程

### 4. 内存溢出

- 限制最大帧大小
- 检查连接池配置
- 检查内存泄漏

## 未来改进

- [ ] 支持流式传输
- [ ] 支持双向流
- [ ] 集成压缩算法
- [ ] 集成加密算法
- [ ] 支持服务发现
- [ ] 支持负载均衡
- [ ] 完善配置管理
- [ ] 添加性能监控

## 参考资料

- [Netty 官方文档](https://netty.io/wiki/)
- [Protocol Buffers](https://developers.google.com/protocol-buffers)
- [HTTP/2 规范](https://http2.github.io/)
- [gRPC 协议](https://grpc.io/docs/)
