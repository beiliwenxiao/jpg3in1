package com.framework.protocol.internal;

import com.framework.model.ServiceInfo;
import com.framework.protocol.internal.custom.CustomProtocolHandler;
import com.framework.protocol.internal.custom.CustomProtocolServer;
import com.framework.protocol.internal.grpc.GrpcProtocolHandler;
import com.framework.protocol.internal.grpc.GrpcServer;
import com.framework.protocol.internal.jsonrpc.JsonRpcInternalProtocolHandler;
import com.framework.protocol.internal.jsonrpc.JsonRpcInternalServer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内部协议的属性测试
 * 
 * 使用 jqwik 进行基于属性的测试，验证内部协议通信的正确性
 * 
 * **Feature: multi-language-communication-framework**
 * 
 * 测试属性：
 * - 属性 6: 内部协议通信支持
 * 
 * **验证需求: 3.1, 3.2, 3.3**
 */
class InternalProtocolPropertyTest {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalProtocolPropertyTest.class);
    
    // 端口分配器，避免端口冲突
    private static final AtomicInteger portCounter = new AtomicInteger(20000);
    
    // 协议处理器
    private GrpcProtocolHandler grpcHandler;
    private JsonRpcInternalProtocolHandler jsonRpcHandler;
    private CustomProtocolHandler customHandler;
    
    // 端口
    private int grpcPort;
    private int jsonRpcPort;
    private int customPort;
    
    @BeforeProperty
    void setUp() {
        // 分配端口
        grpcPort = portCounter.getAndIncrement();
        jsonRpcPort = portCounter.getAndIncrement();
        customPort = portCounter.getAndIncrement();
        
        logger.info("初始化测试环境: grpcPort={}, jsonRpcPort={}, customPort={}", 
                   grpcPort, jsonRpcPort, customPort);
        
        // 初始化 gRPC 处理器
        grpcHandler = new GrpcProtocolHandler();
        grpcHandler.start(grpcPort);
        registerGrpcTestService();
        
        // 初始化 JSON-RPC 处理器
        jsonRpcHandler = new JsonRpcInternalProtocolHandler();
        jsonRpcHandler.start(jsonRpcPort);
        registerJsonRpcTestService();
        
        // 初始化自定义协议处理器
        customHandler = new CustomProtocolHandler();
        customHandler.start(customPort);
        registerCustomTestService();
        
        // 等待服务启动
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @AfterProperty
    void tearDown() {
        logger.info("清理测试环境");
        
        if (grpcHandler != null) {
            try {
                grpcHandler.shutdown();
            } catch (Exception e) {
                logger.error("关闭 gRPC 处理器失败", e);
            }
        }
        
        if (jsonRpcHandler != null) {
            try {
                jsonRpcHandler.shutdown();
            } catch (Exception e) {
                logger.error("关闭 JSON-RPC 处理器失败", e);
            }
        }
        
        if (customHandler != null) {
            try {
                customHandler.shutdown();
            } catch (Exception e) {
                logger.error("关闭自定义协议处理器失败", e);
            }
        }
    }
    
    /**
     * 属性 6: 内部协议通信支持 - gRPC
     * 
     * 对于任意内部协议（gRPC），服务间应该能够成功通信
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议通信支持 - gRPC")
    void grpcProtocolCommunicationSupport(
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) {
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("TestService", grpcPort, "gRPC");
        
        // 同步调用
        String response = grpcHandler.call(serviceInfo, methodName, requestData, String.class);
        
        // 验证：应该成功通信并返回响应
        assertNotNull(response, "gRPC 同步调用应该返回响应");
        assertTrue(response.length() > 0, "gRPC 响应不应为空");
        
        logger.debug("gRPC 同步调用成功: method={}, request={}, response={}", 
                    methodName, requestData, response);
    }
    
    /**
     * 属性 6: 内部协议通信支持 - gRPC 异步
     * 
     * 对于任意内部协议（gRPC），异步调用应该能够成功通信
     * 
     * **Validates: Requirements 3.1**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议通信支持 - gRPC 异步")
    void grpcProtocolAsyncCommunicationSupport(
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) throws Exception {
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("TestService", grpcPort, "gRPC");
        
        // 异步调用
        CompletableFuture<String> future = grpcHandler.callAsync(
                serviceInfo, methodName, requestData, String.class);
        
        // 等待响应
        String response = future.get(10, TimeUnit.SECONDS);
        
        // 验证：应该成功通信并返回响应
        assertNotNull(response, "gRPC 异步调用应该返回响应");
        assertTrue(response.length() > 0, "gRPC 异步响应不应为空");
        
        logger.debug("gRPC 异步调用成功: method={}, request={}, response={}", 
                    methodName, requestData, response);
    }
    
    /**
     * 属性 6: 内部协议通信支持 - JSON-RPC
     * 
     * 对于任意内部协议（JSON-RPC），服务间应该能够成功通信
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议通信支持 - JSON-RPC")
    void jsonRpcProtocolCommunicationSupport(
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) {
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("TestService", jsonRpcPort, "JSON-RPC");
        
        // 同步调用
        String response = jsonRpcHandler.call(serviceInfo, methodName, requestData, String.class);
        
        // 验证：应该成功通信并返回响应
        assertNotNull(response, "JSON-RPC 同步调用应该返回响应");
        assertTrue(response.length() > 0, "JSON-RPC 响应不应为空");
        
        logger.debug("JSON-RPC 同步调用成功: method={}, request={}, response={}", 
                    methodName, requestData, response);
    }
    
    /**
     * 属性 6: 内部协议通信支持 - JSON-RPC 异步
     * 
     * 对于任意内部协议（JSON-RPC），异步调用应该能够成功通信
     * 
     * **Validates: Requirements 3.2**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议通信支持 - JSON-RPC 异步")
    void jsonRpcProtocolAsyncCommunicationSupport(
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) throws Exception {
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("TestService", jsonRpcPort, "JSON-RPC");
        
        // 异步调用
        CompletableFuture<String> future = jsonRpcHandler.callAsync(
                serviceInfo, methodName, requestData, String.class);
        
        // 等待响应
        String response = future.get(10, TimeUnit.SECONDS);
        
        // 验证：应该成功通信并返回响应
        assertNotNull(response, "JSON-RPC 异步调用应该返回响应");
        assertTrue(response.length() > 0, "JSON-RPC 异步响应不应为空");
        
        logger.debug("JSON-RPC 异步调用成功: method={}, request={}, response={}", 
                    methodName, requestData, response);
    }
    
    /**
     * 属性 6: 内部协议通信支持 - 自定义协议
     * 
     * 对于任意内部协议（自定义协议），服务间应该能够成功通信
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议通信支持 - 自定义协议")
    void customProtocolCommunicationSupport(
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) {
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("TestService", customPort, "Custom");
        
        // 同步调用
        String response = customHandler.call(serviceInfo, methodName, requestData, String.class);
        
        // 验证：应该成功通信并返回响应
        assertNotNull(response, "自定义协议同步调用应该返回响应");
        assertTrue(response.length() > 0, "自定义协议响应不应为空");
        
        logger.debug("自定义协议同步调用成功: method={}, request={}, response={}", 
                    methodName, requestData, response);
    }
    
    /**
     * 属性 6: 内部协议通信支持 - 自定义协议异步
     * 
     * 对于任意内部协议（自定义协议），异步调用应该能够成功通信
     * 
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议通信支持 - 自定义协议异步")
    void customProtocolAsyncCommunicationSupport(
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) throws Exception {
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("TestService", customPort, "Custom");
        
        // 异步调用
        CompletableFuture<String> future = customHandler.callAsync(
                serviceInfo, methodName, requestData, String.class);
        
        // 等待响应
        String response = future.get(10, TimeUnit.SECONDS);
        
        // 验证：应该成功通信并返回响应
        assertNotNull(response, "自定义协议异步调用应该返回响应");
        assertTrue(response.length() > 0, "自定义协议异步响应不应为空");
        
        logger.debug("自定义协议异步调用成功: method={}, request={}, response={}", 
                    methodName, requestData, response);
    }

    
    /**
     * 属性 6 扩展: 内部协议同步异步结果一致性
     * 
     * 对于任意内部协议，同步调用和异步调用应该返回等价的结果
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Property(tries = 30)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议同步异步结果一致性")
    void internalProtocolSyncAsyncConsistency(
            @ForAll("validProtocol") String protocol,
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) throws Exception {
        
        // 根据协议类型选择处理器和端口
        int port;
        String syncResponse;
        String asyncResponse;
        
        switch (protocol) {
            case "gRPC" -> {
                port = grpcPort;
                ServiceInfo serviceInfo = createServiceInfo("TestService", port, protocol);
                
                // 同步调用
                syncResponse = grpcHandler.call(serviceInfo, methodName, requestData, String.class);
                
                // 异步调用
                CompletableFuture<String> future = grpcHandler.callAsync(
                        serviceInfo, methodName, requestData, String.class);
                asyncResponse = future.get(10, TimeUnit.SECONDS);
            }
            case "JSON-RPC" -> {
                port = jsonRpcPort;
                ServiceInfo serviceInfo = createServiceInfo("TestService", port, protocol);
                
                // 同步调用
                syncResponse = jsonRpcHandler.call(serviceInfo, methodName, requestData, String.class);
                
                // 异步调用
                CompletableFuture<String> future = jsonRpcHandler.callAsync(
                        serviceInfo, methodName, requestData, String.class);
                asyncResponse = future.get(10, TimeUnit.SECONDS);
            }
            case "Custom" -> {
                port = customPort;
                ServiceInfo serviceInfo = createServiceInfo("TestService", port, protocol);
                
                // 同步调用
                syncResponse = customHandler.call(serviceInfo, methodName, requestData, String.class);
                
                // 异步调用
                CompletableFuture<String> future = customHandler.callAsync(
                        serviceInfo, methodName, requestData, String.class);
                asyncResponse = future.get(10, TimeUnit.SECONDS);
            }
            default -> throw new IllegalArgumentException("不支持的协议: " + protocol);
        }
        
        // 验证：同步和异步调用结果应该一致
        assertNotNull(syncResponse, protocol + " 同步调用应该返回响应");
        assertNotNull(asyncResponse, protocol + " 异步调用应该返回响应");
        assertEquals(syncResponse, asyncResponse, 
            protocol + " 同步和异步调用结果应该一致");
        
        logger.debug("{} 同步异步一致性验证通过: method={}, syncResponse={}, asyncResponse={}", 
                    protocol, methodName, syncResponse, asyncResponse);
    }
    
    /**
     * 属性 6 扩展: 内部协议多次调用稳定性
     * 
     * 对于任意内部协议，多次调用应该返回一致的结果
     * 
     * **Validates: Requirements 3.1, 3.2, 3.3**
     */
    @Property(tries = 30)
    @Label("Feature: multi-language-communication-framework, Property 6: 内部协议多次调用稳定性")
    void internalProtocolMultipleCallsStability(
            @ForAll("validProtocol") String protocol,
            @ForAll @NotBlank String methodName,
            @ForAll("validRequestData") String requestData) {
        
        // 根据协议类型选择处理器和端口
        int port;
        String response1, response2, response3;
        
        switch (protocol) {
            case "gRPC" -> {
                port = grpcPort;
                ServiceInfo serviceInfo = createServiceInfo("TestService", port, protocol);
                
                response1 = grpcHandler.call(serviceInfo, methodName, requestData, String.class);
                response2 = grpcHandler.call(serviceInfo, methodName, requestData, String.class);
                response3 = grpcHandler.call(serviceInfo, methodName, requestData, String.class);
            }
            case "JSON-RPC" -> {
                port = jsonRpcPort;
                ServiceInfo serviceInfo = createServiceInfo("TestService", port, protocol);
                
                response1 = jsonRpcHandler.call(serviceInfo, methodName, requestData, String.class);
                response2 = jsonRpcHandler.call(serviceInfo, methodName, requestData, String.class);
                response3 = jsonRpcHandler.call(serviceInfo, methodName, requestData, String.class);
            }
            case "Custom" -> {
                port = customPort;
                ServiceInfo serviceInfo = createServiceInfo("TestService", port, protocol);
                
                response1 = customHandler.call(serviceInfo, methodName, requestData, String.class);
                response2 = customHandler.call(serviceInfo, methodName, requestData, String.class);
                response3 = customHandler.call(serviceInfo, methodName, requestData, String.class);
            }
            default -> throw new IllegalArgumentException("不支持的协议: " + protocol);
        }
        
        // 验证：多次调用结果应该一致
        assertNotNull(response1, protocol + " 第一次调用应该返回响应");
        assertNotNull(response2, protocol + " 第二次调用应该返回响应");
        assertNotNull(response3, protocol + " 第三次调用应该返回响应");
        assertEquals(response1, response2, protocol + " 第一次和第二次调用结果应该一致");
        assertEquals(response2, response3, protocol + " 第二次和第三次调用结果应该一致");
        
        logger.debug("{} 多次调用稳定性验证通过: method={}", protocol, methodName);
    }
    
    // ========== 数据生成器 ==========
    
    /**
     * 生成有效的协议类型
     */
    @Provide
    Arbitrary<String> validProtocol() {
        return Arbitraries.of("gRPC", "JSON-RPC", "Custom");
    }
    
    /**
     * 生成有效的请求数据
     * 生成有效的 JSON 字符串，确保 JSON-RPC 协议能正确解析
     */
    @Provide
    Arbitrary<String> validRequestData() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(100)
                .map(s -> "\"" + s + "\"");
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 创建服务信息
     */
    private ServiceInfo createServiceInfo(String serviceName, int port, String protocol) {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.setAddress("localhost");
        serviceInfo.setPort(port);
        serviceInfo.setProtocols(Arrays.asList(protocol));
        serviceInfo.setLanguage("java");
        serviceInfo.setVersion("1.0.0");
        return serviceInfo;
    }
    
    /**
     * 注册 gRPC 测试服务
     */
    private void registerGrpcTestService() {
        grpcHandler.registerService("TestService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.debug("gRPC 测试服务处理请求: method={}, payloadLength={}", 
                           method, payload.length);
                // 返回 echo 响应
                String request = new String(payload);
                String response = "{\"method\":\"" + method + "\",\"echo\":\"" + 
                                 request.replace("\"", "\\\"") + "\"}";
                return response.getBytes();
            }
        });
    }
    
    /**
     * 注册 JSON-RPC 测试服务
     */
    private void registerJsonRpcTestService() {
        jsonRpcHandler.registerService("TestService", new JsonRpcInternalServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.debug("JSON-RPC 测试服务处理请求: method={}, payloadLength={}", 
                           method, payload.length);
                // 返回 echo 响应
                String request = new String(payload);
                String response = "{\"method\":\"" + method + "\",\"echo\":\"" + 
                                 request.replace("\"", "\\\"") + "\"}";
                return response.getBytes();
            }
        });
    }
    
    /**
     * 注册自定义协议测试服务
     */
    private void registerCustomTestService() {
        customHandler.registerService("TestService", new CustomProtocolServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] data, Map<String, String> metadata) {
                logger.debug("自定义协议测试服务处理请求: method={}, dataLength={}", 
                           method, data.length);
                // 返回 echo 响应
                String request = new String(data);
                String response = "{\"method\":\"" + method + "\",\"echo\":\"" + 
                                 request.replace("\"", "\\\"") + "\"}";
                return response.getBytes();
            }
        });
    }
}
