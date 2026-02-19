# 多语言通信框架 (Multi-Language Communication Framework)

支持 Java、Golang、PHP 三种语言的微服务通信框架，提供统一的 API、多协议支持和完整的可观测性。

## 功能特性

- **多语言 SDK**：Java / Golang / PHP，API 设计一致
- **外部协议**：REST、WebSocket、JSON-RPC 2.0、MQTT
- **内部协议**：gRPC、JSON-RPC、自定义二进制协议
- **服务注册与发现**：etcd（生产）/ 内存注册中心（开发/测试）
- **负载均衡**：轮询、随机、最少连接
- **容错机制**：重试（指数退避）、熔断器
- **可观测性**：结构化日志、Prometheus 指标、OpenTelemetry 追踪、健康检查
- **安全**：TLS、JWT 认证、API Key、RBAC

## 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| Java | 17+ |
| Maven | 3.8+ |
| Go | 1.21+ |
| PHP | 8.1+ |
| Docker | 20+ (可选) |

### 1. 启动基础设施（可选，需要 etcd）

**Docker 方式：**
```bash
docker-compose up -d
```

**原生方式（Windows）：**
```powershell
scripts\start-native.ps1
```

**原生方式（Linux）：**
```bash
scripts/start-native.sh
```

### 2. 运行所有测试

**Windows：**
```bat
scripts\test-all.bat
```

**Linux：**
```bash
scripts/test-all.sh
```

### 3. 运行性能测试

**Windows：**
```bat
scripts\perf-test.bat 1000
```

**Linux：**
```bash
scripts/perf-test.sh 1000
```

---

## Java SDK

```java
// 创建客户端
FrameworkClient client = new DefaultFrameworkClient(config);

// 同步调用
Response response = client.call("user-service", "getUser",
    Map.of("userId", "123"), Response.class);

// 异步调用
CompletableFuture<Response> future = client.callAsync(
    "user-service", "getUser", request, Response.class);

// 注册服务
client.registerService(serviceInfo);
```

详见 [java-sdk/](java-sdk/)

---

## Golang SDK

```go
// 创建客户端
client := client.NewDefaultFrameworkClient(config)

// 同步调用
resp, err := client.Call(ctx, "user-service", "getUser", request)

// 异步调用
ch := client.CallAsync(ctx, "user-service", "getUser", request)
result := <-ch

// 注册服务
err = client.RegisterService(ctx, serviceInfo)
```

详见 [golang-sdk/](golang-sdk/)

---

## PHP SDK

```php
// 创建客户端
$client = new DefaultFrameworkClient($registry);

// 注册服务
$client->registerService('user-service', function($method, $request) {
    return ['userId' => $request['userId'], 'name' => 'Alice'];
});

// 同步调用
$result = $client->call('user-service', 'getUser', ['userId' => '123']);

// 异步调用（返回 Promise）
$promise = $client->callAsync('user-service', 'getUser', $request);
```

详见 [php-sdk/](php-sdk/)

---

## 项目结构

```
.
├── java-sdk/          # Java SDK (Maven)
├── golang-sdk/        # Golang SDK (Go Modules)
├── php-sdk/           # PHP SDK (Composer)
├── proto/             # Protocol Buffers 定义
├── docker/            # Docker 配置（etcd、Prometheus、Mosquitto）
├── docs/              # 文档
│   ├── cross-language-type-mapping.md  # 跨语言类型映射规范
│   └── etcd-configuration.md          # etcd 配置指南
└── scripts/           # 构建、测试、运维脚本
```

## 跨语言类型映射

详见 [docs/cross-language-type-mapping.md](docs/cross-language-type-mapping.md)

| 通用类型 | Java | Golang | PHP | JSON |
|---------|------|--------|-----|------|
| 整数(32位) | int | int32 | int | number |
| 整数(64位) | long | int64 | int | number/string |
| 浮点数 | double | float64 | float | number |
| 字符串 | String | string | string | string |
| 布尔值 | boolean | bool | bool | boolean |
| 空值 | null | nil | null | null |
| 数组 | List\<T\> | []T | array | array |
| 映射 | Map\<String,V\> | map[string]V | array | object |

## 测试状态

| SDK | 测试数 | 状态 |
|-----|--------|------|
| Java | 162 | ✅ 全部通过 |
| Golang | 全部包 | ✅ 全部通过 |
| PHP | 57 | ✅ 全部通过 |
