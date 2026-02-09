# 外部协议处理属性测试

## 概述

本目录包含外部协议处理的基于属性的测试（Property-Based Testing），使用 jqwik 框架验证外部协议处理的正确性。

## 测试文件

### ExternalProtocolPropertyTest.java

外部协议处理的属性测试，验证以下属性：

#### 属性 3: 外部协议处理完整性
**验证需求: 2.1, 2.2, 2.3, 2.4**

对于任意有效的外部协议请求（REST、WebSocket、JSON-RPC、MQTT），框架应该能够正确解析、处理并返回符合协议规范的响应。

测试方法：
- `externalProtocolHandlingCompleteness()` - 主测试方法
- `restProtocolRoundTripConsistency()` - REST 协议往返一致性扩展测试

#### 属性 4: HTTP 方法支持完整性
**验证需求: 2.5**

对于任意标准 HTTP 方法（GET、POST、PUT、DELETE、PATCH），框架应该能够正确处理该方法的请求。

测试方法：
- `httpMethodSupportCompleteness()` - 主测试方法
- `httpMethodSemanticConsistency()` - HTTP 方法语义一致性扩展测试

#### 属性 5: WebSocket 消息格式支持
**验证需求: 2.6**

对于任意 WebSocket 消息（文本或二进制），框架应该能够正确接收和处理。

测试方法：
- `webSocketMessageFormatSupport()` - 主测试方法
- `webSocketMessageTypePreservation()` - WebSocket 消息类型保持扩展测试

## 测试配置

- **测试框架**: jqwik 1.8.2
- **迭代次数**: 每个属性测试运行 100 次
- **标签格式**: `Feature: multi-language-communication-framework, Property {number}: {property_text}`

## 数据生成器

测试使用以下数据生成器：

1. **validProtocol()** - 生成有效的协议类型（REST、WebSocket、JSON-RPC、MQTT）
2. **validHttpMethod()** - 生成有效的 HTTP 方法（GET、POST、PUT、DELETE、PATCH）
3. **validMessageType()** - 生成有效的消息类型（text、binary）
4. **validRequestBody()** - 生成有效的请求体（字符串、数字、Map）

## 运行测试

### 使用 Maven

```bash
# 运行所有测试
mvn test

# 只运行属性测试
mvn test -Dtest=ExternalProtocolPropertyTest

# 运行特定的属性测试
mvn test -Dtest=ExternalProtocolPropertyTest#externalProtocolHandlingCompleteness
```

### 使用 IDE

在 IntelliJ IDEA 或 Eclipse 中：
1. 右键点击测试类或测试方法
2. 选择 "Run" 或 "Debug"

## 测试覆盖

这些属性测试覆盖了以下场景：

### REST 协议
- 所有标准 HTTP 方法（GET、POST、PUT、DELETE、PATCH）
- 各种请求体类型（字符串、数字、Map）
- 请求响应往返一致性
- HTTP 方法语义一致性

### WebSocket 协议
- 文本消息处理
- 二进制消息处理
- 消息类型保持
- 双向通信

### JSON-RPC 协议
- JSON-RPC 2.0 规范兼容性
- 请求解析和响应生成
- 各种请求体类型

### MQTT 协议
- MQTT 消息接收和处理
- 主题订阅和发布
- 各种消息格式

## 注意事项

1. **模拟依赖**: 测试使用 Mockito 模拟请求处理器，避免依赖实际的网络连接和外部服务
2. **隔离性**: 每个测试都是独立的，不依赖其他测试的状态
3. **确定性**: 虽然使用随机生成的数据，但 jqwik 会记录失败的测试用例，便于重现和调试
4. **性能**: 每个属性测试运行 100 次迭代，总体测试时间约 10-30 秒

## 失败处理

如果属性测试失败，jqwik 会：
1. 显示导致失败的具体输入值
2. 尝试缩小（shrink）失败用例到最小示例
3. 保存失败用例以便重现

失败示例：
```
Property [externalProtocolHandlingCompleteness] failed with sample:
  protocol: "REST"
  service: "userService"
  method: "getUser"
  body: {"id": "123"}
```

## 扩展测试

如需添加新的属性测试：

1. 在 `ExternalProtocolPropertyTest` 类中添加新的测试方法
2. 使用 `@Property` 注解标记
3. 使用 `@Label` 注解添加描述性标签
4. 在注释中说明验证的需求编号
5. 确保测试运行至少 100 次迭代

示例：
```java
@Property(tries = 100)
@Label("Feature: multi-language-communication-framework, Property X: 新属性描述")
void newPropertyTest(@ForAll String param) {
    // 测试逻辑
}
```

## 相关文档

- [jqwik 用户指南](https://jqwik.net/docs/current/user-guide.html)
- [设计文档](../../../../.kiro/specs/multi-language-communication-framework/design.md)
- [需求文档](../../../../.kiro/specs/multi-language-communication-framework/requirements.md)
- [任务列表](../../../../.kiro/specs/multi-language-communication-framework/tasks.md)
