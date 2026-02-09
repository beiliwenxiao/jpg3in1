package com.framework.protocol.adapter;

import com.framework.exception.FrameworkException;
import com.framework.protocol.model.ExternalRequest;
import com.framework.protocol.model.ExternalResponse;
import com.framework.protocol.model.InternalRequest;
import com.framework.protocol.model.InternalResponse;
import com.framework.protocol.router.DefaultMessageRouter;
import com.framework.protocol.router.MessageRouter;
import com.framework.protocol.router.RoutingRule;
import com.framework.protocol.router.ServiceEndpoint;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议转换和路由的属性测试
 * 
 * Feature: multi-language-communication-framework
 * 
 * 测试属性：
 * - 属性 9: 协议转换往返一致性
 * - 属性 10: 消息路由正确性
 * - 属性 11: 协议语义一致性
 * 
 * 验证需求: 4.1, 4.2, 4.3, 4.5
 */
class ProtocolAdapterPropertyTest {

    private final DefaultProtocolAdapter adapter = new DefaultProtocolAdapter();

    // ==================== 属性 9: 协议转换往返一致性 ====================

    /**
     * 属性 9: 协议转换往返一致性
     *
     * 对于任意外部协议消息，转换为内部协议再转换回外部协议应该保持语义等价性。
     * 即: service、method、body 数据在往返转换后保持一致。
     *
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 9: 协议转换往返一致性")
    void protocolConversionRoundTrip(
            @ForAll("supportedProtocol") String protocol,
            @ForAll @NotBlank String service,
            @ForAll @NotBlank String method,
            @ForAll("simpleBody") Map<String, String> body) {

        // 构建外部请求
        ExternalRequest extReq = new ExternalRequest();
        extReq.setProtocol(protocol);
        extReq.setService(service);
        extReq.setMethod(method);
        extReq.setBody(body);
        extReq.setHeaders(new HashMap<>());
        if ("REST".equals(protocol)) {
            extReq.setHttpMethod("POST");
        }

        // 外部 -> 内部
        InternalRequest intReq = adapter.transformRequest(extReq);

        // 验证内部请求保留了关键信息
        assertNotNull(intReq, "内部请求不应为空");
        assertEquals(service, intReq.getService(), "服务名称应保持一致");
        assertEquals(method, intReq.getMethod(), "方法名称应保持一致");
        assertNotNull(intReq.getPayload(), "payload 不应为空");
        assertNotNull(intReq.getTraceId(), "traceId 不应为空");
        assertEquals(protocol, intReq.getSourceProtocol(), "来源协议应正确记录");

        // 构建内部响应（模拟服务处理后返回原始 payload）
        InternalResponse intResp = new InternalResponse();
        intResp.setSuccess(true);
        intResp.setPayload(intReq.getPayload()); // 返回相同的 payload
        intResp.setSourceProtocol(intReq.getSourceProtocol());
        intResp.setMessageType(intReq.getMessageType());
        intResp.setHeaders(new HashMap<>());

        // 内部 -> 外部
        ExternalResponse extResp = adapter.transformResponse(intResp, extReq);

        // 验证往返一致性
        assertNotNull(extResp, "外部响应不应为空");
        assertEquals(200, extResp.getStatusCode(), "成功响应状态码应为 200");
        assertNotNull(extResp.getBody(), "响应体不应为空");
        assertEquals(protocol, extResp.getProtocol(), "响应协议应与请求协议一致");

        // 验证 body 数据往返一致性
        if (extResp.getBody() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) extResp.getBody();
            // JSON-RPC 响应包装在 result 字段中
            if ("JSON-RPC".equals(protocol)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> jsonRpcBody = (Map<String, Object>) extResp.getBody();
                Object result = jsonRpcBody.get("result");
                assertNotNull(result, "JSON-RPC result 不应为空");
                assertEquals(body, result, "JSON-RPC result 数据应与原始 body 一致");
            } else {
                assertEquals(body, responseBody, "响应 body 数据应与原始 body 一致");
            }
        }
    }

    /**
     * 属性 9 补充: 错误响应的协议转换一致性
     *
     * 对于任意协议的错误响应，转换后应保留错误信息。
     *
     * **Validates: Requirements 4.1, 4.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 9: 错误响应协议转换一致性")
    void errorResponseConversionConsistency(
            @ForAll("supportedProtocol") String protocol,
            @ForAll @NotBlank String service,
            @ForAll @NotBlank String method,
            @ForAll @NotBlank String errorMessage) {

        ExternalRequest extReq = new ExternalRequest();
        extReq.setProtocol(protocol);
        extReq.setService(service);
        extReq.setMethod(method);
        extReq.setHeaders(new HashMap<>());

        // 构建错误内部响应
        InternalResponse intResp = new InternalResponse();
        intResp.setSuccess(false);
        intResp.setErrorCode("500");
        intResp.setErrorMessage(errorMessage);
        intResp.setSourceProtocol(protocol);
        intResp.setHeaders(new HashMap<>());

        ExternalResponse extResp = adapter.transformResponse(intResp, extReq);

        assertNotNull(extResp, "错误外部响应不应为空");
        assertNotNull(extResp.getBody(), "错误响应体不应为空");
        assertEquals(protocol, extResp.getProtocol(), "错误响应协议应与请求协议一致");

        // 对于非 JSON-RPC 协议，状态码应反映错误
        if (!"JSON-RPC".equals(protocol)) {
            assertTrue(extResp.getStatusCode() >= 400, "错误响应状态码应 >= 400");
        }
    }

    // ==================== 属性 10: 消息路由正确性 ====================

    /**
     * 属性 10: 消息路由正确性
     *
     * 对于任意服务名称和方法名，消息路由器应该返回正确的目标服务端点。
     *
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 10: 消息路由正确性")
    void messageRoutingCorrectness(
            @ForAll @NotBlank String serviceName,
            @ForAll @NotBlank String methodName,
            @ForAll("validAddress") String address,
            @ForAll("validPort") int port) {

        DefaultMessageRouter router = new DefaultMessageRouter();

        // 注册服务端点
        ServiceEndpoint endpoint = new ServiceEndpoint(
            "svc-" + serviceName, serviceName, address, port, "gRPC");
        router.registerEndpoint(endpoint);

        // 构建请求
        InternalRequest request = new InternalRequest();
        request.setService(serviceName);
        request.setMethod(methodName);

        // 路由
        ServiceEndpoint result = router.route(request);

        // 验证路由结果
        assertNotNull(result, "路由结果不应为空");
        assertEquals(serviceName, result.getServiceName(), "路由到的服务名称应正确");
        assertEquals(address, result.getAddress(), "路由到的地址应正确");
        assertEquals(port, result.getPort(), "路由到的端口应正确");
    }

    /**
     * 属性 10 补充: 服务不可用时应抛出异常
     *
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 10: 服务不可用时路由失败")
    void routingFailsForUnavailableService(
            @ForAll @NotBlank String serviceName,
            @ForAll @NotBlank String methodName) {

        DefaultMessageRouter router = new DefaultMessageRouter();
        // 不注册任何端点

        InternalRequest request = new InternalRequest();
        request.setService(serviceName);
        request.setMethod(methodName);

        // 应该抛出 FrameworkException
        FrameworkException ex = assertThrows(FrameworkException.class, () -> router.route(request));
        assertNotNull(ex.getMessage(), "异常消息不应为空");
    }

    /**
     * 属性 10 补充: 基于内容的路由规则优先于服务名称匹配
     *
     * **Validates: Requirements 4.6**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 10: 路由规则优先级")
    void routingRulePriority(
            @ForAll @NotBlank String serviceName,
            @ForAll @NotBlank String methodName) {

        DefaultMessageRouter router = new DefaultMessageRouter();

        // 注册默认端点
        ServiceEndpoint defaultEndpoint = new ServiceEndpoint(
            "default-svc", serviceName, "default-host", 8080, "gRPC");
        router.registerEndpoint(defaultEndpoint);

        // 注册优先端点
        String priorityServiceId = "priority-svc";
        ServiceEndpoint priorityEndpoint = new ServiceEndpoint(
            priorityServiceId, "priority-service", "priority-host", 9090, "gRPC");
        router.registerEndpoint(priorityEndpoint);

        // 注册高优先级路由规则，将特定方法路由到优先端点
        RoutingRule rule = RoutingRule.forServiceMethod(serviceName, methodName, priorityServiceId);
        router.registerRule(rule);

        InternalRequest request = new InternalRequest();
        request.setService(serviceName);
        request.setMethod(methodName);

        ServiceEndpoint result = router.route(request);

        // 路由规则应优先匹配
        assertNotNull(result, "路由结果不应为空");
        assertEquals(priorityServiceId, result.getServiceId(), "应路由到规则指定的优先端点");
        assertEquals("priority-host", result.getAddress(), "应路由到优先端点的地址");
    }

    // ==================== 属性 11: 协议语义一致性 ====================

    /**
     * 属性 11: 协议语义一致性
     *
     * 对于任意消息类型（请求/响应、发布/订阅），协议转换前后消息的语义类型应该保持一致。
     *
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 11: 协议语义一致性")
    void protocolSemanticConsistency(
            @ForAll("supportedProtocol") String protocol,
            @ForAll @NotBlank String service,
            @ForAll @NotBlank String method) {

        ExternalRequest extReq = new ExternalRequest();
        extReq.setProtocol(protocol);
        extReq.setService(service);
        extReq.setMethod(method);
        extReq.setBody(Map.of("key", "value"));
        extReq.setHeaders(new HashMap<>());

        // 设置协议特有的语义类型
        String expectedMessageType;
        if ("MQTT".equals(protocol)) {
            expectedMessageType = "publish_subscribe";
        } else {
            expectedMessageType = "request_response";
        }

        // 外部 -> 内部
        InternalRequest intReq = adapter.transformRequest(extReq);

        // 验证语义类型保持
        assertNotNull(intReq.getMessageType(), "消息语义类型不应为空");
        assertEquals(expectedMessageType, intReq.getMessageType(),
            protocol + " 协议的消息语义类型应为 " + expectedMessageType);

        // 构建内部响应
        InternalResponse intResp = new InternalResponse();
        intResp.setSuccess(true);
        intResp.setPayload("{\"result\":\"ok\"}".getBytes());
        intResp.setSourceProtocol(protocol);
        intResp.setMessageType(intReq.getMessageType());
        intResp.setHeaders(new HashMap<>());

        // 内部 -> 外部
        ExternalResponse extResp = adapter.transformResponse(intResp, extReq);

        // 验证语义类型在往返后保持一致
        assertNotNull(extResp.getMessageType(), "响应消息语义类型不应为空");
        assertEquals(expectedMessageType, extResp.getMessageType(),
            "响应的消息语义类型应与请求保持一致");
    }

    /**
     * 属性 11 补充: MQTT 带响应主题时语义类型变为请求/响应
     *
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 11: MQTT 响应主题语义切换")
    void mqttResponseTopicSwitchesSemantics(
            @ForAll @NotBlank String service,
            @ForAll @NotBlank String method,
            @ForAll @NotBlank String responseTopic) {

        ExternalRequest extReq = new ExternalRequest();
        extReq.setProtocol("MQTT");
        extReq.setService(service);
        extReq.setMethod(method);
        extReq.setBody(Map.of("data", "test"));
        extReq.setHeaders(new HashMap<>());
        extReq.getMetadata().put("responseTopic", responseTopic);

        InternalRequest intReq = adapter.transformRequest(extReq);

        // 有响应主题时，MQTT 应切换为请求/响应模式
        assertEquals("request_response", intReq.getMessageType(),
            "MQTT 带响应主题时应为 request_response 模式");
    }

    /**
     * 属性 11 补充: REST 协议 HTTP 方法语义保持
     *
     * **Validates: Requirements 4.5**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 11: REST HTTP 方法语义保持")
    void restHttpMethodSemanticPreservation(
            @ForAll("httpMethod") String httpMethod,
            @ForAll @NotBlank String service,
            @ForAll @NotBlank String method) {

        ExternalRequest extReq = new ExternalRequest();
        extReq.setProtocol("REST");
        extReq.setService(service);
        extReq.setMethod(method);
        extReq.setHttpMethod(httpMethod);
        extReq.setHeaders(new HashMap<>());

        InternalRequest intReq = adapter.transformRequest(extReq);

        // HTTP 方法应保存在元数据中
        assertEquals(httpMethod, intReq.getMetadata().get("httpMethod"),
            "HTTP 方法应在内部请求元数据中保持");
        assertEquals("request_response", intReq.getMessageType(),
            "REST 协议应始终为 request_response 模式");
    }

    // ==================== 数据生成器 ====================

    @Provide
    Arbitrary<String> supportedProtocol() {
        return Arbitraries.of("REST", "WebSocket", "JSON-RPC", "MQTT");
    }

    @Provide
    Arbitrary<String> httpMethod() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH");
    }

    @Provide
    Arbitrary<Map<String, String>> simpleBody() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
        ).ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> validAddress() {
        return Arbitraries.of("localhost", "127.0.0.1", "192.168.1.1", "10.0.0.1", "service-host");
    }

    @Provide
    Arbitrary<Integer> validPort() {
        return Arbitraries.integers().between(1024, 65535);
    }
}
