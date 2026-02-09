package com.framework.protocol.internal.custom;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.proto.CustomProtocolProto.*;
import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.zip.CRC32;

/**
 * 自定义二进制协议解码器
 * 
 * 将二进制数据解码为 CustomFrame 对象
 * 
 * **验证需求: 3.3**
 */
public class CustomProtocolDecoder extends ByteToMessageDecoder {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolDecoder.class);
    
    // 协议魔数 "FRMW"
    private static final int MAGIC_NUMBER = 0x46524D57;
    
    // 帧头固定长度（字节）
    private static final int HEADER_LENGTH = 40;
    
    // 帧尾长度（CRC32 校验和）
    private static final int TRAILER_LENGTH = 4;
    
    // 最小帧长度
    private static final int MIN_FRAME_LENGTH = HEADER_LENGTH + TRAILER_LENGTH;
    
    // 最大帧体长度（16MB）
    private static final int MAX_BODY_LENGTH = 16 * 1024 * 1024;
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 检查是否有足够的数据读取帧头
        if (in.readableBytes() < MIN_FRAME_LENGTH) {
            logger.trace("数据不足，等待更多数据: available={}", in.readableBytes());
            return;
        }
        
        // 标记读索引，以便在解码失败时回滚
        in.markReaderIndex();
        
        try {
            // 读取并验证魔数
            int magic = in.readInt();
            if (magic != MAGIC_NUMBER) {
                logger.error("无效的协议魔数: expected=0x{}, actual=0x{}", 
                           Integer.toHexString(MAGIC_NUMBER), 
                           Integer.toHexString(magic));
                throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, 
                                            "无效的协议魔数");
            }
            
            // 读取协议版本
            int version = in.readInt();
            
            // 读取帧类型
            int typeNumber = in.readInt();
            FrameType type = FrameType.forNumber(typeNumber);
            if (type == null || type == FrameType.FRAME_TYPE_UNSPECIFIED) {
                logger.error("无效的帧类型: {}", typeNumber);
                throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, 
                                            "无效的帧类型: " + typeNumber);
            }
            
            // 读取帧标志
            int flags = in.readInt();
            
            // 读取流 ID
            int streamId = in.readInt();
            
            // 读取帧体长度
            int bodyLength = in.readInt();
            
            // 验证帧体长度
            if (bodyLength < 0 || bodyLength > MAX_BODY_LENGTH) {
                logger.error("无效的帧体长度: {}", bodyLength);
                throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, 
                                            "无效的帧体长度: " + bodyLength);
            }
            
            // 读取序列号
            long sequence = in.readLong();
            
            // 读取时间戳
            long timestamp = in.readLong();
            
            // 检查是否有足够的数据读取帧体和校验和
            int totalLength = HEADER_LENGTH + bodyLength + TRAILER_LENGTH;
            if (in.readableBytes() < bodyLength + TRAILER_LENGTH) {
                logger.trace("数据不足，等待更多数据: required={}, available={}", 
                           bodyLength + TRAILER_LENGTH, in.readableBytes());
                // 回滚读索引
                in.resetReaderIndex();
                return;
            }
            
            // 读取帧体
            byte[] bodyBytes = new byte[bodyLength];
            in.readBytes(bodyBytes);
            
            // 读取校验和
            int checksum = in.readInt();
            
            // 验证校验和
            int calculatedChecksum = calculateChecksum(bodyBytes);
            if (checksum != calculatedChecksum) {
                logger.error("校验和不匹配: expected=0x{}, actual=0x{}", 
                           Integer.toHexString(checksum), 
                           Integer.toHexString(calculatedChecksum));
                throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, 
                                            "校验和不匹配");
            }
            
            // 构建帧头
            FrameHeader header = FrameHeader.newBuilder()
                    .setMagic(magic)
                    .setVersion(version)
                    .setType(type)
                    .setFlags(flags)
                    .setStreamId(streamId)
                    .setBodyLength(bodyLength)
                    .setSequence(sequence)
                    .setTimestamp(timestamp)
                    .build();
            
            // 构建帧尾
            FrameTrailer trailer = FrameTrailer.newBuilder()
                    .setChecksum(checksum)
                    .build();
            
            // 构建完整帧
            CustomFrame frame = CustomFrame.newBuilder()
                    .setHeader(header)
                    .setBody(ByteString.copyFrom(bodyBytes))
                    .setTrailer(trailer)
                    .build();
            
            logger.debug("解码自定义协议帧成功: type={}, streamId={}, bodyLength={}", 
                        type, streamId, bodyLength);
            
            // 添加到输出列表
            out.add(frame);
            
        } catch (FrameworkException e) {
            // 回滚读索引
            in.resetReaderIndex();
            throw e;
        } catch (Exception e) {
            logger.error("解码自定义协议帧失败", e);
            // 回滚读索引
            in.resetReaderIndex();
            throw new FrameworkException(ErrorCode.PROTOCOL_ERROR, 
                                        "解码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 计算 CRC32 校验和
     * 
     * @param data 数据
     * @return CRC32 校验和
     */
    private int calculateChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }
}
