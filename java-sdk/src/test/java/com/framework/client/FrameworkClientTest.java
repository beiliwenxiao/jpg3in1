package com.framework.client;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FrameworkClient 单元测试
 * 
 * 测试同步和异步调用、错误处理
 * **验证需求: 1.1**
 */
@DisplayName("FrameworkClient 单元测试")
class FrameworkClientTest {
    
    private FrameworkClient client;
    private TestService testService;
    
    @BeforeEach
    void setUp() {
        client = new DefaultFrameworkClient();
        testService = new TestService();
        client.registerService("testService", testService);
        client.start();
    }
    
    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }
    
    // ========== 同步调用测试 ==========
    
    @Test
    @DisplayName("同步调用 - 成功返回结果")
    void testCallSuccess() {
        String result = client.call("testService", "echo", "Hello", String.class);
        assertEquals("Echo: Hello", result);
    }
    
    @Test
    @DisplayName("同步调用 - 无参数方法")
    void testCallNoParameters() {
        String result = client.call("testService", "getStatus", null, String.class);
        assertEquals("OK", result);
    }
    
    @Test
    @DisplayName("同步调用 - 返回复杂对象")
    void testCallComplexObject() {
        TestData data = new TestData("test", 123);
        TestData result = client.call("testService", "processData", data, TestData.class);
        assertNotNull(result);
        assertEquals("test_processed", result.getName());
        assertEquals(123, result.getValue());
    }
    
    @Test
    @DisplayName("同步调用 - 服务不存在")
    void testCallServiceNotFound() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.call("nonExistentService", "method", null, String.class);
        });
        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("服务未找到"));
    }
    
    @Test
    @DisplayName("同步调用 - 方法不存在")
    void testCallMethodNotFound() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.call("testService", "nonExistentMethod", null, String.class);
        });
        assertEquals(ErrorCode.NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("方法未找到"));
    }
    
    @Test
    @DisplayName("同步调用 - 服务名称为空")
    void testCallEmptyServiceName() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.call("", "method", null, String.class);
        });
        assertEquals(ErrorCode.BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains("服务名称不能为空"));
    }
    
    @Test
    @DisplayName("同步调用 - 方法名称为空")
    void testCallEmptyMethodName() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.call("testService", "", null, String.class);
        });
        assertEquals(ErrorCode.BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains("方法名称不能为空"));
    }
    
    @Test
    @DisplayName("同步调用 - 响应类型为空")
    void testCallNullResponseType() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.call("testService", "echo", "test", null);
        });
        assertEquals(ErrorCode.BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains("响应类型不能为空"));
    }
    
    @Test
    @DisplayName("同步调用 - 客户端未启动")
    void testCallClientNotStarted() {
        FrameworkClient newClient = new DefaultFrameworkClient();
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            newClient.call("testService", "echo", "test", String.class);
        });
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getCode());
        assertTrue(exception.getMessage().contains("客户端未启动"));
    }
    
    @Test
    @DisplayName("同步调用 - 客户端已关闭")
    void testCallClientShutdown() {
        client.shutdown();
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.call("testService", "echo", "test", String.class);
        });
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getCode());
        assertTrue(exception.getMessage().contains("客户端已关闭"));
    }
    
    // ========== 异步调用测试 ==========
    
    @Test
    @DisplayName("异步调用 - 成功返回结果")
    void testCallAsyncSuccess() throws Exception {
        CompletableFuture<String> future = client.callAsync("testService", "echo", "Hello", String.class);
        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("Echo: Hello", result);
    }
    
    @Test
    @DisplayName("异步调用 - 多个并发请求")
    void testCallAsyncConcurrent() throws Exception {
        CompletableFuture<String> future1 = client.callAsync("testService", "echo", "Request1", String.class);
        CompletableFuture<String> future2 = client.callAsync("testService", "echo", "Request2", String.class);
        CompletableFuture<String> future3 = client.callAsync("testService", "echo", "Request3", String.class);
        
        CompletableFuture.allOf(future1, future2, future3).get(5, TimeUnit.SECONDS);
        
        assertEquals("Echo: Request1", future1.get());
        assertEquals("Echo: Request2", future2.get());
        assertEquals("Echo: Request3", future3.get());
    }
    
    @Test
    @DisplayName("异步调用 - 服务不存在")
    void testCallAsyncServiceNotFound() {
        CompletableFuture<String> future = client.callAsync("nonExistentService", "method", null, String.class);
        
        assertThrows(Exception.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });
    }
    
    @Test
    @DisplayName("异步调用 - 异常处理")
    void testCallAsyncException() {
        CompletableFuture<String> future = client.callAsync("testService", "throwException", null, String.class);
        
        assertThrows(Exception.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });
    }
    
    // ========== 流式调用测试 ==========
    
    @Test
    @DisplayName("流式调用 - 返回流数据")
    void testStreamSuccess() {
        Stream<String> stream = client.stream("testService", "getStream", null, String.class);
        List<String> results = stream.collect(Collectors.toList());
        
        assertEquals(3, results.size());
        assertEquals("Item1", results.get(0));
        assertEquals("Item2", results.get(1));
        assertEquals("Item3", results.get(2));
    }
    
    @Test
    @DisplayName("流式调用 - 返回列表")
    void testStreamFromList() {
        Stream<String> stream = client.stream("testService", "getList", null, String.class);
        List<String> results = stream.collect(Collectors.toList());
        
        assertEquals(2, results.size());
        assertEquals("A", results.get(0));
        assertEquals("B", results.get(1));
    }
    
    @Test
    @DisplayName("流式调用 - 空流")
    void testStreamEmpty() {
        Stream<String> stream = client.stream("testService", "getEmptyStream", null, String.class);
        List<String> results = stream.collect(Collectors.toList());
        
        assertTrue(results.isEmpty());
    }
    
    @Test
    @DisplayName("流式调用 - 服务不存在")
    void testStreamServiceNotFound() {
        assertThrows(FrameworkException.class, () -> {
            client.stream("nonExistentService", "method", null, String.class);
        });
    }
    
    // ========== 服务注册测试 ==========
    
    @Test
    @DisplayName("服务注册 - 成功注册")
    void testRegisterServiceSuccess() {
        TestService newService = new TestService();
        assertDoesNotThrow(() -> {
            client.registerService("newService", newService);
        });
        
        // 验证可以调用新注册的服务
        String result = client.call("newService", "echo", "test", String.class);
        assertEquals("Echo: test", result);
    }
    
    @Test
    @DisplayName("服务注册 - 服务名称为空")
    void testRegisterServiceEmptyName() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.registerService("", new TestService());
        });
        assertEquals(ErrorCode.BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains("服务名称不能为空"));
    }
    
    @Test
    @DisplayName("服务注册 - 服务实现为空")
    void testRegisterServiceNullImplementation() {
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.registerService("service", null);
        });
        assertEquals(ErrorCode.BAD_REQUEST, exception.getCode());
        assertTrue(exception.getMessage().contains("服务实现不能为空"));
    }
    
    @Test
    @DisplayName("服务注册 - 覆盖已有服务")
    void testRegisterServiceOverride() {
        TestService newService = new TestService();
        client.registerService("testService", newService);
        
        // 验证服务被覆盖
        String result = client.call("testService", "echo", "test", String.class);
        assertEquals("Echo: test", result);
    }
    
    // ========== 生命周期测试 ==========
    
    @Test
    @DisplayName("生命周期 - 启动客户端")
    void testStart() {
        FrameworkClient newClient = new DefaultFrameworkClient();
        assertDoesNotThrow(() -> {
            newClient.start();
        });
        newClient.shutdown();
    }
    
    @Test
    @DisplayName("生命周期 - 重复启动")
    void testStartTwice() {
        // 第二次启动应该不抛出异常
        assertDoesNotThrow(() -> {
            client.start();
        });
    }
    
    @Test
    @DisplayName("生命周期 - 关闭客户端")
    void testShutdown() {
        assertDoesNotThrow(() -> {
            client.shutdown();
        });
    }
    
    @Test
    @DisplayName("生命周期 - 重复关闭")
    void testShutdownTwice() {
        client.shutdown();
        // 第二次关闭应该不抛出异常
        assertDoesNotThrow(() -> {
            client.shutdown();
        });
    }
    
    @Test
    @DisplayName("生命周期 - 关闭后无法重新启动")
    void testStartAfterShutdown() {
        client.shutdown();
        FrameworkException exception = assertThrows(FrameworkException.class, () -> {
            client.start();
        });
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getCode());
        assertTrue(exception.getMessage().contains("客户端已关闭，无法重新启动"));
    }
    
    // ========== 测试辅助类 ==========
    
    /**
     * 测试服务实现
     */
    public static class TestService {
        
        public String echo(String message) {
            return "Echo: " + message;
        }
        
        public String getStatus() {
            return "OK";
        }
        
        public TestData processData(TestData data) {
            return new TestData(data.getName() + "_processed", data.getValue());
        }
        
        public Stream<String> getStream() {
            return Stream.of("Item1", "Item2", "Item3");
        }
        
        public List<String> getList() {
            return Arrays.asList("A", "B");
        }
        
        public Stream<String> getEmptyStream() {
            return Stream.empty();
        }
        
        public void throwException() {
            throw new RuntimeException("Test exception");
        }
    }
    
    /**
     * 测试数据类
     */
    public static class TestData {
        private String name;
        private int value;
        
        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() {
            return name;
        }
        
        public int getValue() {
            return value;
        }
    }
}
