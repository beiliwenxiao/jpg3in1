package com.framework.serialization.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.google.protobuf.util.JsonFormat;
import com.framework.serialization.SerializationException;
import com.framework.serialization.SerializationFormat;
import com.framework.serialization.Serializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol Buffers 序列化器实现
 * 
 * 基于 protobuf-java 库实现 Protocol Buffers 格式的序列化和反序列化
 * 
 * 需求: 3.4
 */
public class ProtobufSerializer implements Serializer {
    
    // 缓存 Parser 实例以提高性能
    private final Map<Class<?>, Parser<?>> parserCache = new ConcurrentHashMap<>();
    
    // JSON 格式化器，用于 Protobuf 和 JSON 之间的转换
    private final JsonFormat.Printer jsonPrinter;
    private final JsonFormat.Parser jsonParser;
    
    /**
     * 使用默认配置创建 Protobuf 序列化器
     */
    public ProtobufSerializer() {
        this.jsonPrinter = JsonFormat.printer()
                .omittingInsignificantWhitespace()
                .preservingProtoFieldNames();
        this.jsonParser = JsonFormat.parser()
                .ignoringUnknownFields();
    }
    
    @Override
    public byte[] serialize(Object data) throws SerializationException {
        if (data == null) {
            return new byte[0];
        }
        
        if (!(data instanceof Message)) {
            throw new SerializationException(
                "Object must be a Protocol Buffers Message, but was: " + data.getClass().getName(),
                SerializationFormat.PROTOBUF,
                data.getClass()
            );
        }
        
        return ((Message) data).toByteArray();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        
        if (!Message.class.isAssignableFrom(targetType)) {
            throw new SerializationException(
                "Target type must be a Protocol Buffers Message, but was: " + targetType.getName(),
                SerializationFormat.PROTOBUF,
                targetType
            );
        }
        
        try {
            Parser<?> parser = getParser(targetType);
            return (T) parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException(
                "Failed to deserialize Protobuf to " + targetType.getName() + ": " + e.getMessage(),
                SerializationFormat.PROTOBUF,
                targetType,
                e
            );
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] data, Type targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        
        if (targetType instanceof Class) {
            return deserialize(data, (Class<T>) targetType);
        }
        
        throw new SerializationException(
            "Protobuf serializer only supports Class types, not: " + targetType.getTypeName(),
            SerializationFormat.PROTOBUF
        );
    }
    
    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.PROTOBUF;
    }
    
    @Override
    public Set<SerializationFormat> getSupportedFormats() {
        return Collections.singleton(SerializationFormat.PROTOBUF);
    }
    
    @Override
    public boolean supportsType(Class<?> type) {
        return Message.class.isAssignableFrom(type);
    }
    
    /**
     * 获取指定类型的 Parser
     * 
     * @param type Protobuf 消息类型
     * @return Parser 实例
     * @throws SerializationException 如果无法获取 Parser
     */
    @SuppressWarnings("unchecked")
    private <T> Parser<T> getParser(Class<T> type) throws SerializationException {
        return (Parser<T>) parserCache.computeIfAbsent(type, this::createParser);
    }
    
    /**
     * 创建指定类型的 Parser
     * 
     * @param type Protobuf 消息类型
     * @return Parser 实例
     */
    private Parser<?> createParser(Class<?> type) {
        try {
            // 获取 parser() 静态方法
            Method parserMethod = type.getMethod("parser");
            return (Parser<?>) parserMethod.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(
                "Failed to get parser for type: " + type.getName(), e
            );
        }
    }
    
    /**
     * 将 Protobuf 消息转换为 JSON 字符串
     * 
     * @param message Protobuf 消息
     * @return JSON 字符串
     * @throws SerializationException 转换失败时抛出
     */
    public String toJson(Message message) throws SerializationException {
        if (message == null) {
            return "null";
        }
        
        try {
            return jsonPrinter.print(message);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException(
                "Failed to convert Protobuf to JSON: " + e.getMessage(),
                SerializationFormat.PROTOBUF,
                message.getClass(),
                e
            );
        }
    }
    
    /**
     * 从 JSON 字符串解析 Protobuf 消息
     * 
     * @param json JSON 字符串
     * @param builder Protobuf 消息构建器
     * @param <T> 消息类型
     * @return 解析后的 Protobuf 消息
     * @throws SerializationException 解析失败时抛出
     */
    @SuppressWarnings("unchecked")
    public <T extends Message> T fromJson(String json, Message.Builder builder) throws SerializationException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            jsonParser.merge(json, builder);
            return (T) builder.build();
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException(
                "Failed to parse JSON to Protobuf: " + e.getMessage(),
                SerializationFormat.PROTOBUF,
                null,
                e
            );
        }
    }
    
    /**
     * 合并两个 Protobuf 消息
     * 
     * @param target 目标消息构建器
     * @param source 源消息
     * @param <T> 消息类型
     * @return 合并后的消息
     */
    @SuppressWarnings("unchecked")
    public <T extends Message> T merge(Message.Builder target, Message source) {
        target.mergeFrom(source);
        return (T) target.build();
    }
    
    /**
     * 检查消息是否已初始化（所有必需字段都已设置）
     * 
     * @param message Protobuf 消息
     * @return 如果已初始化返回 true
     */
    public boolean isInitialized(Message message) {
        return message != null && message.isInitialized();
    }
    
    /**
     * 获取消息的序列化大小
     * 
     * @param message Protobuf 消息
     * @return 序列化后的字节大小
     */
    public int getSerializedSize(Message message) {
        if (message == null) {
            return 0;
        }
        return message.getSerializedSize();
    }
}
