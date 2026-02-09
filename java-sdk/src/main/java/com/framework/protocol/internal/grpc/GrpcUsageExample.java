package com.framework.protocol.internal.grpc;

import com.framework.model.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * gRPC 使用示例
 * 演示如何使用 gRPC 客户端和服务端
 */
public class GrpcUsageExample {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcUsageExample.class);
    
    /**
     * 服务端示例
     */
    public static void serverExample() throws Exception {
        // 创建 gRPC 服务端
        GrpcServer server = new GrpcServer(9090);
        
        // 注册服务处理器
        server.registerService("UserService", new GrpcServer.ServiceHandler() {
            @Override
            public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                logger.info("处理请求: method={}, payload={}", method, new String(payload));
                
                // 根据方法名处理不同的业务逻辑
                if ("getUser".equals(method)) {
                    return "{\"id\":1,\"name\":\"张三\"}".getBytes();
                } else if ("createUser".equals(method)) {
                    return "{\"success\":true,\"id\":2}".getBytes();
                }
                
                return "{}".getBytes();
            }
        });
        
        // 启动服务端
        server.start();
        logger.info("gRPC 服务端已启动，监听端口: 9090");
        
        // 阻塞等待关闭
        server.blockUntilShutdown();
    }
    
    /**
     * 客户端同步调用示例
     */
    public static void clientSyncExample() {
        // 创建 gRPC 客户端
        GrpcClient client = new GrpcClient("localhost", 9090);
        client.start();
        
        try {
            // 同步调用
            byte[] request = "{\"id\":1}".getBytes();
            byte[] response = client.call(
                    "UserService",
                    "getUser",
                    request,
                    null,
                    5000
            );
            
            logger.info("同步调用响应: {}", new String(response));
            
        } finally {
            client.shutdown();
        }
    }
    
    /**
     * 客户端异步调用示例
     */
    public static void clientAsyncExample() {
        // 创建 gRPC 客户端
        GrpcClient client = new GrpcClient("localhost", 9090);
        client.start();
        
        try {
            // 异步调用
            byte[] request = "{\"name\":\"李四\"}".getBytes();
            CompletableFuture<byte[]> future = client.callAsync(
                    "UserService",
                    "createUser",
                    request,
                    null,
                    5000
            );
            
            // 处理异步响应
            future.thenAccept(response -> {
                logger.info("异步调用响应: {}", new String(response));
            }).exceptionally(ex -> {
                logger.error("异步调用失败", ex);
                return null;
            });
            
            // 等待完成
            future.join();
            
        } finally {
            client.shutdown();
        }
    }
    
    /**
     * 客户端流式调用示例
     */
    public static void clientStreamExample() {
        // 创建 gRPC 客户端
        GrpcClient client = new GrpcClient("localhost", 9090);
        client.start();
        
        try {
            // 流式调用
            byte[] request = "{\"query\":\"all\"}".getBytes();
            Stream<byte[]> responseStream = client.stream(
                    "UserService",
                    "listUsers",
                    request,
                    null,
                    10000
            );
            
            // 处理流式响应
            responseStream.forEach(response -> {
                logger.info("流式响应: {}", new String(response));
            });
            
        } finally {
            client.shutdown();
        }
    }
    
    /**
     * 协议处理器示例
     */
    public static void protocolHandlerExample() {
        // 创建协议处理器
        GrpcProtocolHandler handler = new GrpcProtocolHandler();
        handler.start(9090);
        
        try {
            // 注册服务
            handler.registerService("OrderService", new GrpcServer.ServiceHandler() {
                @Override
                public byte[] handle(String method, byte[] payload, Map<String, String> headers) {
                    logger.info("处理订单请求: method={}", method);
                    return "{\"orderId\":\"12345\"}".getBytes();
                }
            });
            
            // 创建服务信息
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setName("OrderService");
            serviceInfo.setAddress("localhost");
            serviceInfo.setPort(9090);
            serviceInfo.setProtocols(Arrays.asList("gRPC"));
            
            // 调用服务
            String response = handler.call(
                    serviceInfo,
                    "createOrder",
                    "{\"product\":\"手机\",\"quantity\":1}",
                    String.class
            );
            
            logger.info("调用响应: {}", response);
            
        } finally {
            handler.shutdown();
        }
    }
    
    /**
     * 健康检查示例
     */
    public static void healthCheckExample() {
        GrpcClient client = new GrpcClient("localhost", 9090);
        client.start();
        
        try {
            boolean healthy = client.healthCheck();
            logger.info("服务健康状态: {}", healthy ? "健康" : "不健康");
        } finally {
            client.shutdown();
        }
    }
    
    public static void main(String[] args) throws Exception {
        // 运行服务端示例（在单独的线程中）
        new Thread(() -> {
            try {
                serverExample();
            } catch (Exception e) {
                logger.error("服务端示例失败", e);
            }
        }).start();
        
        // 等待服务端启动
        Thread.sleep(2000);
        
        // 运行客户端示例
        clientSyncExample();
        clientAsyncExample();
        healthCheckExample();
        
        logger.info("所有示例执行完成");
    }
}
