package com.framework.protocol.internal.custom;

import com.framework.proto.CustomProtocolProto.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.zip.CRC32;

/**
 * 自定义二进制协议编码器
 * 
 * 将 CustomFrame 对象编码为二进制数据
 * 协议格式：
 * - 魔数（4 字节）：0x46524D57 ("FRMW")
 * - 版本（4 字节）
 * - 帧类型（4 字节）
 * - 帧标志（4 字节）
 * - 流 ID（4 字节）
 * - 帧体长度（4 字节）
 * - 序列号（8 字节）
 * - 时间戳（8 字节）
 * - 帧体（变长）
 * - CRC32 校验和（4 字节）
 * 
 * **验证需求: 3.3**
 */
public class CustomProtocolEncoder extends MessageToByteEncoder<CustomFrame> {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomProtocolEncoder.class);
    
    // 协议魔数 "FRMW"
    private static final int MAGIC_NUMBER = 0x46524D57;
    
    // 协议版本
    private static final int PROTOCOL_VERSION = 1;
    
    // 帧头固定长度（字节）
    private static final int HEADER_LENGTH = 40;
    
    @Override
    protected void encode(ChannelHandlerContext ctx, CustomFrame frame, ByteBuf out) throws Exception {
        logger.debug("编码自定义协议帧: type={}, streamId={}", 
                    frame.getHeader().getType(), 
                    frame.getHeader().getStreamId());
        
        try {
            // 获取帧体数据
            byte[] bodyBytes = frame.getBody().toByteArray();
            
            // 写入帧头
            FrameHeader header = frame.getHeader();
            
            // 魔数
            out.writeInt(MAGIC_NUMBER);
            
            // 版本
            out.writeInt(PROTOCOL_VERSION);
            
            // 帧类型
            out.writeInt(header.getType().getNumber());
            
            // 帧标志
            out.writeInt(header.getFlags());
            
            // 流 ID
            out.writeInt(header.getStreamId());
            
            // 帧体长度
            out.writeInt(bodyBytes.length);
            
            // 序列号
            out.writeLong(header.getSequence());
            
            // 时间戳
            out.writeLong(header.getTimestamp());
            
            // 写入帧体
            out.writeBytes(bodyBytes);
            
            // 计算并写入 CRC32 校验和
            if (frame.hasTrailer()) {
                out.writeInt(frame.getTrailer().getChecksum());
            } else {
                // 计算校验和
                int checksum = calculateChecksum(out, HEADER_LENGTH, bodyBytes.length);
                out.writeInt(checksum);
            }
            
            logger.debug("自定义协议帧编码完成: totalLength={}", out.readableBytes());
            
        } catch (Exception e) {
            logger.error("编码自定义协议帧失败", e);
            throw e;
        }
    }
    
    /**
     * 计算 CRC32 校验和
     * 
     * @param buf 缓冲区
     * @param offset 起始偏移
     * @param length 数据长度
     * @return CRC32 校验和
     */
    private int calculateChecksum(ByteBuf buf, int offset, int length) {
        CRC32 crc32 = new CRC32();
        
        // 保存当前读索引
        int readerIndex = buf.readerIndex();
        
        // 设置读索引到起始位置
        buf.readerIndex(offset);
        
        // 读取数据并计算校验和
        byte[] data = new byte[length];
        buf.readBytes(data);
        crc32.update(data);
        
        // 恢复读索引
        buf.readerIndex(readerIndex);
        
        return (int) crc32.getValue();
    }
}
