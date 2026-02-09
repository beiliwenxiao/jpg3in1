package com.framework.protocol.internal.grpc;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.proto.ServiceProto.*;
import com.framework.proto.FrameworkServiceGrpc;
import com.framework.proto.CommonProto.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 服务端实现
 * 提供服务注册和请求处理功能
 */
public class GrpcServer {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);
    
    private final int port;
    private Server server;
    private final Map<String, ServiceHandler> registeredServices;
    private volatile boolean started;
    
    public GrpcServer(int port) {
        this.port = port;
        this.registeredServices = new ConcurrentHashMap<>();
        this.started = false;
    }
    
    /**
     * 启动 gRPC 服务端
     */
    public void start() throws IOException {
        if (started) {
            logger.warn("gRPC 服务端已经启动");
            return;
        }
        
        logger.info("启动 gRPC 服务端: port={}", port);
        
        try {
            // 创建 gRPC 服务器
            server = ServerBuilder.forPort(port)
                    .addService(new FrameworkServiceImpl())
                    .build()
                    .start();
            
            started = true;
            logger.info("gRPC 服务端启动成功，监听端口: {}", port);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("JVM 关闭，停止 gRPC 服务端");
                try {
                    GrpcServer.this.shutdown();
                } catch (InterruptedException e) {
                    logger.error("关闭 gRPC 服务端时被中断", e);
                    Thread.currentThread().interrupt();
                }
            }));
            
        } catch (IOException e) {
            logger.error("gRPC 服务端启动失败", e);
            throw e;
        }
    }
    
    /**
     * 阻塞等待服务端关闭
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    /**
     * 关闭 gRPC 服务端
     */
    public void shutdown() throws InterruptedException {
        if (!started) {
            logger.warn("gRPC 服务端未启动");
            return;
        }
        
        logger.info("关闭 gRPC 服务端");
        
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
        
        started = false;
        logger.info("gRPC 服务端已关闭");
    }
    
    /**
     * 注册服务处理器
     * 
     * @param serviceName 服务名称
     * @param handler 服务处理器
     */
    public void registerService(String serviceName, ServiceHandler handler) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }
        
        if (handler == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务处理器不能为空");
        }
        
        logger.info("注册 gRPC 服务: {}", serviceName);
        registeredServices.put(serviceName, handler);
    }
    
    /**
     * 注销服务
     * 
     * @param serviceName 服务名称
     */
    public void unregisterService(String serviceName) {
        logger.info("注销 gRPC 服务: {}", serviceName);
        registeredServices.remove(serviceName);
    }
    
    /**
     * 服务处理器接口
     */
    public interface ServiceHandler {
        /**
         * 处理服务调用
         * 
         * @param method 方法名称
         * @param payload 请求负载
         * @param headers 请求头
         * @return 响应负载
         */
        byte[] handle(String method, byte[] payload, Map<String, String> headers);
        
        /**
         * 处理流式调用（可选）
         * 
         * @param method 方法名称
         * @param payload 请求负载
         * @param headers 请求头
         * @param observer 流观察者
         */
        default void handleStream(String method, byte[] payload, 
                                 Map<String, String> headers,
                                 StreamObserver<byte[]> observer) {
            throw new FrameworkException(ErrorCode.NOT_IMPLEMENTED, 
                                        "流式调用未实现");
        }
    }
    
    /**
     * gRPC 服务实现
     */
    private class FrameworkServiceImpl extends FrameworkServiceGrpc.FrameworkServiceImplBase {
        
        @Override
        public void call(CallRequest request, StreamObserver<CallResponse> responseObserver) {
            String service = request.getService();
            String method = request.getMethod();
            
            logger.debug("收到 gRPC 调用: service={}, method={}", service, method);
            
            try {
                // 查找服务处理器
                ServiceHandler handler = registeredServices.get(service);
                
                if (handler == null) {
                    // 服务未找到
                    CallResponse response = CallResponse.newBuilder()
                            .setError(FrameworkError.newBuilder()
                                    .setCode(com.framework.proto.CommonProto.ErrorCode.NOT_FOUND)
                                    .setMessage("服务未找到: " + service)
                                    .setTimestamp(System.currentTimeMillis())
                                    .build())
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                    return;
                }
                
                // 提取请求头
                Map<String, String> headers = new ConcurrentHashMap<>();
                for (MessageHeader header : request.getHeadersList()) {
                    headers.put(header.getKey(), header.getValue());
                }
                
                // 调用服务处理器
                byte[] payload = request.getPayload().toByteArray();
                byte[] result = handler.handle(method, payload, headers);
                
                // 构建响应
                CallResponse.Builder responseBuilder = CallResponse.newBuilder()
                        .setPayload(com.google.protobuf.ByteString.copyFrom(result));
                
                // 添加响应头（如果有）
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    responseBuilder.addHeaders(MessageHeader.newBuilder()
                            .setKey(entry.getKey())
                            .setValue(entry.getValue())
                            .build());
                }
                
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
                
            } catch (FrameworkException e) {
                logger.error("gRPC 调用失败: service={}, method={}", service, method, e);
                
                // 返回错误响应
                CallResponse response = CallResponse.newBuilder()
                        .setError(FrameworkError.newBuilder()
                                .setCode(convertErrorCode(e.getErrorCode()))
                                .setMessage(e.getMessage())
                                .setTimestamp(System.currentTimeMillis())
                                .build())
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                logger.error("gRPC 调用异常: service={}, method={}", service, method, e);
                
                // 返回内部错误
                CallResponse response = CallResponse.newBuilder()
                        .setError(FrameworkError.newBuilder()
                                .setCode(com.framework.proto.CommonProto.ErrorCode.INTERNAL_ERROR)
                                .setMessage("内部错误: " + e.getMessage())
                                .setTimestamp(System.currentTimeMillis())
                                .build())
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        }
        
        @Override
        public void stream(CallRequest request, StreamObserver<StreamData> responseObserver) {
            String service = request.getService();
            String method = request.getMethod();
            
            logger.debug("收到 gRPC 流式调用: service={}, method={}", service, method);
            
            try {
                // 查找服务处理器
                ServiceHandler handler = registeredServices.get(service);
                
                if (handler == null) {
                    responseObserver.onError(new FrameworkException(
                            ErrorCode.NOT_FOUND,
                            "服务未找到: " + service
                    ));
                    return;
                }
                
                // 提取请求头
                Map<String, String> headers = new ConcurrentHashMap<>();
                for (MessageHeader header : request.getHeadersList()) {
                    headers.put(header.getKey(), header.getValue());
                }
                
                // 调用服务处理器的流式方法
                byte[] payload = request.getPayload().toByteArray();
                
                handler.handleStream(method, payload, headers, new StreamObserver<byte[]>() {
                    @Override
                    public void onNext(byte[] data) {
                        responseObserver.onNext(StreamData.newBuilder()
                                .setData(com.google.protobuf.ByteString.copyFrom(data))
                                .setEndOfStream(false)
                                .build());
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        logger.error("流式调用错误: service={}, method={}", service, method, t);
                        responseObserver.onError(t);
                    }
                    
                    @Override
                    public void onCompleted() {
                        responseObserver.onNext(StreamData.newBuilder()
                                .setEndOfStream(true)
                                .build());
                        responseObserver.onCompleted();
                    }
                });
                
            } catch (Exception e) {
                logger.error("gRPC 流式调用异常: service={}, method={}", service, method, e);
                responseObserver.onError(e);
            }
        }
        
        @Override
        public StreamObserver<CallRequest> biStream(StreamObserver<CallResponse> responseObserver) {
            logger.debug("收到 gRPC 双向流调用");
            
            return new StreamObserver<CallRequest>() {
                @Override
                public void onNext(CallRequest request) {
                    String service = request.getService();
                    String method = request.getMethod();
                    
                    try {
                        // 查找服务处理器
                        ServiceHandler handler = registeredServices.get(service);
                        
                        if (handler == null) {
                            CallResponse response = CallResponse.newBuilder()
                                    .setError(FrameworkError.newBuilder()
                                            .setCode(com.framework.proto.CommonProto.ErrorCode.NOT_FOUND)
                                            .setMessage("服务未找到: " + service)
                                            .setTimestamp(System.currentTimeMillis())
                                            .build())
                                    .build();
                            
                            responseObserver.onNext(response);
                            return;
                        }
                        
                        // 提取请求头
                        Map<String, String> headers = new ConcurrentHashMap<>();
                        for (MessageHeader header : request.getHeadersList()) {
                            headers.put(header.getKey(), header.getValue());
                        }
                        
                        // 调用服务处理器
                        byte[] payload = request.getPayload().toByteArray();
                        byte[] result = handler.handle(method, payload, headers);
                        
                        // 发送响应
                        CallResponse response = CallResponse.newBuilder()
                                .setPayload(com.google.protobuf.ByteString.copyFrom(result))
                                .build();
                        
                        responseObserver.onNext(response);
                        
                    } catch (Exception e) {
                        logger.error("双向流调用异常: service={}, method={}", service, method, e);
                        
                        CallResponse response = CallResponse.newBuilder()
                                .setError(FrameworkError.newBuilder()
                                        .setCode(com.framework.proto.CommonProto.ErrorCode.INTERNAL_ERROR)
                                        .setMessage("内部错误: " + e.getMessage())
                                        .setTimestamp(System.currentTimeMillis())
                                        .build())
                                .build();
                        
                        responseObserver.onNext(response);
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    logger.error("双向流调用错误", t);
                }
                
                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
        
        @Override
        public void healthCheck(HealthCheckResponse request, 
                               StreamObserver<HealthCheckResponse> responseObserver) {
            logger.debug("收到健康检查请求");
            
            // 返回健康状态
            HealthCheckResponse response = HealthCheckResponse.newBuilder()
                    .setStatus(HealthStatus.HEALTHY)
                    .setMessage("gRPC 服务端运行正常")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        /**
         * 转换错误码
         */
        private com.framework.proto.CommonProto.ErrorCode convertErrorCode(ErrorCode errorCode) {
            return switch (errorCode) {
                case BAD_REQUEST -> com.framework.proto.CommonProto.ErrorCode.BAD_REQUEST;
                case UNAUTHORIZED -> com.framework.proto.CommonProto.ErrorCode.UNAUTHORIZED;
                case FORBIDDEN -> com.framework.proto.CommonProto.ErrorCode.FORBIDDEN;
                case NOT_FOUND -> com.framework.proto.CommonProto.ErrorCode.NOT_FOUND;
                case TIMEOUT -> com.framework.proto.CommonProto.ErrorCode.TIMEOUT;
                case INTERNAL_ERROR -> com.framework.proto.CommonProto.ErrorCode.INTERNAL_ERROR;
                case NOT_IMPLEMENTED -> com.framework.proto.CommonProto.ErrorCode.NOT_IMPLEMENTED;
                case SERVICE_UNAVAILABLE -> com.framework.proto.CommonProto.ErrorCode.SERVICE_UNAVAILABLE;
                case PROTOCOL_ERROR -> com.framework.proto.CommonProto.ErrorCode.PROTOCOL_ERROR;
                case SERIALIZATION_ERROR -> com.framework.proto.CommonProto.ErrorCode.SERIALIZATION_ERROR;
                case ROUTING_ERROR -> com.framework.proto.CommonProto.ErrorCode.ROUTING_ERROR;
                case CONNECTION_ERROR -> com.framework.proto.CommonProto.ErrorCode.CONNECTION_ERROR;
            };
        }
    }
}
