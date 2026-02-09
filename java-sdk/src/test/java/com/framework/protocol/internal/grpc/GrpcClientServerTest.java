package com.framework.protocol.internal.grpc;

import com.framework.exception.FrameworkException;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gRPC 客户端和服务端单元测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GrpcClientServerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcClientServerTest.class);
    private static final int TEST_PORT = 9091;
    
    private static GrpcServer server;
    private GrpcClient client;
    
    @BeforeAll
    static void setupServer() throws Exception {
        logger.info("启动测试服务端");
        
        // 创建并启动服务端
        server = new GrpcServer(TEST_PORT);
        
        // 注册测试服务
        server.registerService("TestService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.info("处理请求: method={}, payload={}", method, new String(payload));
                
                // 根据方法返回不同的响应
                switch (method) {
                    case "echo":
                        return payload;
                    case "uppercase":
                        return new String(payload).toUpperCase().getBytes();
                    case "error":
                        throw new FrameworkException(
                            com.framework.exception.ErrorCode.BAD_REQUEST,
                            "测试错误"
                        );
                    default:
                        return "unknown method".getBytes();
                }
            }
        });
        
        server.start();
        
        // 等待服务端启动
        Thread.sleep(1000);
    }
    
    @AfterAll
    static void teardownServer() throws Exception {
        logger.info("关闭测试服务端");
        if (server != null) {
            server.shutdown();
        }
    }
    
    @BeforeEach
    void setupClient() {
        logger.info("创建测试客户端");
        client = new GrpcClient("localhost", TEST_PORT);
        client.start();
    }
    
    @AfterEach
    void teardownClient() {
        logger.info("关闭测试客户端");
        if (client != null) {
            client.shutdown();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("测试同步调用 - echo 方法")
    void testSyncCall_Echo() {
        // 准备测试数据
        String testMessage = "Hello gRPC";
        byte[] request = testMessage.getBytes();
        
        // 执行调用
        byte[] response = client.call(
            "TestService",
            "echo",
            request,
            null,
            5000
        );
        
        // 验证响应
        assertNotNull(response);
        assertEquals(testMessage, new String(response));
        
        logger.info("同步调用测试通过");
    }
    
    @Test
    @Order(2)
    @DisplayName("测试同步调用 - uppercase 方法")
    void testSyncCall_Uppercase() {
        // 准备测试数据
        String testMessage = "hello world";
        byte[] request = testMessage.getBytes();
        
        // 执行调用
        byte[] response = client.call(
            "TestService",
            "uppercase",
            request,
            null,
            5000
        );
        
        // 验证响应
        assertNotNull(response);
        assertEquals("HELLO WORLD", new String(response));
        
        logger.info("uppercase 方法测试通过");
    }
    
    @Test
    @Order(3)
    @DisplayName("测试同步调用 - 带请求头")
    void testSyncCall_WithHeaders() {
        // 准备测试数据
        String testMessage = "test with headers";
        byte[] request = testMessage.getBytes();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-ID", "12345");
        headers.put("X-User-ID", "user-001");
        
        // 执行调用
        byte[] response = client.call(
            "TestService",
            "echo",
            request,
            headers,
            5000
        );
        
        // 验证响应
        assertNotNull(response);
        assertEquals(testMessage, new String(response));
        
        logger.info("带请求头的调用测试通过");
    }
    
    @Test
    @Order(4)
    @DisplayName("测试异步调用")
    void testAsyncCall() throws Exception {
        // 准备测试数据
        String testMessage = "async test";
        byte[] request = testMessage.getBytes();
        
        // 执行异步调用
        CompletableFuture<byte[]> future = client.callAsync(
            "TestService",
            "echo",
            request,
            null,
            5000
        );
        
        // 等待响应
        byte[] response = future.get(10, TimeUnit.SECONDS);
        
        // 验证响应
        assertNotNull(response);
        assertEquals(testMessage, new String(response));
        
        logger.info("异步调用测试通过");
    }
    
    @Test
    @Order(5)
    @DisplayName("测试异步调用 - 多个并发请求")
    void testAsyncCall_Concurrent() throws Exception {
        // 创建多个异步请求
        CompletableFuture<byte[]> future1 = client.callAsync(
            "TestService", "echo", "request1".getBytes(), null, 5000
        );
        
        CompletableFuture<byte[]> future2 = client.callAsync(
            "TestService", "echo", "request2".getBytes(), null, 5000
        );
        
        CompletableFuture<byte[]> future3 = client.callAsync(
            "TestService", "uppercase", "hello".getBytes(), null, 5000
        );
        
        // 等待所有请求完成
        CompletableFuture.allOf(future1, future2, future3).get(10, TimeUnit.SECONDS);
        
        // 验证响应
        assertEquals("request1", new String(future1.get()));
        assertEquals("request2", new String(future2.get()));
        assertEquals("HELLO", new String(future3.get()));
        
        logger.info("并发异步调用测试通过");
    }
    
    @Test
    @Order(6)
    @DisplayName("测试错误处理")
    void testErrorHandling() {
        // 准备测试数据
        byte[] request = "error test".getBytes();
        
        // 执行调用，期望抛出异常
        assertThrows(FrameworkException.class, () -> {
            client.call("TestService", "error", request, null, 5000);
        });
        
        logger.info("错误处理测试通过");
    }
    
    @Test
    @Order(7)
    @DisplayName("测试服务未找到")
    void testServiceNotFound() {
        // 准备测试数据
        byte[] request = "test".getBytes();
        
        // 调用不存在的服务
        assertThrows(FrameworkException.class, () -> {
            client.call("NonExistentService", "method", request, null, 5000);
        });
        
        logger.info("服务未找到测试通过");
    }
    
    @Test
    @Order(8)
    @DisplayName("测试健康检查")
    void testHealthCheck() {
        // 执行健康检查
        boolean healthy = client.healthCheck();
        
        // 验证健康状态
        assertTrue(healthy, "服务应该是健康的");
        
        logger.info("健康检查测试通过");
    }
    
    @Test
    @Order(9)
    @DisplayName("测试空负载")
    void testEmptyPayload() {
        // 使用空负载
        byte[] request = new byte[0];
        
        // 执行调用
        byte[] response = client.call(
            "TestService",
            "echo",
            request,
            null,
            5000
        );
        
        // 验证响应
        assertNotNull(response);
        assertEquals(0, response.length);
        
        logger.info("空负载测试通过");
    }
    
    @Test
    @Order(10)
    @DisplayName("测试大负载")
    void testLargePayload() {
        // 创建大负载（1MB）
        byte[] request = new byte[1024 * 1024];
        for (int i = 0; i < request.length; i++) {
            request[i] = (byte) (i % 256);
        }
        
        // 执行调用
        byte[] response = client.call(
            "TestService",
            "echo",
            request,
            null,
            10000
        );
        
        // 验证响应
        assertNotNull(response);
        assertEquals(request.length, response.length);
        assertArrayEquals(request, response);
        
        logger.info("大负载测试通过");
    }
    
    @Test
    @Order(11)
    @DisplayName("测试客户端未启动")
    void testClientNotStarted() {
        // 创建未启动的客户端
        GrpcClient uninitializedClient = new GrpcClient("localhost", TEST_PORT);
        
        // 尝试调用，期望抛出异常
        assertThrows(FrameworkException.class, () -> {
            uninitializedClient.call(
                "TestService",
                "echo",
                "test".getBytes(),
                null,
                5000
            );
        });
        
        logger.info("客户端未启动测试通过");
    }
    
    @Test
    @Order(12)
    @DisplayName("测试服务注册和注销")
    void testServiceRegistration() throws Exception {
        // 注册新服务
        server.registerService("DynamicService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                return "dynamic response".getBytes();
            }
        });
        
        // 调用新服务
        byte[] response = client.call(
            "DynamicService",
            "test",
            "request".getBytes(),
            null,
            5000
        );
        
        assertEquals("dynamic response", new String(response));
        
        // 注销服务
        server.unregisterService("DynamicService");
        
        // 再次调用，期望失败
        assertThrows(FrameworkException.class, () -> {
            client.call("DynamicService", "test", "request".getBytes(), null, 5000);
        });
        
        logger.info("服务注册和注销测试通过");
    }
}
