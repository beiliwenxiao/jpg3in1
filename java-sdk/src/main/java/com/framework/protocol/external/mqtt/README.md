# MQTT 协议处理器

基于 Eclipse Paho MQTT 客户端实现的 MQTT 协议处理器。

## 功能特性

- ✅ 支持 MQTT 消息的接收和发布
- ✅ 支持 QoS 0、1、2 三种服务质量级别
- ✅ 支持主题订阅和取消订阅
- ✅ 支持通配符主题匹配（+ 和 #）
- ✅ 支持自定义主题处理器
- ✅ 支持自动重连
- ✅ 支持用户名密码认证
- ✅ 支持 JSON 和纯文本消息格式
- ✅ 支持请求-响应模式

## 配置说明

在 `application.yml` 或 `application.properties` 中配置 MQTT 参数：

### YAML 配置示例

```yaml
framework:
  mqtt:
    enabled: true                              # 是否启用 MQTT 处理器
    broker-url: tcp://localhost:1883           # MQTT Broker 地址
    client-id: framework-mqtt-client           # 客户端 ID（会自动添加随机后缀）
    username: admin                            # 用户名（可选）
    password: password                         # 密码（可选）
    connection-timeout: 30                     # 连接超时时间（秒）
    keep-alive-interval: 60                    # 保持连接时间（秒）
    automatic-reconnect: true                  # 是否自动重连
    clean-session: true                        # 是否清除会话
    default-qos: 1                             # 默认 QoS 级别（0, 1, 2）
    subscribe-topics:                          # 订阅的主题列表
      - framework/+/+                          # 订阅所有服务和方法
      - system/events/#                        # 订阅所有系统事件
```

### Properties 配置示例

```properties
framework.mqtt.enabled=true
framework.mqtt.broker-url=tcp://localhost:1883
framework.mqtt.client-id=framework-mqtt-client
framework.mqtt.username=admin
framework.mqtt.password=password
framework.mqtt.connection-timeout=30
framework.mqtt.keep-alive-interval=60
framework.mqtt.automatic-reconnect=true
framework.mqtt.clean-session=true
framework.mqtt.default-qos=1
framework.mqtt.subscribe-topics[0]=framework/+/+
framework.mqtt.subscribe-topics[1]=system/events/#
```

## 使用方法

### 1. 发布消息

```java
@Autowired
private MqttProtocolHandler mqttHandler;

// 发布简单消息
mqttHandler.publish("framework/user/create", "{\"name\":\"张三\",\"age\":25}");

// 发布消息并指定 QoS 和保留标志
mqttHandler.publish("framework/user/create", "{\"name\":\"张三\",\"age\":25}", 2, true);
```

### 2. 订阅主题

```java
// 订阅主题（使用默认 QoS）
mqttHandler.subscribe("framework/user/+", 1);

// 取消订阅
mqttHandler.unsubscribe("framework/user/+");
```

### 3. 注册自定义主题处理器

```java
// 注册处理器
mqttHandler.registerTopicHandler("framework/user/+", (topic, payload, qos, retained) -> {
    System.out.println("收到消息: topic=" + topic + ", payload=" + payload);
    // 自定义处理逻辑
});

// 移除处理器
mqttHandler.removeTopicHandler("framework/user/+");
```

### 4. 使用 Lambda 表达式注册处理器

```java
mqttHandler.registerTopicHandler("system/events/#", 
    (topic, payload, qos, retained) -> {
        logger.info("系统事件: {}", payload);
        // 处理系统事件
    }
);
```

## 消息格式

### JSON 格式消息（推荐）

```json
{
  "service": "user",
  "method": "create",
  "body": {
    "name": "张三",
    "age": 25
  },
  "responseTopic": "framework/user/create/response"
}
```

字段说明：
- `service`: 服务名称（必填）
- `method`: 方法名称（必填）
- `body`: 请求体（可选）
- `responseTopic`: 响应主题（可选，用于请求-响应模式）

### 纯文本消息

如果消息不是 JSON 格式，处理器会将整个消息作为 body，并从主题中提取服务和方法信息。

主题格式：`framework/{service}/{method}`

例如：
- 主题：`framework/user/create`
- 消息：`{"name":"张三","age":25}`

## 主题通配符

MQTT 支持两种通配符：

- `+`：单层通配符，匹配一个层级
  - 例如：`framework/+/create` 匹配 `framework/user/create`、`framework/order/create`
  
- `#`：多层通配符，匹配多个层级
  - 例如：`framework/#` 匹配 `framework/user/create`、`framework/user/update/profile`

## QoS 级别

- **QoS 0**：最多一次（At most once）
  - 消息可能丢失，不会重复
  - 性能最高，适合不重要的数据

- **QoS 1**：至少一次（At least once）
  - 消息保证到达，但可能重复
  - 平衡性能和可靠性，推荐使用

- **QoS 2**：恰好一次（Exactly once）
  - 消息保证到达且不重复
  - 性能最低，适合关键数据

## 请求-响应模式

MQTT 本身是发布-订阅模式，但可以通过响应主题实现请求-响应：

### 发送请求

```java
String requestTopic = "framework/user/query";
String responseTopic = "framework/user/query/response/" + UUID.randomUUID();

// 先订阅响应主题
mqttHandler.subscribe(responseTopic, 1);

// 注册响应处理器
mqttHandler.registerTopicHandler(responseTopic, (topic, payload, qos, retained) -> {
    System.out.println("收到响应: " + payload);
    // 处理响应后取消订阅
    mqttHandler.unsubscribe(responseTopic);
});

// 发送请求
String request = String.format(
    "{\"service\":\"user\",\"method\":\"query\",\"body\":{\"id\":123},\"responseTopic\":\"%s\"}",
    responseTopic
);
mqttHandler.publish(requestTopic, request);
```

## 连接状态检查

```java
// 检查是否已连接
boolean connected = mqttHandler.isConnected();

// 获取客户端 ID
String clientId = mqttHandler.getClientId();
```

## 错误处理

处理器会自动处理以下错误：

1. **连接丢失**：如果启用了 `automatic-reconnect`，客户端会自动重连
2. **消息处理失败**：记录错误日志，不影响其他消息的处理
3. **订阅失败**：抛出 `FrameworkException` 异常

## 最佳实践

1. **使用 JSON 格式消息**：便于解析和处理
2. **合理设置 QoS**：根据业务重要性选择合适的 QoS 级别
3. **使用通配符订阅**：减少订阅数量，提高性能
4. **启用自动重连**：提高系统可靠性
5. **使用唯一的客户端 ID**：避免客户端冲突（框架会自动添加随机后缀）
6. **清理响应主题**：请求-响应模式中，处理完响应后及时取消订阅

## 安全建议

1. **使用 SSL/TLS**：生产环境使用 `ssl://` 协议
2. **启用认证**：配置用户名和密码
3. **限制主题权限**：在 MQTT Broker 中配置 ACL
4. **加密敏感数据**：对敏感消息内容进行加密

## 兼容性

- 支持 MQTT 3.1 和 3.1.1 协议
- 兼容主流 MQTT Broker：
  - Eclipse Mosquitto
  - EMQ X
  - HiveMQ
  - AWS IoT Core
  - Azure IoT Hub

## 验证需求

**验证需求: 2.4** - WHEN 客户端通过 MQTT 发布消息时，THE Framework SHALL 接收并处理 MQTT 消息
