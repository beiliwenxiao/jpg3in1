# JSON-RPC 内部协议快速开始

## 快速示例

### 1. 启动服务端（5 行代码）

```java
JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();
handler.start(9091); // 启动服务端，监听 9091 端口

// 注册服务
handler.registerService("UserService", (method, payload, headers) -> {
    // 处理请求并返回响应
    return "{\"id\":1,\"name\":\"张三\"}".getBytes();
});
```

### 2. 客户端调用（5 行代码）

```java
JsonRpcInternalProtocolHandler client = new JsonRpcInternalProtocolHandler();
client.start(0); // 仅客户端模式

ServiceInfo serviceInfo = new ServiceInfo();
serviceInfo.setName("UserService");
serviceInfo.setAddress("localhost");
serviceInfo.setPort(9091);

// 同步调用
Map result = client.call(serviceInfo, "getUser", request, Map.class);
```

## 核心概念

### 方法命名规则

JSON-RPC 方法名格式：`服务名.方法名`

例如：
- `UserService.getUser`
- `OrderService.createOrder`
- `PaymentService.processPayment`

### 请求响应流程

```
客户端                                    服务端
  |                                        |
  |  1. 构建请求对象                        |
  |------------------------------------>  |
  |  JSON-RPC Request                     |
  |  {                                    |
  |    "jsonrpc": "2.0",                  |
  |    "method": "UserService.getUser",   |
  |    "params": {...},                   |
  |    "id": 1                            |
  |  }                                    |
  |                                       |
  |                    2. 解析请求         |
  |                    3. 路由到服务处理器  |
  |                    4. 执行业务逻辑     |
  |                    5. 构建响应         |
  |  <------------------------------------  |
  |  JSON-RPC Response                    |
  |  {                                    |
  |    "jsonrpc": "2.0",                  |
  |    "result": {...},                   |
  |    "id": 1                            |
  |  }                                    |
  |                                       |
  |  6. 反序列化响应                       |
  |                                       |
```

## 完整示例

### 服务端完整代码

```java
import com.framework.protocol.internal.jsonrpc.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

public class ServerExample {
    public static void main(String[] args) throws Exception {
        // 创建协议处理器
        JsonRpcInternalProtocolHandler handler = new JsonRpcInternalProtocolHandler();
        
        // 启动服务端
        handler.start(9091);
        
        // 注册用户服务
        handler.registerService("UserService", new JsonRpcInternalServer.ServiceHandler() {
            private ObjectMapper mapper = new ObjectMapper();
            
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                try {
                    if ("getUser".equals(method)) {
                        // 解析请求
                        Map request = mapper.readValue(payload, Map.class);
                        int userId = (Integer) request.get("userId");
                        
                        // 构建响应
                        Map<String, Object> user = new HashMap<>();
                        user.put("id", userId);
                        user.put("name", "用户" + userId);
                        user.put("email", "user" + userId + "@example.com");
                        
                        return mapper.writeValueAsBytes(user);
                    }
                    
                    throw new RuntimeException("未知方法: " + method);
                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        
        System.out.println("服务端已启动，监听端口 9091");
        
        // 保持运行
        Thread.currentThread().join();
    }
}
```

### 客户端完整代码

```java
import com.framework.protocol.internal.jsonrpc.*;
import com.framework.model.ServiceInfo;
import java.util.HashMap;
import java.util.Map;

public class ClientExample {
    public static void main(String[] args) throws Exception {
        // 创建协议处理器（仅客户端）
        JsonRpcInternalProtocolHandler client = new JsonRpcInternalProtocolHandler();
        client.start(0);
        
        // 构建服务信息
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName("UserService");
        serviceInfo.setAddress("localhost");
        serviceInfo.setPort(9091);
        
        try {
            // 构建请求
            Map<String, Object> request = new HashMap<>();
            request.put("userId", 1);
            
            // 同步调用
            Map response = client.call(serviceInfo, "getUser", request, Map.class);
            
            System.out.println("用户信息: " + response);
            // 输出: 用户信息: {id=1, name=用户1, email=user1@example.com}
            
        } finally {
            client.shutdown();
        }
    }
}
```

## 异步调用示例

```java
// 异步调用
CompletableFuture<Map> future = client.callAsync(
    serviceInfo, 
    "getUser", 
    request, 
    Map.class
);

// 处理结果
future.thenAccept(response -> {
    System.out.println("异步获取用户信息: " + response);
}).exceptionally(e -> {
    System.err.println("调用失败: " + e.getMessage());
    return null;
});

// 等待完成
future.join();
```

## 常见问题

### Q1: 如何处理错误？

服务处理器中抛出异常会自动转换为 JSON-RPC 错误响应：

```java
handler.registerService("UserService", (method, payload, headers) -> {
    if (!"getUser".equals(method)) {
        throw new FrameworkException(ErrorCode.NOT_FOUND, "方法未找到");
    }
    // ... 处理逻辑
});
```

### Q2: 如何传递复杂对象？

使用 Jackson 自动序列化/反序列化：

```java
// 服务端
User user = new User(1, "张三", "zhangsan@example.com");
return mapper.writeValueAsBytes(user);

// 客户端
User user = client.call(serviceInfo, "getUser", request, User.class);
```

### Q3: 如何配置超时时间？

在调用时指定超时参数（目前在内部实现中固定为 30 秒）：

```java
// 未来可以通过配置类设置
JsonRpcInternalConfig config = new JsonRpcInternalConfig();
config.getClient().setReadTimeout(60000); // 60 秒
```

### Q4: 支持批量请求吗？

当前版本不支持批量请求，每次调用处理一个请求。

### Q5: 如何监控服务健康状态？

使用内置的健康检查：

```java
JsonRpcInternalClient client = new JsonRpcInternalClient("localhost", 9091);
client.start();
boolean healthy = client.healthCheck();
```

## 下一步

- 查看 [README.md](README.md) 了解详细文档
- 查看 [JsonRpcInternalUsageExample.java](JsonRpcInternalUsageExample.java) 了解更多示例
- 查看 [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) 了解实现细节

## 性能提示

1. **复用连接**: 协议处理器会自动维护连接池，避免重复创建客户端
2. **异步调用**: 对于高并发场景，使用 `callAsync` 提高吞吐量
3. **合理设置超时**: 根据业务特点调整超时时间
4. **资源释放**: 使用完毕后调用 `shutdown()` 释放资源
