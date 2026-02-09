package com.framework.protocol.internal.custom;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.proto.CustomProtocolProto.*;
import com.google.protobuf.ByteString;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义二进制协议客户端
 * 
 * 使用 Netty 实现高性能的异步网络通信
 * 
 * **验证需求: 3.3**
 */
public class CustomProtocolClient {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolClient.class);
    
    private final String host;
    private final int port;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private volatile boolean started;
    
    // 序列号生成器
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    
    // 待处理的请求（序列号 -> CompletableFuture）
    private final Map<Long, CompletableFuture<CustomFrame>> pendingRequests = new ConcurrentHashMap<>();
    
    public CustomProtocolClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.started = false;
    }
    
    /**
     * 启动客户端
     */
    public void start() {
        if (started) {
            logger.warn("自定义协议客户端已经启动");
            return;
        }
        
        logger.info("启动自定义协议客户端: {}:{}", host, port);
        
        try {
            workerGroup = new NioEventLoopGroup();
            
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 添加编解码器
                            pipeline.addLast("decoder", new CustomProtocolDecoder());
                            pipeline.addLast("encoder", new CustomProtocolEncoder());
                            
                            // 添加业务处理器
                            pipeline.addLast("handler", new CustomProtocolClientHandler());
                        }
                    });
            
            // 连接服务器
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            
            started = true;
            logger.info("自定义协议客户端启动成功");
            
        } catch (Exception e) {
            logger.error("自定义协议客户端启动失败", e);
            shutdown();
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, 
                                        "客户端启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 关闭客户端
     */
    public void shutdown() {
        if (!started) {
            logger.warn("自定义协议客户端未启动");
            return;
        }
        
        logger.info("关闭自定义协议客户端");
        
        try {
            // 关闭通道
            if (channel != null) {
                channel.close().sync();
            }
            
            // 关闭事件循环组
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            }
            
            // 取消所有待处理的请求
            for (CompletableFuture<CustomFrame> future : pendingRequests.values()) {
                future.completeExceptionally(new FrameworkException(
                        ErrorCode.CONNECTION_ERROR,
                        "客户端已关闭"
                ));
            }
            pendingRequests.clear();
            
            started = false;
            logger.info("自定义协议客户端已关闭");
            
        } catch (InterruptedException e) {
            logger.error("关闭自定义协议客户端时被中断", e);
            Thread.currentThread().interrupt();
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "关闭客户端失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 发送数据帧（同步）
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param data 数据
     * @param metadata 元数据
     * @param timeout 超时时间（毫秒）
     * @return 响应帧
     */
    public CustomFrame call(String service, String method, byte[] data, 
                           Map<String, String> metadata, long timeout) {
        try {
            return callAsync(service, method, data, metadata, timeout)
                    .get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("同步调用失败: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 发送数据帧（异步）
     * 
     * @param service 服务名称
     * @param method 方法名称
     * @param data 数据
     * @param metadata 元数据
     * @param timeout 超时时间（毫秒）
     * @return 响应帧的 CompletableFuture
     */
    public CompletableFuture<CustomFrame> callAsync(String service, String method, byte[] data,
                                                    Map<String, String> metadata, long timeout) {
        validateState();
        
        logger.debug("异步调用: service={}, method={}", service, method);
        
        CompletableFuture<CustomFrame> future = new CompletableFuture<>();
        
        try {
            // 生成序列号
            long sequence = sequenceGenerator.incrementAndGet();
            
            // 构建数据帧体
            DataFrame.Builder dataFrameBuilder = DataFrame.newBuilder()
                    .setService(service)
                    .setMethod(method)
                    .setData(ByteString.copyFrom(data));
            
            // 添加元数据
            if (metadata != null) {
                dataFrameBuilder.putAllMetadata(metadata);
            }
            
            DataFrame dataFrame = dataFrameBuilder.build();
            
            // 构建帧头
            FrameHeader header = FrameHeader.newBuilder()
                    .setMagic(0x46524D57) // "FRMW"
                    .setVersion(1)
                    .setType(FrameType.DATA)
                    .setFlags(0)
                    .setStreamId(0) // 简单实现，使用流 ID 0
                    .setBodyLength(dataFrame.getSerializedSize())
                    .setSequence(sequence)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            // 构建完整帧
            CustomFrame frame = CustomFrame.newBuilder()
                    .setHeader(header)
                    .setBody(dataFrame.toByteString())
                    .build();
            
            // 注册待处理的请求
            pendingRequests.put(sequence, future);
            
            // 发送帧
            channel.writeAndFlush(frame).addListener((ChannelFutureListener) channelFuture -> {
                if (!channelFuture.isSuccess()) {
                    logger.error("发送帧失败: sequence={}", sequence, channelFuture.cause());
                    pendingRequests.remove(sequence);
                    future.completeExceptionally(new FrameworkException(
                            ErrorCode.CONNECTION_ERROR,
                            "发送失败: " + channelFuture.cause().getMessage(),
                            channelFuture.cause()
                    ));
                } else {
                    logger.debug("帧发送成功: sequence={}", sequence);
                }
            });
            
            // 设置超时
            workerGroup.schedule(() -> {
                if (!future.isDone()) {
                    pendingRequests.remove(sequence);
                    future.completeExceptionally(new FrameworkException(
                            ErrorCode.TIMEOUT,
                            "请求超时"
                    ));
                }
            }, timeout, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            logger.error("异步调用失败: service={}, method={}", service, method, e);
            future.completeExceptionally(new FrameworkException(
                    ErrorCode.INTERNAL_ERROR,
                    "调用失败: " + e.getMessage(),
                    e
            ));
        }
        
        return future;
    }
    
    /**
     * 发送 Ping 帧
     * 
     * @return Pong 帧的 CompletableFuture
     */
    public CompletableFuture<CustomFrame> ping() {
        validateState();
        
        logger.debug("发送 Ping 帧");
        
        CompletableFuture<CustomFrame> future = new CompletableFuture<>();
        
        try {
            // 生成序列号
            long sequence = sequenceGenerator.incrementAndGet();
            
            // 构建 Ping 帧体
            PingFrame pingFrame = PingFrame.newBuilder()
                    .setData(System.currentTimeMillis())
                    .build();
            
            // 构建帧头
            FrameHeader header = FrameHeader.newBuilder()
                    .setMagic(0x46524D57)
                    .setVersion(1)
                    .setType(FrameType.PING)
                    .setFlags(0)
                    .setStreamId(0)
                    .setBodyLength(pingFrame.getSerializedSize())
                    .setSequence(sequence)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            // 构建完整帧
            CustomFrame frame = CustomFrame.newBuilder()
                    .setHeader(header)
                    .setBody(pingFrame.toByteString())
                    .build();
            
            // 注册待处理的请求
            pendingRequests.put(sequence, future);
            
            // 发送帧
            channel.writeAndFlush(frame);
            
            // 设置超时（5 秒）
            workerGroup.schedule(() -> {
                if (!future.isDone()) {
                    pendingRequests.remove(sequence);
                    future.completeExceptionally(new FrameworkException(
                            ErrorCode.TIMEOUT,
                            "Ping 超时"
                    ));
                }
            }, 5, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.error("发送 Ping 失败", e);
            future.completeExceptionally(new FrameworkException(
                    ErrorCode.INTERNAL_ERROR,
                    "Ping 失败: " + e.getMessage(),
                    e
            ));
        }
        
        return future;
    }
    
    /**
     * 验证客户端状态
     */
    private void validateState() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "客户端未启动");
        }
        
        if (channel == null || !channel.isActive()) {
            throw new FrameworkException(ErrorCode.CONNECTION_ERROR, "连接未建立");
        }
    }
    
    /**
     * 客户端处理器
     */
    private class CustomProtocolClientHandler extends SimpleChannelInboundHandler<CustomFrame> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, CustomFrame frame) {
            logger.debug("收到响应帧: type={}, sequence={}", 
                        frame.getHeader().getType(), 
                        frame.getHeader().getSequence());
            
            long sequence = frame.getHeader().getSequence();
            CompletableFuture<CustomFrame> future = pendingRequests.remove(sequence);
            
            if (future != null) {
                // 检查是否是错误帧
                if (frame.getHeader().getType() == FrameType.ERROR) {
                    try {
                        ErrorFrame errorFrame = ErrorFrame.parseFrom(frame.getBody());
                        future.completeExceptionally(new FrameworkException(
                                convertErrorCode(errorFrame.getCode()),
                                errorFrame.getMessage()
                        ));
                    } catch (Exception e) {
                        future.completeExceptionally(new FrameworkException(
                                ErrorCode.PROTOCOL_ERROR,
                                "解析错误帧失败: " + e.getMessage(),
                                e
                        ));
                    }
                } else {
                    future.complete(frame);
                }
            } else {
                logger.warn("收到未知序列号的响应: sequence={}", sequence);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("客户端处理器异常", cause);
            ctx.close();
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.warn("连接已断开");
            
            // 取消所有待处理的请求
            for (CompletableFuture<CustomFrame> future : pendingRequests.values()) {
                future.completeExceptionally(new FrameworkException(
                        ErrorCode.CONNECTION_ERROR,
                        "连接已断开"
                ));
            }
            pendingRequests.clear();
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
