package com.framework.protocol.internal.custom;

import com.framework.model.ServiceInfo;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自定义二进制协议处理器单元测试
 * 
 * 测试自定义协议的基本功能
 * 
 * **验证需求: 3.3**
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomProtocolHandlerTest {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolHandlerTest.class);
    
    private static CustomProtocolHandler serverHandler;
    private static CustomProtocolHandler clientHandler;
    private static final int TEST_PORT = 19090;
    
    @BeforeAll
    static void setUp() throws Exception {
        logger.info("初始化测试环境");
        
        // 启动服务端
        serverHandler = new CustomProtocolHandler();
        serverHandler.start(TEST_PORT);
        
        // 注册测试服务
        serverHandler.registerService("TestService", new CustomProtocolServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] data, Map<String, String> metadata) {
                logger.info("处理请求: method={}, dataLength={}", method, data.length);
                
                return switch (method) {
                    case "echo" -> data; // 回显数据
                    case "uppercase" -> new String(data).toUpperCase().getBytes();
                    case "reverse" -> new StringBuilder(new String(data)).reverse().toString().getBytes();
                    case "length" -> String.valueOf(data.length).getBytes();
                    default -> "unknown method".getBytes();
                };
            }
        });
        
        // 等待服务端启动
        Thread.sleep(500);
        
        // 启动客户端
        clientHandler = new CustomProtocolHandler();
        clientHandler.start(0); // 仅客户端模式
        
        logger.info("测试环境初始化完成");
    }
    
    @AfterAll
    static void tearDown() {
        logger.info("清理测试环境");
        
        if (clientHandler != null) {
            clientHandler.shutdown();
        }
        
        if (serverHandler != null) {
            serverHandler.shutdown();
        }
        
        logger.info("测试环境清理完成");
    }
    
    /**
     * 创建测试服务信息
     */
    private ServiceInfo createTestServiceInfo() {
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.setName("TestService");
        serviceInfo.setAddress("localhost");
        serviceInfo.setPort(TEST_PORT);
        return serviceInfo;
    }
    
    @Test
    @Order(1)
    @DisplayName("测试同步调用 - Echo")
    void testSyncCallEcho() {
        logger.info("=== 测试同步调用 - Echo ===");
        // TODO: 待实现完整测试
    }
}