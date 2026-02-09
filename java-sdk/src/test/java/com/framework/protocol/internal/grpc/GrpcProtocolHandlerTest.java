package com.framework.protocol.internal.grpc;

import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gRPC 协议处理器单元测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GrpcProtocolHandlerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcProtocolHandlerTest.class);
    private static final int TEST_PORT = 9092;
    
    private GrpcProtocolHandler handler;
    
    @BeforeEach
    void setup() {
        logger.info("创建协议处理器");
        handler = new GrpcProtocolHandler();
        handler.start(TEST_PORT);
        
        // 注册测试服务
        handler.registerService("UserService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.info("处理用户服务请求: method={}", method);
                
                switch (method) {
                    case "getUser":
                        return "{\"id\":1,\"name\":\"张三\"}".getBytes();
                    case "createUser":
                        return "{\"success\":true,\"id\":2}".getBytes();
                    case "deleteUser":
                        return "{\"success\":true}".getBytes();
                    default:
                        return "{}".getBytes();
                }
            }
        });
    }
    
    @AfterEach
    void teardown() {
        logger.info("关闭协议处理器");
        if (handler != null) {
            handler.shutdown();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("测试协议处理器启动和关闭")
    void testStartAndShutdown() {
        // 创建新的处理器
        GrpcProtocolHandler newHandler = new GrpcProtocolHandler();
        
        // 启动
        assertDoesNotThrow(() -> newHandler.start(9093));
        
        // 关闭
        assertDoesNotThrow(() -> newHandler.shutdown());
        
        logger.info("启动和关闭测试通过");
    }
    
    @Test
    @Order(2)
    @DisplayName("测试同步调用 - String 类型")
    void testCall_StringType() throws InterruptedException {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 执行调用
        String response = handler.call(
            serviceInfo,
            "getUser",
            "{\"id\":1}",
            String.class
        );
        
        // 验证响应
        assertNotNull(response);
        assertTrue(response.contains("张三"));
        
        logger.info("String 类型调用测试通过");
    }
    
    @Test
    @Order(3)
    @DisplayName("测试同步调用 - byte[] 类型")
    void testCall_ByteArrayType() throws InterruptedException {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 执行调用
        byte[] response = handler.call(
            serviceInfo,
            "createUser",
            "{\"name\":\"李四\"}".getBytes(),
            byte[].class
        );
        
        // 验证响应
        assertNotNull(response);
        assertTrue(new String(response).contains("success"));
        
        logger.info("byte[] 类型调用测试通过");
    }
    
    @Test
    @Order(4)
    @DisplayName("测试异步调用")
    void testCallAsync() throws Exception {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 执行异步调用
        CompletableFuture<String> future = handler.callAsync(
            serviceInfo,
            "getUser",
            "{\"id\":1}",
            String.class
        );
        
        // 等待响应
        String response = future.get(10, TimeUnit.SECONDS);
        
        // 验证响应
        assertNotNull(response);
        assertTrue(response.contains("张三"));
        
        logger.info("异步调用测试通过");
    }
    
    @Test
    @Order(5)
    @DisplayName("测试多个并发异步调用")
    void testCallAsync_Concurrent() throws Exception {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 创建多个异步请求
        CompletableFuture<String> future1 = handler.callAsync(
            serviceInfo, "getUser", "{\"id\":1}", String.class
        );
        
        CompletableFuture<String> future2 = handler.callAsync(
            serviceInfo, "createUser", "{\"name\":\"王五\"}", String.class
        );
        
        CompletableFuture<String> future3 = handler.callAsync(
            serviceInfo, "deleteUser", "{\"id\":3}", String.class
        );
        
        // 等待所有请求完成
        CompletableFuture.allOf(future1, future2, future3).get(10, TimeUnit.SECONDS);
        
        // 验证响应
        assertTrue(future1.get().contains("张三"));
        assertTrue(future2.get().contains("success"));
        assertTrue(future3.get().contains("success"));
        
        logger.info("并发异步调用测试通过");
    }
    
    @Test
    @Order(6)
    @DisplayName("测试服务注册")
    void testRegisterService() throws InterruptedException {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 注册新服务
        handler.registerService("OrderService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                return "{\"orderId\":\"12345\"}".getBytes();
            }
        });
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("OrderService");
        
        // 调用新服务
        String response = handler.call(
            serviceInfo,
            "createOrder",
            "{\"product\":\"手机\"}",
            String.class
        );
        
        // 验证响应
        assertNotNull(response);
        assertTrue(response.contains("12345"));
        
        logger.info("服务注册测试通过");
    }
    
    @Test
    @Order(7)
    @DisplayName("测试服务注销")
    void testUnregisterService() throws InterruptedException {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 注册服务
        handler.registerService("TempService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                return "temp response".getBytes();
            }
        });
        
        // 验证服务可用
        ServiceInfo serviceInfo = createServiceInfo("TempService");
        String response = handler.call(serviceInfo, "test", "request", String.class);
        assertNotNull(response);
        
        // 注销服务
        handler.unregisterService("TempService");
        
        // 再次调用，期望失败
        assertThrows(FrameworkException.class, () -> {
            handler.call(serviceInfo, "test", "request", String.class);
        });
        
        logger.info("服务注销测试通过");
    }
    
    @Test
    @Order(8)
    @DisplayName("测试客户端连接池")
    void testClientPool() throws InterruptedException {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 多次调用同一服务（应该复用连接）
        for (int i = 0; i < 5; i++) {
            String response = handler.call(
                serviceInfo,
                "getUser",
                "{\"id\":" + i + "}",
                String.class
            );
            assertNotNull(response);
        }
        
        logger.info("客户端连接池测试通过");
    }
    
    @Test
    @Order(9)
    @DisplayName("测试空请求")
    void testNullRequest() throws InterruptedException {
        // 等待服务端完全启动
        Thread.sleep(500);
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 使用 null 请求
        String response = handler.call(
            serviceInfo,
            "getUser",
            null,
            String.class
        );
        
        // 验证响应
        assertNotNull(response);
        
        logger.info("空请求测试通过");
    }
    
    @Test
    @Order(10)
    @DisplayName("测试处理器未启动")
    void testHandlerNotStarted() {
        // 创建未启动的处理器
        GrpcProtocolHandler uninitializedHandler = new GrpcProtocolHandler();
        
        // 创建服务信息
        ServiceInfo serviceInfo = createServiceInfo("UserService");
        
        // 尝试调用，期望抛出异常
        assertThrows(FrameworkException.class, () -> {
            uninitializedHandler.call(serviceInfo, "getUser", "request", String.class);
        });
        
        logger.info("处理器未启动测试通过");
    }
    
    @Test
    @Order(11)
    @DisplayName("测试获取服务端")
    void testGetServer() {
        // 获取服务端
        GrpcServer server = handler.getServer();
        
        // 验证服务端不为空
        assertNotNull(server);
        
        logger.info("获取服务端测试通过");
    }
    
    @Test
    @Order(12)
    @DisplayName("测试不启动服务端")
    void testNoServerMode() {
        // 创建不启动服务端的处理器（端口为 0）
        GrpcProtocolHandler clientOnlyHandler = new GrpcProtocolHandler();
        clientOnlyHandler.start(0);
        
        try {
            // 验证服务端为空
            assertNull(clientOnlyHandler.getServer());
            
            // 尝试注册服务，期望抛出异常
            assertThrows(FrameworkException.class, () -> {
                clientOnlyHandler.registerService("TestService", 
                    new GrpcServer.ServiceHandler() {
                        @Override
                        public byte[] handle(String method, byte[] payload, 
                                           Map<String, String> headers) {
                            return new byte[0];
                        }
                    });
            });
            
            logger.info("不启动服务端测试通过");
            
        } finally {
            clientOnlyHandler.shutdown();
        }
    }
    
    /**
     * 创建测试用的服务信息
     */
    private ServiceInfo createServiceInfo(String serviceName) {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName(serviceName);
        serviceInfo.setAddress("localhost");
        serviceInfo.setPort(TEST_PORT);
        serviceInfo.setProtocols(Arrays.asList("gRPC"));
        serviceInfo.setLanguage("java");
        serviceInfo.setVersion("1.0.0");
        return serviceInfo;
    }
}
