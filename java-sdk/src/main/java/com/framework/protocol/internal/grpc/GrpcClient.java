package com.framework.protocol.internal.grpc;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.proto.ServiceProto.*;
import com.framework.proto.FrameworkServiceGrpc;
import com.framework.proto.CommonProto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * gRPC 客户端实现
 * 提供同步、异步和流式调用功能
 */
public class GrpcClient {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcClient.class);
    
    private final String host;
    private final int port;
    private ManagedChannel channel;
    private FrameworkServiceGrpc.FrameworkServiceBlockingStub blockingStub;
    private FrameworkServiceGrpc.FrameworkServiceStub asyncStub;
    private volatile boolean started;
    
    public GrpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.started = false;
    }
    
    /**
     * 启动 gRPC 客户端
     */
    public void start() {
        if (started) {
            logger.warn("gRPC 客户端已经启动");
            return;
        }
        
        logger.info("启动 gRPC 客户端: {}:{}", host, port);
        
        try {
            // 创建 gRPC 通道
            channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext() // 开发环境使用明文，生产环境应使用 TLS
                    .build();
            
            // 创建存根
            blockingStub = FrameworkServiceGrpc.newBlockingStub(channel);
            asyncStub = FrameworkServiceGrpc.newStub(channel);
            
            started = true;
            logger.info("gRPC 客户端启动成功");
            
        } catch (Exception e) {
            logger.error("gRPC 客户端启动失败", e);
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                                        "gRPC 客户端启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 关闭 gRPC 客户端
     */
    public void shutdown() {
        if (!started) {
            logger.warn("gRPC 客户端未启动");
            return;
        }
        
        logger.info("关闭 gRPC 客户端");
        
        try {
            if (channel != null) {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
            
            started = false;
            logger.info("gRPC 客户端已关闭");
            
        } catch (InterruptedException e) {
            logger.error("关闭 gRPC 客户端时被中断", e);
            Thread.currentThread().interrupt();
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "关闭 gRPC 客户端失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 同步调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param payload 请求负载
     * @param headers 请求头
     * @param timeout 超时时间（毫秒）
     * @return 响应负载
     */
    public byte[] call(String service, String method, byte[] payload, 
                      Map<String, String> headers, long timeout) {
        validateState();
        
        logger.debug("gRPC 同步调用: service={}, method={}", service, method);
        
        try {
            // 构建请求
            CallRequest.Builder requestBuilder = CallRequest.newBuilder()
                    .setService(service)
                    .setMethod(method)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(payload))
                    .setTimeout(timeout);
            
            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.addHeaders(MessageHeader.newBuilder()
                            .setKey(entry.getKey())
                            .setValue(entry.getValue())
                            .build());
                }
            }
            
            // 发送请求
            CallResponse response = blockingStub.call(requestBuilder.build());
            
            // 检查错误
            if (response.hasError()) {
                FrameworkError error = response.getError();
                throw new FrameworkException(
                        convertErrorCode(error.getCode()),
                        error.getMessage()
                );
            }
            
            // 返回响应负载
            return response.getPayload().toByteArray();
            
        } catch (FrameworkException e) {
            logger.error("gRPC 调用失败: service={}, method={}", service, method, e);
            throw e;
        } catch (Exception e) {
            logger.error("gRPC 调用异常: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "gRPC 调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 异步调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param payload 请求负载
     * @param headers 请求头
     * @param timeout 超时时间（毫秒）
     * @return 响应负载的 CompletableFuture
     */
    public CompletableFuture<byte[]> callAsync(String service, String method, byte[] payload,
                                               Map<String, String> headers, long timeout) {
        validateState();
        
        logger.debug("gRPC 异步调用: service={}, method={}", service, method);
        
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        try {
            // 构建请求
            CallRequest.Builder requestBuilder = CallRequest.newBuilder()
                    .setService(service)
                    .setMethod(method)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(payload))
                    .setTimeout(timeout);
            
            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.addHeaders(MessageHeader.newBuilder()
                            .setKey(entry.getKey())
                            .setValue(entry.getValue())
                            .build());
                }
            }
            
            // 发送异步请求
            asyncStub.call(requestBuilder.build(), new StreamObserver<CallResponse>() {
                @Override
                public void onNext(CallResponse response) {
                    // 检查错误
                    if (response.hasError()) {
                        FrameworkError error = response.getError();
                        future.completeExceptionally(new FrameworkException(
                                convertErrorCode(error.getCode()),
                                error.getMessage()
                        ));
                    } else {
                        // 返回响应负载
                        future.complete(response.getPayload().toByteArray());
                    }
                }
                
                @Override
                public void onError(Throwable t) {
                    logger.error("gRPC 异步调用失败: service={}, method={}", service, method, t);
                    future.completeExceptionally(new FrameworkException(
                            ErrorCode.INTERNAL_ERROR,
                            "gRPC 异步调用失败: " + t.getMessage(),
                            t
                    ));
                }
                
                @Override
                public void onCompleted() {
                    // 请求完成
                }
            });
            
        } catch (Exception e) {
            logger.error("gRPC 异步调用异常: service={}, method={}", service, method, e);
            future.completeExceptionally(new FrameworkException(
                    ErrorCode.INTERNAL_ERROR,
                    "gRPC 异步调用失败: " + e.getMessage(),
                    e
            ));
        }
        
        return future;
    }
    
    /**
     * 流式调用服务
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param payload 请求负载
     * @param headers 请求头
     * @param timeout 超时时间（毫秒）
     * @return 响应数据流
     */
    public Stream<byte[]> stream(String service, String method, byte[] payload,
                                Map<String, String> headers, long timeout) {
        validateState();
        
        logger.debug("gRPC 流式调用: service={}, method={}", service, method);
        
        try {
            // 构建请求
            CallRequest.Builder requestBuilder = CallRequest.newBuilder()
                    .setService(service)
                    .setMethod(method)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(payload))
                    .setTimeout(timeout);
            
            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.addHeaders(MessageHeader.newBuilder()
                            .setKey(entry.getKey())
                            .setValue(entry.getValue())
                            .build());
                }
            }
            
            // 发送流式请求
            List<byte[]> results = new ArrayList<>();
            
            blockingStub.stream(requestBuilder.build()).forEachRemaining(streamData -> {
                results.add(streamData.getData().toByteArray());
            });
            
            return results.stream();
            
        } catch (Exception e) {
            logger.error("gRPC 流式调用异常: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "gRPC 流式调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 健康检查
     * 
     * @return 健康状态
     */
    public boolean healthCheck() {
        validateState();
        
        try {
            HealthCheckResponse request = HealthCheckResponse.newBuilder()
                    .setStatus(HealthStatus.HEALTH_STATUS_UNSPECIFIED)
                    .build();
            
            HealthCheckResponse response = blockingStub.healthCheck(request);
            
            return response.getStatus() == HealthStatus.HEALTHY;
            
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            return false;
        }
    }
    
    /**
     * 验证客户端状态
     */
    private void validateState() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "gRPC 客户端未启动");
        }
    }
    
    /**
     * 转换错误码
     */
    private ErrorCode convertErrorCode(com.framework.proto.CommonProto.ErrorCode protoErrorCode) {
        return switch (protoErrorCode.getNumber()) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 408 -> ErrorCode.TIMEOUT;
            case 500 -> ErrorCode.INTERNAL_ERROR;
            case 501 -> ErrorCode.NOT_IMPLEMENTED;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            case 600 -> ErrorCode.PROTOCOL_ERROR;
            case 601 -> ErrorCode.SERIALIZATION_ERROR;
            case 602 -> ErrorCode.ROUTING_ERROR;
            case 603 -> ErrorCode.CONNECTION_ERROR;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
