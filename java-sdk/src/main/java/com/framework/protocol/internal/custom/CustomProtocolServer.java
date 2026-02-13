package com.framework.protocol.internal.custom;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.proto.CustomProtocolProto.*;
import com.google.protobuf.ByteString;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 自定义二进制协议服务端
 * 
 * 使用 Netty 实现高性能的异步网络服务
 * 
 * **验证需求: 3.3**
 */
public class CustomProtocolServer {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolServer.class);
    
    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final Map<String, ServiceHandler> registeredServices;
    private volatile boolean started;
    
    public CustomProtocolServer(int port) {
        this.port = port;
        this.registeredServices = new ConcurrentHashMap<>();
        this.started = false;
    }
    
    /**
     * 启动服务端
     */
    public void start() throws IOException {
        if (started) {
            logger.warn("自定义协议服务端已经启动");
            return;
        }
        
        logger.info("启动自定义协议服务端: port={}", port);
        
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 添加编解码器
                            pipeline.addLast("decoder", new CustomProtocolDecoder());
                            pipeline.addLast("encoder", new CustomProtocolEncoder());
                            
                            // 添加业务处理器
                            pipeline.addLast("handler", new CustomProtocolServerHandler());
                        }
                    });
            
            // 绑定端口
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            started = true;
            logger.info("自定义协议服务端启动成功，监听端口: {}", port);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("JVM 关闭，停止自定义协议服务端");
                try {
                    CustomProtocolServer.this.shutdown();
                } catch (InterruptedException e) {
                    logger.error("关闭自定义协议服务端时被中断", e);
                    Thread.currentThread().interrupt();
                }
            }));
            
        } catch (Exception e) {
            logger.error("自定义协议服务端启动失败", e);
            try {
                shutdown();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("服务端启动失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 阻塞等待服务端关闭
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }
    
    /**
     * 关闭服务端
     */
    public void shutdown() throws InterruptedException {
        if (!started) {
            logger.warn("自定义协议服务端未启动");
            return;
        }
        
        logger.info("关闭自定义协议服务端");
        
        try {
            // 关闭服务器通道
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            
            // 关闭事件循环组
            if (bossGroup != null) {
                bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            }
            
            started = false;
            logger.info("自定义协议服务端已关闭");
            
        } catch (InterruptedException e) {
            logger.error("关闭自定义协议服务端时被中断", e);
            Thread.currentThread().interrupt();
            throw e;
        }
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
        
        logger.info("注册自定义协议服务: {}", serviceName);
        registeredServices.put(serviceName, handler);
    }
    
    /**
     * 注销服务
     * 
     * @param serviceName 服务名称
     */
    public void unregisterService(String serviceName) {
        logger.info("注销自定义协议服务: {}", serviceName);
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
         * @param data 请求数据
         * @param metadata 元数据
         * @return 响应数据
         */
        byte[] handle(String method, byte[] data, Map<String, String> metadata);
    }
    
    /**
     * 服务端处理器
     */
    private class CustomProtocolServerHandler extends SimpleChannelInboundHandler<CustomFrame> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, CustomFrame frame) {
            logger.debug("收到请求帧: type={}, sequence={}", 
                        frame.getHeader().getType(), 
                        frame.getHeader().getSequence());
            
            try {
                CustomFrame response = handleFrame(frame);
                ctx.writeAndFlush(response);
                
            } catch (Exception e) {
                logger.error("处理请求失败", e);
                
                // 发送错误响应
                CustomFrame errorResponse = createErrorFrame(
                        frame.getHeader().getSequence(),
                        ErrorCode.INTERNAL_ERROR,
                        "处理请求失败: " + e.getMessage()
                );
                ctx.writeAndFlush(errorResponse);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.error("服务端处理器异常", cause);
            ctx.close();
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            logger.info("客户端连接: {}", ctx.channel().remoteAddress());
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            logger.info("客户端断开: {}", ctx.channel().remoteAddress());
        }
        
        /**
         * 处理帧
         */
        private CustomFrame handleFrame(CustomFrame frame) throws Exception {
            FrameType type = frame.getHeader().getType();
            
            return switch (type) {
                case DATA -> handleDataFrame(frame);
                case PING -> handlePingFrame(frame);
                case CLOSE -> handleCloseFrame(frame);
                default -> createErrorFrame(
                        frame.getHeader().getSequence(),
                        ErrorCode.NOT_IMPLEMENTED,
                        "不支持的帧类型: " + type
                );
            };
        }
        
        /**
         * 处理数据帧
         */
        private CustomFrame handleDataFrame(CustomFrame frame) throws Exception {
            // 解析数据帧体
            DataFrame dataFrame = DataFrame.parseFrom(frame.getBody());
            
            String service = dataFrame.getService();
            String method = dataFrame.getMethod();
            byte[] data = dataFrame.getData().toByteArray();
            Map<String, String> metadata = dataFrame.getMetadataMap();
            
            logger.debug("处理数据帧: service={}, method={}", service, method);
            
            // 查找服务处理器
            ServiceHandler handler = registeredServices.get(service);
            
            if (handler == null) {
                return createErrorFrame(
                        frame.getHeader().getSequence(),
                        ErrorCode.NOT_FOUND,
                        "服务未找到: " + service
                );
            }
            
            try {
                // 调用服务处理器
                byte[] result = handler.handle(method, data, metadata);
                
                // 构建响应数据帧
                DataFrame responseDataFrame = DataFrame.newBuilder()
                        .setService(service)
                        .setMethod(method)
                        .setData(ByteString.copyFrom(result))
                        .putAllMetadata(metadata)
                        .build();
                
                // 构建响应帧头
                FrameHeader responseHeader = FrameHeader.newBuilder()
                        .setMagic(0x46524D57)
                        .setVersion(1)
                        .setType(FrameType.DATA)
                        .setFlags(0)
                        .setStreamId(frame.getHeader().getStreamId())
                        .setBodyLength(responseDataFrame.getSerializedSize())
                        .setSequence(frame.getHeader().getSequence())
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                
                // 构建响应帧
                return CustomFrame.newBuilder()
                        .setHeader(responseHeader)
                        .setBody(responseDataFrame.toByteString())
                        .build();
                
            } catch (FrameworkException e) {
                return createErrorFrame(
                        frame.getHeader().getSequence(),
                        e.getErrorCode(),
                        e.getMessage()
                );
            } catch (Exception e) {
                return createErrorFrame(
                        frame.getHeader().getSequence(),
                        ErrorCode.INTERNAL_ERROR,
                        "处理失败: " + e.getMessage()
                );
            }
        }
        
        /**
         * 处理 Ping 帧
         */
        private CustomFrame handlePingFrame(CustomFrame frame) throws Exception {
            logger.debug("处理 Ping 帧");
            
            // 解析 Ping 帧体
            PingFrame pingFrame = PingFrame.parseFrom(frame.getBody());
            
            // 构建 Pong 帧体（回显 Ping 数据）
            PingFrame pongFrame = PingFrame.newBuilder()
                    .setData(pingFrame.getData())
                    .build();
            
            // 构建 Pong 帧头
            FrameHeader pongHeader = FrameHeader.newBuilder()
                    .setMagic(0x46524D57)
                    .setVersion(1)
                    .setType(FrameType.PONG)
                    .setFlags(0)
                    .setStreamId(frame.getHeader().getStreamId())
                    .setBodyLength(pongFrame.getSerializedSize())
                    .setSequence(frame.getHeader().getSequence())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            // 构建 Pong 帧
            return CustomFrame.newBuilder()
                    .setHeader(pongHeader)
                    .setBody(pongFrame.toByteString())
                    .build();
        }
        
        /**
         * 处理关闭帧
         */
        private CustomFrame handleCloseFrame(CustomFrame frame) {
            logger.debug("处理关闭帧");
            
            // 构建关闭确认帧
            FrameHeader closeHeader = FrameHeader.newBuilder()
                    .setMagic(0x46524D57)
                    .setVersion(1)
                    .setType(FrameType.CLOSE)
                    .setFlags(0)
                    .setStreamId(frame.getHeader().getStreamId())
                    .setBodyLength(0)
                    .setSequence(frame.getHeader().getSequence())
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            return CustomFrame.newBuilder()
                    .setHeader(closeHeader)
                    .build();
        }
        
        /**
         * 创建错误帧
         */
        private CustomFrame createErrorFrame(long sequence, ErrorCode errorCode, String message) {
            logger.debug("创建错误帧: code={}, message={}", errorCode, message);
            
            // 构建错误帧体
            ErrorFrame errorFrame = ErrorFrame.newBuilder()
                    .setCode(convertErrorCode(errorCode))
                    .setMessage(message)
                    .setStreamId(0)
                    .build();
            
            // 构建错误帧头
            FrameHeader errorHeader = FrameHeader.newBuilder()
                    .setMagic(0x46524D57)
                    .setVersion(1)
                    .setType(FrameType.ERROR)
                    .setFlags(0)
                    .setStreamId(0)
                    .setBodyLength(errorFrame.getSerializedSize())
                    .setSequence(sequence)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            
            // 构建错误帧
            return CustomFrame.newBuilder()
                    .setHeader(errorHeader)
                    .setBody(errorFrame.toByteString())
                    .build();
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
